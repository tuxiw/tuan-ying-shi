package com.example.tuanyingshi.data.remote.parse

import com.example.tuanyingshi.data.remote.backend.DedicatedBackendClient
import com.example.tuanyingshi.data.remote.backend.AnimeCardVO
import com.example.tuanyingshi.data.remote.backend.AnimeDetailVO
import com.example.tuanyingshi.data.remote.backend.BannerVO
import com.example.tuanyingshi.data.remote.backend.DanmakuItemVO
import com.example.tuanyingshi.data.remote.backend.EpisodeVO
import com.example.tuanyingshi.data.remote.backend.HomeSectionVO
import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.EpisodeBean
import com.example.tuanyingshi.data.remote.dto.HomeBannerBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.data.remote.dandanplay.DanmakuItem
import com.example.tuanyingshi.domain.model.AnimeStatus
import com.example.tuanyingshi.domain.model.AnimeType
import com.example.tuanyingshi.domain.model.Region
import com.example.tuanyingshi.util.log

/**
 * 专属 APP 后端模式数据源（实验功能）：把专属后端 `/api/v1` 内容接口适配成 [AnimeSource]。
 *
 * 仅在 [com.example.tuanyingshi.util.DedicatedBackendPrefs.isEnabled] 为 true 时，
 * 由 [com.example.tuanyingshi.util.SourceHolder] 的三个 getter 返回，用于「内容数据」来源。
 * 与 [BackendAnimeSource]（[com.example.tuanyingshi.util.BackendPrefs] 自建后端，内容 + 用户功能）
 * 相互独立、互不干扰：本源只接管内容，用户功能仍走自建后端。
 */
object DedicatedBackendAnimeSource : AnimeSource {

    private const val TAG = "DedicatedBackendAnimeSource"
    private const val SOURCE_NAME = "团影视专属后端"

    override val DEFAULT_DOMAIN: String get() = "dedicated_backend"
    override var baseUrl: String = ""
    override var WEB_URL: String = "dedicated_backend"

    private val api get() = DedicatedBackendClient.api

    // ───────────────────────── 首页 ─────────────────────────

    override suspend fun getHomeData(): List<HomeBean> {
        val home = runCatching { api.home().data }.getOrNull() ?: return emptyList()
        val banners = home.banners?.mapNotNull { it.toBannerBean() }.orEmpty()
        val sections = home.sections.orEmpty()
        if (sections.isEmpty()) {
            return if (banners.isNotEmpty()) {
                listOf(HomeBean(title = "推荐", animes = emptyList(), banners = banners))
            } else {
                emptyList()
            }
        }
        // 首页把第一个区块的 banners 透传到首屏（HomeViewModel 从第一个 HomeBean 取 banners）。
        return sections.mapIndexed { i, sec ->
            val animes = (sec.animeList ?: sec.animes).orEmpty().map { it.toBean() }
            if (i == 0) {
                HomeBean(title = sec.title ?: "推荐", animes = animes, banners = banners)
            } else {
                HomeBean(title = sec.title ?: "", animes = animes)
            }
        }
    }

