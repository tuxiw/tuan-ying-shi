package com.example.tuanyingshi.domain.model

/**
 * 详情页用——包含选集、相关推荐、多线路（对标 LaQoo AnimeDetail）。
 *
 * 扩展字段（region/type/status/year/rating 等）为团影视分类筛选保留，
 * LaQoo 原版不包含这些字段，默认值不影响的页展示。
 */
data class AnimeDetail(
    val title: String,
    val img: String,
    val desc: String,
    val tags: List<String> = emptyList(),
    val lastPosition: Int = 0,
    val episodes: List<Episode> = emptyList(),
    val relatedAnimes: List<Anime> = emptyList(),
    val channelIndex: Int = 0,
    val channels: Map<Int, List<Episode>> = emptyMap(),
    /** 各线路名称，索引与排序后的 channels key 一一对应（用于详情/播放器线路切换 Tab）。 */
    val channelNames: List<String> = emptyList(),
    /* 团影视扩展字段（分类筛选用） */
    val region: Region = Region.JP,
    val type: AnimeType = AnimeType.SERIES,
    val status: AnimeStatus = AnimeStatus.ONGOING,
    val year: Int = 2026,
    val rating: Double = 0.0,
    val totalEpisodes: Int = 0,
    val latestEpisodeLabel: String = "",
    /** 提供本详情数据的来源标识（CSS 规则源 id / 内置源 id），空表示未知。 */
    val sourceId: String = "",
    /** 提供本详情数据的来源名（如某个 CSS 规则源名称 / "次元城"），用于详情页"来源"展示。 */
    val sourceName: String = "",
    /** 来源图标 URL（CSS 规则源才有），空表示使用默认占位图标。 */
    val iconUrl: String = "",
)
