package com.example.tuanyingshi.data.remote.dto

import com.example.tuanyingshi.domain.model.Anime

/**
 * @param title 动漫名称
 * @param img 图片url（获取时间表时可为空）
 * @param url 动漫详情url
 * @param episodeName 集数
 */
data class AnimeBean(
    val title: String,
    val img: String,
    val url: String,
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
    /** 结果来源名（如某个 CSS 规则源的名称），用于在 UI 上标注"数据来自哪个源"。 */
    val sourceName: String = "",
    /** 结果来源图标 URL（CSS 规则源才有），空表示使用默认占位图标。 */
    val iconUrl: String = "",
    /** 提供本条结果的 CSS 规则源 id（聚合搜索时携带，详情页据此直达对应源，避免查全部）。 */
    val sourceId: String = "",
) {
    fun toAnime(): Anime {
        return Anime(
            title = title,
            img = img,
            detailUrl = url,
            episodeName = episodeName,
            tags = tags,
            categories = categories,
            year = year,
            zoneId = zoneId,
            hits = hits,
            score = score,
            totalEpisodes = totalEpisodes,
            currentEpisode = currentEpisode,
            sourceName = sourceName,
            iconUrl = iconUrl,
            sourceId = sourceId,
        )
    }
}