    // ───────────────────────── 周表 ─────────────────────────

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        val map = runCatching { api.scheduleWeek().data }.getOrNull() ?: return emptyMap()
        // 后端 scheduleWeek() 返回的键为 1..7（1=周一 … 7=周日），
        // 而 ScheduleViewModel 按下标 0..6（0=周一 … 6=周日）索引，故整体左移 1 位对齐。
        return map
            .mapKeys { (k, _) -> ((k.toIntOrNull() ?: 1) - 1).coerceIn(0, 6) }
            .mapValues { (_, list) -> list.orEmpty().map { it.toBean() } }
    }

    // ───────────────────────── 排行 ─────────────────────────

    override suspend fun getRanking(): Map<String, List<AnimeBean>> {
        val tv = runCatching { api.ranking(1, 20, "week").data }.getOrNull().orEmpty()
        val movie = runCatching { api.ranking(2, 20, "week").data }.getOrNull().orEmpty()
        return mapOf(
            "TV番组" to tv.map { it.toBean() },
            "剧场番组" to movie.map { it.toBean() },
        )
    }

    // ───────────────────────── 搜索 ─────────────────────────

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        val resp = runCatching { api.search(query, page, 20).data }.getOrNull() ?: return emptyList()
        return resp.records.orEmpty().map { it.toBean() }
    }

    // ───────────────────────── 分类筛选 ─────────────────────────

    override suspend fun getFilterData(
        zoneId: Int,
        tag: String?,
        year: Int?,
        orderBy: String?,
        page: Int,
    ): List<AnimeBean> {
        val q = LinkedHashMap<String, String>()
        q["zoneId"] = zoneId.toString()
        if (!tag.isNullOrBlank()) q["tag"] = tag
        if (year != null) q["year"] = year.toString()
        if (!orderBy.isNullOrBlank()) q["orderBy"] = orderBy
        q["page"] = page.toString()
        q["size"] = "20"
        val resp = runCatching { api.filterList(q).data }.getOrNull() ?: return emptyList()
        return resp.records.orEmpty().map { it.toBean() }
    }

    // ───────────────────────── 详情 ─────────────────────────

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val id = extractId(detailUrl)
        if (id.isBlank()) return emptyDetail()
        val d = runCatching { api.animeDetail(id).data }.getOrNull() ?: return emptyDetail()
        "getAnimeDetail('${d.title}'): episodes=${(d.episodes?.size ?: 0)}, related=${(d.relatedAnimes?.size ?: 0)}".log(TAG)
        val related = d.relatedAnimes.orEmpty().map { it.toBean() }
        val episodes = d.episodes.orEmpty().map { it.toEpisodeBean() }
        val channels = d.channels.orEmpty()
            .mapKeys { it.key.toIntOrNull() ?: 0 }
            .mapValues { (_, list) -> list.orEmpty().map { ep -> ep.toEpisodeBean() } }
        return AnimeDetailBean(
            title = d.title ?: "",
            imgUrl = firstNonBlank(d.img, d.imgUrl, d.cover) ?: "",
            desc = firstNonBlank(d.desc, d.description) ?: "",
            tags = d.tags.orEmpty(),
            relatedAnimes = related,
            episodes = episodes,
            channels = channels,
            region = mapRegion(d.region),
            type = mapType(d.type),
            status = mapStatus(d.status),
            year = d.year ?: 2026,
            rating = d.rating ?: d.score ?: 0.0,
            totalEpisodes = d.totalEpisodes ?: 0,
            latestEpisodeLabel = d.latestEpisodeLabel ?: "",
            sourceId = "dedicated_backend",
            sourceName = SOURCE_NAME,
            iconUrl = "",
        )
    }

    // ───────────────────────── 视频：后端直接给出播放地址 ─────────────────────────

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        // 后端 episode.url 已是可直接播放的地址（m3u8 / 后端 play 端点），原样返回即可。
        return VideoBean(
            videoUrl = episodeUrl,
            headers = emptyMap(),
            sourceId = "dedicated_backend",
            sourceName = SOURCE_NAME,
        )
    }

    // ───────────────────────── 弹幕（供 PlayerViewModel 在专属后端模式下调用）─────────────────────────

    suspend fun getDanmaku(animeId: Long): List<DanmakuItem> {
        val resp = runCatching { api.danmaku(animeId = animeId, withRelated = true).data }
            .getOrNull() ?: return emptyList()
        return resp.comments.orEmpty().mapNotNull { it.toDanmakuItem() }
    }

    // ───────────────────────── 工具 ─────────────────────────

    private fun emptyDetail(): AnimeDetailBean =
        AnimeDetailBean(title = "", imgUrl = "", desc = "", relatedAnimes = emptyList())

    /** 从 detailUrl（可能是 `/api/v1/anime/123` 或 `123`）提取纯数字 id。 */
    private fun extractId(detailUrl: String): String {
        val tail = detailUrl.substringAfterLast("/")
        return tail.takeWhile { it.isDigit() }
    }

    private fun firstNonBlank(vararg v: String?): String? = v.firstOrNull { !it.isNullOrBlank() }

    private fun mapRegion(s: String?): Region =
        runCatching { Region.valueOf(s ?: "JP") }.getOrDefault(Region.JP)

    private fun mapType(s: String?): AnimeType =
        runCatching { AnimeType.valueOf(s ?: "SERIES") }.getOrDefault(AnimeType.SERIES)

    private fun mapStatus(s: String?): AnimeStatus =
        runCatching { AnimeStatus.valueOf(s ?: "ONGOING") }.getOrDefault(AnimeStatus.ONGOING)

    private fun AnimeCardVO.toBean(): AnimeBean {
        // AnimeBean.url 即详情地址（toAnime() 会映射到 Anime.detailUrl），专属后端模式下用站内 id 作为该 key。
        val idStr = id?.toString()
            ?: detailUrl?.substringAfterLast("/")?.takeIf { it.any { c -> c.isDigit() } }
            ?: url
            ?: ""
        return AnimeBean(
            title = title ?: "",
            img = firstNonBlank(img, imgUrl, cover) ?: "",
            url = idStr,
            episodeName = episodeName ?: "",
            tags = tags.orEmpty(),
            categories = categories.orEmpty(),
            year = year,
            zoneId = zoneId,
            hits = hits,
            score = score,
            totalEpisodes = totalEpisodes ?: 0,
            currentEpisode = currentEpisode ?: 0,
            sourceName = SOURCE_NAME,
            sourceId = "dedicated_backend",
        )
    }

    private fun EpisodeVO.toEpisodeBean(): EpisodeBean =
        EpisodeBean(name = name ?: title ?: "", url = url ?: videoUrl ?: "")

    private fun BannerVO.toBannerBean(): HomeBannerBean? {
        val image = firstNonBlank(imageUrl, image) ?: return null
        return HomeBannerBean(
            title = title ?: "",
            imageUrl = image,
            actionType = actionType,
            actionValue = actionValue,
        )
    }

    private fun DanmakuItemVO.toDanmakuItem(): DanmakuItem? {
        val text = m ?: return null
        val p = this.p ?: return DanmakuItem(time = 0.0, text = text)
        val parts = p.split(",")
        val time = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val mode = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val colorInt = parts.getOrNull(2)?.toIntOrNull() ?: 16777215
        val color = "#%06X".format(0xFFFFFF and colorInt)
        return DanmakuItem(time = time, text = text, color = color, mode = mode)
    }
}
