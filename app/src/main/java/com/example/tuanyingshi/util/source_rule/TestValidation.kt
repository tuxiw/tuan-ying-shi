package com.example.tuanyingshi.util.source_rule

/**
 * CSS 资源「验证问题」的共用类型。
 *
 * 对齐 animeko 的验证模型：把「页面被拦（网络 / WAF 层）」与「配置写错（选择器不匹配）」彻底分开，
 * 并在逐条目上加字段级校验标签。
 *
 * 与 animeko 的关键差异：animeko 依赖真实 HTTP 状态码（404 / 429 / 403 / 468）来判定 [BlockReason]，
 * 而本项目用系统 WebView 渲染后读 DOM，**拿不到 HTTP 状态码**，因此 [BlockReason] 一律靠页面内容启发式判定
 * （见 [RuleExecutor.detectBlockReason]）。
 */

/**
 * 对齐 animeko `BlockReason`：页面被拦的原因（网络 / WAF 层）。
 *
 * 枚举取值与 animeko 一一对应：`Captcha` / `RateLimited` / `NotFound` / `Forbidden`，
 * 额外保留 `WAF`（反爬挑战页，对应 animeko 在拿不到结构化 selector 时的 `Captcha(Unknown)`）。
 */
enum class BlockReason {
    /** 反爬 / 人机验证 / 挑战页（如次元城返回「不提供服务」、Cloudflare "Just a moment"）。 */
    WAF,

    /** 站点冷却 / 限流页（"访问频率过高"、"请稍后再试" 等）。 */
    RATE_LIMITED,

    /** 403 类被拒（内容启发式判定，无 HTTP 状态码可用）。 */
    FORBIDDEN,

    /** 404 类（资源不存在）。 */
    NOT_FOUND,

    /** 需要人工解验证码（当前站点暂无此流程，预留）。 */
    CAPTCHA,
}

/**
 * 对齐 animeko `MatchTag`：逐条目的字段级校验标签。
 *
 * @param label 字段名（如「标题」「链接」「EP」「播放地址」）
 * @param isMatch 字段校验通过
 * @param isMissing 字段缺失（如链接为空、EP 序号解析不出来）
 *
 * 二选一：`isMissing` 优先于 `isMatch`；两者皆否表示「字段存在但不匹配」（如播放地址不以 http 开头）。
 */
data class MatchTag(
    val label: String,
    val isMatch: Boolean = false,
    val isMissing: Boolean = false,
) {
    val display: String
        get() = when {
            isMissing -> "$label：缺失"
            isMatch -> "$label：✓"
            else -> "$label：✗"
        }
}

/**
 * 对齐 animeko 的 `Blocked(reason)`（页面被拦）与 `InvalidConfig`（配置有误）二分：
 *
 * - [Blocked]：页面加载失败 / 被 WAF 拦截 / 限流 / 需要验证码 —— 问题在**网络或站点侧**；
 * - [InvalidConfig]：页面正常加载，但**你的选择器没匹配到内容** —— 问题在**配置侧**。
 *
 * 这条区分是 animeko 验证体系的核心：用户据此判断「是站方把我挡了」还是「我的规则写错了」。
 */
sealed interface TestIssue {
    val message: String

    data class Blocked(
        val reason: BlockReason,
        override val message: String,
    ) : TestIssue

    data class InvalidConfig(
        override val message: String,
    ) : TestIssue
}
