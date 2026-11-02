package com.lanlinju.videoplayer

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.view.OrientationEventListener
import android.view.Window
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import android.graphics.Bitmap
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.view.PixelCopy
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import com.lanlinju.videoplayer.enhancement.ColorAssistMode
import com.lanlinju.videoplayer.enhancement.VideoEnhancementController
import com.lanlinju.videoplayer.enhancement.VideoEnhancementMode
import com.lanlinju.videoplayer.enhancement.createVideoEnhancementController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(UnstableApi::class)
@Composable
fun rememberVideoPlayerState(
    hideControllerAfterMs: Long = 6000,
    videoPositionPollInterval: Long = 500,
    isAutoOrientation: Boolean = true,
    context: Context = LocalContext.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    config: ExoPlayer.Builder.() -> Unit = {
        setLoadControl(loadControlCreator())
        setSeekForwardIncrementMs(15 * 1000)
        setSeekBackIncrementMs(15 * 1000)
    }
): VideoPlayerState = remember {
    VideoPlayerStateImpl(
        player = ExoPlayer.Builder(context).apply(config).build(),
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
        window = (context as Activity).window,
        context = context,
        coroutineScope = coroutineScope,
        isAutoOrientation = isAutoOrientation,
        hideControllerAfterMs = hideControllerAfterMs,
        videoPositionPollInterval = videoPositionPollInterval
    ).also {
        it.player.addListener(it)
    }
}

