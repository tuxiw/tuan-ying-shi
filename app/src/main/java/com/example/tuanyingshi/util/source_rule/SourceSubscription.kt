package com.example.tuanyingshi.util.source_rule

import kotlinx.serialization.Serializable

/**
 * 数据源订阅：指向一个远程规则 JSON（弹弹 play / 影视订阅导出格式）。
 *
 * 订阅下的数据源（[SourceRule]）由 [SourceSubscriptionRepository] 拉取后写入 [SourceRuleRepository]，
 * 并以 [SourceRule.subscriptionId] 关联回本订阅，便于在设置页分组展示与整体更新。
 *
 * @param id 订阅唯一 id（如 `sub_tuanyingshi`）
 * @param name 展示名（如「团影视」）
 * @param url 订阅 JSON 地址
 * @param format 订阅格式：`dandanplay`（弹弹 play 导出格式，默认）/ `kazumi`（KazumiRules 仓库）
 * @param lastUpdateTime 最近一次成功/失败更新的时间（epoch ms），0 表示从未更新
 * @param lastUpdateSuccess 最近一次更新是否成功
 * @param sourceIds 最近一次更新写入的 [SourceRule.id] 列表（用于统计「包含 xx 个数据源」）
 */
@Serializable
data class SourceSubscription(
    val id: String,
    val name: String,
    val url: String,
    val format: String = "dandanplay",
    val lastUpdateTime: Long = 0L,
    val lastUpdateSuccess: Boolean = false,
    val sourceIds: List<String> = emptyList(),
)
