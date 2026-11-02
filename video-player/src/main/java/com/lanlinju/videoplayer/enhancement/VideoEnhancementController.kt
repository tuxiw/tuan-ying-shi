/*
 * 移植自 animeko (OpenAni)，原许可证为 GNU AGPLv3。
 * 原文件：app/shared/video-player/src/commonMain/kotlin/videoenhancement/VideoEnhancementController.kt
 *         app/shared/video-player/src/androidMain/kotlin/videoenhancement/VideoEnhancementController.android.kt
 *
 * 改动：去掉 mediamp / PlayerKernelConfig 依赖，改为直接持有 ExoPlayer，
 *       通过 exoPlayer.setVideoEffects(...) 应用 GlEffect。
 */

@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lanlinju.videoplayer.enhancement

import androidx.media3.common.Effect
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.CoroutineContext

/** 视频画质增强模式。 */
enum class VideoEnhancementMode {
    /** 关闭所有增强。 */
    OFF,

    /** 仅复原（Anime4K Restore CNN S），开销低。 */
    PERFORMANCE,

    /** 复原 + 2x 超分（Anime4K CNN M），画质最佳、开销高。 */
    QUALITY,
}

interface VideoEnhancementController : AutoCloseable {
    val mode: StateFlow<VideoEnhancementMode>

    fun setMode(mode: VideoEnhancementMode)

    /**
     * 设置色彩辅助模式（色觉辅助 / 灰度 / 高对比）。
     *
     * 与画质增强共用同一条 Media3 特效管线：两者任一开启时都会重建一次 effect 列表，
     * 因此可以叠加（超分 + 色觉校正）而不互相覆盖。
     */
    fun setColorMode(mode: ColorAssistMode)

    fun setViewportSize(width: Int, height: Int)

    /**
     * 换集 / 重绑 surface 后**重建**特效管线（保持当前档位不变）。
     *
     * Media3 的 effect graph 与片源、surface 绑定：换集后沿用旧管线时，GL 处理链路可能
     * 不再输出新帧（表现为「画面卡住 / 不更新 / 黑屏」）。这里清空后重建一次即可恢复出帧。
     *
     * 已关闭增强时为空操作。
     */
    fun reapply()
}

/**
 * 创建并启动一个 [VideoEnhancementController]。
 *
 * @param exoPlayer 已创建好的 [ExoPlayer] 实例，增强效果将通过 [ExoPlayer.setVideoEffects] 应用。
 * @param initEffectGraphInAdvance 是否在使用者未启用增强时，仍提前设置空 effect 列表。
 *   Media3 要求在首次 prepare 之前就建立 effect graph，才能支持播放中动态切换效果。
 * @param parentCoroutineContext 控制器协程的父上下文（通常与播放页生命周期绑定）。
 */
fun createVideoEnhancementController(
    exoPlayer: ExoPlayer,
    initEffectGraphInAdvance: Flow<Boolean>,
    parentCoroutineContext: CoroutineContext,
): VideoEnhancementController {
    return ExoPlayerVideoEnhancementController(
        exoPlayer,
        exoPlayer::setVideoEffects,
        initEffectGraphInAdvance,
        parentCoroutineContext,
    )
}

