package com.example.tuanyingshi.domain.model

/**
 * 一个 CSS 规则源对某部番剧提供的选集数据（animeko 中对应一个 WebSource：
 * 其 channels 即为「线路」）。供详情页「数据源」与播放器「换源」选择器使用。
 */
data class SourceCandidate(
    /** 源标识（CSS 规则源 id）。 */
    val sourceId: String,
    /** 源名称（如某个 CSS 规则源名称）。 */
    val sourceName: String,
    /** 源图标 URL（CSS 规则源才有）。 */
    val iconUrl: String,
    /** 各线路（key=线路序号）对应的选集。 */
    val channels: Map<Int, List<Episode>>,
    /** 各线路名称（与 channels 排序后 key 对齐）。 */
    val channelNames: List<String>,
)
