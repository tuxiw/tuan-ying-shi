package com.lanlinju.videoplayer

import android.content.res.Configuration
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lanlinju.videoplayer.enhancement.VideoEnhancementMode
import com.lanlinju.videoplayer.icons.Fullscreen
import com.lanlinju.videoplayer.icons.FullscreenExit
import com.lanlinju.videoplayer.component.Slider
import com.lanlinju.videoplayer.icons.Subtitles
import com.lanlinju.videoplayer.icons.SubtitlesOff
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** 进度帧预览取帧防抖（毫秒）：停止拖动多久后才真正去解码一帧。 */
/** 进度帧预览的分桶粒度（毫秒）：与 VideoPlayerState.loadFramePreview 内部一致，按秒取帧与缓存。 */
private const val PREVIEW_BUCKET_MS = 1000L

/** 松手后气泡保留时长（毫秒）：立刻消失会让用户根本看不到取到的帧。 */
private const val PREVIEW_HIDE_DELAY_MS = 260L

/**
 * 播放器控件层。在 animeko 风格基础上重构：顶部栏新增「画质增强（Anime4K 超分）」下拉、
 * 截图、播放信息层开关；拖动进度条/手势快进时显示进度帧预览；原有倍速 / 画面 / 选集 / 弹幕 / 下一集保留。
 */
@Composable
fun VideoPlayerControl(
    state: VideoPlayerState,
    title: String,
    subtitle: String? = null,
    background: Color = Color.Black.copy(0.2f),
    contentColor: Color = Color.LightGray,
    progressLineColor: Color = MaterialTheme.colorScheme.inversePrimary,
    danmakuEnabled: Boolean,
    onBackClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onDanmakuClick: (Boolean) -> Unit = {},
    optionsContent: (@Composable () -> Unit)? = null,
    screenshotEnabled: Boolean = true,
    framePreviewEnabled: Boolean = true,
    statsEnabled: Boolean = true,
    /** 快速打开播放设置的入口；为 null 时不显示该按钮（宿主未接入导航时不显示）。 */
    onSettingsClick: (() -> Unit)? = null,
    /** 「更多」面板的全部宿主能力（小窗 / 投屏 / 稍后再看 / 各类开关）。回调为 null 即不显示。 */
    moreConfig: PlayerMoreConfig = PlayerMoreConfig(),
) {
    // 进度帧预览：拖动时异步取真实帧；取不到（源不支持 / 走代理拿不到）时降级为仅显示时间。
    // previewPositionMs 是「松手后仍短暂保留」的位置副本，纯粹为了让气泡不要闪没。
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewPositionMs by remember { mutableStateOf<Long?>(null) }
    if (framePreviewEnabled) {
        val scrubPositionMs = state.scrubPreviewPositionMs.value
        // 节流而非防抖：以「秒桶」为 key（与 loadFramePreview 的分桶一致）。
        // 之前用 scrubPositionMs 做 key + delay 防抖，拖动时位置每帧都变 → 每次都重启并放弃，
        // 结果只有手指停住 140ms 才可能取到帧，而一松手气泡又立刻消失 —— 表现为「帧预览从不出现」。
        // 按秒分桶后：同一秒内不重启任务，跨秒才重启（并复用 1 秒粒度的缓存），拖动即可实时出图。
        val bucket = scrubPositionMs?.let { it / PREVIEW_BUCKET_MS }
        LaunchedEffect(bucket) {
            if (scrubPositionMs == null || bucket == null) {
                // 松手后延迟收起，让用户看清最后取到的那一帧
                delay(PREVIEW_HIDE_DELAY_MS)
                if (state.scrubPreviewPositionMs.value == null) {
                    previewPositionMs = null
                    previewBitmap = null
                }
                return@LaunchedEffect
            }
            val bucketStart = bucket * PREVIEW_BUCKET_MS
            val bitmap = state.loadFramePreview(bucketStart)
            // 只有仍停留在同一个桶才落地，避免过期的慢结果覆盖当前帧
            if (state.scrubPreviewPositionMs.value?.let { it / PREVIEW_BUCKET_MS } == bucket) {
                previewBitmap = bitmap
                previewPositionMs = scrubPositionMs
            }
        }
    }

    // 「更多」面板是右侧竖栏，要贴播放器右缘，不能再套控制栏的横向安全边距；
    // 同时它自带不透明底，画面在左侧继续可见，因此此时不再叠控制层的压暗层。
    val moreVisible = state.isMoreUiVisible.value

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (moreVisible) Color.Transparent else background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding(),
                        end = horizontalPadding(),
                        top = 18.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ControlHeader(
                    modifier = Modifier.fillMaxWidth(),
                    state = state,
                    title = title,
                    subtitle = subtitle,
                    isSeeking = state.isSeeking.value,
                    onBackClick = onBackClick,
                    screenshotEnabled = screenshotEnabled,
                    statsEnabled = statsEnabled,
                    optionsContent = optionsContent,
                    onSettingsClick = onSettingsClick,
                    onMoreClick = { state.showMoreUi() },
                )
                Spacer(Modifier.size(1.dp))

                BottomControlBar(
                    modifier = Modifier.fillMaxWidth(),
                    progressLineColor = progressLineColor,
                    state = state,
                    enabledDanmaku = danmakuEnabled,
                onNextClick = onNextClick,
                onDanmakuClick = onDanmakuClick,
                // 没有选集数据（本地 / 外链播放页）就不显示「选集」入口：
                // 点开「更多」也只是空面板，不如直接不摆出来。
                showEpisodeShortcut = moreConfig.episodes.isNotEmpty(),
            )
            }

            // 「更多」面板：倍速 / 画面尺寸 / 选集 等选项都收在这里（右侧竖栏 / 底部抽屉），
            // 不再在播放器中间弹居中面板 —— 中间面板会大面积盖住画面，且在平板分栏下位置很怪。
            if (state.isMoreUiVisible.value) {
                PlayerMorePanel(
                    state = state,
                    config = moreConfig,
                    onClose = state::hideMoreUi,
                )
            }

            // 进度帧预览：拖动进度条或手势快进时显示目标帧 + 时间。
            // 拖动中用实时位置，松手后短暂沿用最后一帧的位置（previewPositionMs），避免气泡闪没。
            if (framePreviewEnabled) {
                val positionMs = state.scrubPreviewPositionMs.value ?: previewPositionMs
                if (positionMs != null) {
                    FramePreviewTooltip(
                        positionMs = positionMs,
                        durationMs = state.videoDurationMs.value,
                        preview = previewBitmap,
                    )
                }
            }
        }
    }
}

