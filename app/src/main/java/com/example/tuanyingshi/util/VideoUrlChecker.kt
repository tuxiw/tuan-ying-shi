package com.example.tuanyingshi.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 播放前链接预检：用与播放一致的代理配置（动态 ProxySelector）探测视频直链是否可访问。
 * - m3u8：GET 读取清单首行，须以 `#EXTM3U` 开头才算 HLS 可播
 * - 其它（mp4 等）：仅检查响应头 2xx，不下载正文
 * 不可访问返回 false，由播放页提示用户「链接不可播放」。
 */
object VideoUrlChecker {

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxySelector(dynamicProxySelector())
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /** 预检直链是否可播放。headers 与真实播放保持一致（如 Referer）。 */
    suspend fun canPlay(url: String, headers: Map<String, String> = emptyMap()): Boolean =
        withContext(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            val isM3u8 = url.contains(".m3u8", ignoreCase = true)
            "canPlay: 开始预检 url=${url.take(160)} isM3u8=$isM3u8 headers=${headers.size}".log("VideoUrlChecker")
            val result = runCatching {
                if (url.startsWith("/")) return@withContext true // 本地文件始终可播
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                headers.forEach { (k, v) -> requestBuilder.header(k, v) }

                client.newCall(requestBuilder.build()).execute().use { resp ->
                    if (isM3u8) {
                        // HLS 清单：响应头 2xx 且首行是 #EXTM3U
                        val firstLine = resp.body?.byteStream()?.bufferedReader()?.use { it.readLine()?.trim() }
                        val ok = resp.isSuccessful && firstLine?.startsWith("#EXTM3U") == true
                        "canPlay: code=${resp.code} firstLine=${firstLine?.take(48)} ok=$ok".log("VideoUrlChecker")
                        ok
                    } else {
                        // 普通直链（mp4 等）：只读响应头即关闭，不下载正文
                        "canPlay: code=${resp.code} isSuccessful=${resp.isSuccessful}".log("VideoUrlChecker")
                        resp.isSuccessful
                    }
                }
            }.getOrDefault(false)
            "canPlay: 结论=$result cost=${System.currentTimeMillis() - t0}ms".log("VideoUrlChecker")
            result
        }
}
