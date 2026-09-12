package com.example.tuanyingshi.util.source_rule

import kotlinx.serialization.Serializable

/**
 * 反反爬虫验证类型（对齐 Kazumi `CaptchaType`）。
 *
 * - [IMAGE_CAPTCHA] (1)：WebView 抓取验证码图片，引导用户手动输入后提交。
 * - [AUTO_CLICK_BUTTON] (2)：WebView 检测到验证按钮后自动点击，无需用户交互。
 * - [CUSTOM_JAVASCRIPT] (3)：WebView 执行规则提供的验证脚本（window.KazumiCaptcha.done/clicked）。
 *
 * 保留整数常量以便规则里直接写数字，向后兼容。
 */
object CaptchaType {
    const val IMAGE_CAPTCHA = 1
    const val AUTO_CLICK_BUTTON = 2
    const val CUSTOM_JAVASCRIPT = 3
}

/**
 * 反反爬虫验证页检测方式（对齐 Kazumi `CaptchaDetectType`）。
 *
 * - [XPATH] (1)：用 XPath 在文档里查找验证页特征元素。
 * - [TEXT] (2)：在原始 HTML 文本里包含某段文本即判定为验证页。
 * - [REGEX] (3)：用正则匹配原始 HTML。
 */
object CaptchaDetectType {
    const val XPATH = 1
    const val TEXT = 2
    const val REGEX = 3
}

/**
 * 反反爬虫配置（对齐 Kazumi `AntiCrawlerConfig`）。
 *
 * 仅当 XPath/CSS 搜索响应为「验证页」时，[KazumiWebViewVerifier] 才会用 WebView 加载搜索页，
 * 按 [captchaType] 完成验证并保存 Cookie，随后重试搜索。
 *
 * 字段命名刻意与 KazumiRules 的 `antiCrawlerConfig` 保持一致，便于从 KazumiRules 导入时零转换。
 */
@Serializable
data class AntiCrawlerConfig(
    /** 是否启用反反爬虫功能。 */
    val enabled: Boolean = false,

    /**
     * 验证类型（[CaptchaType]）。
     * - [CaptchaType.IMAGE_CAPTCHA] (1)：图片验证码，需要用户手动输入；
     * - [CaptchaType.AUTO_CLICK_BUTTON] (2)：自动点击验证按钮，无需用户交互；
     * - [CaptchaType.CUSTOM_JAVASCRIPT] (3)：执行规则提供的验证脚本。
     */
    val captchaType: Int = CaptchaType.IMAGE_CAPTCHA,

    /** 验证码图片元素的 XPath 选择器（仅 captchaType == 1 时使用）。通过 Canvas 抓取其像素回传。 */
    val captchaImage: String = "",

    /** 验证码输入框元素的 XPath 选择器（仅 captchaType == 1 时使用）。 */
    val captchaInput: String = "",

    /**
     * 验证按钮元素的 XPath 选择器。
     * - captchaType == 1：提交验证码的按钮；
     * - captchaType == 2：目标验证按钮（如"我不是机器人"）；
     * - captchaType == 3：不使用。
     */
    val captchaButton: String = "",

    /** 验证页检测方式（[CaptchaDetectType]）。 */
    val captchaDetectType: Int = CaptchaDetectType.XPATH,

    /**
     * 验证页检测内容。
     * 根据 [captchaDetectType] 可表示 XPath、普通文本或正则表达式。
     * 留空时退化为「页面里存在 [captchaImage] 或 [captchaButton] 元素即判定为验证页」
     * （对齐 Kazumi 的 fallback 逻辑，ciyuancheng 等缺省 detectValue 的规则正是如此触发）。
     */
    val captchaDetectValue: String = "",

    /** 自定义 JS 验证脚本（仅 captchaType == 3 时使用）。 */
    val captchaScript: String = "",
) {
    companion object {
        val EMPTY = AntiCrawlerConfig()
    }
}
