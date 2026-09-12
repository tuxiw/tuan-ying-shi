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
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
        // 设置默认视频显示大小为：横屏模式下宽高比为16:9的大小
        val width = (maxHeight * VIDEO_ASPECT_RATIO_16_9).toInt() // default 16 : 9
        return this.copy(maxWidth = width)
    }

    var width = maxWidth
    var height = maxHeight
    val constraintAspectRatio: Float = (width / height).toFloat()
    val difference = aspectRatio / constraintAspectRatio - 1

    if (kotlin.math.abs(difference) <= MAX_ASPECT_RATIO_DIFFERENCE_FRACTION) {
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

@OptIn(UnstableApi::class)
internal fun mediaSourceCreator(url: String, headers: Map<String, String>, context: Context): MediaSource {
    val mediaItem = mediaItemCreator(url)
    val isRemoteHttp = isHttpScheme(url)

    val dataSourceFactory = if (isRemoteHttp) {
        // 远程视频（http/https）走 OkHttpDataSource：
        // - 动态代理选择器每次建连实时读取宿主 App 注入的代理（团影视 ProxyHolder），
        //   保证视频直链（m3u8 / mp4）也遵循 App 内代理设置，改代理后重新播放即生效；
        // - media3 1.4.1 的 DefaultHttpDataSource 不支持代理，故改用 OkHttpDataSource。
        val okHttpClient = OkHttpClient.Builder()
            .proxySelector(PlayerProxySelector)
            .apply { playerProxyAuthenticator?.invoke()?.let { proxyAuthenticator(it) } }
            .build()
        OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(headers)
    } else {
        // 本地/共享文件（content://、file:// 等）：OkHttpDataSource 不支持这些 scheme，
        // 必须用 ExoPlayer 自带的 DefaultDataSource（内部按 scheme 选择 ContentDataSource /
        // FileDataSource），否则外部打开的 MP4 等会加载失败。
        DefaultDataSource.Factory(context)
    }

    return if (mediaItem.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8) {
        // HLS 流必须用 HlsMediaSource，ProgressiveMediaSource 无法解析 m3u8 清单
        HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    } else {
        ProgressiveMediaSource.Factory(dataSourceFactory)
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
