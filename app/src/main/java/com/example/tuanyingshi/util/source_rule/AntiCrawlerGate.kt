package com.example.tuanyingshi.util.source_rule

import org.seimicrawler.xpath.JXDocument

import com.example.tuanyingshi.util.log

/**
 * 反爬网关：负责「检测验证页」与「触发 WebView 验证」。
 *
 * 检测逻辑 [detectsCaptchaChallenge] 完全对齐 Kazumi `XPathRuleStrategy.detectsCaptchaChallenge`：
 * - 配置了 `captchaDetectValue` 时，按 `captchaDetectType`（text / regex / xpath）匹配；
 * - 未配置时退化为「页面里存在 `captchaImage` 或 `captchaButton` 元素即判定为验证页」
 *   （ciyuancheng 等缺省 detectValue 的规则正是靠此触发）。
 *
 * [maybeVerifySearch] 在搜索抓到疑似验证页时调用 [KazumiWebViewVerifier]，验证通过后返回
 * 收割到的页面 HTML，供上层直接解析（省一次请求，对齐 Kazumi 的 tryParseHarvestedSearch）。
 */
object AntiCrawlerGate {

    /**
     * 判断原始 HTML 是否为验证页。
     */
    fun detectsCaptchaChallenge(raw: String, config: AntiCrawlerConfig): Boolean {
        if (!config.enabled) return false
        val detectValue = config.captchaDetectValue.trim()
        if (detectValue.isNotEmpty()) {
            when (config.captchaDetectType) {
                CaptchaDetectType.TEXT -> return raw.contains(detectValue, ignoreCase = true)
                CaptchaDetectType.REGEX -> return runCatching {
                    Regex(
                        detectValue,
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                    ).containsMatchIn(raw)
                }.getOrDefault(false)
                CaptchaDetectType.XPATH -> return xpathExists(raw, detectValue)
            }
        }
        // fallback：验证页特征元素存在即判定为验证页
        return xpathExists(raw, config.captchaImage) || xpathExists(raw, config.captchaButton)
    }

    /**
     * 若 [html] 是验证页且规则启用了反爬，则执行 WebView 验证并返回收割到的 HTML；
     * 否则（非验证页 / 未启用 / 用户取消 / 无输入提供方）返回 null，调用方保留原 [html]。
     */
    suspend fun maybeVerifySearch(rule: SourceRule, keyword: String, html: String): String? {
        val cfg = rule.antiCrawler
        if (!cfg.enabled) return null
        if (!detectsCaptchaChallenge(html, cfg)) return null
        "$TAG maybeVerifySearch: 命中验证页，启动 WebView 验证 rule=${rule.name}".log(TAG)
        return KazumiWebViewVerifier.verifySearch(rule, keyword)?.html
    }

    private fun xpathExists(raw: String, xpath: String): Boolean {
        if (xpath.isBlank()) return false
        return runCatching {
            JXDocument.create(raw).sel(xpath).isNotEmpty()
        }.getOrDefault(false)
    }

    private const val TAG = "AntiCrawlerGate"
}
