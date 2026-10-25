package com.example.tuanyingshi.data.remote.parse

import com.example.tuanyingshi.data.remote.FilterPage
import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.EpisodeBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.data.remote.girigiri.GirigiriApi
import com.example.tuanyingshi.data.remote.girigiri.GirigiriDetail
import com.example.tuanyingshi.data.remote.girigiri.GirigiriListPage
import com.example.tuanyingshi.data.remote.girigiri.GirigiriVod
import com.example.tuanyingshi.domain.model.AnimeStatus
import com.example.tuanyingshi.domain.model.AnimeType
import com.example.tuanyingshi.domain.model.Region
import com.example.tuanyingshi.util.getDefaultDomain
import com.example.tuanyingshi.util.log
import com.example.tuanyingshi.util.source_rule.SourceCookieManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Calendar

/**
 * Girigiri（girigirilove.com）**API 资源**数据源。
 *
 * 站点为 macCMS 架构，全部数据走 m3u8 子域 JSON 接口（见 [GirigiriApi]），
 * 与通用 CSS 规则引擎完全解耦；域名仅在「数据源」里作为宿主配置。
 *
 * URL 约定（站内跳转统一用这两种）：
 * - 详情：`detail/{vod_id}`
 * - 选集：详情接口直出的 m3u8 绝对地址（播放时原样返回，无需再解析）
 */
object GirigiriSource : AnimeSource {

    private const val TAG = "GirigiriSource"
    const val SOURCE_ID = "builtin_girigiri"

    /** 来源名（历史记录等按来源过滤时使用，与各映射处的 sourceName 一致）。 */
    const val SOURCE_NAME = "Girigiri"

    override val DEFAULT_DOMAIN: String = "https://ani.girigirilove.com"
    override var baseUrl: String = getDefaultDomain()
    override var WEB_URL: String = "ani.girigirilove.com"

    /** 桌面 Chrome UA（接口与播放请求均按浏览器指纹发送）。 */
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 搜索每页条数（≥ SearchViewModel 的 SEARCH_PAGE_SIZE 判定线，保证「加载更多」可用）。 */
    private const val SEARCH_LIMIT = 20

    override fun onEnter() {
        super.onEnter()
        // 把当前选中的 Web 域同步给 API 客户端（派生 m3u8 接口域 / Referer / Origin）
        GirigiriApi.BASE_URL = baseUrl
    }

    // ───────────────────────── 首页 ─────────────────────────

    override suspend fun getHomeData(): List<HomeBean> = coroutineScope {
        val weekDeferred = async { runCatching { GirigiriApi.weekList() }.getOrDefault(emptyList()) }
        val recentDeferred = async {
            runCatching { GirigiriApi.list(typeId = GirigiriApi.TYPE_ID_TV, limit = 24) }
                .getOrDefault(GirigiriListPage(0, emptyList()))
        }
        val likeDeferred = async { runCatching { GirigiriApi.likeList(pageSize = 20) }.getOrDefault(emptyList()) }

        val week = weekDeferred.await()
        val recent = recentDeferred.await()
        val like = likeDeferred.await()

        // 每日更新：取今天（0=周一 .. 6=周日）的排期；今天无数据时回退第一天
        val todayIndex = todayWeekdayIndex()
        val today = week.firstOrNull { it.week - 1 == todayIndex }?.videos
            ?: week.firstOrNull()?.videos.orEmpty()

        buildList {
            if (today.isNotEmpty()) {
                add(HomeBean(title = "每日更新", animes = today.map { it.toAnimeBean() }))
            }
            if (recent.rows.isNotEmpty()) {
                add(HomeBean(title = "最近更新", animes = recent.rows.map { it.toAnimeBean() }))
            }
            if (like.isNotEmpty()) {
                add(HomeBean(title = "猜你喜欢", animes = like.map { it.toAnimeBean() }))
            }
        }
    }

