package com.example.tuanyingshi.util.source_rule

import android.webkit.CookieManager
import com.example.tuanyingshi.util.log
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * 每条规则（按 host 隔离）的 Cookie / UA 管理器。
 *
 * 对齐 Kazumi `PluginCookieManager` 的**会话级**语义：
 * - WebView 完成反爬验证后，把 [CookieManager.getCookie] 拿到的 cookie 字符串存进**进程内内存**；
 * - 验证 Cookie 通常与 User-Agent 绑定，故同时记录 WebView 真实 UA，供后续 OkHttp 请求对齐指纹；
 * - 之后同源（同 host）的 OkHttp 请求按需读取并组装 `Cookie` 请求头，从而绕过 WAF。
 *
 * 与「持久化到 SharedPreferences」相比，这里**仅会话有效、重启需重新验证**——
 * 完全复刻 Kazumi 的行为（用户明确要求「按源 CookieJar，仅会话」）。
 * 注意：内存存储，明文，进程被杀即丢失。
 */
object SourceCookieManager {

    /** host -> (cookieHeader, ua?) */
    private val store = ConcurrentHashMap<String, Pair<String, String?>>()

    /**
     * 从 WebView 捕获的 cookie 字符串存起来（会话级，仅内存）。
     *
     * @param pageUrl 当前页面地址，用于推导 host
     * @param cookieString `CookieManager.getCookie(url)` 返回的 "k1=v1; k2=v2"
     * @param userAgent 验证时 WebView 使用的 UA（可选）
     */
    suspend fun saveFromWebView(pageUrl: String, cookieString: String, userAgent: String? = null) {
        val host = runCatching { URI(pageUrl).host }.getOrNull() ?: return
        if (cookieString.isBlank()) return
        store[host] = cookieString.trim() to userAgent?.trim()?.takeIf { it.isNotEmpty() }
        "$TAG saveFromWebView: host=$host cookieLen=${cookieString.length}".log(TAG)
    }

    /** 读取某 host 的 Cookie 请求头字符串（含最近匹配的父域）。无则返回 null。 */
    fun cookieHeaderForHost(host: String): String? {
        var h = host
        while (h.isNotEmpty()) {
            val v = store[h]?.first
            if (!v.isNullOrBlank()) return v
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }
        return null
    }

    /** 读取某 host 验证时使用的 User-Agent（用于对齐指纹）。无则返回 null。 */
    fun userAgentForHost(host: String): String? {
        var h = host
        while (h.isNotEmpty()) {
            val v = store[h]?.second
            if (!v.isNullOrBlank()) return v
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }
        return null
    }

    /** 直接读取 WebView 当前对该 host 的 cookie（验证后立即可用，无需持久化）。 */
    fun webViewCookieNow(pageUrl: String): String {
        return runCatching { CookieManager.getInstance().getCookie(pageUrl) }.getOrNull() ?: ""
    }

    private const val TAG = "SourceCookieManager"
}
