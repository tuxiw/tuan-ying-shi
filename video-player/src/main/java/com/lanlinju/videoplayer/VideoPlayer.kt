package com.lanlinju.videoplayer

import android.media.AudioManager
import android.provider.Settings
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlinx.coroutines.delay

@JvmInline
value class ResizeMode private constructor(val value: Int) {
    companion object {
        val Fit = ResizeMode(0) /* 自适应 */
        val FixedWidth = ResizeMode(1)
        val FixedHeight = ResizeMode(2)
        val Fill = ResizeMode(3) /* 以改变视频纵横比的形式填充屏幕 */
        val Full = ResizeMode(4) /* 不改变视频纵横比的形式充满屏幕 */
        val Zoom = ResizeMode(5)
        val FixedRatio_16_9 = ResizeMode(6)
        val FixedRatio_4_3 = ResizeMode(7)
    }
}

/**
 * 按 [aspectRatio] 与 [resizeMode] 把内容缩放到「视频画面」大小，并在可用区域内居中，
 * 节点自身仍占据整个可用区域（内容居中放置）。
 *
 * 对外部公开：播放页的弹幕覆盖层需要与视频画面**完全同源**地定尺居中，
 * 若弹幕层自己另算一套 letterbox 内边距，一旦片源比例或 ResizeMode 与假设不符，
 * 两边就会错位（表现为弹幕“悬在画面中间”）。复用本修饰器即可保证永远贴合视频画面。
 */
fun Modifier.adaptiveLayout(
    aspectRatio: Float,
    resizeMode: ResizeMode = ResizeMode.Fit
) = layout { measurable, constraints ->
    val resizedConstraint = constraints.resizeForVideo(resizeMode, aspectRatio)
    val placeable = measurable.measure(resizedConstraint)
    layout(constraints.maxWidth, constraints.maxHeight) {
        // Center x and y axis relative to the layout
        placeable.placeRelative(
            x = (constraints.maxWidth - resizedConstraint.maxWidth) / 2,
            y = (constraints.maxHeight - resizedConstraint.maxHeight) / 2
        )
    }
}

private fun Modifier.defaultPlayerDragGestures(playerState: VideoPlayerState) =
    this.pointerInput(Unit) {
        var downX = 0f
        var downY = 0f
        var isChangePosition = false
        var isChangeVolume = false
        var isChangeBrightness = false
        var gestureDownPosition = 0L
        var gestureDownVolume = 0
        var gestureDownBrightness = 0f
        val maxFastForwardDuration = 180_000L // 最大快进时长为180秒
        val threshold = (12.dp).toPx() // 拖动距离超过12dp后开始计算
        val audioManager = playerState.audioManager
        val layoutParams = playerState.window.attributes
        detectDragGestures(
            onDragStart = { offset ->
                downX = offset.x
                downY = offset.y
                isChangePosition = false
                isChangeVolume = false
                isChangeBrightness = false
            },             onDragEnd = {
                if (isChangePosition) playerState.onSeeked()
                playerState.setScrubPreviewPositionMs(null)
                playerState.onChanged()
            }) { change, dragAmount ->
                // 双指缩放 / 旋转期间让位：否则先落下的那根手指会同时触发音量 / 亮度 / 快进
                val sinceTransform = android.os.SystemClock.uptimeMillis() - playerState.lastTransformGestureMs
                if (sinceTransform < TRANSFORM_GESTURE_GRACE_MS) return@detectDragGestures
                val deltaX = change.position.x - downX
            var deltaY = change.position.y - downY
            val absDeltaX = abs(deltaX)
            val absDeltaY = abs(deltaY)
            if (!isChangePosition && !isChangeVolume && !isChangeBrightness
                && downY > size.height * 0.15f && downY < size.height * 0.9f
            ) {
                if (absDeltaX > threshold || absDeltaY > threshold) {
                    if (absDeltaX >= threshold) {
                        isChangePosition = true
                        gestureDownPosition = playerState.player.currentPosition
                    } else {
                        if (change.position.x < size.width * 0.5f) {
                            isChangeBrightness = true
                            if (layoutParams.screenBrightness < 0f) {
                                gestureDownBrightness = Settings.System.getInt(
                                    playerState.window.context.contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS
                                ).toFloat()
                            } else {
                                gestureDownBrightness = layoutParams.screenBrightness * 255
                            }
                        } else {
                            isChangeVolume = true
                            gestureDownVolume =
                                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        }
                    }
                }
            }
            if (isChangePosition && playerState.videoDurationMs.value > 0) {
                val deltaMs = deltaX / size.width * maxFastForwardDuration
                val seekTimePosition = gestureDownPosition + deltaMs
                val progress =
                    (seekTimePosition / playerState.videoDurationMs.value).coerceIn(0f..1f)
                playerState.onSeeking(progress)
                playerState.setScrubPreviewPositionMs((progress * playerState.videoDurationMs.value).toLong())
            }
            if (isChangeVolume) {
                deltaY = -deltaY
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val deltaV = (max * deltaY * 3 / size.height).toInt()
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC, gestureDownVolume + deltaV, 0
                )
                val volumePercent = (gestureDownVolume + deltaV) / max.toFloat()
                playerState.onChangeVolume(volumePercent.coerceIn(0f..1f))
            }
            if (isChangeBrightness) {
                deltaY = -deltaY
                val deltaV = 255 * deltaY * 3 / size.height
                val brightnessPercent = ((gestureDownBrightness + deltaV) / 255)
                layoutParams.screenBrightness = brightnessPercent.coerceIn(0.01f..1f)
                playerState.window.attributes = layoutParams
                playerState.onChangeBrightness(brightnessPercent.coerceIn(0f..1f))
            }
        }
    }