    // ───────────────────────── 详情 ─────────────────────────

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val vodId = parseVodId(detailUrl) ?: return emptyDetail()
        val detail = runCatching { GirigiriApi.detail(vodId) }.onFailure {
            it.log(TAG, "getAnimeDetail($detailUrl) failed")
        }.getOrNull() ?: return emptyDetail()
        return detail.toAnimeDetailBean()
    }

    // ───────────────────────── 播放 ─────────────────────────

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        // 选集 URL 即 m3u8 直链（详情接口已带播放地址），直接返回
        if (episodeUrl.startsWith("http://") || episodeUrl.startsWith("https://")) {
            return VideoBean(
                videoUrl = episodeUrl,
                headers = playHeaders(),
                sourceId = SOURCE_ID,
                sourceName = SOURCE_NAME,
            )
        }
        // 兜底：传入 detail/{id} 形式 → 拉详情取首条线路首集
        val vodId = parseVodId(episodeUrl) ?: return VideoBean(
            videoUrl = "", sourceId = SOURCE_ID, sourceName = SOURCE_NAME
        )
        val detail = runCatching { GirigiriApi.detail(vodId) }.getOrNull() ?: return VideoBean(
            videoUrl = "", sourceId = SOURCE_ID, sourceName = SOURCE_NAME
        )
        val url = detail.playLines.firstOrNull()?.episodes?.firstOrNull()?.url.orEmpty()
        "getVideoData(detail/$vodId) -> ${if (url.isBlank()) "EMPTY" else "ok"}".log(TAG)
        return VideoBean(
            videoUrl = url,
            headers = playHeaders(),
            sourceId = SOURCE_ID,
            sourceName = SOURCE_NAME,
        )
    }

    /** 播放请求头：UA + Referer（站点要求）+ 过盾 Cookie（若有）。 */
    private fun playHeaders(): Map<String, String> = buildMap {
        val cookie = SourceCookieManager.cookieHeaderForHost(GirigiriApi.apiHost).orEmpty()
        val ua = SourceCookieManager.userAgentForHost(GirigiriApi.apiHost) ?: DESKTOP_UA
        put("User-Agent", ua)
        put("Referer", "${baseUrl.trimEnd('/')}/")
        if (cookie.isNotBlank()) put("Cookie", cookie)
    }

    // ───────────────────────── 搜索 ─────────────────────────

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        if (query.isBlank()) return emptyList()
        val result = runCatching {
            GirigiriApi.list(
                vodName = query,
                offset = (page - 1) * SEARCH_LIMIT,
                limit = SEARCH_LIMIT,
            )
        }.onFailure { it.log(TAG, "getSearchData('$query', $page) failed") }.getOrNull()
            ?: return emptyList()
        return result.rows.map { it.toAnimeBean() }
    }

    // ───────────────────────── 周日程 ─────────────────────────

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        val week = runCatching { GirigiriApi.weekList() }.onFailure {
            it.log(TAG, "getWeekData failed")
        }.getOrDefault(emptyList())
        // 源层约定：key 0=周一 .. 6=周日（接口 week 为 1..7）
        return week.mapNotNull { day ->
            val key = day.week - 1
            if (key in 0..6) key to day.videos.map { it.toAnimeBean() } else null
        }.toMap()
    }

    // ───────────────────────── 排行榜 ─────────────────────────

    /**
     * 排行榜：map key 与 [com.example.tuanyingshi.ui.ranking.RankingCategory] 的
     * TV番组 / 剧场番组 label 一致（Girigiri 模式下排行榜只有这两个分类）。
     */
    override suspend fun getRanking(): Map<String, List<AnimeBean>> = coroutineScope {
        val tvDeferred = async {
            runCatching { GirigiriApi.ranking(GirigiriApi.RANK_ID_TV, pageSize = 20) }.getOrNull()
        }
        val movieDeferred = async {
            runCatching { GirigiriApi.ranking(GirigiriApi.RANK_ID_MOVIE, pageSize = 20) }.getOrNull()
        }
        buildMap {
            tvDeferred.await()?.rows?.filter { it.vodName.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?.let { put("TV番组", it.map { v -> v.toAnimeBean() }) }
            movieDeferred.await()?.rows?.filter { it.vodName.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?.let { put("剧场番组", it.map { v -> v.toAnimeBean() }) }
        }
    }

    // ───────────────────────── 分类筛选 ─────────────────────────

    /**
     * 分类筛选（Vod/get_list）。
     * - zoneId：1=TV番组 → type_id=2；2=剧场番组 → type_id=21；
     * - tag：题材 → vod_class；year：年份 → vod_year（含 "2000至90年代" 等非数字值）；
     * - orderBy：hits=热度 / score=评分，null=更新时间；
     * - region/type/status：接口无对应维度，忽略。
     */
    override suspend fun getFilterData(
        zoneId: Int,
        tag: String?,
        year: String?,
        orderBy: String?,
        region: String?,
        type: String?,
        status: String?,
        page: Int,
    ): FilterPage<AnimeBean> {
        val typeId = if (zoneId == 2) GirigiriApi.TYPE_ID_MOVIE else GirigiriApi.TYPE_ID_TV
        val limit = 24
        val result = runCatching {
            GirigiriApi.list(
                typeId = typeId,
                offset = (page - 1) * limit,
                limit = limit,
                vodClass = tag?.takeIf { it.isNotBlank() },
                vodYear = year?.takeIf { it.isNotBlank() },
                orderBy = orderBy?.takeIf { it.isNotBlank() },
            )
        }.onFailure { it.log(TAG, "getFilterData(zone=$zoneId, tag=$tag, year=$year, page=$page) failed") }
            .getOrNull() ?: return FilterPage(emptyList())
        val loaded = (page - 1) * limit + result.rows.size
        return FilterPage(
            items = result.rows.map { it.toAnimeBean() },
            hasMore = result.rows.isNotEmpty() && loaded < result.total,
        )
    }

    // ───────────────────────── 诊断 ─────────────────────────

    /**
     * 诊断探针：站点 Web 前端在部分地区不可达，改为探测 m3u8 接口域（时间表接口原文）。
     */
    override suspend fun getRootHtml(): String = GirigiriApi.rootProbe()

    // ───────────────────────── 映射 ─────────────────────────

    private fun GirigiriVod.toAnimeBean(): AnimeBean = AnimeBean(
        title = vodName,
        img = GirigiriApi.pic(vodPic),
        url = "detail/$vodId",
        // 列表行用备注（"更新至04集 / 23點30分" / "已完结"），周日程行用 vod_time
        episodeName = vodRemarks.ifBlank { vodTime },
        categories = vodClass.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() },
        year = vodYear.toIntOrNull(),
        hits = vodHits.takeIf { it > 0 },
        score = vodScore.toDoubleOrNull(),
        sourceName = SOURCE_NAME,
        sourceId = SOURCE_ID,
    )

    private fun GirigiriDetail.toAnimeDetailBean(): AnimeDetailBean {
        // 播放线路：cht / chs 等 → channels（多线路时 UI 展示 线路1 / 线路2 …）
        val channels = playLines.mapIndexedNotNull { index, line ->
            val episodes = line.episodes.map { EpisodeBean(name = formatEpisodeName(it.name), url = it.url) }
            if (episodes.isEmpty()) null else index to episodes
        }.toMap()
        val firstLineEpisodes = channels[0].orEmpty()
        val totalEpisodes = channels.values.maxOfOrNull { it.size } ?: 0

        val tags = buildList {
            addAll(vodClass.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() })
            if (vodVersion.isNotBlank()) add(vodVersion)
            if (vodState.isNotBlank()) add(vodState)
        }.distinct()

        return AnimeDetailBean(
            title = vodName,
            imgUrl = GirigiriApi.pic(vodPic),
            desc = vodBlurb.ifBlank { stripHtml(vodContent) },
            tags = tags,
            relatedAnimes = emptyList(),
            episodes = firstLineEpisodes,
            channels = channels,
            region = Region.JP,
            type = if (vodVersion.contains("OVA", ignoreCase = true)) AnimeType.OVA else AnimeType.SERIES,
            status = if (vodRemarks.contains("完结")) AnimeStatus.FINISHED else AnimeStatus.ONGOING,
            year = vodYear.toIntOrNull() ?: 2026,
            rating = vodScore.toDoubleOrNull() ?: 0.0,
            totalEpisodes = totalEpisodes,
            latestEpisodeLabel = firstLineEpisodes.lastOrNull()?.name.orEmpty(),
            sourceId = SOURCE_ID,
            sourceName = SOURCE_NAME,
        )
    }

    /** 集名规范化：纯数字 → 第XX集；空 → 正片；其余原样。 */
    private fun formatEpisodeName(name: String): String {
        val n = name.trim()
        if (n.isEmpty()) return "正片"
        if (n.all { it.isDigit() }) return "第${n}集"
        return n
    }

    /** 去掉简介里的 HTML 标签与常见实体（详情接口 vod_content 为富文本）。 */
    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        return html
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("[ \\t]*\\r?\\n[ \\t]*"), "\n")
            .trim()
    }

    private fun emptyDetail(): AnimeDetailBean = AnimeDetailBean(
        title = "", imgUrl = "", desc = "", relatedAnimes = emptyList(),
        sourceId = SOURCE_ID, sourceName = SOURCE_NAME,
    )

    // ───────────────────────── 工具 ─────────────────────────

    /**
     * 从详情 URL 取 vod_id。兼容 `detail/{id}` / `?vod_id={id}` / 裸数字；
     * 旧版网页式地址（如 `/xxx.html`）无法推导，返回 null。
     */
    private fun parseVodId(url: String): Long? {
        val u = url.trim()
        if (u.isEmpty()) return null
        Regex("detail/(\\d+)").find(u)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        Regex("vod_id=(\\d+)").find(u)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        return u.substringAfterLast('/').substringBefore('?').substringBefore('#')
            .toLongOrNull()
    }

    /** 今天星期几的索引：0=周一 .. 6=周日（与周日程 key 约定一致）。 */
    private fun todayWeekdayIndex(): Int {
        val cal = Calendar.getInstance()
        // Calendar: SUNDAY=1 .. SATURDAY=7
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
}
