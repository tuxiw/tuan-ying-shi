package com.example.tuanyingshi.util

import com.example.tuanyingshi.util.source_rule.RuleEngineKind

/**
 * 用户可管理的「资源源」配置（对标旧 SettingsViewModel 里写死的内置源列表）。
 *
 * - 内置源 [isBuiltIn] = true：可被用户修改（名称 / 域名），但**不可删除**；
 * - 用户自定义源 [isBuiltIn] = false：可新增 / 修改 / 删除。
 *
 * [type] 决定使用哪个解析器（与 [SourceMode] 一一对应），[baseUrl] 为自定义站点域名
 * （切源时会被写入对应解析器单例，使首页 / 详情 / 播放都走该域名）。
 */
data class ResourceSource(
    val id: String,
    val name: String,
    val type: SourceMode,
    val baseUrl: String,
    val isBuiltIn: Boolean,
    /**
     * 当 [type] == [SourceMode.Rule] 时，指向 [com.example.tuanyingshi.util.source_rule.SourceRuleRepository]
     * 中对应规则的 id；其余类型恒为 null。规则源不落库到本仓库（动态从规则仓库派生）。
     */
    val ruleId: String? = null,
    /**
     * 当该规则源来自远程订阅时，指向 [com.example.tuanyingshi.util.source_rule.SourceSubscription.id]；
     * 空字符串表示非订阅来源（用户独立创建或内置）。用于在设置页「数据源订阅」区域分组展示。
     */
    val subscriptionId: String = "",
    /**
     * 图标 URL，目前仅对 CSS 规则源有意义（取对应 [SourceRule.iconUrl]）。
     * API 源与自定义源留空，使用默认占位图标。
     */
    val iconUrl: String = "",
    /**
     * 是否为「弹弹play（API 源）」聚合源：选中后，搜索 / 选集 / 播放统一走全部 CSS 规则源，
     * 浏览与元数据仍走次元城 API。该源为内置虚拟源（[isBuiltIn] = true），不落库。
     */
    val aggregateCss: Boolean = false,
    /**
     * 规则源的解析引擎（[com.example.tuanyingshi.util.source_rule.RuleEngineKind]）。
     * 仅 [type] == [SourceMode.Rule] 时有意义：[RuleEngineKind.Xpath] 显示「XPATH源」、
     * [RuleEngineKind.Css] 显示「外部资源」；非规则源恒为 null。
     */
    val engine: RuleEngineKind? = null,
)

/** 解析器类型 → 中文标签（用于设置页下拉与展示）。 */
fun SourceMode.label(): String = when (this) {
    SourceMode.Cycanime -> "次元城"
    SourceMode.Silisili -> "嘶哩嘶哩"
    SourceMode.Girigiri -> "Girigiri"
    SourceMode.Rule -> "CSS源"
}
