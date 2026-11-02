package com.example.tuanyingshi.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
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

    /** 预检失败后的重试次数（瞬时抖动 / 首包慢不应直接判死）。 */
    private const val MAX_PRECHECK_ATTEMPTS = 3
    /** 每次重试前的退避间隔。 */
    private const val PRECHECK_RETRY_DELAY_MS = 800L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxySelector(dynamicProxySelector())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
    }

    /** 单次预检尝试。本地文件由调用方短路，这里只处理远程直链。 */
    private fun attemptPlay(url: String, headers: Map<String, String>, isM3u8: Boolean): Boolean {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }

        return client.newCall(requestBuilder.build()).execute().use { resp ->
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
    }

    /** 预检直链是否可播放。headers 与真实播放保持一致（如 Referer）。 */
    suspend fun canPlay(url: String, headers: Map<String, String> = emptyMap()): Boolean =
        withContext(Dispatchers.IO) {
            if (url.startsWith("/")) return@withContext true // 本地文件始终可播
            val t0 = System.currentTimeMillis()
            val isM3u8 = url.contains(".m3u8", ignoreCase = true)
            "canPlay: 开始预检 url=${url.take(160)} isM3u8=$isM3u8 headers=${headers.size}".log("VideoUrlChecker")

            var lastOk = false
            repeat(MAX_PRECHECK_ATTEMPTS) { attempt ->
                lastOk = runCatching { attemptPlay(url, headers, isM3u8) }.getOrDefault(false)
                if (lastOk) return@withContext true // 任一成功即视为可播
                if (attempt < MAX_PRECHECK_ATTEMPTS - 1) {
                    "canPlay: 第${attempt + 1}次预检失败，准备重试".log("VideoUrlChecker")
                    delay(PRECHECK_RETRY_DELAY_MS)
                }
            }
            "canPlay: 结论=$lastOk cost=${System.currentTimeMillis() - t0}ms".log("VideoUrlChecker")
            lastOk
        }
}