/** 顶部栏：返回 + 标题 + 画质增强下拉 / 截图 / 播放信息 + 宿主自定义入口。 */
@Composable
private fun ControlHeader(
    modifier: Modifier = Modifier,
    state: VideoPlayerState,
    title: String,
    subtitle: String?,
    isSeeking: Boolean,
    onBackClick: (() -> Unit)?,
    screenshotEnabled: Boolean = true,
    statsEnabled: Boolean = true,
    optionsContent: (@Composable () -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    onMoreClick: () -> Unit = {},
) {
    if (isSeeking) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            modifier = Modifier.size(BigIconButtonSize),
            onClick = { onBackClick?.invoke() }
        ) {
            Icon(imageVector = Icons.Rounded.ArrowBackIos, contentDescription = null)
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                text = title,
                color = LocalContentColor.current,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            subtitle?.let {
                Text(
                    text = it,
                    color = LocalContentColor.current.copy(0.80f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 右侧操作区：画质增强 / 截图 / 播放信息 / 宿主自定义入口 / 设置，统一间距避免拥挤
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 画质增强（Anime4K 超分）下拉
            EnhancementDropdown(state = state)
            // 截图
            if (screenshotEnabled) ScreenshotButton(state = state)
            // 播放信息层开关
            if (statsEnabled) StatsButton(state = state)

            optionsContent?.invoke()

            // 快速打开播放设置（由宿主提供导航；未接入时不显示）
            if (onSettingsClick != null) {
                AdaptiveIconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(SmallIconButtonSize)
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "播放设置")
                }
            }

            // 「更多」：小窗播放 / 投屏 / 定时关闭 / 色彩辅助（不再往顶栏堆图标）
            // 定时关闭生效时图标高亮，用户不用打开面板也能看出「定时器正在跑」。
            val sleepDeadline by SleepTimer.deadlineElapsedMs.collectAsState()
            AdaptiveIconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(SmallIconButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多",
                    tint = if (sleepDeadline != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current
                    },
                )
            }
        }
    }
}

