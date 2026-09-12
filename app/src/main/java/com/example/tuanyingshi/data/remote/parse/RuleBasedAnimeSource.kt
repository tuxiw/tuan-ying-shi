package com.example.tuanyingshi.data.remote.parse

import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.util.source_rule.RuleExecutor
import com.example.tuanyingshi.util.source_rule.SourceRule
import java.net.URI

/**
 * CSS 规则源：把 [SourceRule] 映射到 [AnimeSource]。
 *
 * 路由策略（用户选择 CSS 源作为默认数据源时）：
 * - 首页 / 周表 / 分类 / 排行榜：始终走次元城 API（浏览类发现流，规则模型暂不覆盖）；
 * - 番剧详情页：元数据走次元城（详情 URL 为 cycani 时），剧集（选集）按标题在 CSS 站点匹配获取；
 * - 搜索 / 播放源（视频直链）：走 CSS 规则。
 */
class RuleBasedAnimeSource(private val rule: SourceRule) : AnimeSource {

    override val DEFAULT_DOMAIN: String = rule.search.baseUrl.ifBlank { "" }
    override var baseUrl: String = rule.search.baseUrl
    override var WEB_URL: String = runCatching { URI(rule.search.baseUrl).host ?: "" }.getOrDefault("")

    // ── 浏览类：始终次元城 API ──
    override suspend fun getHomeData(): List<HomeBean> = CycanimeSource.getHomeData()
    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> = CycanimeSource.getWeekData()
    override suspend fun getFilterData(
        zoneId: Int,
        tag: String?,
        year: Int?,
        orderBy: String?,
        page: Int,
    ): List<AnimeBean> = CycanimeSource.getFilterData(zoneId, tag, year, orderBy, page)
    override suspend fun getRanking(): Map<String, List<AnimeBean>> = CycanimeSource.getRanking()

    // ── 详情页：元数据走次元城，剧集走 CSS ──
    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val isCycani = detailUrl.contains("cycani", ignoreCase = true)
        val cyMeta = if (isCycani) {
            runCatching { CycanimeSource.getAnimeDetail(detailUrl) }.getOrNull()
        } else {
            null
        }
        val fromCss: Boolean
        val bean = if (isCycani) {
            // 元数据来自次元城；剧集按标题在 CSS 站点匹配获取并覆盖
            val cssDetail = findCssDetailByTitle(cyMeta?.title ?: "")
            fromCss = cssDetail != null
            when {
                cyMeta != null && cssDetail != null -> cyMeta.copy(channels = cssDetail.channels)
                cyMeta != null -> cyMeta
                cssDetail != null -> cssDetail
                else -> null
            }
        } else {
            // 非次元城 URL（如来自 CSS 搜索结果）：整页走 CSS
            fromCss = true
            RuleExecutor.getAnimeDetail(rule, detailUrl)
        }
        // 标注册提供本详情数据的 CSS 规则源（animeko：结果始终携带 mediaSource 信息）
        return bean?.let { if (fromCss) it.copy(sourceId = rule.id, sourceName = rule.name, iconUrl = rule.iconUrl) else it }
            ?: AnimeDetailBean(title = cyMeta?.title ?: "", imgUrl = "", desc = "", relatedAnimes = emptyList())
    }

    // ── 搜索：CSS（走 animeko 的多名称 + 平台过滤语义） ──
    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        // 单 CSS 规则源不翻页：page>1 视为已到底，返回空，避免分页无限重复（与聚合源一致）。
        if (page > 1) return emptyList()
        return RuleExecutor.toAnimeBeans(RuleExecutor.searchSubjects(rule, listOf(query)))
            .map { it.copy(sourceName = rule.name, sourceId = rule.id) }
    }

    // ── 播放源：CSS ──
    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val match = RuleExecutor.matchVideoDetailed(rule, episodeUrl)
        return VideoBean(
            videoUrl = match.url,
            headers = match.headers,
            sourceId = rule.id,
            sourceName = rule.name,
            iconUrl = rule.iconUrl,
        )
    }

    /**
     * 在 CSS 站点按标题搜索并取最匹配条目，返回其详情（含剧集）。
     *
     * 对齐 animeko web 源：web 源**不按标题相似度硬筛**（`createFiltersForSubject` 返回空），
     * 只信搜索结果——因为搜索关键词本就由条目名构造，命中结果即为目标条目。
     * 故这里不再设 ≥80/≥50 的匹配门槛，直接取相似度最高的结果下钻详情，
     * 避免正确条目因相似度偏低被误杀（旧实现会因 matchRate<50 而整体回退 null）。
     */
    private suspend fun findCssDetailByTitle(title: String): AnimeDetailBean? {
        if (title.isBlank()) return null
        val results = RuleExecutor.searchSubjects(rule, listOf(title))
        if (results.isEmpty()) return null
        val best = results.maxByOrNull { RuleExecutor.matchRate(it.title, title) } ?: return null
        if (best.title.isBlank()) return null
        return RuleExecutor.getAnimeDetail(rule, best.url)
    }
}
