package com.example.tuanyingshi.download.core

import com.example.tuanyingshi.download.utils.contentLength
import com.example.tuanyingshi.download.utils.isSupportRange
import com.example.tuanyingshi.download.utils.log
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

interface HttpClientFactory {
    fun create(): OkHttpClient
}

object DefaultHttpClientFactory : HttpClientFactory {
    override fun create(): OkHttpClient {
        val builder = OkHttpClient().newBuilder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
        // 挂动态代理选择器：每次建连时实时读取宿主 App 注入的配置，
        // 客户端被 DownloadConfig 缓存后改代理也能立即生效。
        builder.proxySelector(DownloadProxySelector)
        httpProxyAuthenticator?.invoke()?.let { builder.proxyAuthenticator(it) }
        return builder.build()
    }
}

/**
 * 动态代理选择器：
 * - provider 返回具体 [Proxy]（含 NO_PROXY 强制直连）：覆盖为该代理；
 * - provider 为 null 或未注入：委托系统默认选择器（OkHttp 原生行为）。
 */
private object DownloadProxySelector : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        val proxy = httpProxyProvider?.invoke()
        return if (proxy != null) {
            listOf(proxy)
        } else {
            runCatching { ProxySelector.getDefault()?.select(uri).orEmpty() }
                .getOrDefault(emptyList())
                .ifEmpty { listOf(Proxy.NO_PROXY) }
        }
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        runCatching { ProxySelector.getDefault()?.connectFailed(uri, sa, ioe) }
    }
}

interface DownloadDispatcher {
    fun dispatch(downloadTask: DownloadTask, resp: Response<ResponseBody>): Downloader
}

object DefaultDownloadDispatcher : DownloadDispatcher {
    override fun dispatch(downloadTask: DownloadTask, resp: Response<ResponseBody>): Downloader {
        return if (resp.isM3u8()) {
            "M3u8Downloader".log()
            M3u8Downloader(downloadTask.coroutineScope)
        } else if (downloadTask.config.disableRangeDownload || !resp.isSupportRange()) {
            "NormalDownloader".log()
            NormalDownloader(downloadTask.coroutineScope)
        } else {
            "RangeDownloader".log()
            RangeDownloader(downloadTask.coroutineScope)
        }
    }
}

/**
 * 判断响应是否为 m3u8 播放列表：
 * 1. 优先按 Content-Type（application/vnd.apple.mpegurl / application/x-mpegurl 等）判断；
 * 2. 回退到读取响应体首行是否为 #EXTM3U（不消费原始流，使用 peekBody）。
 * 避免旧逻辑只靠 URL 子串 "m3u8" 判断，导致带 token/query 的真实 m3u8 地址被误分发到普通下载器。
 */
private fun Response<ResponseBody>.isM3u8(): Boolean {
    val contentType = raw().header("Content-Type")
    if (contentType != null) {
        val lower = contentType.lowercase()
        if (lower.contains("mpegurl") || lower.contains("application/x-mpegurl")) {
            return true
        }
    }
    return runCatching {
        raw().peekBody(64).byteStream().bufferedReader().use {
            it.readLine()?.trim()?.startsWith("#EXTM3U") == true
        }
    }.getOrDefault(false)
}

interface FileValidator {
    fun validate(
        file: File,
        param: DownloadParam,
        resp: Response<ResponseBody>
    ): Boolean
}

object DefaultFileValidator : FileValidator {
    override fun validate(
        file: File,
        param: DownloadParam,
        resp: Response<ResponseBody>
    ): Boolean {
        return file.length() == resp.contentLength()
    }
}