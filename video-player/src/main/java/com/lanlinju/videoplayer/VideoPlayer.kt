package com.lanlinju.videoplayer

import android.media.AudioManager
import android.provider.Settings
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

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
            }, onDragEnd = {
                if (isChangePosition) playerState.onSeeked()
                playerState.onChanged()
            }) { change, dragAmount ->
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
            if (playerState.isPlaying.value) {
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
    controller: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Box(modifier = modifier.fillMaxSize()) {
        // 视频画面层：手势（点击显隐控件 / 滑动快进·音量·亮度）只挂在这一层。
        // 与上层控件层互为兄弟、互不干扰，避免抢走进度条拖动与按钮的触摸事件。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .defaultPlayerTapGestures(playerState, haptics)
                .defaultPlayerDragGestures(playerState)
        ) {
            AndroidView(
                modifier = Modifier
                    .adaptiveLayout(
                        aspectRatio = playerState.videoSize.value.aspectRatio(),
                        resizeMode = playerState.videoResizeMode.value
                    ),
                factory = { context ->
                    // 用 TextureView 而非 SurfaceView：TextureView 在 View 层级内合成，
                    // 不受全屏沉浸模式切换（systemBars 显隐）导致的窗口 inset 变动影响，
                    // 也不会被上层 Compose 背景遮挡，可根除「播放/切全屏时黑屏」问题。
                    TextureView(context).also {
                        it.isOpaque = true
                        playerState.player.setVideoTextureView(it)
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = playerState.isControlUiVisible.value,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            controller()
        }
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
    onBackPress: () -> Unit,
    onError: () -> Unit = {},
    controller: @Composable () -> Unit,
) {
    val context = LocalContext.current
    playerState.onError = onError
    VideoPlayer(
        modifier = modifier,
        playerState = playerState,
        controller = controller
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> playerState.showControlUi()
                Lifecycle.Event.ON_STOP -> playerState.player.pause()
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
        playerState.player.setVideoUrl(url, headers, context)
        playerState.player.prepare()
        playerState.player.seekTo(videoPosition)
        playerState.player.playWhenReady = true
        playerState.showControlUi()
    }

    BackHandler {
        onBackPress()
    }
}