/** 画质增强模式下拉：关闭 / 性能（复原）/ 质量（复原 + 2x 超分）。 */
@Composable
private fun EnhancementDropdown(state: VideoPlayerState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AdaptiveIconButton(onClick = { expanded = true }, modifier = Modifier.size(SmallIconButtonSize)) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = "画质增强")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val modes = listOf(
                "关闭" to VideoEnhancementMode.OFF,
                "性能（复原）" to VideoEnhancementMode.PERFORMANCE,
                "质量（复原+超分）" to VideoEnhancementMode.QUALITY,
            )
            modes.forEach { (label, mode) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        state.setEnhancementMode(mode)
                        expanded = false
                    },
                    trailingIcon = {
                        if (state.enhancementMode.value == mode) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

/** 截图：抓取当前帧并保存。 */
@Composable
private fun ScreenshotButton(state: VideoPlayerState) {
    val scope = rememberCoroutineScope()
    AdaptiveIconButton(
        onClick = { scope.launch { state.screenshot() } },
        modifier = Modifier.size(SmallIconButtonSize)
    ) {
        Icon(Icons.Rounded.PhotoCamera, contentDescription = "截图")
    }
}

/** 播放信息层开关。 */
@Composable
private fun StatsButton(state: VideoPlayerState) {
    AdaptiveIconButton(
        onClick = { state.setShowStats(!state.showStats.value) },
        modifier = Modifier.size(SmallIconButtonSize)
    ) {
        Icon(Icons.Rounded.Info, contentDescription = "播放信息")
    }
}

/**
 * 进度帧预览：拖动进度条 / 手势快进时显示目标位置的真实画面 + 时间。
 *
 * [preview] 为 null 表示尚未取到帧或该源不支持取帧（远程源走 App 内代理时常见），
 * 此时**不能留一个空白占位框**，直接只显示时间，避免看起来像坏了。
 */
@Composable
private fun FramePreviewTooltip(positionMs: Long, durationMs: Long, preview: Bitmap?) {
    val pos = positionMs.milliseconds
    val dur = durationMs.milliseconds
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color.Black.copy(0.7f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(160.dp, 90.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black)
                )
            }
            Spacer(Modifier.height(if (preview != null) 4.dp else 0.dp))
            Text(prettyVideoTimestamp(pos, dur), color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * 手势反馈浮层：滑动调节音量 / 亮度时显示图标 + 百分比 + 进度条；长按倍速时显示当前倍速。
 *
 * ⚠️ 必须在控制层的 AnimatedVisibility 之外渲染：[VideoPlayerStateImpl.onChangeVolume] 与
 * [VideoPlayerStateImpl.onChangeBrightness] 只置标志位、不会唤出控制条，若放在控制层内部，
 * 控制条隐藏时滑动就完全没有反馈（正是「手势没有提示和图标」的原因）。
 */
@Composable
internal fun GestureFeedbackOverlay(state: VideoPlayerState) {
    val changingVolume = state.isChangingVolume.value
    val changingBrightness = state.isChangingBrightness.value
    val longPress = state.isLongPress.value
    if (!changingVolume && !changingBrightness && !longPress) return

    val (icon, label) = when {
        longPress -> Icons.Rounded.FastForward to "倍速"
        changingVolume -> Icons.Rounded.VolumeUp to "音量"
        else -> Icons.Rounded.BrightnessMedium to "亮度"
    }
    val progress = state.volumeBrightnessProgress.value.coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color.Black.copy(0.72f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (longPress) {
                Text(
                    text = "${state.player.playbackParameters.speed}x 倍速播放中",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "$label ${(progress * 100).roundToInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.width(120.dp),
                )
            }
        }
    }
}

/** ResizeMode → 面板上的中文名。 */
internal fun resizeLabel(mode: ResizeMode): String = when (mode) {
    ResizeMode.Fit -> "适应"
    ResizeMode.Fill -> "铺满"
    ResizeMode.Full -> "全屏"
    ResizeMode.FixedRatio_16_9 -> "16:9"
    ResizeMode.FixedRatio_4_3 -> "4:3"
    ResizeMode.FixedWidth -> "宽"
    ResizeMode.FixedHeight -> "高"
    ResizeMode.Zoom -> "缩放"
    else -> "适应"
}

@Composable
private fun BottomControlBar(
    modifier: Modifier,
    progressLineColor: Color,
    state: VideoPlayerState,
    enabledDanmaku: Boolean,
    onNextClick: () -> Unit,
    onDanmakuClick: (Boolean) -> Unit,
    showEpisodeShortcut: Boolean = true,
) {
    val timestamp =
        remember(
            state.videoDurationMs.value,
            state.videoPositionMs.value.milliseconds.inWholeSeconds
        ) {
            prettyVideoTimestamp(
                state.videoPositionMs.value.milliseconds,
                state.videoDurationMs.value.milliseconds
            )
        }

    Column(modifier = modifier) {
        if (!state.isSeeking.value) {
            TimelineControl(
                timestamp = timestamp,
                isFullScreen = state.isFullscreen.value,
                onFullScreenToggle = { state.control.setFullscreen(!state.isFullscreen.value) }
            )
        }

        Slider(
            value = state.videoProgress.value.safeValue(),
            secondValue = state.videoBufferedProgress.value.safeValue(),
            onClick = { state.onClickSlider(it) },
            onValueChange = {
                state.onSeeking(it)
                state.setScrubPreviewPositionMs((it * state.videoDurationMs.value).toLong())
            },
            onValueChangeFinished = {
                state.onSeeked()
                state.setScrubPreviewPositionMs(null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            isSeeking = state.isSeeking.value,
            color = progressLineColor,
        )

        if (!state.isSeeking.value) {
            // 倍速 / 画面尺寸 / 选集的选项都在「更多」面板里，底栏只保留快捷入口：
            // 点击直接打开「更多」并定位到对应分区（选集会默认展开），不再弹居中面板。
            PlaybackControl(
                isPlaying = state.isPlaying.value,
                enabledDanmaku = enabledDanmaku,
                onPlayPause = { if (state.isPlaying.value) state.control.pause() else state.control.play() },
                onNextClick = onNextClick,
                onDanmakuClick = onDanmakuClick,
                showEpisodeShortcut = showEpisodeShortcut,
                speedText = state.speedText.value,
                resizeText = state.resizeText.value,
                onSpeedClick = { state.showMoreUi(MoreFocus.SPEED) },
                onResizeClick = { state.showMoreUi(MoreFocus.PICTURE_SIZE) },
                onEpisodeClick = { state.showMoreUi(MoreFocus.EPISODE) },
            )
        } else Spacer(modifier = Modifier.size(MediumIconButtonSize))
    }
}

@Composable
private fun TimelineControl(
    timestamp: String,
    isFullScreen: Boolean,
    onFullScreenToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = timestamp, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.weight(1.0f))
        AdaptiveIconButton(
            modifier = Modifier.size(SmallIconButtonSize),
            onClick = onFullScreenToggle
        ) {
            Icon(
                imageVector = if (isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun PlaybackControl(
    isPlaying: Boolean,
    enabledDanmaku: Boolean,
    onPlayPause: () -> Unit,
    onNextClick: () -> Unit,
    onDanmakuClick: (Boolean) -> Unit,
    showEpisodeShortcut: Boolean = true,
    speedText: String,
    resizeText: String,
    onSpeedClick: () -> Unit,
    onResizeClick: () -> Unit,
    onEpisodeClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayPauseButton(isPlaying, onPlayPause)
            NextEpisodeIcon(onClick = onNextClick)
            DanmakuIcon(onClick = onDanmakuClick, danmakuEnabled = enabledDanmaku)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showEpisodeShortcut) {
                AdaptiveTextButton(text = "选集", onClick = onEpisodeClick)
            }
            AdaptiveTextButton(text = speedText, onClick = onSpeedClick)
            AdaptiveTextButton(text = resizeText, onClick = onResizeClick)
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onPlayPause: () -> Unit) {
    AdaptiveIconButton(
        modifier = Modifier.size(MediumIconButtonSize),
        onClick = onPlayPause
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(),
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null
        )
    }
}

@Composable
private fun horizontalPadding(): Dp {
    return 8.dp + if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        24.dp
    } else 0.dp
}

private fun Float?.safeValue() = this?.takeIf { !it.isNaN() } ?: 0f

@Composable
private fun DanmakuIcon(
    danmakuEnabled: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveIconButton(
        onClick = { onClick(!danmakuEnabled) },
        modifier.size(MediumIconButtonSize),
    ) {
        if (danmakuEnabled) {
            Icon(Icons.Rounded.Subtitles, contentDescription = "禁用弹幕")
        } else {
            Icon(Icons.Rounded.SubtitlesOff, contentDescription = "启用弹幕")
        }
    }
}

@Composable
private fun NextEpisodeIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveIconButton(
        modifier = modifier.size(MediumIconButtonSize),
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_next),
            contentDescription = "下一集"
        )
    }
}

@Composable
fun AdaptiveTextButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    color: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    AdaptiveIconButton(
        modifier = modifier.size(MediumIconButtonSize),
        onClick = onClick
    ) {
        Text(
            text = text,
            color = color,
            style = style,
        )
    }
}

@Composable
private fun AdaptiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabledIndication: Boolean = true,
    content: @Composable () -> Unit
) {
    val indication = LocalIndication.current

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = if (enabledIndication) indication else null
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private val BigIconButtonSize = 52.dp
private val MediumIconButtonSize = 42.dp
private val SmallIconButtonSize = 32.dp
