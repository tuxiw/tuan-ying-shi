package com.lanlinju.videoplayer

import android.content.Context
import android.net.Uri
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.ui.unit.Constraints
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient


internal fun VideoSize.aspectRatio(): Float =
    if (height == 0 || width == 0) 0f else (width * pixelWidthHeightRatio) / height

/**
 * The [FrameLayout] will not resize itself if the fractional difference between its natural
 * aspect ratio and the requested aspect ratio falls below this threshold.
 *
 *
 * This tolerance allows the view to occupy the whole of the screen when the requested aspect
 * ratio is very close, but not exactly equal to, the aspect ratio of the screen. This may reduce
 * the number of view layers that need to be composited by the underlying system, which can help
 * to reduce power consumption.
 */
private const val MAX_ASPECT_RATIO_DIFFERENCE_FRACTION = 0.01f
private const val VIDEO_ASPECT_RATIO_16_9 = 16f.div(9f) // 16 : 9, 1.7777778
private const val VIDEO_ASPECT_RATIO_4_3 = 4f.div(3f)  //   4 : 3, 1.3333334

internal fun Constraints.resizeForVideo(
    mode: ResizeMode,
    aspectRatio: Float
): Constraints {
    if (aspectRatio <= 0f) {
        // videoSize 未知（首次加载 / 切集瞬间）按 16:9 兜底，但必须钳制在可用区域内：
        // 之前直接 width = maxHeight * 16/9，在「高>宽×9/16」的面板（平板左右分栏、竖屏）
        // 会算出比容器更宽的尺寸，画面溢出裁切、表现为换集时播放器被放大。改为等比 letterbox。
        val widthByHeight = (maxHeight * VIDEO_ASPECT_RATIO_16_9).toInt()
        return if (widthByHeight <= maxWidth) {
            this.copy(maxWidth = widthByHeight)
        } else {
            this.copy(maxHeight = (maxWidth / VIDEO_ASPECT_RATIO_16_9).toInt())
        }
    }

    var width = maxWidth
    var height = maxHeight
    val constraintAspectRatio: Float = (width / height).toFloat()
    val difference = aspectRatio / constraintAspectRatio - 1

    // 只在「自适应」下允许「比例接近就不处理」的短路：
    // 该短路原本是为了省掉一次合成层，但它会让 4:3 / 16:9 / 满屏 这些档位在
    // 播放器区域比例≈视频比例时（手机竖屏下播放区固定 16:9、片源也是 16:9，极其常见）
    // **全部退化成同一个尺寸** —— 用户切来切去画面纹丝不动，就是「调整画面尺寸没效果」。
    // 这些档位本就应该强制按目标比例出图，所以只对 Fit 保留短路。
    if (mode == ResizeMode.Fit &&
        kotlin.math.abs(difference) <= MAX_ASPECT_RATIO_DIFFERENCE_FRACTION
    ) {
        // 视频比例与屏幕比例十分接近，不处理
        return this
    }

    when (mode) {
        ResizeMode.Fit -> {
            if (difference > 0) { /* difference 大于零 为竖屏模式 */
                height = (width / aspectRatio).toInt()
            } else { /* 横屏模式 */
                width = (height * aspectRatio).toInt()
            }
        }

        ResizeMode.Zoom -> {
            if (difference > 0) {
                width = (height * aspectRatio).toInt()
            } else {
                height = (width / aspectRatio).toInt()
            }
        }

        ResizeMode.FixedWidth -> {
            height = (width / aspectRatio).toInt()
        }

        ResizeMode.FixedHeight -> {
            width = (height * aspectRatio).toInt()
        }

        ResizeMode.FixedRatio_16_9 -> {
            if (difference > 0) {
                height = (width / VIDEO_ASPECT_RATIO_16_9).toInt()
            } else {
                width = (height * VIDEO_ASPECT_RATIO_16_9).toInt()
            }
        }

        ResizeMode.FixedRatio_4_3 -> {
            if (difference > 0) {
                height = (width / VIDEO_ASPECT_RATIO_4_3).toInt()
            } else {
                width = (height * VIDEO_ASPECT_RATIO_4_3).toInt()
            }
        }

        ResizeMode.Full -> {
            if (difference > 0) {
                width = (height * aspectRatio).toInt()
            } else {
                height = (width / aspectRatio).toInt()
            }
        }

        ResizeMode.Fill -> Unit
    }

    return this.copy(maxWidth = width, maxHeight = height)
}


/**
 * Will return a timestamp denoting the current video [position] and the [duration] in the following
 * format "mm:ss / mm:ss"
 * **/
fun prettyVideoTimestamp(
    position: Duration,
    duration: Duration
): String = buildString {
    appendMinutesAndSeconds(position)
    append("/")
    appendMinutesAndSeconds(duration)
}

/**
 * Will split [duration] in minutes and seconds and append it to [this] in the following format "mm:ss"
 * */
private fun StringBuilder.appendMinutesAndSeconds(duration: Duration) {
    val minutes = duration.inWholeMinutes
    val seconds = (duration - minutes.minutes).inWholeSeconds
    appendDoubleDigit(minutes)
    append(':')
    appendDoubleDigit(seconds)
}

/**
 * Will append [value] as double digit to [this].
 * If a single digit value is passed, ex: 4 then a 0 will be added as prefix resulting in 04
 * */
private fun StringBuilder.appendDoubleDigit(value: Long) {
    if (value < 10) {
        append(0)
        append(value)
    } else {
        append(value)
    }
}

