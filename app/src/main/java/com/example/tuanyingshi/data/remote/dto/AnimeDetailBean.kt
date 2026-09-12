package com.example.tuanyingshi.data.remote.dto

import com.example.tuanyingshi.domain.model.AnimeDetail
import com.example.tuanyingshi.domain.model.AnimeStatus
import com.example.tuanyingshi.domain.model.AnimeType
import com.example.tuanyingshi.domain.model.Region

data class AnimeDetailBean(
    val title: String,
    val imgUrl: String,
    val desc: String,
    val tags: List<String> = emptyList(),
    val relatedAnimes: List<AnimeBean>,
    val episodes: List<EpisodeBean> = emptyList(),
    val channels: Map<Int, List<EpisodeBean>> = emptyMap(),
    /* 团影视扩展字段 */
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
) {
    fun toAnimeDetail(): AnimeDetail {
        // 后端模式下 channels 已含全部线路（每路一份选集），而 episodes 仅填充了默认线路 0 的选集。
        // 必须以 channels 为准，否则多线路会被压成单路；CSS 源通常不填 channels，回退到 episodes（单线路）。
        val tempChannels = if (channels.isNotEmpty()) {
            channels.mapValues { (_, list) -> list.sanitize().map { it.toEpisode() } }
        } else {
            mapOf(0 to episodes.sanitize().map { it.toEpisode() })
        }
        // 按 key 稳定排序，线路 Tab 与 channelNames 索引对齐
        val sortedChannels = tempChannels.toSortedMap()
        val channelNames = if (sortedChannels.size <= 1) {
            listOf("默认")
        } else {
            sortedChannels.keys.mapIndexed { index, _ -> "线路${index + 1}" }
        }
        return AnimeDetail(
            title = title,
            img = imgUrl,
            desc = desc,
            tags = tags.map { it.uppercase() },
            lastPosition = 0,
            episodes = sortedChannels[0] ?: emptyList(),
            relatedAnimes = relatedAnimes.sanitizeAnime().map { it.toAnime() },
            channels = sortedChannels,
            channelNames = channelNames,
            region = region,
            type = type,
            status = status,
            year = year,
            rating = rating,
            totalEpisodes = totalEpisodes,
            latestEpisodeLabel = latestEpisodeLabel,
            sourceId = sourceId,
            sourceName = sourceName,
            iconUrl = iconUrl,
        )
    }

    /**
     * 清洗选集列表：过滤空链接 / `javascript:` 占位链接，并按 url 去重。
     * 站点页面常把「播放」按钮、未解锁剧集写成 href="javascript:..."，
     * 若不过滤，UI 用 url 作 LazyColumn key 时会出现重复 key 直接崩溃。
     */
    private fun List<EpisodeBean>.sanitize(): List<EpisodeBean> =
        filter { it.url.isNotBlank() && !it.url.startsWith("javascript:", ignoreCase = true) }
            .distinctBy { it.url }

    /** 清洗相关推荐：过滤空 / `javascript:` 链接并按 url 去重（url 会作为 UI key）。 */
    private fun List<AnimeBean>.sanitizeAnime(): List<AnimeBean> =
        filter { it.url.isNotBlank() && !it.url.startsWith("javascript:", ignoreCase = true) }
            .distinctBy { it.url }
}