class VideoPlayerStateImpl(
    override val player: ExoPlayer,
    override val audioManager: AudioManager,
    override val window: Window,
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val isAutoOrientation: Boolean,
    private val hideControllerAfterMs: Long,
    private val videoPositionPollInterval: Long,
) : VideoPlayerState, Player.Listener {
    override val videoSize = mutableStateOf(player.videoSize)
    override val videoResizeMode = mutableStateOf(ResizeMode.Fit)
    override val videoScale = mutableFloatStateOf(1f)
    override val videoRotation = mutableFloatStateOf(0f)
    override val videoPositionMs = mutableStateOf(0L)
    override val videoDurationMs = mutableStateOf(0L)

    override val isFullscreen = mutableStateOf(false)
    override val isPlaying = mutableStateOf(player.isPlaying)
    override val isLoading = mutableStateOf(true)
    override val isEnded = mutableStateOf(false)
    override val isError = mutableStateOf(false)
    override val playbackState = mutableStateOf(player.playbackState)

    /** 播放错误回调（ExoPlayer onPlayerError 时触发），供宿主做自动切线路等处理。 */
    override var onError: (() -> Unit)? = null

    override val isSeeking = mutableStateOf(false)
    override val isLongPress = mutableStateOf(false)
    override val isChangingVolume = mutableStateOf(false)
    override val isChangingBrightness = mutableStateOf(false)

    override val videoProgress = mutableStateOf(0F)
    override val videoBufferedProgress = mutableStateOf(0F)
    override val volumeBrightnessProgress = mutableStateOf(0F)

    override val speedText = mutableStateOf("倍速")
    override val resizeText = mutableStateOf("适应")

    override val isOptionsUiVisible = mutableStateOf(false)
    override val isControlUiVisible = mutableStateOf(false)
    override val isSpeedUiVisible = mutableStateOf(false)
    override val isResizeUiVisible = mutableStateOf(false)
    override val isEpisodeUiVisible = mutableStateOf(false)
    override val isMoreUiVisible = mutableStateOf(false)
    override val moreFocus = mutableStateOf(MoreFocus.NONE)

    // ===== 视频画质增强 =====
    private val enhancementController: VideoEnhancementController =
        createVideoEnhancementController(player, MutableStateFlow(true), coroutineScope.coroutineContext)
    override val enhancementMode = mutableStateOf(VideoEnhancementMode.OFF)
    override val colorAssistMode = mutableStateOf(ColorAssistMode.OFF)
    override val loopMode = mutableStateOf(LoopMode.NONE)
    override val mirrorEnabled = mutableStateOf(false)
    override var backgroundAudioEnabled: Boolean = false
        set(value) {
            field = value
            // 熄屏后仍要出声就必须持有唤醒锁：否则 CPU 一睡，播放立刻停。
            // 网络流用 WAKE_MODE_NETWORK（同时保住 Wi-Fi），关闭时显式回到 NONE 释放。
            runCatching {
                player.setWakeMode(if (value) C.WAKE_MODE_NETWORK else C.WAKE_MODE_NONE)
            }
        }
    override var autoEnhancementOptimize: Boolean = true
    override var inPipMode: Boolean = false
    override var resumeOnReturn: Boolean = false
    override var pipToggleSize: (() -> Unit)? = null

    /** 低帧率持续起始时刻（elapsedRealtime 毫秒）；0 表示当前不处于低帧率状态。 */
    private var lowFpsSinceMs = 0L

    /** 自动降级下限：自动降档不会低于此档（默认性能模式，绝不自动彻底关闭）。 */
    override var autoEnhancementDowngradeFloor: VideoEnhancementMode = VideoEnhancementMode.PERFORMANCE

    // ===== 截图 / 信息层 / 进度预览 =====
    override val showStats = mutableStateOf(false)
    override val playerStats = mutableStateOf(PlayerStats())
    override val scrubPreviewPositionMs = mutableStateOf<Long?>(null)
    /** 承载画面的 View（现在是 TextureView，保留 View 类型以便将来换回 SurfaceView）。 */
    private var videoViewRef: View? = null
    /** 最近一次双指缩放 / 旋转事件的时间戳（uptimeMillis）；0 表示从未触发。 */
    override var lastTransformGestureMs: Long = 0L

    // 轨道上报的静态信息，由 onTracksChanged 填充；实测帧率 / 实测码率单独维护后合并进 playerStats
    private var trackResolution = "—"
    private var trackCodec = "—"
    private var trackBitrateKbps = 0
    private var trackFrameRate = 0f
    private var measuredFrameRate = 0f
    /** 实测码率（kbps）：由 [bitrateMeter] 按「分片字节数 / 分片时长」滑动平均得到；0 表示尚未测到。 */
    private var measuredBitrateKbps = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 实测渲染帧率兜底：HLS / 部分封装不上报 [Format.frameRate]，此时用真实渲染帧间隔估算。
     *
     * 用环形缓冲统计最近 N 帧的跨度，并且**最多每秒刷新一次**——该回调跑在播放线程上，
     * 高频改 Compose 状态会拖累渲染，所以只在主线程每秒合并进 [playerStats] 一次。
     */
    private val frameRateMeter = object : VideoFrameMetadataListener {
        private val samples = LongArray(FRAME_RATE_SAMPLE_COUNT)
        private var filled = 0
        private var cursor = 0
        private var lastPublishMs = 0L

        override fun onVideoFrameAboutToBeRendered(
            presentationTimeUs: Long,
            releaseTimeNs: Long,
            format: Format,
            mediaFormat: MediaFormat?
        ) {
            samples[cursor] = presentationTimeUs
            cursor = (cursor + 1) % FRAME_RATE_SAMPLE_COUNT
            if (filled < FRAME_RATE_SAMPLE_COUNT) filled++
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastPublishMs < FRAME_RATE_PUBLISH_INTERVAL_MS || filled < 2) return
            lastPublishMs = nowMs
            val newest = samples[(cursor - 1 + FRAME_RATE_SAMPLE_COUNT) % FRAME_RATE_SAMPLE_COUNT]
            val oldest = samples[if (filled < FRAME_RATE_SAMPLE_COUNT) 0 else cursor]
            val spanUs = newest - oldest
            if (spanUs <= 0) return
            val fps = (filled - 1).toFloat() * 1_000_000f / spanUs
            if (fps < 1f || fps > 240f) return // 异常值（seek 等）直接丢弃
            mainHandler.post {
                measuredFrameRate = fps
                refreshStats()
                monitorFrameRate()
            }
        }

        fun reset() {
            filled = 0
            cursor = 0
            lastPublishMs = 0L
        }
    }

    /**
     * 实测码率兜底：HLS 与多数 mp4 **不会**在 [Format] 里给 averageBitrate / peakBitrate
     * （信息层的「码率」因此长期显示「—」）。这里改为按
     * 「分片下载字节数 / 该分片对应的媒体时长」滑动平均，估算真实码率。
     *
     * 单位推导：`bytes * 8 = bits`，`bits / ms = kbit/s`，故 `bytesLoaded * 8 / durMs` 直接是 kbps。
     * 只统计媒体分片（排除清单 / 密钥 / DRM / 字幕 / 独立音轨），并丢弃明显异常的样本
     * （首帧极小分片、seek 后的超大分片）。
     *
     * ⚠️ 必须声明在下面的 `init` 块**之前**：Kotlin 的属性初始化与 init 块按书写顺序执行，
     * 写反了 init 里拿到的是 null，传给 `addAnalyticsListener` 会直接 NPE 崩溃。
     */
    @OptIn(UnstableApi::class)
    private val bitrateMeter = object : AnalyticsListener {
        private val samples = LongArray(BITRATE_SAMPLE_COUNT)
        private var filled = 0
        private var cursor = 0
        // 兜底采样用（见下方 fallback）：上一次 load 完成时的播放位置
        private var lastLoadPosMs = -1L

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            if (mediaLoadData.dataType != C.DATA_TYPE_MEDIA) return
            // muxed 分片多报 DEFAULT/UNKNOWN，独立音轨报 AUDIO（要排除，否则把音频码率也算进去）
            val type = mediaLoadData.trackType
            if (type != C.TRACK_TYPE_VIDEO && type != C.TRACK_TYPE_DEFAULT && type != C.TRACK_TYPE_UNKNOWN) return
            val durMs = mediaLoadData.mediaEndTimeMs - mediaLoadData.mediaStartTimeMs
            val kbps = if (durMs > 0) {
                (loadEventInfo.bytesLoaded * 8L) / durMs
            } else {
                // fallback：progressive（mp4 直链 / 本地文件）不上报分片时间，
                // 改用「两次 load 之间实际播过的时长」作分母估算。
                val pos = player.currentPosition
                val prev = lastLoadPosMs
                lastLoadPosMs = pos
                if (prev < 0 || !player.isPlaying) return
                val played = pos - prev
                if (played < BITRATE_MIN_WINDOW_MS || played > BITRATE_MAX_WINDOW_MS) return
                (loadEventInfo.bytesLoaded * 8L) / played
            }
            if (kbps < BITRATE_MIN_KBPS || kbps > BITRATE_MAX_KBPS) return
            samples[cursor] = kbps
            cursor = (cursor + 1) % BITRATE_SAMPLE_COUNT
            if (filled < BITRATE_SAMPLE_COUNT) filled++
            var sum = 0L
            for (i in 0 until filled) sum += samples[i]
            val avg = (sum / filled).toInt()
            // 分片加载频率不高（秒级），可以直接回主线程刷新
            mainHandler.post {
                measuredBitrateKbps = avg
                refreshStats()
            }
        }

        /** 换集 / 切轨时清空：旧片源的码率不能带进新片源。 */
        fun reset() {
            filled = 0
            cursor = 0
            lastLoadPosMs = -1L
            measuredBitrateKbps = 0
        }
    }

    init {
        attachFrameRateMeter()
        attachBitrateMeter()
    }

    @OptIn(UnstableApi::class)
    private fun attachFrameRateMeter() {
        player.setVideoFrameMetadataListener(frameRateMeter)
    }

    @OptIn(UnstableApi::class)
    private fun attachBitrateMeter() {
        player.addAnalyticsListener(bitrateMeter)
    }

    /**
     * 合并轨道静态信息与实测值：
     * - 帧率：优先轨道上报值，取不到才用实测渲染帧率；
     * - 码率：优先实测值（真实），测不到（如一次性加载的本地文件）才用轨道声明值，
     *   此时 [PlayerStats.bitrateEstimated] 为 false（声明值不需要标注估算）。
     */
    private fun refreshStats() {
        val bitrate = if (measuredBitrateKbps > 0) measuredBitrateKbps else trackBitrateKbps
        playerStats.value = PlayerStats(
            resolution = trackResolution,
            codec = trackCodec,
            bitrateKbps = bitrate,
            bitrateEstimated = trackBitrateKbps <= 0 && measuredBitrateKbps > 0,
            frameRate = if (trackFrameRate > 0f) trackFrameRate else measuredFrameRate,
        )
    }

    /**
     * 低帧率监测：每帧率采样刷新时被调用（来自 [frameRateMeter] 的主线程回投）。
     *
     * 仅当「自动降级」开启、当前处于某个超分档位、且实测帧率持续低于 [LOW_FPS_THRESHOLD]
     * 超过 [LOW_FPS_SUSTAIN_MS] 时，自动把档位降一档（质量→性能→关闭）并提示用户。
     * 这样既能在设备性能不足时主动改善卡顿，又不会反复抖动（每次降级后清空计时，也绝不自动升档）。
     */
    private fun monitorFrameRate() {
        if (!autoEnhancementOptimize) {
            lowFpsSinceMs = 0L
            return
        }
        val mode = enhancementMode.value
        if (mode == VideoEnhancementMode.OFF) {
            lowFpsSinceMs = 0L
            return
        }
        val fps = measuredFrameRate
        // 0（尚未测到）或异常高值都不计入；只盯「明显偏低」的区间
        if (fps < 1f || fps >= LOW_FPS_THRESHOLD) {
            lowFpsSinceMs = 0L
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (lowFpsSinceMs == 0L) {
            lowFpsSinceMs = now
            return
        }
        if (now - lowFpsSinceMs >= LOW_FPS_SUSTAIN_MS) {
            autoDowngradeEnhancement()
            lowFpsSinceMs = 0L
        }
    }

    /**
     * 按设备性能自动降一档画质增强，并提示用户当前帧率。
     *
     * 受 [autoEnhancementDowngradeFloor] 约束——自动降级不会低于该档：
     * - 下限为性能模式（默认）时，质量档会自动降到性能档，但**不会自动彻底关闭**，把最终开关留给用户；
     *   若已在性能档（或下限）仍偏低，仅提示，不越权替用户关闭。
     * - 下限为关闭时，才允许一路降到关闭画质增强（原行为）。
     */
    private fun autoDowngradeEnhancement() {
        val cur = enhancementMode.value
        val floor = autoEnhancementDowngradeFloor
        val next = when {
            cur == VideoEnhancementMode.QUALITY -> VideoEnhancementMode.PERFORMANCE
            cur == VideoEnhancementMode.PERFORMANCE && floor == VideoEnhancementMode.OFF -> VideoEnhancementMode.OFF
            else -> cur // 已达下限，无法继续自动降级
        }
        val fpsText = measuredFrameRate.toInt().toString()
        if (next != cur) {
            setEnhancementMode(next)
            Toast.makeText(
                context,
                if (next == VideoEnhancementMode.PERFORMANCE) {
                    "画质增强帧率过低（约 ${fpsText} fps），已自动切换为性能模式"
                } else {
                    "画质增强帧率过低（约 ${fpsText} fps），已自动关闭"
                },
                Toast.LENGTH_LONG,
            ).show()
        } else {
            // 已到自动降级下限仍偏低：仅提示，不自动关闭，避免越权替用户关掉画质增强。
            Toast.makeText(
                context,
                "画质增强帧率仍偏低（约 ${fpsText} fps），可手动降低或关闭",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * 当拖动Slider或屏幕时，更新Slider进度条位置
     */
    override val onSeeking: (Float) -> Unit
        get() = {
            controlUiLastInteractionMs = 0
            isSeeking.value = true
            isEnded.value = false
            if (!isControlUiVisible.value) showControlUi()
            this.videoProgress.value = it
        }

    /**
     * 当拖动Slider或屏幕结束时，更新视频播放位置
     */
    override val onSeeked: () -> Unit
        get() = {
            isSeeking.value = false
            player.seekTo((player.duration * videoProgress.value).toLong())
        }

    /**
     * 当点Slider时，更改进度条的值和视频播放进度
     */
    override val onClickSlider: (progress: Float) -> Unit
        get() = { progress ->
            controlUiLastInteractionMs = 0
            isEnded.value = false
            this.videoProgress.value = progress
            player.seekTo((player.duration * videoProgress.value).toLong())
        }

    override fun onChangeVolume(value: Float) {
        isChangingVolume.value = true
        volumeBrightnessProgress.value = value
    }

    override fun onChangeBrightness(value: Float) {
        isChangingBrightness.value = true
        volumeBrightnessProgress.value = value
    }

    override fun onChanged() {
        isChangingVolume.value = false
        isChangingBrightness.value = false
    }

    override fun onLongPress() {
        isLongPress.value = true
        val currentSpeed = player.playbackParameters.speed
        control.setPlaybackSpeed(currentSpeed * 2)
    }

    override fun onDisLongPress() {
        isLongPress.value = false
        val currentSpeed = player.playbackParameters.speed
        control.setPlaybackSpeed(currentSpeed.div(2))
    }

    override val control = object : VideoPlayerControl {
        override fun play() {
            controlUiLastInteractionMs = 0
            player.play()
        }

        override fun pause() {
            controlUiLastInteractionMs = 0
            player.pause()
        }

        override fun forward() {
            controlUiLastInteractionMs = 0
            player.seekForward()
        }

        override fun rewind() {
            controlUiLastInteractionMs = 0
            player.seekBack()
        }

        override fun skip(skipMs: Long) {
            controlUiLastInteractionMs = 0
            if (videoDurationMs.value == 0L) return
            val positionMs = (player.currentPosition + skipMs).coerceIn(0, videoDurationMs.value)
            player.seekTo(positionMs)
        }

        override fun retry() {
            isError.value = false
            isLoading.value = true
            player.prepare()
        }

        override fun setFullscreen(value: Boolean) {
            controlUiLastInteractionMs = 0
            isFullscreen.value = value
        }

        override fun setVideoResize(mode: ResizeMode) {
            controlUiLastInteractionMs = 0
            videoResizeMode.value = mode
            // 同步「画面尺寸」行显示的当前值：宿主一进入播放页就会按偏好设置一次，
            // 若不同步文字，「更多」面板里会一直显示默认的「适应」而不管实际比例。
            resizeText.value = resizeLabel(mode)
        }

        override fun setPlaybackSpeed(speed: Float) {
            player.setPlaybackSpeed(speed)
            // 同步倍速文字：底栏与「更多」面板都读它。长按倍速（onLongPress）也走这里，
            // 否则手势改了速度而界面上的文字还停在旧值。
            speedText.value = "${speed}x"
        }
    }

    override fun setLoading(loading: Boolean) {
        isLoading.value = loading
    }

    private var pollVideoPositionJob: Job? = null
    private var controlUiLastInteractionMs = 0L

    override fun hideControlUi() {
        controlUiLastInteractionMs = 0
        isControlUiVisible.value = false
        pollVideoPositionJob?.cancel()
        pollVideoPositionJob = null
    }

    override fun showControlUi() {
        controlUiLastInteractionMs = 0
        isControlUiVisible.value = true
        pollVideoPositionJob?.cancel()
        pollVideoPositionJob = coroutineScope.launch {
            while (true) {
                val dur = videoDurationMs.value
                if (dur > 0) {
                    val cur = player.currentPosition
                    // 防止「播放进度超出视频总长度」（超分切换重建特效管线时，currentPosition 可能瞬间
                    // 读到大于时长的异常值）：展示用的进度与位置一律以真实时长为上界夹紧。
                    val pos = if (cur > dur) dur else cur
                    videoPositionMs.value = pos
                    if (!isSeeking.value) {
                        // 播放进度
                        videoProgress.value =
                            (pos.toFloat() / dur).coerceIn(0f, 1f)
                        // 缓冲进度
                        videoBufferedProgress.value =
                            (player.bufferedPosition / dur.toFloat()).coerceIn(0f, 1f)
                    }
                } else {
                    if (!isSeeking.value) {
                        videoProgress.value = 0f
                        videoBufferedProgress.value = 0f
                    }
                }
                // 任一功能面板（倍速 / 画面尺寸 / 选集 / 更多）打开时暂停自动隐藏计时：
                // 否则用户慢慢挑选项时，6 秒一到控制层会连面板一起消失。
                val panelOpen = isSpeedUiVisible.value || isResizeUiVisible.value ||
                    isEpisodeUiVisible.value || isMoreUiVisible.value
                controlUiLastInteractionMs = if (panelOpen) {
                    0L
                } else {
                    controlUiLastInteractionMs + videoPositionPollInterval
                }

                delay(videoPositionPollInterval)
                if (controlUiLastInteractionMs >= hideControllerAfterMs) {
                    hideControlUi()
                    break
                }
            }
        }
    }

    override fun setSpeedText(text: String) {
        speedText.value = text
    }

    override fun setResizeText(text: String) {
        resizeText.value = text
    }

    override fun hideOptionsUi() {
        isOptionsUiVisible.value = true
    }

    override fun showOptionsUi() {
        isOptionsUiVisible.value = false
    }

    override fun showSpeedUi() {
        // 注意：不能 hideControlUi()，否则会把整个控制层（含本面板）一起收起，表现为「点了倍速按钮 UI 消失」。
        // 面板渲染于控制层内部，保持控制层可见即可；showControlUi 顺便重置自动隐藏计时器。
        isSpeedUiVisible.value = true
        showControlUi()
    }

    override fun hideSpeedUi() {
        isSpeedUiVisible.value = false
    }

    override fun showResizeUi() {
        isResizeUiVisible.value = true
        showControlUi()
    }

    override fun hideResizeUi() {
        isResizeUiVisible.value = false
    }

    override fun showEpisodeUi() {
        isEpisodeUiVisible.value = true
        showControlUi()
    }

    override fun hideEpisodeUi() {
        isEpisodeUiVisible.value = false
    }

    override fun showMoreUi(focus: MoreFocus) {
        moreFocus.value = focus
        isMoreUiVisible.value = true
        // 同时收起其它面板，避免「更多」面板叠在倍速 / 选集之上
        isSpeedUiVisible.value = false
        isResizeUiVisible.value = false
        isEpisodeUiVisible.value = false
        showControlUi()
    }

    override fun hideMoreUi() {
        isMoreUiVisible.value = false
        // 聚焦只在「本次打开」有效：关掉面板就复位，下次从 ⋮ 进入时选集是收起的
        moreFocus.value = MoreFocus.NONE
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying.value = isPlaying
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        // 仅在时长有效（>0）时更新，避免超分切换重建管线期间读到 0 / TIME_UNSET 导致画面异常。
        if (playbackState == Player.STATE_READY) {
            val d = player.duration
            if (d > 0) videoDurationMs.value = d
        }
        this.playbackState.value = playbackState
        when (playbackState) {
            Player.STATE_IDLE -> Unit
            Player.STATE_BUFFERING -> isLoading.value = true
            Player.STATE_READY -> isLoading.value = false
            Player.STATE_ENDED -> isEnded.value = true
        }
    }

    /**
     * 记录「失去焦点前是否真的在播放」。仅当为 true 时，焦点恢复后才自动续播；
     * 否则（用户已手动暂停 / 本就未播放）焦点恢复也不应自动唤醒，避免「在别的 App
     * 看视频、对方结束后本播放器被自动唤起在后台播放」的诡异现象。
     */
    private var wasPlayingBeforeFocusLoss = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // 只有之前因焦点被抢占而正在播放时才恢复；手动暂停 / 未播放的都不恢复。
                if (wasPlayingBeforeFocusLoss) {
                    wasPlayingBeforeFocusLoss = false
                    control.play() // 重新获得焦点，恢复播放
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeFocusLoss = false
                control.pause() // Permanent loss of audio focus，Pause playback immediately
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeFocusLoss = player.isPlaying
                control.pause() // 暂时失去音频焦点，暂停播放
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 暂时失去音频焦点，但可继续播放，系统默认降低音量；
                // 记下当前是否在播，便于焦点彻底恢复时（必要时）续播。
                wasPlayingBeforeFocusLoss = player.isPlaying
            }
        }
    }

    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).run {
            setAudioAttributes(AudioAttributes.Builder().run {
                setUsage(AudioAttributes.USAGE_MEDIA)
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                build()
            })
            setAcceptsDelayedFocusGain(true)
            setOnAudioFocusChangeListener(audioFocusChangeListener)
            build()
        }

    private val orientationEventListener = object : OrientationEventListener(context) {
        private var currentOrientation = 270
        private val activity = context as Activity
        override fun onOrientationChanged(orientation: Int) {
            if (!isFullscreen.value || orientation == ORIENTATION_UNKNOWN) {
                return
            }

            if (orientation in 261..279) {
                if (currentOrientation == 270) return
                currentOrientation = 270

                coroutineScope.launch {
                    delay(300)
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }

            } else if (orientation in 81..99) {
                if (currentOrientation == 90) return
                currentOrientation = 90

                coroutineScope.launch {
                    delay(300)
                    activity.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            }
        }

    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        // 切集时 ExoPlayer 会先上报 VideoSize.UNKNOWN(0x0) 再解析出新尺寸：
        // 保留上一次的已知尺寸，避免「换集瞬间画面按 0 比例兜底被放大裁切」的闪烁；
        // 只有从未拿到过尺寸（真·无视频轨）时才采纳 0x0。
        if (videoSize.width == 0 || videoSize.height == 0) {
            if (this.videoSize.value.width == 0 && this.videoSize.value.height == 0) {
                this.videoSize.value = videoSize
            }
            return
        }
        this.videoSize.value = videoSize
    }

    override fun onPlayerError(error: PlaybackException) {
        // 超分（视频增强）失败时优先自愈：自动降级/关闭并恢复播放，不让播放器卡在错误页。
        if (tryRecoverFromEnhancementFailure(error)) return
        isError.value = true
        onError?.invoke()
    }

    /**
     * 超分（视频增强）导致播放失败时的自动降级/关闭并恢复播放。
     *
     * Media3 的 setVideoEffects 是异步投递到渲染线程的，着色器在渲染线程才真正加载/编译，
     * 一旦失败会包装成 VideoFrameProcessingException 并让整个播放被判定为失败。
     * 这里识别这类失败，沿「质量 -> 性能 -> 关闭」降级链自动退让并重新 prepare 恢复播放。
     *
     * @return true 表示已自行处理（不再走普通错误流程，也就不会误触发宿主的自动切线路）
     */
    private fun tryRecoverFromEnhancementFailure(error: PlaybackException): Boolean {
        val current = enhancementMode.value
        if (current == VideoEnhancementMode.OFF) return false
        if (!isVideoEffectFailure(error)) return false

        val fallback =
            if (current == VideoEnhancementMode.QUALITY) VideoEnhancementMode.PERFORMANCE
            else VideoEnhancementMode.OFF

        // 先移除出问题的 effect graph，再重新 prepare 恢复播放（尽量保留原进度）。
        val positionMs = player.currentPosition
        setEnhancementMode(fallback)

        Toast.makeText(
            context,
            if (fallback == VideoEnhancementMode.OFF) {
                "画质增强不可用，已自动关闭并恢复播放"
            } else {
                "高质量超分不可用，已自动降级为性能模式"
            },
            Toast.LENGTH_LONG,
        ).show()

        isError.value = false
        isLoading.value = true
        player.prepare()
        if (positionMs > 0) player.seekTo(positionMs)
        player.playWhenReady = true
        return true
    }

    /**
     * 判断播放错误是否由视频特效（超分）管线引起：沿 cause 链查找特效相关异常。
     * 用类名匹配而非类型判断，避免依赖可能变化的内部类（如 VideoSink.VideoSinkException）。
     */
    private fun isVideoEffectFailure(error: Throwable): Boolean {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 16) {
            val name = cause.javaClass.name
            if (name.contains("VideoFrameProcessingException") || name.contains("VideoSinkException")) {
                return true
            }
            cause = cause.cause
            depth++
        }
        return false
    }

    override fun onRenderedFirstFrame() {
        isError.value = false
        isEnded.value = false
    }

    // ===== 视频画质增强 / 截图 / 信息层 / 进度预览 =====

    override fun setEnhancementMode(mode: VideoEnhancementMode) {
        enhancementController.setMode(mode)
        enhancementMode.value = mode
        // 用户/自动刚切换档位：清空低帧率计时，避免刚升档就被旧采样误判自动降回。
        lowFpsSinceMs = 0L
        // 超分（视频增强）开关会重建特效管线，画面可能短暂复位，导致时长 / 进度读数瞬间不一致
        // （表现为「播放进度超出视频总长度」）。特效稳定后重新对齐：仅在时长尚未成功读取时补读，
        // 并把展示进度收敛到真实时长以内。
        coroutineScope.launch {
            delay(500)
            val d = player.duration
            if (d > 0 && videoDurationMs.value <= 0) videoDurationMs.value = d
            val cur = player.currentPosition
            if (videoDurationMs.value > 0 && cur > videoDurationMs.value) {
                videoPositionMs.value = videoDurationMs.value
            }
        }
    }

    override fun setViewportSize(width: Int, height: Int) {
        enhancementController.setViewportSize(width, height)
    }

    override fun setColorAssistMode(mode: ColorAssistMode) {
        enhancementController.setColorMode(mode)
        colorAssistMode.value = mode
    }

    override fun setLoopMode(mode: LoopMode) {
        loopMode.value = mode
        // 只有「单集循环」能交给播放器自己（REPEAT_MODE_ONE）；
        // 「列表循环」需要跨集切换，由宿主在播放结束回调里处理。
        player.repeatMode =
            if (mode == LoopMode.SINGLE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override fun setMirrorEnabled(enabled: Boolean) {
        mirrorEnabled.value = enabled
        applyVideoTransform()
    }

    override fun setVideoTransform(scale: Float, rotationDeg: Float) {
        // 收敛范围：太小看不清、太大单帧内存与绘制开销都失控；旋转归一化到 (-180, 180]。
        val clamped = if (scale.isFinite()) scale.coerceIn(MIN_VIDEO_SCALE, MAX_VIDEO_SCALE) else 1f
        val deg = if (rotationDeg.isFinite()) rotationDeg % 360f else 0f
        val normalized = when {
            deg > 180f -> deg - 360f
            deg <= -180f -> deg + 360f
            else -> deg
        }
        if (clamped == videoScale.value && normalized == videoRotation.value) return
        videoScale.value = clamped
        videoRotation.value = normalized
        applyVideoTransform()
    }

    override fun resetVideoTransform() {
        setVideoTransform(1f, 0f)
    }

    /**
     * 把「镜像 / 缩放 / 旋转」落到承载画面的 View 上。
     *
     * 必须是 **TextureView** 才有效：SurfaceView 的内容不在应用窗口里（靠打孔合成），
     * 自 Android N 起只同步「位置 + 正向缩放」，**旋转与负缩放（镜像）都会被丢弃**
     * （CameraX PreviewView、VideoView.setRotation 都是同一个坑）。
     * TextureView 走正常 View 绘制链路，旋转 / 镜像 / 缩放全部支持。
     * 只影响视频层，弹幕与控制层不受影响。
     */
    private fun applyVideoTransform() {
        val view = videoViewRef ?: return
        val scale = videoScale.value
        val mirror = if (mirrorEnabled.value) -1f else 1f
        view.scaleX = scale * mirror
        view.scaleY = scale
        view.rotation = videoRotation.value
    }

    /** 换集后重建特效管线的延迟任务；换集很快时只保留最后一次。 */
    private var enhancementReapplyJob: Job? = null

    /** 最近一次装载（prepare）过的片源地址，见 [shouldLoadSource]。 */
    private var loadedUrl: String? = null

    override fun shouldLoadSource(url: String): Boolean {
        val isNew = loadedUrl != url
        loadedUrl = url
        return isNew
    }

    override fun onMediaSourceSwitch() {
        // 清掉上一集遗留状态，避免新集沿用旧读数（时长 / 进度 / 错误态 / 帧率采样）。
        frameRateMeter.reset()
        measuredFrameRate = 0f
        lowFpsSinceMs = 0L
        isError.value = false
        isEnded.value = false
        isLoading.value = true
        videoPositionMs.value = 0L
        videoProgress.value = 0f
        videoBufferedProgress.value = 0f
        // 顺序重绑 surface：clear → set 在同一条主线程消息内完成，结果确定。
        // 这是「全屏换集画面过大」与「开启超分换集不出帧 / 黑屏」的根治手段。
        rebindVideoSurface()
    }

    override fun rebindVideoSurface() {
        val view = videoViewRef ?: return
        runCatching {
            when (view) {
                is TextureView -> {
                    player.clearVideoTextureView(view)
                    player.setVideoTextureView(view)
                }
                is SurfaceView -> {
                    player.clearVideoSurface()
                    player.setVideoSurfaceView(view)
                }
            }
        }
    }

    override fun refreshEnhancementForNewSource() {
        val mode = enhancementMode.value
        val color = colorAssistMode.value
        // 色彩辅助同样挂在 Media3 特效管线上，也需要随新片源重建，否则换集后色彩可能丢失。
        if (mode == VideoEnhancementMode.OFF && color == ColorAssistMode.OFF) return
        enhancementReapplyJob?.cancel()
        enhancementReapplyJob = coroutineScope.launch {
            // 等 prepare 与首帧上屏的窗口过去再重建，避免与管线初始化抢资源。
            delay(ENHANCEMENT_REAPPLY_DELAY_MS)
            // 期间用户可能已手动关闭超分 / 色彩辅助，重建前再确认一次。
            if (enhancementMode.value == VideoEnhancementMode.OFF &&
                colorAssistMode.value == ColorAssistMode.OFF
            ) {
                return@launch
            }
            enhancementController.reapply()
        }
    }

    override fun setShowStats(value: Boolean) {
        showStats.value = value
    }

    override fun setScrubPreviewPositionMs(ms: Long?) {
        scrubPreviewPositionMs.value = ms
    }

    override fun setVideoSurfaceView(view: View) {
        videoViewRef = view
        // 换绑 / 新建 View 时把当前变换带上：镜像、缩放、旋转都要在新 View 上立即生效。
        applyVideoTransform()
    }

    override var screenshotDirProvider: (() -> java.io.File)? = null
    override var onScreenshotSaved: ((java.io.File, android.net.Uri) -> Unit)? = null

    override suspend fun screenshot(): android.net.Uri? {
        val view = videoViewRef ?: return null
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return null
        val bitmap = when (view) {
            // TextureView 直接取当前纹理（已包含镜像 / 缩放 / 旋转后的结果）；
            // PixelCopy 只接受 SurfaceView / Window，对 TextureView 会直接失败。
            is TextureView -> view.getBitmap(w, h) ?: return null
            is SurfaceView -> {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val done = CompletableDeferred<Boolean>()
                PixelCopy.request(view, bmp, { result ->
                    done.complete(result == PixelCopy.SUCCESS)
                }, Handler(Looper.getMainLooper()))
                if (!done.await()) return null
                bmp
            }
            else -> return null
        }
        // 落盘走 IO 线程：截图是整帧 PNG，主线程写文件会造成明显卡顿。
        return withContext(Dispatchers.IO) {
            // 宿主注入的目录优先（一般是「用户选择的下载目录 /img」），否则回退应用私有 Pictures。
            val dir = screenshotDirProvider?.invoke()
                ?: java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "tuanyingshi")
            if (!dir.exists() && !dir.mkdirs()) {
                notifyScreenshotFailed()
                return@withContext null
            }
            val file = java.io.File(dir, "shot_${System.currentTimeMillis()}.png")
            val ok = try {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            } catch (_: Throwable) {
                false
            }
            if (!ok) {
                runCatching { file.delete() }
                notifyScreenshotFailed()
                return@withContext null
            }
            // 扫描进媒体库，使文件管理器 / 相册能看到（宿主目录在公共存储区时尤其需要）。
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            val uri = android.net.Uri.fromFile(file)
            onScreenshotSaved?.invoke(file, uri)
            uri
        }
    }

    /** 截图失败时给个可见反馈（原实现静默失败，用户点了没反应）。 */
    private fun notifyScreenshotFailed() {
        mainHandler.post {
            runCatching { Toast.makeText(context, "截图失败", Toast.LENGTH_SHORT).show() }
        }
    }

    // ===== 进度帧预览 =====
    private var sourceUrl: String? = null
    private var sourceHeaders: Map<String, String> = emptyMap()
    private val previewCache = LinkedHashMap<Long, Bitmap?>(16, 0.75f, true)
    /** 连续取帧失败计数：达到 [PREVIEW_MAX_CONSECUTIVE_FAILS] 后停止尝试；换源时清零。 */
    private var previewFailCount = 0
    /** HLS 分片取帧器（惰性创建：只有确认是 m3u8 源才会用到）。 */
    private var hlsPreview: HlsFramePreviewFetcher? = null

    override fun setMediaSource(url: String, headers: Map<String, String>) {
        if (sourceUrl != url) {
            synchronized(previewCache) { previewCache.clear() }
            previewFailCount = 0
            // 换源：清掉上一个源的分片缓存与流量计数
            synchronized(this) { hlsPreview?.reset() }
        }
        sourceUrl = url
        sourceHeaders = headers
    }

    /**
     * 取 [positionMs] 处的预览帧。按 [PREVIEW_BUCKET_MS] 分桶并缓存（含失败结果，避免同一个点反复重试卡 UI）。
     *
     * 注意：远程源走的是 App 内 OkHttp 代理，而 MediaMetadataRetriever 直连源站，
     * 因此受 WAF / 鉴权保护的源可能取帧失败——此时返回 null，UI 降级为仅显示时间。
     */
    override suspend fun loadFramePreview(positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val url = sourceUrl ?: return@withContext null
        val bucket = (positionMs / PREVIEW_BUCKET_MS) * PREVIEW_BUCKET_MS
        val (hit, cached) = takeCachedPreview(bucket)
        if (hit) return@withContext cached

        // 先走原生 MediaMetadataRetriever（本地 / 已下载 / 直链 mp4 有效且零额外流量）。
        // 该源已连续多次失败 → 判定不支持，不再反复发起阻塞式取帧。
        val direct = if (previewFailCount >= PREVIEW_MAX_CONSECUTIVE_FAILS) {
            null
        } else {
            runCatching { extractFrame(url, bucket) }.getOrNull().also {
                if (it != null) previewFailCount = 0 else previewFailCount++
            }
        }
        // 原生取不到且是 HLS 源 → 走「App 内 OkHttp 链路」拉分片本地解码，
        // 这是在线 m3u8 / 防盗链源能出缩略图的唯一可行路径。
        val bitmap = direct ?: if (isHlsSource(url)) hlsFramePreview().fetch(url, sourceHeaders, bucket) else null

        putPreview(bucket, bitmap)
        bitmap
    }

    private fun hlsFramePreview(): HlsFramePreviewFetcher = synchronized(this) {
        hlsPreview ?: HlsFramePreviewFetcher(context).also { hlsPreview = it }
    }

    private fun isHlsSource(url: String): Boolean =
        url.contains(".m3u8", ignoreCase = true) || url.contains("application/x-mpegURL", ignoreCase = true)

    /**
     * 取帧：本地文件直接按路径取；远程源先带请求头尝试，失败再退化成不带头。
     * 部分源（CDN / 防盗链）会因自定义头被拒，不带头反而能取到，所以保留两种路径。
     */
    private fun extractFrame(url: String, bucket: Long): Bitmap? {
        if (url.startsWith("/")) return grabFrame(url, null, bucket)
        grabFrame(url, sourceHeaders, bucket)?.let { return it }
        if (sourceHeaders.isEmpty()) return null
        return grabFrame(url, null, bucket)
    }

    private fun grabFrame(url: String, headers: Map<String, String>?, bucket: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (headers == null) {
                retriever.setDataSource(url)
            } else {
                @Suppress("DEPRECATION")
                retriever.setDataSource(url, headers)
            }
            retriever.getFrameAtTime(
                bucket * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** 命中缓存返回 (true, 缓存值)；未命中返回 (false, null)。缓存值可能为 null（已确认取不到）。 */
    private fun takeCachedPreview(bucket: Long): Pair<Boolean, Bitmap?> {
        synchronized(previewCache) {
            return if (previewCache.containsKey(bucket)) true to previewCache[bucket]
            else false to null
        }
    }

    private fun putPreview(bucket: Long, bitmap: Bitmap?) {
        synchronized(previewCache) {
            previewCache[bucket] = bitmap
            if (previewCache.size > PREVIEW_CACHE_MAX) {
                val it = previewCache.entries.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        val format = if (group != null && group.mediaTrackGroup.length > 0) {
            group.mediaTrackGroup.getFormat(0)
        } else {
            null
        }
        trackResolution = if (format != null && format.width > 0 && format.height > 0) {
            "${format.width}x${format.height}"
        } else {
            "—"
        }
        trackCodec = format?.codecs ?: format?.sampleMimeType ?: "—"
        // 平均码率优先；HLS 常只给峰值或不给，退而用 peakBitrate / 声明 bitrate，
        // 三者都拿不到时由 bitrateMeter 实测兜底（见 refreshStats）。
        trackBitrateKbps = when {
            format == null -> 0
            format.averageBitrate > 0 -> format.averageBitrate / 1000
            format.peakBitrate > 0 -> format.peakBitrate / 1000
            format.bitrate > 0 -> format.bitrate / 1000
            else -> 0
        }
        trackFrameRate = if (format != null && format.frameRate > 0f) format.frameRate else 0f
        frameRateMeter.reset()
        measuredFrameRate = 0f
        bitrateMeter.reset()
        refreshStats()
    }

    override fun registerListener() {
        if (isAutoOrientation) {
            orientationEventListener.enable()
        }
        audioManager.requestAudioFocus(focusRequest)
    }

    override fun unregisterListener() {
        if (isAutoOrientation) {
            orientationEventListener.disable()
        }
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

}


interface VideoPlayerState {
    val player: ExoPlayer
    val audioManager: AudioManager
    val window: Window

    val videoSize: State<VideoSize>
    val videoResizeMode: State<ResizeMode>
    /** 画面缩放倍数（双指缩放）；1f 为原始大小。 */
    val videoScale: State<Float>
    /** 画面旋转角度（双指旋转），单位度；0f 为不旋转。 */
    val videoRotation: State<Float>

    /** 最近一次双指缩放 / 旋转事件的时间戳（uptimeMillis）：单指拖动据此让位，避免手势打架。 */
    var lastTransformGestureMs: Long
    val videoPositionMs: State<Long>    /*当控制组件显示时才会更新这个值，获取视频当前进度用player.currentPosition*/
    val videoDurationMs: State<Long>    /*视频时长*/

    val isFullscreen: State<Boolean>
    val isPlaying: State<Boolean>
    val isLoading: State<Boolean>
    val isEnded: State<Boolean>
    val isError: State<Boolean>
    /** 播放错误回调（ExoPlayer onPlayerError 时触发），供宿主做自动切线路等处理。 */
    var onError: (() -> Unit)?
    val playbackState: State<Int>

    val isSeeking: State<Boolean>
    val isLongPress: State<Boolean>
    val isChangingVolume: State<Boolean>
    val isChangingBrightness: State<Boolean>
    val videoProgress: State<Float> /*进度条百分比 0f - 1f*/
    val videoBufferedProgress: State<Float>
    val volumeBrightnessProgress: State<Float>

    val onSeeking: (dragProcess: Float) -> Unit     // 当拖动进度条时调用
    val onSeeked: () -> Unit                        // 当拖动进度条结束时调用
    val onClickSlider: (progress: Float) -> Unit // 当点击进度条时调用

    val speedText: State<String>
    val resizeText: State<String>

    val isOptionsUiVisible: State<Boolean>
    val isControlUiVisible: State<Boolean>
    val isSpeedUiVisible: State<Boolean>
    val isResizeUiVisible: State<Boolean>
    val isEpisodeUiVisible: State<Boolean>
    val control: VideoPlayerControl

    fun setLoading(loading: Boolean)

    fun onChangeVolume(value: Float)
    fun onChangeBrightness(value: Float)
    fun onChanged()

    fun onLongPress()
    fun onDisLongPress()

    fun registerListener()
    fun unregisterListener()

    fun setSpeedText(text: String)
    fun setResizeText(text: String)

    fun hideOptionsUi()
    fun showOptionsUi()

    fun hideControlUi()
    fun showControlUi()

    fun showSpeedUi()
    fun hideSpeedUi()

    fun showResizeUi()
    fun hideResizeUi()

    fun showEpisodeUi()
    fun hideEpisodeUi()

    /** 「更多」面板（小窗播放 / 投屏 / 定时关闭 / 色彩辅助 / 倍速 / 画面尺寸 / 选集）是否可见。 */
    val isMoreUiVisible: State<Boolean>
    /** 本次打开「更多」时要聚焦的分区；面板关闭后复位为 [MoreFocus.NONE]。 */
    val moreFocus: State<MoreFocus>
    fun showMoreUi(focus: MoreFocus = MoreFocus.NONE)
    fun hideMoreUi()

    // ===== 视频画质增强（Anime4K 超分 / Lanczos 锐化） =====
    /** 当前增强模式（关闭 / 性能 / 质量）。 */
    val enhancementMode: State<VideoEnhancementMode>
    /** 设置增强模式，内部通过 ExoPlayer.setVideoEffects 应用对应 GlEffect。 */
    fun setEnhancementMode(mode: VideoEnhancementMode)
    /** 当前色彩辅助模式（不开启 / 红绿色弱 / 蓝黄色弱 / 灰度 / 高对比）。 */
    val colorAssistMode: State<ColorAssistMode>
    /** 设置色彩辅助模式；与画质增强共用同一条特效管线，可叠加生效。 */
    fun setColorAssistMode(mode: ColorAssistMode)

    /** 当前循环播放模式。 */
    val loopMode: State<LoopMode>
    /** 设置循环播放模式；单集循环直接生效（播放器内置），列表循环由宿主处理跨集。 */
    fun setLoopMode(mode: LoopMode)

    /** 画面是否水平镜像（只影响视频层，弹幕与控件不变）。 */
    val mirrorEnabled: State<Boolean>
    /** 设置画面镜像；直接翻转 SurfaceView，不重建特效管线。 */
    fun setMirrorEnabled(enabled: Boolean)

    /**
     * 后台听视频：应用退到后台（ON_STOP）时是否**不暂停**、继续出声。
     * 宿主把偏好同步进来即可；播放器生命周期回调据此决定是否暂停。
     */
    var backgroundAudioEnabled: Boolean

    /**
     * 是否处于小窗（画中画）。置 true 后 Activity 的 ON_STOP 不再暂停播放——
     * 小窗里画面依旧可见，用户按「小窗播放」就是要接着看，被生命周期回调暂停就成了 bug。
     * 由宿主在**进入小窗之前**置位（避免 ON_STOP 先到），退出小窗时复位。
     */
    var inPipMode: Boolean

    /**
     * 「跳到 App 内其它页面（如播放设置）后返回时自动续播」标记，由宿主在跳转前置 true。
     *
     * 背景：播放器在 ON_STOP 时会暂停播放（后台听视频未开启时）。这个 ON_STOP 既可能来自
     * 「跳到别的页面」，也可能来自「App 退到后台」——后者回来后**不应该**自动播放
     * （用户可能只是切回来看看），所以不能无差别地在 ON_START 里 play()。
     * 宿主只在「自己主动跳走且当时正在播放」时置位（典型：从播放页打开播放设置），
     * 播放器在随后的 ON_START 里消费一次并把播放接回来，实现断点继播。
     */
    var resumeOnReturn: Boolean

    /**
     * 小窗（画中画）里的「双击调整小窗大小」钩子，由宿主注入；为 null 时不处理。
     *
     * Android 12+ 的双击放大 / 缩小是**系统手势**，触摸事件根本不会投递到小窗里的 Activity，
     * 所以这个钩子只在少数仍会把事件交给应用的机型 / ROM 上生效（此时双击=切换小窗尺寸，
     * 而不是播放 / 暂停）。宿主实现见 `PipController.toggleSize`。
     */
    var pipToggleSize: (() -> Unit)?
    /** 设置视口（播放器可绘制区域）尺寸，供 Lanczos 锐化按视口缩放输出。 */
    fun setViewportSize(width: Int, height: Int)

    /**
     * 画质增强「按设备性能自动降级」开关。默认开启。
     * 开启后，若实测渲染帧率持续低于阈值，会自动把超分档位降一档（质量→性能→关闭）并提示用户；
     * 关闭后仅显示帧率、不再自动调整，完全由用户手动控制。
     */
    var autoEnhancementOptimize: Boolean

    /**
     * 画质增强自动降级的「下限」档位：自动降级不会低于此档。
     * 默认 [VideoEnhancementMode.PERFORMANCE]（质量→性能即停，绝不自动彻底关闭）；
     * 设为 [VideoEnhancementMode.OFF] 则允许一路降到关闭。
     */
    var autoEnhancementDowngradeFloor: VideoEnhancementMode

    // ===== 换集 / 换线路 =====

    /**
     * 切换片源（换集 / 换线路 / 换源）时调用：**顺序执行**一次 surface 重绑，并清理上一集遗留状态。
     * 必须在新片源 prepare **之前**调用，让新源一开始就渲染在尺寸正确的 surface 上。
     *
     * 为什么必须重绑：全屏下切集时播放器容器尺寸不变（onSizeChanged 不触发），
     * ExoPlayer 会继续复用旧 surface 与旧特效管线，画面可能仍按上一集的尺寸 / 比例输出
     * （表现为「画面过大，手动缩放一下才正常」），极端情况下（超分开启）管线不再出帧 → 黑屏。
     *
     * 为什么不能改为「销毁重建 SurfaceView」：Compose 中新 View 的 factory 在组合阶段执行、
     * 旧 View 的 onRelease（clearVideoSurface）在 applyChanges 阶段执行，顺序颠倒会让 player
     * 最终处于「无 surface」状态，表现为换集永久黑屏。这里 clear → set 在同一条主线程消息内
     * 顺序执行，结果确定。
     */
    fun onMediaSourceSwitch()

    /**
     * 判断 [url] 是否是一个「尚未装载过的新片源」，顺便把它记为已装载。
     *
     * 返回 false = 同一个片源被重新组合（宿主布局分支切换 / 进出小窗时屏幕最小宽度变化，
     * 播放页在「平板分栏」与「手机竖排」之间切换 → VideoPlayer 被搬到另一分支 →
     * `LaunchedEffect(url)` 重跑）。此时若照常重新 prepare + seekTo(进入页面时的进度)，
     * 播放位置会被打回页面刚打开时的值，表现为「一切到小窗就从头播放」。
     */
    fun shouldLoadSource(url: String): Boolean

    /**
     * 只把当前 surface 重新绑给 player（clear → set，同一条主线程消息内完成），
     * 不触碰任何播放状态。用于「同一片源被重新组合」的场景（见 [shouldLoadSource]）：
     * 此时进度、加载态、帧率采样都还是有效的，重置它们反而会引入假转圈。
     */
    fun rebindVideoSurface()

    /**
     * 换片源后重建超分（视频特效）管线，仅在画质增强开启时生效。
     *
     * Media3 的 effect graph 与片源 / surface 绑定：换集后复用旧管线时，GL 处理链路可能
     * 不再输出新帧（表现为「画面卡住 / 不更新」）。这里在新源 prepare 后延迟一小段时间
     * 重建一次，避免与 prepare 期间的管线初始化抢资源。
     */
    fun refreshEnhancementForNewSource()

    // ===== 截图 =====
    /** 抓取当前渲染帧并保存到相册目录，返回文件 Uri；失败返回 null。 */
    suspend fun screenshot(): android.net.Uri?

    /**
     * 截图保存目录提供器（由宿主注入）。
     *
     * 为 null（未注入）时回退到应用私有 Pictures 目录。宿主可用它把截图
     * 定向到「用户选择的下载目录 /img」等位置。
     *
     * 每次截图都会调用一次，因此宿主可以在其中重新读取最新的用户设置。
     */
    var screenshotDirProvider: (() -> java.io.File)?

    /**
     * 截图保存成功回调（宿主用于发状态栏通知等），参数为保存的文件与对应 Uri。
     * 注意：在 IO 线程回调，宿主需自行切线程再做 UI 操作。
     */
    var onScreenshotSaved: ((java.io.File, android.net.Uri) -> Unit)?

    // ===== 播放信息层 =====
    val showStats: State<Boolean>
    fun setShowStats(value: Boolean)
    /** 实时播放信息（分辨率 / 编码 / 码率 / 帧率等），供信息层展示。 */
    val playerStats: State<PlayerStats>

    // ===== 进度帧预览 =====
    /** 拖动进度条时预览的目标位置（毫秒）；非拖动时为 null。 */
    val scrubPreviewPositionMs: State<Long?>
    fun setScrubPreviewPositionMs(ms: Long?)

    /** 记录当前片源地址与请求头，供 [loadFramePreview] 取帧；换源时应重新调用。 */
    fun setMediaSource(url: String, headers: Map<String, String>)

    /**
     * 抓取 [positionMs] 处的预览帧（按秒分桶并缓存）。
     * 源不支持或取帧失败返回 null——UI 必须降级为仅显示时间，不能空白等待。
     */
    suspend fun loadFramePreview(positionMs: Long): Bitmap?

    /** 由视频渲染层在创建承载画面的 View（TextureView / SurfaceView）时回调，供截图与变换使用。 */
    fun setVideoSurfaceView(view: View)

    /**
     * 设置画面变换：缩放 [scale]（0.5–4，越界自动收敛）与旋转 [rotationDeg]（度）。
     * 双指手势每帧都会调，因此只做轻量赋值，不重建任何播放资源。
     */
    fun setVideoTransform(scale: Float, rotationDeg: Float)

    /** 一键还原：缩放回到 1、旋转回到 0（镜像等其它开关不受影响）。 */
    fun resetVideoTransform()
}

interface VideoPlayerControl {
    fun play()
    fun pause()

    fun forward()
    fun rewind()
    fun skip(skipMs: Long)

    fun retry()

    fun setFullscreen(value: Boolean)
    fun setVideoResize(mode: ResizeMode)
    fun setPlaybackSpeed(speed: Float)
}

/** 实时播放信息，供播放信息层展示。 */
data class PlayerStats(
    val resolution: String = "—",
    val codec: String = "—",
    val bitrateKbps: Int = 0,
    /** 码率为实测估算值（轨道未声明码率时按分片字节数推算），UI 应加「≈」标注。 */
    val bitrateEstimated: Boolean = false,
    val frameRate: Float = 0f,
)

/** 实测码率滑动平均的样本数（分片数）。 */
private const val BITRATE_SAMPLE_COUNT = 6

/** 实测码率样本合理下限（kbps）：低于此多为极小分片 / 初始化段，丢弃。 */
private const val BITRATE_MIN_KBPS = 32L

/** 实测码率样本合理上限（kbps）：高于此多为 seek 后的超大分片，丢弃。 */
private const val BITRATE_MAX_KBPS = 200_000L

/** fallback 采样窗口下限（毫秒）：两次 load 间播得太短（seek / 卡顿）不算数。 */
private const val BITRATE_MIN_WINDOW_MS = 200L

/** fallback 采样窗口上限（毫秒）：间隔过长说明早已缓冲满，与码率无关，丢弃。 */
private const val BITRATE_MAX_WINDOW_MS = 5000L

/** 实测帧率采样窗口帧数。 */
private const val FRAME_RATE_SAMPLE_COUNT = 24

/** 实测帧率对外刷新间隔（毫秒）：播放线程高频回调，避免每帧改 Compose 状态。 */
private const val FRAME_RATE_PUBLISH_INTERVAL_MS = 1000L

/** 画质增强自动降级阈值：实测渲染帧率低于此值（且持续一段时间）则自动降一档。 */
private const val LOW_FPS_THRESHOLD = 20f

/** 画质增强低帧率需持续多久（毫秒）才触发自动降级，避免 seek 等瞬时抖动误判。 */
private const val LOW_FPS_SUSTAIN_MS = 3000L

/** 换集后延迟多久重建超分特效管线（毫秒）：等 prepare 与首帧窗口过去，避免抢资源。 */
private const val ENHANCEMENT_REAPPLY_DELAY_MS = 600L

/** 进度帧预览的分桶粒度（毫秒）：同一秒内复用同一张图。 */
private const val PREVIEW_BUCKET_MS = 1000L

/** 单指拖动在双指手势结束后继续让位的时间（毫秒）：覆盖事件间隙，避免手势打架。 */
internal const val TRANSFORM_GESTURE_GRACE_MS = 250L

/** 双指缩放的下限：再小画面就没信息量了。 */
private const val MIN_VIDEO_SCALE = 0.5f

/** 双指缩放的上限：再大单帧绘制开销陡增，且毫无可用性。 */
private const val MAX_VIDEO_SCALE = 4f

/** 进度帧预览缓存上限（含失败结果）。 */
private const val PREVIEW_CACHE_MAX = 24

/**
 * 连续取帧失败达到此次数即判定「该源不支持取帧」，后续直接返回 null。
 * MediaMetadataRetriever 是阻塞式 API 且没有超时，远程源（尤其走 App 内代理、受 WAF / 鉴权保护的源）
 * 很可能每次都卡很久才失败；不熔断会持续占用 IO 线程，拖垮整个 App 的协程调度。
 */
private const val PREVIEW_MAX_CONSECUTIVE_FAILS = 3