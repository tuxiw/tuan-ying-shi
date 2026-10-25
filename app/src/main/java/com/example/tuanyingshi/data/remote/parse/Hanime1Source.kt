package com.example.tuanyingshi.data.remote.parse

import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.EpisodeBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Api
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Video
import com.example.tuanyingshi.domain.model.AnimeStatus
import com.example.tuanyingshi.domain.model.AnimeType
import com.example.tuanyingshi.domain.model.Region
import com.example.tuanyingshi.util.ChromeMobileUserAgent
import com.example.tuanyingshi.util.log
import com.example.tuanyingshi.util.source_rule.SourceCookieManager
import java.net.URI

/**
 * Hanime1（hanime1.me）API 资源数据源。
 *
 * 与 [com.example.tuanyingshi.util.source_rule.SourceRule]（CSS 规则源）**完全无关**：
 * 数据由 [Hanime1Api] 直连站点抓取、[Hanime1Parser] 专用解析得到，走 [SourceMode.Hanime1]。
 * 域名仅在「数据源」里作为宿主配置，解析逻辑不依赖任何 CSS 选择器规则引擎。
 *
 * URL 约定（站内跳转统一用这两种）：
 * - 详情：`video/{code}`（code 即 `watch?v=` 后的标识）
 * - 选集（系列里的某一集）同 `video/{code}`
 */
object Hanime1Source : AnimeSource {

    private const val TAG = "Hanime1Source"
    const val SOURCE_ID = "builtin_hanime1"

    /** 来源名（历史记录等按来源过滤时使用，与各映射处的 sourceName 一致）。 */
    const val SOURCE_NAME = "Hanime1"

    /**
     * Hanime1 全站为成人内容，所有条目统一追加的 NSFW 标签。
     * 命中 [com.example.tuanyingshi.util.isNsfw] 关键词，NSFW 屏蔽开启时被 filterContent 剔除。
     */
    private const val NSFW_TAG = "NSFW"

    override val DEFAULT_DOMAIN: String = "https://hanime1.me"
    override var baseUrl: String = DEFAULT_DOMAIN
    override var WEB_URL: String = "hanime1.me"

    override fun onEnter() {
        super.onEnter()
        Hanime1Api.BASE_URL = baseUrl
    }

    /** `video/{code}` / `watch?v={code}` → code。 */
    private fun parseCode(url: String): String {
        val u = url.trim()
        u.substringAfterLast("watch?v=", "")
            .takeIf { it.isNotBlank() }
            ?.let { return it.substringBefore("&").substringBefore("#") }
        u.substringAfterLast("/video/", "")
            .takeIf { it.isNotBlank() }
            ?.let { return it.substringBefore("?").substringBefore("/").substringBefore("#") }
        return u.substringAfterLast("/").substringBefore("?").substringBefore("#")
    }

    // ───────────────────────── 首页 ─────────────────────────

    override suspend fun getHomeData(): List<HomeBean> {
        val home = Hanime1Api.home() ?: return emptyList()
        val sections = mutableListOf<HomeBean>()
        home.banner?.let {
            // 头图单独作为一个带 banners 的首屏区块，便于现有 UI 复用 BannerCarousel
            sections.add(
                HomeBean(
                    title = "推荐",
                    banners = listOf(
                        com.example.tuanyingshi.data.remote.dto.HomeBannerBean(
                            title = it.title,
                            imageUrl = it.picUrl,
                            actionType = "video",
                            actionValue = it.videoCode,
                        )
                    ),
                    animes = emptyList(),
                )
            )
        }
        home.categories.forEach { cat ->
            sections.add(
                HomeBean(
                    title = cat.title,
                    animes = cat.videos.map { it.toAnimeBean() },
                )
            )
        }
        return sections
    }

