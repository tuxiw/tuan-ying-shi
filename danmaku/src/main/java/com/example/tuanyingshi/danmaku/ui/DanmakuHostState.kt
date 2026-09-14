package com.example.tuanyingshi.danmaku.ui

import android.util.Log
import androidx.annotation.UiThread
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tuanyingshi.danmaku.api.DanmakuLocation
import com.example.tuanyingshi.danmaku.api.DanmakuPresentation
import kotlin.math.floor


@Composable
fun rememberDanmakuHostState(
    danmakuConfig: DanmakuConfig = DanmakuConfig.Default,
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium,
): DanmakuHostState {
    val density = LocalDensity.current
    val danmakuTextMeasurer = rememberTextMeasurer(200)
    // 以 config 为 key：用户在弹幕设置页调整任何参数都会触发 State 重建，让
    // trackHeight / 轨道数 / enableTop 等都按最新配置生效（重建期间丢失当前
    // 显示中的弹幕是可接受的，因为用户改设置是低频操作）。
    return remember(danmakuConfig) {
        DanmakuHostState(danmakuConfig, density, baseStyle, danmakuTextMeasurer)
    }
}

class DanmakuHostState(
    val config: DanmakuConfig = DanmakuConfig.Default,
    val density: Density,
    val baseStyle: TextStyle,
    val danmakuTextMeasurer: TextMeasurer,
    val trackStubMeasurer: TextMeasurer = danmakuTextMeasurer,
) {
    // 注意：尺寸用普通 var（非快照状态）。在 onSizeChanged（layout 阶段）写入时不会触发
    // 重组回退；轨道重建交由 LaunchedEffect（布局稳定后）执行，避免弹幕缩在“中间”。
    internal var hostWidth: Int = 0
    internal var hostHeight: Int = 0
    internal var trackWidth: Int = 0
    internal var diagFrame: Int = 0

    /**
     * 所有在 [floatingTracks], [topTracks] 和 [bottomTracks] 弹幕.
     */
    internal val presentFloatingDanmaku = mutableStateListOf<FloatingDanmaku<StyledDanmaku>>()
    internal val presentFixedDanmaku = mutableListOf<FixedDanmaku<StyledDanmaku>>()

    // 弹幕轨道
    internal val floatingTracks = mutableListOf<FloatingDanmakuTrack<StyledDanmaku>>()
    internal val topTracks = mutableListOf<FixedDanmakuTrack<StyledDanmaku>>()
    internal val bottomTracks = mutableListOf<FixedDanmakuTrack<StyledDanmaku>>()

    /* 计时器，用于计算弹幕在屏幕上的[FloatingDanmaku]滚动距离和[FixedDanmaku]停留时间 */
    internal var elapsedFrameTimeNanos: Long = 0L
    internal val isDebug by mutableStateOf(config.isDebug)
    internal var paused by mutableStateOf(false)

    /**
     * 当前视频播放倍速（来自 ExoPlayer.playbackParameters.speed）。
     * 跟随倍速开启时（[DanmakuConfig.followPlaybackSpeed]），所有滚动弹幕的实际位移
     * 速度 = [FloatingDanmaku.speedPxPerSecond] * [playbackSpeed]，1.5x 播放时弹幕同步加速。
     * PlayerScreen 通过监听 [com.example.tuanyingshi.util.PlayerSpeed] 变化实时回写。
     */
    var playbackSpeed: Float = 1f

    /**
     * 去重窗口：最近出现过的弹幕文本（大小写不敏感）。
     * 容量上限 200 条；超出时一次性清空（避免内存无限增长 + 简单重置）。
     * 与 [DanmakuConfig.dedupEnabled] 联动：关闭时不参与 trySend 判定。
     */
    internal val recentDanmakuTexts: ArrayDeque<String> = ArrayDeque()

    internal val trackHeight by lazy {
        val dummyDanmaku = dummyDanmaku(
            trackStubMeasurer,
            baseStyle,
            config.style,
            "Lorem Ipsum"
        )
        val verticalPadding = with(density) {
            (config.danmakuTrackProperties.verticalPadding * 2).dp.toPx()
        }
        // 行高倍数：把字号撑高后再算轨道高度，让上下间距随倍数放大（密度相应降低）。
        val lineHeight = dummyDanmaku.danmakuHeight * config.danmakuTrackProperties.lineHeightMultiplier
        (lineHeight + verticalPadding).toInt()
    }

    /**
     * 关键词屏蔽：文本包含任一关键词（大小写不敏感）即丢弃。空列表短路返回 true。
     * 由调用方按需传入「当前生效的关键词」以支持运行时切换。
     */
    private fun matchesKeyword(text: String): Boolean {
        if (config.keywordFilter.isEmpty()) return false
        val lower = text.lowercase()
        return config.keywordFilter.any { it.isNotBlank() && lower.contains(it.lowercase()) }
    }

    /**
     * 在开启去重的前提下，命中重复文本则返回 true（应跳过此条弹幕）。
     * 同时把新接收的文本压入窗口；超容时丢弃最旧的整批。
     */
    private fun isDuplicateAndRemember(text: String): Boolean {
        if (!config.dedupEnabled) return false
        val key = text.lowercase()
        if (recentDanmakuTexts.contains(key)) return true
        recentDanmakuTexts.addLast(key)
        if (recentDanmakuTexts.size > 200) {
            // 简单重置：丢弃最旧的 100 条，避免线性膨胀。
            repeat(100) { if (recentDanmakuTexts.isNotEmpty()) recentDanmakuTexts.removeFirst() }
        }
        return false
    }

    /**
     * 尝试发送弹幕到屏幕, 如果当前时间点已没有更多轨道可以使用则会发送失败.
     *
     * 对于一定发送成功的版本, 请查看 [DanmakuHostState.send].
     * 若是浮动弹幕则加入到 [presentFloatingDanmaku], 固定弹幕加到 [presentFixedDanmaku].
     *
     * @return 如果发送成功则返回 true
     * @see DanmakuHostState.send
     */
    fun trySend(danmaku: DanmakuPresentation): Boolean = trySend(danmaku, 0L)

    /**
     * [trySend] 的内部实现，额外接受一个「已滚动/已显示时长」[elapsedMillis]。
     *
     * 普通发送传 0（从屏幕右缘开始滚）；seek 后的重新装填传该弹幕相对当前进度的过去时长，
     * 让它直接出现在此刻本应处于的位置上，避免「一坨弹幕同时从右边缘涌出」。
     *
     * @return 如果发送成功则返回 true
     */
    internal fun trySend(danmaku: DanmakuPresentation, elapsedMillis: Long): Boolean {
        // 关键词屏蔽 + 去重前置过滤；任一命中则丢弃该条弹幕。
        if (matchesKeyword(danmaku.danmaku.text)) return false
        if (isDuplicateAndRemember(danmaku.danmaku.text)) return false

        val styledDanmaku = StyledDanmaku(
            presentation = danmaku,
            measurer = danmakuTextMeasurer,
            baseStyle = baseStyle,
            style = config.style,
            enableColor = config.enableColor,
            isDebug = config.isDebug,
        )
        return when (danmaku.danmaku.location) {
            DanmakuLocation.NORMAL -> {
                val placed = floatingTracks.firstNotNullOfOrNull {
                    it.tryPlaceProgressed(styledDanmaku, elapsedMillis)
                }
                if (placed != null) {
                    presentFloatingDanmaku.add(placed)
                    true
                } else if (config.allowOverlap && elapsedMillis == 0L) {
                    // 海量弹幕：常规放不下时，选最近已消失位置最靠右的轨道强制放置（叠加渲染）。
                    // 重新装填（elapsedMillis > 0）时不做叠加兜底，否则又会出现整屏弹幕糊在一起。
                    val bestTrack = floatingTracks.minByOrNull { track ->
                        track.danmakuList.lastOrNull()?.screenPosX ?: Float.MAX_VALUE
                    }
                    val placedOverlap = bestTrack?.let { it.place(styledDanmaku) }
                    if (placedOverlap != null) {
                        presentFloatingDanmaku.add(placedOverlap)
                        true
                    } else false
                } else false
            }

            DanmakuLocation.TOP -> {
                val fixedDanmaku = topTracks.firstNotNullOfOrNull {
                    it.tryPlaceProgressed(styledDanmaku, elapsedMillis)
                }
                fixedDanmaku?.also(presentFixedDanmaku::add) != null
            }

            DanmakuLocation.BOTTOM -> {
                val fixedDanmaku = bottomTracks.firstNotNullOfOrNull {
                    it.tryPlaceProgressed(styledDanmaku, elapsedMillis)
                }
                fixedDanmaku?.also(presentFixedDanmaku::add) != null
            }
        }
    }

    /**
     * 逻辑帧 tick, 主要用于移除超出屏幕外或超过时间的弹幕
     */
    @UiThread
    fun tick() {
        floatingTracks.forEach { it.tick() }
        topTracks.forEach { it.tick() }
        bottomTracks.forEach { it.tick() }
    }

    /**
     * 在每一帧中调用，主要用于更新浮动弹幕的位置。
     * 该方法为一个协程循环，确保弹幕的位置根据时间的变化得到更新。
     */
    internal suspend fun interpolateFrameLoop() {
        var lastFrameTimeNanos = withFrameNanos { it }

        while (true) {
            withFrameNanos { currentFrameTimeNanos ->
                val delta = currentFrameTimeNanos - lastFrameTimeNanos

                elapsedFrameTimeNanos += delta
                lastFrameTimeNanos = currentFrameTimeNanos

                // 更新浮动弹幕的位置
                // 跟随倍速：把视频播放倍速叠加到弹幕位移速度上（关闭时 playbackSpeed=1f 等价原行为）。
                val speedScale = if (config.followPlaybackSpeed) playbackSpeed else 1f
                for (danmaku in presentFloatingDanmaku) {
                    val time = (elapsedFrameTimeNanos - danmaku.placeTimeNanos) / 1_000_000_000f
                    val x = time * danmaku.speedPxPerSecond * speedScale // 已行驶的距离
                    danmaku.updatePosX(danmaku.placePosition - x)
                }
            }
        }
    }

    /**
     * 设置弹幕轨道的数量。根据Host高度和配置中的显示区域比例，计算出轨道数量并初始化。
     *
     * 旋转屏幕 / 窗口尺寸变化时（Activity 声明了 configChanges 不会重建 Compose 树）：
     * 旧轨道实例的 trackWidth / hostHeight 在构造时已固化，[setTrackCountImpl] 在 `size == count`
     * 时直接 return、count 变化也只动尾部，已显示的 FloatingDanmaku / FixedDanmaku 仍按旧尺寸算 X/Y，
     * 视觉上"挤在新视频区中央"。检测到尺寸变化时清空已显示弹幕并强制重建所有轨道，让新轨道用新尺寸。
     */
    private var lastSizedTrackWidth = 0
    private var lastSizedHostHeight = 0

    internal fun setTrackCount() {
        val sizeChanged = lastSizedTrackWidth != hostWidth || lastSizedHostHeight != hostHeight
        trackWidth = hostWidth
        val trackCount = floor(hostHeight / trackHeight * config.displayArea)
            .coerceAtLeast(1f)
            .toInt()
        Log.d("DanmakuSizing", "setTrackCount hostW=$hostWidth hostH=$hostHeight trackH=$trackHeight area=${config.displayArea} sizeChanged=$sizeChanged -> count=$trackCount")
        if (sizeChanged) {
            // 旋转 / 窗口尺寸变化：清空屏幕并强制重建所有轨道，避免旧弹幕按旧尺寸停留在新视频区中央。
            clearPresentDanmaku()
            floatingTracks.clear()
            topTracks.clear()
            bottomTracks.clear()
        }
        initTrackCount(trackCount, config)
        lastSizedTrackWidth = hostWidth
        lastSizedHostHeight = hostHeight
    }

    /**
     * 更新弹幕轨道数量, 同时也会更新轨道属性
     */
    @UiThread
    private fun initTrackCount(count: Int, config: DanmakuConfig) {
        val newFloatingTrackSpeed =
            with(density) { this@DanmakuHostState.config.baseSpeed.dp.toPx() }
        val newFloatingTrackSafeSeparation =
            with(density) { this@DanmakuHostState.config.safeSeparation.toPx() }

        floatingTracks.setTrackCountImpl(if (config.enableFloating) count else 0) { index ->
            FloatingDanmakuTrack(
                trackIndex = index,
                elapsedFrameTimeNanos = { elapsedFrameTimeNanos },
                trackHeight = trackHeight,
                trackWidth = trackWidth,
                density = density,
                baseSpeedPxPerSecond = newFloatingTrackSpeed,
                safeSeparation = newFloatingTrackSafeSeparation,
                // speedMultiplier = floatingSpeedMultiplierState,
                onRemoveDanmaku = { removed ->
                    presentFloatingDanmaku.removeFirst { it.danmaku == removed.danmaku }
                },
            )
        }
        topTracks.setTrackCountImpl(if (config.enableTop) count else 0) { index ->
            FixedDanmakuTrack(
                trackIndex = index,
                elapsedFrameTimeNanos = { elapsedFrameTimeNanos },
                trackHeight = trackHeight,
                trackWidth = trackWidth,
                hostHeight = hostHeight,
                fromBottom = false,
                durationMillis = config.danmakuTrackProperties.fixedDanmakuPresentDuration,
                onRemoveDanmaku = { removed -> presentFixedDanmaku.removeFirst { it.danmaku == removed.danmaku } },
            )
        }
        bottomTracks.setTrackCountImpl(if (config.enableBottom) count else 0) { index ->
            FixedDanmakuTrack(
                trackIndex = index,
                elapsedFrameTimeNanos = { elapsedFrameTimeNanos },
                trackHeight = trackHeight,
                trackWidth = trackWidth,
                hostHeight = hostHeight,
                fromBottom = true,
                durationMillis = config.danmakuTrackProperties.fixedDanmakuPresentDuration,
                onRemoveDanmaku = { removed -> presentFixedDanmaku.removeFirst { it.danmaku == removed.danmaku } },
            )
        }
    }

    /**
     * 清除当前显示的所有弹幕。
     */
    @UiThread
    fun clearPresentDanmaku() {
        floatingTracks.forEach { it.clearAll() }
        topTracks.forEach { it.clearAll() }
        bottomTracks.forEach { it.clearAll() }

        check(presentFloatingDanmaku.size == 0) {
            "presentFloatingDanmaku is not totally cleared after releasing track."
        }
        check(presentFixedDanmaku.size == 0) {
            "presentFixedDanmaku is not totally cleared after releasing track."
        }
    }

    /**
     * 清空屏幕并以这些弹幕填充. 常见于快进/快退时.
     *
     * 关键点：**按每条弹幕自己的播放时间把它放回「此刻本应处于」的位置**，而不是让它从屏幕右缘
     * 重新开始滚动。否则整个窗口（默认 10 秒）内的弹幕会在同一帧从同一条竖线上涌出、叠成一坨，
     * 也就是「拖动进度条后弹幕糊满屏幕」的现象。
     *
     * @param list 由引擎给出，按播放时间升序（由旧到新）。
     * @param playTimeMillis 当前播放器的时间
     */
    suspend fun repopulate(
        list: List<DanmakuPresentation> = emptyList(),
        playTimeMillis: Long = 0L
    ) {
        clearPresentDanmaku()
        if (list.isEmpty()) return

        for (presentation in list) {
            val elapsedMillis = playTimeMillis - presentation.danmaku.playTimeMillis
            if (elapsedMillis < 0L) continue // 还没到播放时间，交给后续正常的 Add 流程
            // 已滚出屏幕 / 已过显示时长的弹幕会在轨道内部被丢弃（返回 false），不占位、不显示。
            trySend(presentation, elapsedMillis)
        }
    }

    /**
     * Sends the danmaku to the screen, guaranteeing its placement on a track without collisions.
     * Todo: 已知Bug，发送的弹幕会被后面的弹幕撞击，导致重叠
     *
     * @param danmaku The danmaku to be sent to the screen.
     */
    suspend fun send(danmaku: DanmakuPresentation) {
        if (trySend(danmaku)) return
        val styledDanmaku = StyledDanmaku(
            presentation = danmaku,
            measurer = danmakuTextMeasurer,
            baseStyle = baseStyle,
            style = config.style,
            enableColor = config.enableColor,
            isDebug = config.isDebug
        )

        // Randomly select a track from floatingTracks
        val selectedTrack = floatingTracks.random()

        // Mark the track as unavailable
        selectedTrack.forbided = true

        // Get the last FloatingDanmaku from the selected track
        val last = selectedTrack.danmakuList.lastOrNull() ?: run {
            selectedTrack.place(styledDanmaku).let(presentFloatingDanmaku::add)
            selectedTrack.forbided = false
            return
        }
        // Calculate the remaining distance for the last danmaku to fully enter the screen
        val safeSeparation = 16.dp.toPx(density)

        // Place the new danmaku
        val sendDanmaku = selectedTrack.place(styledDanmaku)

        if (last.speedPxPerSecond < sendDanmaku.speedPxPerSecond) {
            // Calculate the exit time for the last danmaku
            val exitDistance = last.screenPosX + last.danmaku.danmakuWidth + safeSeparation
            val exitTime = exitDistance / last.speedPxPerSecond

            // Calculate how far the new danmaku will move during the last's exit time
            val distance = sendDanmaku.speedPxPerSecond * exitTime

            // Set the new danmaku's position to avoid overtaking the last one
            sendDanmaku.placePosition = distance.coerceAtLeast(trackWidth.toFloat())
        } else {
            // Calculate the remaining distance for the last danmaku to fully enter the screen
            val remainingDistance = last.danmaku.danmakuWidth - last.distanceX
            // Place the new danmaku directly behind the last one
            sendDanmaku.placePosition += remainingDistance + safeSeparation
        }
        presentFloatingDanmaku.add(sendDanmaku)
        selectedTrack.forbided = false
    }

    fun play() {
        paused = false
    }

    fun pause() {
        paused = true
    }
}

private fun <D : SizeSpecifiedDanmaku, DT, T : DanmakuTrack<D, DT>>
        MutableList<T>.setTrackCountImpl(count: Int, newInstance: (index: Int) -> T) {
    when {
        size == count -> return
        // 清除 track 的同时要把 track 里的 danmaku 也要清除
        count < size -> repeat(size - count) { removeAt(lastIndex).clearAll() }
        else -> addAll(List(count - size) { newInstance(size + it) })
    }
}

private inline fun <T> MutableList<T>.removeFirst(predicate: (T) -> Boolean): T? {
    val index = indexOfFirst(predicate)
    if (index == -1) return null
    return removeAt(index)
}

private fun Dp.toPx(density: Density): Float {
    return with(density) { toPx() }
}