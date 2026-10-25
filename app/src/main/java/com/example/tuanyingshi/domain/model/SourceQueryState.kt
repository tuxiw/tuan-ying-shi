package com.example.tuanyingshi.domain.model

/**
 * 单个 CSS 规则源在一次「全源查询」中的状态（数据源选择器「详细模式」用）。
 */
enum class SourceQueryStatus {
    /** 尚未查询到该源。 */
    Pending,

    /** 查询中。 */
    Querying,

    /** 查询成功（拿到线路/选集）。 */
    Ok,

    /** 查询完成但无该番剧的资源。 */
    Empty,

    /** 查询失败（网络错误 / 解析异常）。 */
    Failed,

    /** 命中验证页，需要用户先处理验证（Cloudflare 等）。 */
    NeedsVerify,

    /** 提前结束扫描时被跳过（已有可用数据，剩余源不再查询）。 */
    Stopped,
}

/**
 * 一个规则源的查询状态快照。
 *
 * @param ruleId 规则 id（与 [SourceCandidate.sourceId] 对应）
 * @param episodeCount 查询成功时的选集总数（全部线路求和）
 */
data class SourceQueryState(
    val ruleId: String,
    val name: String,
    val iconUrl: String,
    val status: SourceQueryStatus = SourceQueryStatus.Pending,
    val episodeCount: Int = 0,
)
