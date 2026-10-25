package com.example.tuanyingshi.util.source_rule

import org.jsoup.Jsoup

/**
 * Cloudflare 挑战页识别器。
 *
 * 移植自 HaRu（Flutter）客户端的 `HanimePageParser.isChallenge`，并做两点适配：
 * 1. 本项目 OkHttp/WebView 两条链路都拿不到可靠状态码（WebView 完全没有，OkHttp 有），
 *    因此状态码是可选输入，命中 [CF_STATUS_CODES] 直接判挑战；
 * 2. 采用「字符串粗筛 → DOM 精判」两段式，避免正文里出现 "cloudflare" 字样（CDN 脚本、
 *    邮箱保护等）就被误判为验证页——这是本项目先前纯关键词方案最大的误伤来源。
 *
 * 关键词集合与 HaRu 完全一致（CF 各代挑战页的文案/标识）：
 * `just a moment` / `cloudflare` / `attention required` / `checking your browser` /
 * `verify you are human` / `you have been blocked` / `cf-turnstile-response` 等。
 */
object CloudflareDetector {

    /** Clearance cookie 名（通过 Cloudflare 挑战后下发，httpOnly）。 */
    const val CLEARANCE_COOKIE = "cf_clearance"

    /** Cloudflare 拦截常用状态码：403（被盾）/ 503（盾前置错误页）/ 429（CF 限流）。
     *  命中状态码时仍需正文复核，避免把站点自有 403 误判成 CF。 */
    private val CF_STATUS_CODES = setOf(403, 503, 429)

    /** 粗筛关键词：正文里一个都不含时直接放行，避免频繁 Jsoup 解析（正则/包含检测很廉价）。 */
    private val PRE_FILTER = listOf(
        "just a moment",
        "cloudflare",
        "attention required",
        "checking your browser",
        "verify you are human",
        "you have been blocked",
        "cf-turnstile-response",
        "cf-browser-verification",
        "__cf_chl",
        "cf_chl_opt",
        "enable javascript and cookies to continue",
    )

    /** 只取正文前若干字符做粗筛，超大页面不必全量扫描。 */
    private const val PRE_SCAN_CHARS = 20_000

    /**
     * 是否为 Cloudflare 挑战 / 拦截页。
     *
     * @param statusCode HTTP 状态码（可为 null，表示不可用，如 WebView 链路）
     * @param body 页面正文（可为 null / 空白 → false）
     */
    fun isChallenge(statusCode: Int?, body: String?): Boolean {
        if (statusCode != null && statusCode in CF_STATUS_CODES) {
            // 403/503 且正文确实是 CF 页面 → 挑战；状态码本身不足以判定（部分站点自有 403）
            if (body != null && preHit(body) && confirm(body)) return true
        }
        if (body.isNullOrBlank()) return false
        if (!preHit(body)) return false
        return confirm(body)
    }

    /** 只看内容（无状态码，WebView 收割结果用）。 */
    fun isChallenge(body: String?): Boolean = isChallenge(null, body)

    /** 第一段：廉价的子串粗筛。 */
    private fun preHit(body: String): Boolean {
        val probe = if (body.length > PRE_SCAN_CHARS) body.substring(0, PRE_SCAN_CHARS) else body
        val lower = probe.lowercase()
        return PRE_FILTER.any { it in lower }
    }

    /**
     * 第二段：DOM 精判（对齐 HaRu 的二次确认），
     * 命中 title / 正文特征文案 / turnstile 隐藏字段 之一才算挑战页。
     */
    private fun confirm(body: String): Boolean {
        return try {
            val doc = Jsoup.parse(body)
            val title = doc.title().lowercase()
            if (title.contains("just a moment") ||
                title.contains("attention required") ||
                title.contains("cloudflare")
            ) {
                return true
            }
            val text = doc.body().text().lowercase()
            if (text.contains("checking your browser") ||
                text.contains("verify you are human") ||
                text.contains("you have been blocked") ||
                text.contains("enable javascript and cookies to continue")
            ) {
                return true
            }
            doc.selectFirst("input[name=cf-turnstile-response]") != null ||
                doc.selectFirst("[class*=cf-turnstile]") != null ||
                doc.selectFirst("iframe[src*=challenges.cloudflare.com]") != null ||
                doc.selectFirst("form[id=challenge-form]") != null
        } catch (_: Throwable) {
            // 解析失败退化为粗筛结论（已经优缺点命中，宁可判为被拦）
            true
        }
    }

    /** cookie 串里是否已有 clearance（`a=1; cf_clearance=xxx`）。 */
    fun hasClearance(cookieHeader: String?): Boolean {
        if (cookieHeader.isNullOrBlank()) return false
        for (part in cookieHeader.split(";")) {
            val name = part.substringBefore('=').trim()
            if (name.equals(CLEARANCE_COOKIE, ignoreCase = true)) return true
        }
        return false
    }

    /**
     * 页面 HTML 是否仍含 Cloudflare 挑战标记——通关判定的核心。
     *
     * 对齐 Han1meViewer 的通过判定：它正是用 `#challenge-form` / `#challenge-success-text` /
     * `#challenge-error-text` 是否存在 + `cf_clearance` 是否在 Cookie 里来判定通关。
     * **不能**用全页扫描 "cloudflare" 字样（真实站点页同样含 cloudflare 脚本/CDN，会误判成挑战页，
     * 导致过盾成功后仍被当成挑战、永不完成）。
     */
    fun containsChallengeMarkers(html: String?): Boolean {
        if (html.isNullOrBlank()) return false
        return html.contains("#challenge-form") ||
            html.contains("#challenge-success-text") ||
            html.contains("#challenge-error-text")
    }

    /** 取 URL 的站点根（`https://host/`），用于 `CookieManager.getCookie()` 与 host 比对。 */
    fun originOf(url: String): String = runCatching {
        val uri = java.net.URI(url)
        "${uri.scheme}://${uri.authority}/"
    }.getOrDefault(url)

    private const val TAG = "CloudflareDetector"
}
