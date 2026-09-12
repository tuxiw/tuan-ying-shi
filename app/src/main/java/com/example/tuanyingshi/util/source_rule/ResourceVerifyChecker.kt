package com.example.tuanyingshi.util.source_rule

import com.example.tuanyingshi.util.DownloadManager
import com.example.tuanyingshi.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder

/**
 * 资源验证检查器：一键探测「哪些源当前弹出验证页（即需要用户验证）」。
 *
 * 仅针对启用了 [SourceRule.antiCrawler] 的源规则（API 内置源 / 无反爬的源直接跳过）；
 * 用占位关键词发一次搜索请求，按 [AntiCrawlerGate.detectsCaptchaChallenge] 判定。
 *
 * 探测请求与 [RuleExecutor.fetchHttpText] 保持一致的会话语义：自动附带该 host 已保存的
 * clearance cookie 与验证时使用的 UA，因此「已验证过的源」探测时不会再命中验证页，
 * 自然不会出现在候选列表里——这正是期望行为。
 *
 * 同时暴露 [buildSearchRequestUrl] 供 [KazumiWebViewVerifier] 复用，按引擎拼出正确的搜索地址
 * （CSS 用 `{keyword}`、Xpath 用 `@keyword`、Api 用 `@keyword` 模板占位符）。
 */
object ResourceVerifyChecker {

    private const val TAG = "ResourceVerify"

    /** 探测用的占位关键词（仅用于触发搜索页，结果本身无意义）。 */
    const val PROBE_KEYWORD = "test"

    /**
     * 检查单个源规则当前是否弹出验证页。
     * - 未启用 antiCrawler → false（无需验证，直接跳过）；
     * - 搜索地址为空 → false；
     * - 抓取失败 → false（不误报为「需要验证」）；
     * 返回 true 表示「需要验证」。
     */
    suspend fun needsVerification(rule: SourceRule): Boolean {
        val cfg = rule.antiCrawler
        if (!cfg.enabled) return false
        val url = buildSearchRequestUrl(rule, PROBE_KEYWORD) ?: return false
        val html = probeHtml(url, rule) ?: return false
        val hit = AntiCrawlerGate.detectsCaptchaChallenge(html, cfg)
        "$TAG needsVerification: ${rule.name} url=$url hit=$hit".log(TAG)
        return hit
    }

    /**
     * 按引擎构造搜索请求 URL（复用各引擎的关键词占位符约定）。
     * 返回 null 表示该源没有可用的搜索地址（无需/无法验证）。
     */
    internal fun buildSearchRequestUrl(rule: SourceRule, keyword: String): String? {
        return when (rule.engine) {
            RuleEngineKind.Api -> {
                val url = rule.apiSearch.url
                if (url.isBlank()) null else renderKzTemplate(url, mapOf("keyword" to keyword))
            }
            RuleEngineKind.Xpath -> {
                val url = rule.xpath.searchUrl
                if (url.isBlank()) null else url.replace("@keyword", encodeKeyword(keyword))
            }
            else -> { // Css
                val url = rule.search.searchUrl
                if (url.isBlank()) null else url.replace("{keyword}", keyword)
            }
        }
    }

    /** 发一次带会话 cookie/UA 的 GET（对齐 [RuleExecutor] 的会话语义）。 */
    private suspend fun probeHtml(url: String, rule: SourceRule): String? = withContext(Dispatchers.IO) {
        runCatching {
            val host = runCatching { URI(url).host }.getOrNull() ?: ""
            val cookie = SourceCookieManager.cookieHeaderForHost(host)
            val ua = SourceCookieManager.userAgentForHost(host) ?: rule.headers.userAgent
            val reqBuilder = Request.Builder().url(url)
                .addHeader("User-Agent", ua)
                .addHeader(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.8",
                )
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            if (rule.headers.referer.isNotBlank()) reqBuilder.addHeader("Referer", rule.headers.referer)
            if (cookie != null) reqBuilder.addHeader("Cookie", cookie)
            val resp = DownloadManager.getOkHttpClient().newCall(reqBuilder.get().build()).execute()
            val code = resp.code
            val text = resp.body?.charStream()?.readText()
            if (code !in 200..299) {
                "$TAG probeHtml: ${rule.name} 非2xx code=$code".log(TAG)
                null
            } else text
        }.getOrElse { e ->
            "$TAG probeHtml: ${rule.name} ${e.javaClass.simpleName}: ${e.message}".log(TAG)
            null
        }
    }

    /** 模板变量替换：@name → URL 编码后的值（对齐 Kazumi `_renderTemplate`）。 */
    private fun renderKzTemplate(template: String, vars: Map<String, String>): String {
        val re = Regex("(?<![A-Za-z0-9_])@([A-Za-z_][A-Za-z0-9_]*)")
        return re.replace(template) { m -> URLEncoder.encode(vars[m.groupValues[1]] ?: "", "UTF-8") }
    }

    private fun encodeKeyword(s: String): String = URLEncoder.encode(s, "UTF-8")
}