internal fun mediaItemCreator(url: String): MediaItem {
    if (url.startsWith("/")) { // 本地视频文件（绝对路径：内置共享存储 / SD 卡）
        return MediaItem.fromUri(Uri.fromFile(File(url)))
    }
    val builder = MediaItem.Builder().setUri(url) // 远程视频文件类型处理
    if (url.contains(".m3u8")) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }
    return builder.build()
}

/**
 * 播放器（视频直链）共用的 OkHttp 客户端：
 * - 复用单一连接池（keep-alive），避免每次装载片源都新建 OkHttpClient 导致连接无法复用、
 *   首包建立 TCP/TLS 抖动 —— 这正是「视频有概率无法正常播放」的根因之一；
 * - 统一的连接/读写超时（比默认 10s 更宽，兼容慢 CDN 与首包慢的 m3u8）；
 * - 仍走动态 ProxySelector + 代理鉴权，保证与 App 代理设置一致。
 */
private val sharedVideoHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .proxySelector(PlayerProxySelector)
        .apply { playerProxyAuthenticator?.invoke()?.let { proxyAuthenticator(it) } }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .build()
}

/**
 * 对网络类错误更「宽容」的加载错误重试策略：
 * - 提高最小重试次数（默认 3 → 6）：HLS 每个分片都是独立请求，瞬时抖动应自动重试而非直接报错；
 * - 退避上限从默认 5s 放宽到 8s，更容忍慢首包；
 * - 5xx 这类瞬时服务端错误（500/502/503/504）**不拉黑**码率/变体：默认策略会对连续失败拉黑变体
 *   60s~5min，导致整段黑屏或永久跳片，这里改由重试机制消化；其余真实错误（403/404/416 等）沿用默认排除。
 */
@OptIn(UnstableApi::class)
private class NetworkFriendlyLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 6

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        // 复用默认退避逻辑（瞬时 IO 错误才重试、致命错误返回 TIME_UNSET），仅放大上限到 8s。
        val base = super.getRetryDelayMsFor(loadErrorInfo)
        return if (base == C.TIME_UNSET) base else base.coerceAtMost(8000L)
    }

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        val e = loadErrorInfo.exception
        // 5xx 瞬时服务端错误不拉黑变体/分片，交给上面的重试机制；其余（403/404/416 等真实错误）沿用默认排除。
        if (e is HttpDataSource.InvalidResponseCodeException) {
            val code = e.responseCode
            if (code == 500 || code == 502 || code == 503 || code == 504) return null
        }
        return super.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)
    }
}

@OptIn(UnstableApi::class)
internal fun mediaSourceCreator(url: String, headers: Map<String, String>, context: Context): MediaSource {
    val mediaItem = mediaItemCreator(url)
    val isRemoteHttp = isHttpScheme(url)

    val dataSourceFactory = if (isRemoteHttp) {
        // 远程视频（http/https）走 OkHttpDataSource：
        // - 动态代理选择器每次建连实时读取宿主 App 注入的代理（团影视 ProxyHolder），
        //   保证视频直链（m3u8 / mp4）也遵循 App 内代理设置，改代理后重新播放即生效；
        // - media3 1.4.1 的 DefaultHttpDataSource 不支持代理，故改用 OkHttpDataSource；
        // - 复用 [sharedVideoHttpClient] 的连接池，避免每次播放重建连接、首包抖动。
        OkHttpDataSource.Factory(sharedVideoHttpClient)
            .setDefaultRequestProperties(headers)
    } else {
        // 本地/共享文件（content://、file:// 等）：OkHttpDataSource 不支持这些 scheme，
        // 必须用 ExoPlayer 自带的 DefaultDataSource（内部按 scheme 选择 ContentDataSource /
        // FileDataSource），否则外部打开的 MP4 等会加载失败。
        DefaultDataSource.Factory(context)
    }

    // HLS 流必须用 HlsMediaSource，ProgressiveMediaSource 无法解析 m3u8 清单；
    // 两者都挂上网络友好的重试策略，瞬断/慢首包自动重试而非直接进错误页。
    return if (mediaItem.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8) {
        HlsMediaSource.Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(NetworkFriendlyLoadErrorPolicy())
            .createMediaSource(mediaItem)
    } else {
        ProgressiveMediaSource.Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(NetworkFriendlyLoadErrorPolicy())
            .createMediaSource(mediaItem)
    }
}

/** 仅 http / https 视为「远程视频」，需要走 OkHttp 代理；其余（content / file / 等）视为本地。 */
private fun isHttpScheme(url: String): Boolean {
    val scheme = runCatching { Uri.parse(url).scheme }.getOrNull()
    return scheme == "http" || scheme == "https"
}

@OptIn(UnstableApi::class)
internal fun ExoPlayer.setVideoUrl(url: String, headers: Map<String, String>, context: Context) {
    if (url.startsWith("/")) {
        // 本地视频文件（绝对路径）：无需代理 / 请求头，直接播放
        setMediaItem(mediaItemCreator(url))
    } else {
        // 非本地路径：按 scheme 分流（远程 http(s) 走 OkHttp 代理；content/file 走 DefaultDataSource）
        setMediaSource(mediaSourceCreator(url, headers, context))
    }
}

@OptIn(UnstableApi::class)
internal fun loadControlCreator(): LoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(50_000, 90_000, 2000, 5000)
        .setBackBuffer(20_000, true)
        .build()
}
