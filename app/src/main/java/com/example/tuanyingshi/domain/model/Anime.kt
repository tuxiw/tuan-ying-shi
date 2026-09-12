package com.example.tuanyingshi.domain.model

/**
 * 列表/卡片用——极简，只有展示需要的字段（对标 LaQoo Anime）。
 */
data class Anime(
    val title: String,
    val img: String,
    val detailUrl: String,
    val episodeName: String = "",
    val tags: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val year: Int? = null,
    val zoneId: Int? = null,
    val hits: Long? = null,
    val score: Double? = null,
    /** 总集数（后端 AnimeCardVO.totalEpisodes，0 表示未知）。 */
    val totalEpisodes: Int = 0,
    /** 当前更新到的集数（后端 AnimeCardVO.currentEpisode，0 表示未知）。 */
    val currentEpisode: Int = 0,
    /** 结果来源名（如某个 CSS 规则源的名称），空表示内置源。 */
    val sourceName: String = "",
    /** 结果来源图标 URL（CSS 规则源才有），空表示使用默认占位图标。 */
    val iconUrl: String = "",
    /** 提供本条结果的 CSS 规则源 id（聚合搜索时携带，详情页据此直达对应源）。 */
    val sourceId: String = "",
)

/**
 * 卡片「集数」徽标文案：优先用后端给的现成文案（episodeName，如「更新至 12 集」），
 * 缺失时回退到 totalEpisodes / currentEpisode 拼出「全 X 集」/「更新至 X 集」/「共 X 集」。
 * 非后端源通常不填总数，episodeName 即权威文案，故回退为空、不影响原展示。
 */
val Anime.episodeBadge: String
    get() = episodeName.ifBlank {
        when {
            currentEpisode > 0 -> "更新至 $currentEpisode 集"
            totalEpisodes > 0 -> "共 $totalEpisodes 集"
            else -> ""
        }
    }