internal class ExoPlayerVideoEnhancementController(
    private val exoPlayer: ExoPlayer,
    private val setVideoEffects: (List<Effect>) -> Unit,
    preinitVideoEffects: Flow<Boolean>,
    parentCoroutineContext: CoroutineContext,
) : VideoEnhancementController {
    private val controllerJob = SupervisorJob(parentCoroutineContext[Job])
    private val scope = CoroutineScope(parentCoroutineContext + controllerJob)
    private val mutableMode = MutableStateFlow(VideoEnhancementMode.OFF)
    private val mutableColorMode = MutableStateFlow(ColorAssistMode.OFF)
    private val viewportSize = MutableStateFlow<VideoDimensions?>(null)

    override val mode: StateFlow<VideoEnhancementMode> = mutableMode.asStateFlow()

    private var appliedMode = VideoEnhancementMode.OFF
    private var appliedColorMode = ColorAssistMode.OFF
    private var appliedScaler: DesktopStyleLanczosSharpEffect? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        // Media3 要求在首次 prepare 之前就存在 effect graph，才能支持播放中切换效果。
        scope.launch {
            if (preinitVideoEffects.first()) {
                setVideoEffects(emptyList())
            }
        }
        startObserving()
    }

    private fun startObserving() {
        combine(mode, mutableColorMode, viewportSize) { mode, colorMode, viewportSize ->
            apply(mode, colorMode, viewportSize)
        }.launchIn(scope)
    }

    override fun setMode(mode: VideoEnhancementMode) {
        mutableMode.value = mode
    }

    override fun setColorMode(mode: ColorAssistMode) {
        mutableColorMode.value = mode
    }

    override fun setViewportSize(width: Int, height: Int) {
        viewportSize.value = dimensionsOrNull(width, height)
    }

    override fun reapply() {
        val mode = appliedMode
        val color = appliedColorMode
        if (mode == VideoEnhancementMode.OFF && color == ColorAssistMode.OFF) return
        // 先清空（restore 会把 applied* 置为关闭态），下一轮 Looper 消息再按原档位重建：
        // 同一帧内连续替换 effect 列表会令 Media3 播放线程死锁（见 apply() 注释），
        // 故两次调用必须跨消息，而不是紧挨着执行。
        restore()
        mainHandler.post {
            if (!controllerJob.isCancelled) apply(mode, color, viewportSize.value)
        }
    }

    override fun close() {
        mainHandler.removeCallbacksAndMessages(null)
        restore()
        scope.cancel()
    }

    private fun apply(
        mode: VideoEnhancementMode,
        colorMode: ColorAssistMode,
        viewportSize: VideoDimensions?,
    ) {
        val enhancementOn = mode != VideoEnhancementMode.OFF
        val colorOn = colorMode != ColorAssistMode.OFF

        if (!enhancementOn && !colorOn) {
            restore()
            return
        }

        // 仅模式变化才替换 effect 列表。着色器在 configure() 中接收输入尺寸、scaler 自行跟随视口，
        // 因此元数据变化或视口 resize 都不会重建 effect graph：编译 quality 着色器会卡住播放，
        // 且 Media3 在 effect 列表被快速连续替换时会令播放线程死锁（全屏切换的中间布局尺寸就会触发）。
        if (mode == appliedMode && colorMode == appliedColorMode) {
            appliedScaler?.viewportSize = viewportSize
            return
        }

        val scaler = if (enhancementOn) {
            DesktopStyleLanczosSharpEffect().apply { this.viewportSize = viewportSize }
        } else {
            null
        }
        // 兜底：effect 列表由我们拼装，一旦某个环节出问题（播放器已释放 / 管线不支持），
        // 宁可放弃这些增益，也不能把整段播放搞崩。
        runCatching {
            setVideoEffects(
                buildList {
                    when (mode) {
                        VideoEnhancementMode.OFF -> Unit
                        VideoEnhancementMode.PERFORMANCE -> add(Anime4kRestoreEffect)
                        VideoEnhancementMode.QUALITY -> {
                            add(Anime4kRestoreQualityEffect)
                            add(Anime4kUpscaleQualityEffect)
                        }
                    }
                    scaler?.let { add(it) }
                    // 色彩辅助放在管线最后：先完成超分/锐化再统一做色彩映射，
                    // 避免色觉校正矩阵被后续的锐化着色器再处理一次而失真。
                    //
                    // 兜底：颜色矩阵由我们手写，尺寸/数值一旦写错，Media3 是在**渲染线程**
                    // 抛 IllegalStateException 的（表现为 ExoPlaybackException「播放出错」，
                    // 整段视频直接播不了）。这里宁可放弃色彩辅助，也不能把播放搞崩。
                    runCatching { colorAssistEffectOrNull(colorMode) }
                        .onFailure {
                            android.util.Log.w("VideoEnhancement", "色彩辅助矩阵构建失败，已跳过", it)
                        }
                        .getOrNull()
                        ?.let { add(it) }
                },
            )
        }.onFailure {
            android.util.Log.w("VideoEnhancement", "特效管线应用失败，已跳过", it)
        }
        appliedMode = mode
        appliedColorMode = colorMode
        appliedScaler = scaler
    }

    private fun restore() {
        if (appliedMode == VideoEnhancementMode.OFF && appliedColorMode == ColorAssistMode.OFF) return
        runCatching { setVideoEffects(emptyList()) }
            .onFailure { android.util.Log.w("VideoEnhancement", "清空特效失败", it) }
        appliedMode = VideoEnhancementMode.OFF
        appliedColorMode = ColorAssistMode.OFF
        appliedScaler = null
    }
}

internal data class VideoDimensions(
    val width: Int,
    val height: Int,
)

private fun dimensionsOrNull(width: Int?, height: Int?): VideoDimensions? =
    if (width != null && height != null && width > 0 && height > 0) {
        VideoDimensions(width, height)
    } else {
        null
    }