    // ───────────────────────── 详情 ─────────────────────────

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val code = parseCode(detailUrl)
        val detail = Hanime1Api.video(code) ?: return AnimeDetailBean(
            title = "", imgUrl = "", desc = "", relatedAnimes = emptyList(), sourceId = SOURCE_ID, sourceName = SOURCE_NAME
        )
        return detail.toAnimeDetailBean()
    }

    // ───────────────────────── 播放 ─────────────────────────

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val code = parseCode(episodeUrl)
        val detail = Hanime1Api.video(code) ?: return VideoBean(videoUrl = "", sourceId = SOURCE_ID, sourceName = SOURCE_NAME)
        // 选最高清晰度
        val best = detail.videoUrls.entries.maxByOrNull { it.key.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            ?: detail.videoUrls.entries.firstOrNull()
        val url = best?.value ?: ""
        val host = runCatching { URI(baseUrl).host }.getOrDefault(WEB_URL)
        val cookie = SourceCookieManager.cookieHeaderForHost(host).orEmpty()
        val ua = SourceCookieManager.userAgentForHost(host) ?: ChromeMobileUserAgent
        val headers = buildMap {
            put("User-Agent", ua)
            put("Referer", "$baseUrl/")
            if (cookie.isNotBlank()) put("Cookie", cookie)
        }
        "getVideoData($code) -> ${if (url.isNotBlank()) "ok(${best?.key})" else "EMPTY"}".log(TAG)
        return VideoBean(
            videoUrl = url,
            headers = headers,
            sourceId = SOURCE_ID,
            sourceName = "Hanime1",
        )
    }

    // ───────────────────────── 搜索 ─────────────────────────

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        return Hanime1Api.search(query = query, page = page).map { it.toAnimeBean() }
    }

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> = emptyMap()

    /**
     * 排行榜：站点无独立排行接口，用高级搜索按「类型 + 观看次数」聚合。
     * 返回的 map key 必须与 [com.example.tuanyingshi.ui.ranking.RankingCategory] 的
     * 里番 / 泡面番 label 一致（Hanime1 模式下排行榜只有这两个分类）。
     */
    override suspend fun getRanking(): Map<String, List<AnimeBean>> {
        val lianfan = Hanime1Api.search(query = "", genre = "裏番", sort = "觀看次數")
            .map { it.toAnimeBean() }
        val paomian = Hanime1Api.search(query = "", genre = "泡麵番", sort = "觀看次數")
            .map { it.toAnimeBean() }
        return buildMap {
            if (lianfan.isNotEmpty()) put("里番", lianfan)
            if (paomian.isNotEmpty()) put("泡面番", paomian)
        }
    }

    // ───────────────────────── 映射 ─────────────────────────

    private fun Hanime1Video.toAnimeBean(): AnimeBean = AnimeBean(
        title = title,
        img = coverUrl,
        url = "video/$videoCode",
        episodeName = if (duration.isNotBlank()) duration else "",
        // Hanime1 全站成人内容：统一打 NSFW 标签（屏蔽开启时由 filterContent 剔除）
        tags = buildList {
            if (genre.isNotBlank()) add(genre)
            add(NSFW_TAG)
        },
        sourceName = SOURCE_NAME,
        sourceId = SOURCE_ID,
    )

    private fun com.example.tuanyingshi.data.remote.hanime1.Hanime1Detail.toAnimeDetailBean(): AnimeDetailBean {
        val episodes = playlist.map {
            EpisodeBean(name = it.title.ifBlank { "正片" }, url = "video/${it.videoCode}")
        }
        return AnimeDetailBean(
            title = title,
            imgUrl = coverUrl,
            desc = buildString {
                chineseTitle?.takeIf { it.isNotBlank() }?.let { append("【$it】\n") }
                introduction?.takeIf { it.isNotBlank() }?.let { append(it) }
            }.trim(),
            relatedAnimes = related.map { it.toAnimeBean() },
            episodes = episodes,
            channels = if (episodes.isEmpty()) emptyMap() else mapOf(0 to episodes),
            region = Region.JP,
            type = AnimeType.SERIES,
            status = AnimeStatus.FINISHED,
            year = 2026,
            rating = 0.0,
            totalEpisodes = episodes.size,
            latestEpisodeLabel = episodes.lastOrNull()?.name ?: "",
            // 全站成人内容：打 NSFW 标签，详情/播放页的直达屏蔽检查据此生效
            tags = (tags + NSFW_TAG).distinct(),
            sourceId = SOURCE_ID,
            sourceName = SOURCE_NAME,
        )
    }
}