/**
 * 双指缩放 / 旋转手势。
 *
 * **关键修复**：原先直接用 `detectTransformGestures`，而它在**单指**移动越过 touchSlop 后
 * 也会 `consume()` 掉 move 事件，从而把下面的单指拖动手势（快进 / 音量 / 亮度）整个吞掉，
 * 表现为「手势控制失效」。所以这里弃用 `detectTransformGestures`，改成只在「两根及以上手指
 * 同时按下」时才处理的自定义 detector：
 *   - 单指 / 零指：一律放行、不消费，让单击 / 双击 / 拖动照常工作；
 *   - 双指及以上：计算缩放与旋转并消费事件，避免被误当成单指拖动。
 *
 * 每一帧只把增量交给 [VideoPlayerState.setVideoTransform]（轻量赋值，不重建任何播放资源），
 * 因此拖动过程实时跟手，松手即停，不需要额外动画。
 */
private fun Modifier.videoTransformGestures(playerState: VideoPlayerState) =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            var lastDistance = 0f
            var lastAngle = 0f
            while (true) {
                val event = awaitPointerEvent()
                // 只有两指及以上才算「缩放 / 旋转」手势；单指 / 零指原样放行给下层的单击 / 双击 / 拖动。
                if (event.changes.size < 2) {
                    lastDistance = 0f
                    lastAngle = 0f
                    continue
                }
                val a = event.changes[0].position
                val b = event.changes[1].position
                val dist = hypot(b.x - a.x, b.y - a.y)
                val angle = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()).toFloat()
                if (lastDistance > 0f && dist > 0f) {
                    val zoom = dist / lastDistance
                    var rotation = angle - lastAngle
                    // 角度回绕：从 +179° 跨到 -179° 时差值会变成 ~358°，要折回成 ~-2°。
                    if (rotation > 180f) rotation -= 360f
                    if (rotation < -180f) rotation += 360f
                    if (zoom != 1f || rotation != 0f) {
                        playerState.lastTransformGestureMs = android.os.SystemClock.uptimeMillis()
                        playerState.setVideoTransform(
                            playerState.videoScale.value * zoom,
                            playerState.videoRotation.value + rotation,
                        )
                    }
                }
                lastDistance = dist
                lastAngle = angle
                // 双指事件必须消费掉，否则会被下层的单指拖动手势误当成快进 / 音量 / 亮度。
                for (change in event.changes) change.consume()
            }
        }
    }

