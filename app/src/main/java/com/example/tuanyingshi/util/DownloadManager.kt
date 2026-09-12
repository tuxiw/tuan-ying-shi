package com.example.tuanyingshi.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 统一 HTML 抓取入口（对标 LaQoo DownloadManager）。
 * OkHttp + 伪造 Host 头绕过次元城 WAF。
 */
object DownloadManager {

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .readTimeout(1L, TimeUnit.MINUTES)
        .applyProxy()
        .build()

    suspend fun getHtml(url: String,host: String,headers: Map<String, String> = emptyMap()): String {
        return withContext(Dispatchers.IO) {
            val finalHeaders = if (headers.isEmpty()) {
                mapOf(
                    "Host" to host,
                    "User-Agent" to DESKTOP_UA,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                    "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                    "Upgrade-Insecure-Requests" to "1",
                )
            } else {
                headers
            }

            val request = Request.Builder()
                .url(url)
                .headers(finalHeaders.toHeaders())
                .get()
                .build()

            val response = client.newCall(request).execute()
            var html: String
            if (response.isSuccessful) {
                response.body!!.let { body ->
                    html = body.charStream().readText()
                }
            } else {
                throw IOException(response.toString())
            }
            html
        }
    }

    /**
     * 同步抓取 HTML（仅供 shouldInterceptRequest 内部使用，已处于后台线程）。
     */
    fun getHtmlSync(url: String): String? {
        val headers = mapOf(
            "Host" to url,
            "User-Agent" to DESKTOP_UA,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "zh-CN,zh;q=0.9",
        )
        val request = Request.Builder()
            .url(url)
            .headers(headers.toHeaders())
            .get()
            .build()
        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.charStream()?.readText()
            } else null
        } catch (_: IOException) {
            null
        }
    }

    fun getOkHttpClient(): OkHttpClient = client
}