private fun Modifier.defaultPlayerTapGestures(
    playerState: VideoPlayerState,
    haptics: HapticFeedback,
) = this.pointerInput(Unit) {
    detectTapGestures(
        onTap = {
            if (playerState.isControlUiVisible.value) {
                playerState.hideControlUi()
            } else {
                playerState.showControlUi()
            }
        },
        onDoubleTap = {
            // 小窗里：优先当作「切换小窗尺寸」（宿主注入了钩子且确实在小窗中）。
            // 播放 / 暂停在小窗里由系统悬浮控件负责，双击改尺寸更有价值。
            val pipToggle = playerState.pipToggleSize
            if (playerState.inPipMode && pipToggle != null) {
                pipToggle.invoke()
            } else if (playerState.isPlaying.value) {
                playerState.control.pause()
            } else {
                playerState.control.play()
            }
        },
        onLongPress = {
            if (playerState.isPlaying.value) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                playerState.onLongPress()
            }
        },
        onPress = {
            tryAwaitRelease() /* 等待按压结束 */
            if (playerState.isLongPress.value) {
                playerState.onDisLongPress()
            }
        }
    )
}

@Composable
private fun VideoPlayer(
    modifier: Modifier,
    playerState: VideoPlayerState,
    gestureEnabled: Boolean = true,
    overlay: @Composable () -> Unit = {},
    controller: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Box(modifier = modifier.fillMaxSize()) {
        // 视频画面层：手势（点击显隐控件 / 滑动快进·音量·亮度）只挂在这一层。
        // 与上层控件层互为兄弟、互不干扰，避免抢走进度条拖动与按钮的触摸事件。
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 缩放 / 旋转后画面会超出自身布局边界（旋转 45°、放大 2x 都是如此），
                // 不裁剪的话会画到播放器区域之外、压在下面的信息面板上。
                .clipToBounds()
                .then(if (gestureEnabled) Modifier.defaultPlayerTapGestures(playerState, haptics) else Modifier)
                .then(if (gestureEnabled) Modifier.defaultPlayerDragGestures(playerState) else Modifier)
                .then(if (gestureEnabled) Modifier.videoTransformGestures(playerState) else Modifier)
                .onSizeChanged { size -> playerState.setViewportSize(size.width, size.height) }
        ) {
            // 重要：这里**绝不能**用 key(url) 重建承载画面的 View。
            // Compose 中新 View 的 factory 在「组合」阶段执行、旧 View 的 onRelease
            // （其内部的 clearVideoTextureView）在「applyChanges」阶段执行——顺序颠倒会让
            // player 最终处于「无 surface」状态，表现为换集后永久黑屏 / 画面不更新。
            // 换集真正需要的重绑，改到 LaunchedEffect(url) 里顺序执行（见 onMediaSourceSwitch）。
            AndroidView(
                modifier = Modifier
                    .adaptiveLayout(
                        aspectRatio = playerState.videoSize.value.aspectRatio(),
                        resizeMode = playerState.videoResizeMode.value
                    ),
                factory = { context ->
                    // 用 TextureView 而不是 SurfaceView：
                    // SurfaceView 的内容不在应用窗口里（靠打孔合成），自 Android N 起只同步
                    // 「位置 + 正向缩放」，**旋转与镜像（负缩放）都会被丢弃**
                    // （CameraX PreviewView、VideoView.setRotation 都是同一个坑）。
                    // 双指缩放·旋转、镜像翻转都要求完整视图变换，只有 TextureView 能做到。
                    // 代价是多一次合成（略费电），但 1080p 完全可接受。
                    TextureView(context).also { tv ->
                        playerState.player.setVideoTextureView(tv)
                        playerState.setVideoSurfaceView(tv)
                    }
                },
                onRelease = { tv -> runCatching { playerState.player.clearVideoTextureView(tv) } },
            )
        }

        // 画面叠加层（弹幕等）：夹在「视频画面」与「控制层」之间。
        // 放这里是为了同时满足两件事：
        //   1. 在视频 SurfaceView 之上 → 弹幕可见（SurfaceView 会穿透窗口，画在它下面的东西看不见）；
        //   2. 在控制层之下 → 弹幕不会盖住顶部 / 底部按钮，也不会盖住「更多」等覆盖面板，
        //      因此无需在面板打开时把弹幕隐藏（隐藏会让弹幕引擎停摆 / 回来时跳帧）。
        overlay()

        AnimatedVisibility(
            visible = playerState.isControlUiVisible.value,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            controller()
        }

        // 「一键还原」：只有画面被双指缩放 / 旋转过才出现，平时不占地方。
        // 放在播放器层而不是控制层——控制栏会自动隐藏，而用户捏完很可能正好在隐藏期，
        // 那样就找不到还原入口了。
        val videoScale by playerState.videoScale
        val videoRotation by playerState.videoRotation
        if (videoScale != 1f || videoRotation != 0f) {
            ResetTransformChip(
                scale = videoScale,
                rotation = videoRotation,
                onClick = { playerState.resetVideoTransform() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 14.dp),
            )
        }

        // 播放信息层（分辨率 / 码率 / 编码 / 帧率）：常驻显示，不随控制栏自动隐藏；
        // 定位于顶部左侧、控制栏下方，避免遮挡返回 / 标题 / 右侧按钮与底部控制条。
        if (playerState.showStats.value) {
            StatsOverlay(stats = playerState.playerStats.value)
        }

        // 手势反馈（音量 / 亮度 / 长按倍速）：调节音量亮度不会唤出控制条，
        // 因此必须挂在控制层的可见性动画之外，否则控制条隐藏时滑动毫无反馈。
        GestureFeedbackOverlay(playerState)
    }
}

/**
 * @param playerState state to attach to this composable.
 * @param lifecycleOwner required to manage the ExoPlayer instance.
 * @param controller you can use [VideoPlayerControl] or alternatively implement your own
 * */
@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    url: String,
    videoPosition: Long,
    playerState: VideoPlayerState,
    headers: Map<String, String> = emptyMap(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    releaseOnDispose: Boolean = true,
    gestureEnabled: Boolean = true,
    onBackPress: () -> Unit,
    onError: () -> Unit = {},
    /**
     * 画在视频之上、控制层之下的内容层（典型用途：弹幕）。
     * 与控制层的关系是「控制层盖住它」，所以弹幕不会遮挡按钮与覆盖面板，
     * 面板打开时也无需暂停弹幕渲染。
     */
    overlay: @Composable () -> Unit = {},
    controller: @Composable () -> Unit,
) {
    val context = LocalContext.current
    playerState.onError = onError
    VideoPlayer(
        modifier = modifier,
        playerState = playerState,
        gestureEnabled = gestureEnabled,
        overlay = overlay,
        controller = controller
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    playerState.showControlUi()
                    // 断点继播：宿主在跳走（如打开播放设置）前置了位 → 回来把播放接上。
                    // 只消费一次；App 退后台再回来不会命中（宿主没置位），不会擅自自动播放。
                    if (playerState.resumeOnReturn) {
                        playerState.resumeOnReturn = false
                        if (!playerState.player.isPlaying) playerState.control.play()
                    }
                }
                // 后台听视频开启时不暂停：用户按 Home / 锁屏时继续出声。
                // SurfaceView 的 surface 会被销毁，但 ExoPlayer 通过 SurfaceHolder.Callback
                // 自行处理重建，回到前台仍能恢复画面。
                Lifecycle.Event.ON_STOP -> if (!playerState.backgroundAudioEnabled && !playerState.inPipMode) {
                    playerState.player.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        playerState.registerListener()
        onDispose {
            playerState.unregisterListener()
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 仅当宿主把 player 的生命周期完全交给本组件时才释放。
            // 宿主在屏幕级 remember 了 playerState（跨布局分支复用同一 player）时，
            // 应传 false，避免分支切换导致 player 被释放后新实例复用已死 player
            // （表现为 Handler on a dead thread / 平板黑屏）。
            if (releaseOnDispose) {
                playerState.player.release()
            }
        }
    }

    LaunchedEffect(url) {
        // 同一个片源被重新组合时（进出小窗会改变屏幕最小宽度，播放页可能在
        // 「平板分栏 / 手机竖排」之间切换，VideoPlayer 被搬到另一个分支 → 本 effect 重跑），
        // 绝不能重新 prepare + seekTo(videoPosition)：那会把进度打回进入页面时的值，
        // 表现为「一切小窗就从头播放」。此时只重绑 surface，位置交给 player 自己保持。
        if (!playerState.shouldLoadSource(url)) {
            // 只重绑 surface：不重置进度与加载态（player 本来就还处于 ready，
            // 若把 isLoading 置 true 又没有新的 ready 事件来复位，转圈会一直停在那儿）。
            playerState.rebindVideoSurface()
            return@LaunchedEffect
        }
        // 换集 / 换线路：先做一次确定性的 surface 重绑并清掉上一集遗留状态。
        // 必须在设置新片源之前调用，让新源一开始就绑在尺寸正确的 surface 上。
        playerState.onMediaSourceSwitch()
        // 供进度帧预览取帧使用（MediaMetadataRetriever 需要原始地址与请求头）
        playerState.setMediaSource(url, headers)
        playerState.player.setVideoUrl(url, headers, context)
        playerState.player.prepare()
        playerState.player.seekTo(videoPosition)
        playerState.player.playWhenReady = true
        playerState.showControlUi()
        // 超分（视频特效）管线与新片源绑定，换集后需重建一次，否则可能出现不出帧 / 画面不更新。
        playerState.refreshEnhancementForNewSource()
    }

    BackHandler {
        onBackPress()
    }

    // 定时关闭：到点自动暂停（保留播放页，用户可继续手动播放）。
    // 放在播放器层而不是控件层——控件层会随控制栏隐藏而移除，定时器必须一直有效。
    val sleepDeadline by SleepTimer.deadlineElapsedMs.collectAsState()
    LaunchedEffect(sleepDeadline) {
        val deadline = sleepDeadline ?: return@LaunchedEffect
        val remaining = deadline - android.os.SystemClock.elapsedRealtime()
        if (remaining > 0) delay(remaining)
        // 期间用户可能已经取消或改成了别的时间，只有仍是同一个截止时刻才真正执行
        if (SleepTimer.deadlineElapsedMs.value == deadline) {
            SleepTimer.markFired()
            playerState.control.pause()
            runCatching { Toast.makeText(context, "定时关闭：已暂停播放", Toast.LENGTH_SHORT).show() }
        }
    }
}

/**
 * 「一键还原」胶囊：显示当前缩放 / 旋转量，点击把画面还原到原始大小与角度。
 *
 * 之所以显示具体数值：双指手势没有刻度，用户往往不知道自己捏到了多少，
 * 有读数才能判断「要不要还原」以及「还原后差多少」。
 */
@Composable
private fun ResetTransformChip(
    scale: Float,
    rotation: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.RestartAlt,
            contentDescription = "还原画面",
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "还原 ${(scale * 100).toInt()}% · ${rotation.toInt()}°",
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/**
 * 播放信息叠加层（分辨率 / 码率 / 编码 / 帧率）。
 *
 * 与 [VideoPlayerControl] 内的其它控件平级但**独立渲染、常驻显示**：即使用户点击屏幕让控制栏自动隐藏，
 * 这层信息仍保留在画面上，方便随时查看当前画质。位置刻意放在「顶部左侧、控制栏下方」，
 * 既避开返回 / 标题 / 右侧按钮，也避开底部播放控制条，不会遮挡任何可点击区域。
 */
@Composable
private fun StatsOverlay(stats: PlayerStats) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 76.dp, end = 16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text("分辨率: ${stats.resolution}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            // 轨道未声明码率时由分片实测推算，加「≈」标注避免被当成精确值
            val bitrate = if (stats.bitrateKbps > 0) {
                "${if (stats.bitrateEstimated) "≈" else ""}${stats.bitrateKbps} kbps"
            } else {
                "—"
            }
            Text("码率: $bitrate", color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text("编码: ${stats.codec}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            val fps = if (stats.frameRate > 0f) String.format("%.2f fps", stats.frameRate) else "—"
            Text("帧率: $fps", color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}
