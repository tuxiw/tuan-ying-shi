package com.example.tuanyingshi.data.remote.parse

import com.example.tuanyingshi.data.remote.api.cycani.CycaniApiService
import com.example.tuanyingshi.data.remote.api.cycani.CycaniAuthManager
import com.example.tuanyingshi.data.remote.api.cycani.CycaniVideoDto
import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.EpisodeBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.HomeBannerBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.data.remote.parse.util.WebViewUtil
import com.example.tuanyingshi.domain.model.AnimeStatus
import com.example.tuanyingshi.domain.model.AnimeType
import com.example.tuanyingshi.domain.model.Region
import com.example.tuanyingshi.util.DefaultUserAgent
import com.example.tuanyingshi.util.DownloadManager
import com.example.tuanyingshi.util.applyProxy
import com.example.tuanyingshi.util.getDefaultDomain
import com.example.tuanyingshi.util.log
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 次元城（cycani.org）数据源。
 *
 * 逐步从 WebView/HTML 抓取迁移到 HTTP API：
 * - 首页推荐：/api/index/recommend（TV番组 / 剧场版）+ /api/app/adverts?position=banner（轮播图）
 * - 排行：/api/ranks/1/videos（接口对接，需登录）
 * - 详情/选集/播放：/api/videos/{id} + /api/videos/{id}/sections + /api/v2/sections/{id}/play-url
 * - 搜索/排期：保留 WebView/OkHttp HTML 抓取作为兜底，待后续接口补齐后替换
 *
 * 所有 API/HTTP 请求统一附加固定头（x-app-name 等），并在登录后自动带上 Authorization。
 */
object CycanimeSource : AnimeSource {

    override var WEB_URL: String = "www.cycani.org"
    override val DEFAULT_DOMAIN: String = "https://" + WEB_URL
    override var baseUrl: String = getDefaultDomain()
        set(value) {
            field = value
            // 自定义域名（镜像）后，缓存的 Retrofit / ApiService 必须重建，否则仍指向旧 baseUrl
            retrofitCache = null
        }

    private const val TAG = "CycanimeSource"
    private const val ZONE_ID_TV = 1
    private const val ZONE_ID_MOVIE = 2

    private val webViewUtil: WebViewUtil by lazy { WebViewUtil() }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(1L, TimeUnit.MINUTES)
            .addInterceptor(CycaniHeaderInterceptor())
            .applyProxy()
            .apply {
                if (com.example.tuanyingshi.BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .build()
    }

    private var retrofitCache: Retrofit? = null
    private val retrofit: Retrofit
        get() = retrofitCache ?: Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .also { retrofitCache = it }

    private val apiService: CycaniApiService
        get() = retrofit.create(CycaniApiService::class.java)

    override fun onExit() {
        webViewUtil.clearWeb()
    }

    /**
     * 抓取页面 HTML：优先用 WebView 渲染（绕过次元城 WAF 的 JS 挑战 / React 水合），
     * WebView 失败（超时、无 WebView 环境）再退回纯 OkHttp。
     */
    private suspend fun fetchDocument(url: String): Document {
        val html = webViewUtil.getPageHtml(url) ?: DownloadManager.getHtml(url, WEB_URL)
        return Jsoup.parse(html)
    }

    override suspend fun getHomeData(): List<HomeBean> {
        // 1. 优先走 /api/index/recommend 拿「TV番组」「剧场版」等分组。
        // 2. 并行/串行拿 /api/app/adverts?position=banner 轮播图，挂到首个区块。
        // 3. recommend 失败则退回 /api/ranks/1/videos 或 HTML 兜底。
        val banners = runCatching { fetchBanners() }.getOrDefault(emptyList())

        val recommendSections = runCatching { buildRecommendHomeSections() }.getOrDefault(emptyList())
        if (recommendSections.isNotEmpty()) {
            return recommendSections.mapIndexed { index, bean ->
                if (index == 0) bean.copy(banners = banners) else bean
            }
        }

        // recommend 失败：退回排行接口（保留旧首页 TV/动画 区块）。
        val rankSections = runCatching { buildRankHomeSections() }.getOrDefault(emptyList())
        if (rankSections.isNotEmpty()) {
            return rankSections.mapIndexed { index, bean ->
                if (index == 0) bean.copy(banners = banners) else bean
            }
        }

        // 都失败才走整页 HTML 抓取（模拟器上很慢，仅兜底）。
        return fetchHtmlHomeSections(banners)
    }

    /**
     * 首页轮播图：/api/app/adverts?position=banner。
     */
    private suspend fun fetchBanners(): List<HomeBannerBean> {
        val response = apiService.getBannerAdverts()
        if (!response.isSuccess()) {
            throw IllegalStateException("cycani banner error: ${response.msg} (code=${response.code})")
        }
        return response.data?.list.orEmpty().mapNotNull { advert ->
            val imageUrl = advert.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            HomeBannerBean(
                title = advert.name.takeIf { it.isNotBlank() } ?: "",
                imageUrl = imageUrl,
                actionType = advert.actionType,
                actionValue = advert.actionValue,
            )
        }
    }

    /**
     * 用 /api/index/recommend 构建首页区块。
     * 接口按 name 分组（TV番组、剧场版…），直接把每个分组映射为一个 HomeBean。
     * 如果某个视频缺少标题/封面，会通过 /api/videos/{id} 补充后重试。
     */
    private suspend fun buildRecommendHomeSections(): List<HomeBean> {
        val response = apiService.getRecommend()
        if (!response.isSuccess()) {
            throw IllegalStateException("cycani recommend error: ${response.msg} (code=${response.code})")
        }
        val groups = response.data?.list.orEmpty()
        return groups.mapNotNull { group ->
            val animes = group.videos.mapNotNull { dto -> mapRecommendVideoToAnimeBean(dto) }
            if (animes.isEmpty()) return@mapNotNull null
            HomeBean(title = group.name, animes = animes)
        }
    }

    /**
     * 把推荐接口的视频对象映射为 AnimeBean；若标题或封面缺失，
     * 通过 /api/videos/{id} 查一次番剧信息补齐。
     */
    private suspend fun mapRecommendVideoToAnimeBean(dto: com.example.tuanyingshi.data.remote.api.cycani.CycaniVideoDto): AnimeBean? {
        val hasInfo = dto.title.isNotBlank() && !dto.coverUrl.isNullOrBlank()
        val enriched = if (!hasInfo && dto.videoId > 0) {
            runCatching { apiService.getVideoInfo(dto.videoId).data }
                .onFailure { it.log(TAG, "enrich video info failed for ${dto.videoId}") }
                .getOrNull()
        } else {
            null
        }

        val title = enriched?.title?.takeIf { it.isNotBlank() }
            ?: dto.title.takeIf { it.isNotBlank() }
            ?: return null
        val cover = enriched?.coverUrl?.takeIf { it.isNotBlank() }
            ?: dto.coverUrl?.takeIf { it.isNotBlank() }
            ?: ""
        val episodeName = enriched?.remarks ?: dto.remarks.orEmpty()
        val videoId = enriched?.id ?: dto.videoId
        return AnimeBean(
            title = title,
            img = cover,
            url = "anime/$videoId",
            episodeName = episodeName,
        )
    }

    /**
     * 兜底：整页 HTML 抓取，生成「推荐」区块。
     */
    private suspend fun fetchHtmlHomeSections(banners: List<HomeBannerBean>): List<HomeBean> {
        val document = runCatching { fetchDocument(baseUrl) }.getOrNull() ?: return emptyList()
        val homeBeanList = mutableListOf<HomeBean>()
        val sections = document.select("section.content-visibility-auto")
        if (sections.isNotEmpty()) {
            sections.forEach { section ->
                val title = section.selectFirst("h2")?.text()?.takeIf { it.isNotBlank() } ?: "推荐"
                val moreUrl = section.selectFirst("div.mb-4 > a")?.attr("href") ?: ""
                val animes = getAnimeList(section)
                if (animes.isNotEmpty()) {
                    homeBeanList.add(HomeBean(title = title, moreUrl = moreUrl, animes = animes))
                }
            }
        } else {
            val animes = getAnimeList(document)
            if (animes.isNotEmpty()) {
                homeBeanList.add(HomeBean(title = "推荐", moreUrl = "", animes = animes))
            }
        }
        return homeBeanList.mapIndexed { index, bean ->
            if (index == 0) bean.copy(banners = banners) else bean
        }
    }

    /**
     * 用 /api/ranks/{zone_id}/videos 构建首页的 TV / 动画区块。
     * 按每个视频的 categories 标签分流：含 "TV" → TV 热播；含 "动画"/"番剧"/"动漫" → 动画热播；
     * 其余（分类标签未知）兜底归入动画热播，并在调试模式打印未命中分类以便核对。
     */
    private suspend fun buildRankHomeSections(): List<HomeBean> {
        val response = apiService.getRankVideos(ZONE_ID_TV)
        if (!response.isSuccess()) {
            throw IllegalStateException("cycani rank error: ${response.msg} (code=${response.code})")
        }
        val items = response.data?.list.orEmpty()
        val tv = mutableListOf<AnimeBean>()
        val anime = mutableListOf<AnimeBean>()
        val unmatched = mutableListOf<String>()

        items.forEach { dto ->
            val bean = AnimeBean(
                title = dto.title,
                img = dto.coverUrl.orEmpty(),
                url = "anime/${dto.videoId}",
                episodeName = dto.remarks.orEmpty(),
                tags = dto.tags,
                categories = dto.categories,
                year = dto.year,
                zoneId = dto.zoneId,
                hits = dto.hits,
                score = dto.score,
            )
            val cats = dto.categories
            when {
                cats.any { it.contains("TV", ignoreCase = true) } -> tv.add(bean)
                cats.any {
                    it.contains("动画", ignoreCase = true) ||
                        it.contains("番剧", ignoreCase = true) ||
                        it.contains("动漫", ignoreCase = true)
                } -> anime.add(bean)
                else -> {
                    unmatched.add(cats.joinToString("/"))
                    anime.add(bean)
                }
            }
        }

        Log.d(
            TAG,
            "buildRankHomeSections: total=${items.size}, tv=${tv.size}, anime=${anime.size}, " +
                "unmatchedCats=${unmatched.distinct()}",
        )

        val sections = mutableListOf<HomeBean>()
        if (tv.isNotEmpty()) sections.add(HomeBean(title = "TV 热播", animes = tv))
        if (anime.isNotEmpty()) sections.add(HomeBean(title = "动画热播", animes = anime))
        return sections
    }

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val videoId = detailUrl.substringAfterLast("/").toLongOrNull()
        return if (videoId != null) {
            runCatching { getAnimeDetailFromApi(videoId) }
                .onFailure {
                    it.log(TAG, "getAnimeDetail API failed for id=$videoId, fallback to HTML")
                }
                .getOrElse { getAnimeDetailFromHtml(detailUrl) }
        } else {
            getAnimeDetailFromHtml(detailUrl)
        }
    }

    /**
     * 通过 cycani API 获取番剧详情与选集。
     * 选集 URL 用 "section/{sectionId}" 形式，后续 [getVideoData] 可直接调播放地址接口。
     */
    private suspend fun getAnimeDetailFromApi(videoId: Long): AnimeDetailBean {
        val infoResponse = apiService.getVideoInfo(videoId)
        if (!infoResponse.isSuccess()) {
            throw IllegalStateException("cycani video info error: ${infoResponse.msg} (code=${infoResponse.code})")
        }
        val info = infoResponse.data ?: throw IllegalStateException("cycani video info empty")

        val playerCode = info.playFrom?.firstOrNull()?.code ?: "cychub"
        val sectionsResponse = apiService.getVideoSections(videoId, playerCode)
        if (!sectionsResponse.isSuccess()) {
            throw IllegalStateException("cycani sections error: ${sectionsResponse.msg} (code=${sectionsResponse.code})")
        }
        val sections = sectionsResponse.data?.list.orEmpty()

        val episodes = sections.map { EpisodeBean(name = it.title, url = "section/${it.id}") }

        return AnimeDetailBean(
            title = info.title,
            imgUrl = info.coverUrl.orEmpty(),
            desc = info.description.orEmpty(),
            tags = (info.tags.orEmpty() + info.categories.orEmpty()).distinct(),
            relatedAnimes = emptyList(),
            episodes = episodes,
            channels = emptyMap(),
            region = mapArea(info.area),
            type = mapType(info.state),
            status = if (info.completed == true) AnimeStatus.FINISHED else AnimeStatus.ONGOING,
            year = info.year ?: 2026,
            rating = info.score ?: 0.0,
            totalEpisodes = info.total ?: 0,
            latestEpisodeLabel = info.remarks.orEmpty(),
        )
    }

    /**
     * 兜底：从旧 HTML 详情页解析（当 API 不可用或 detailUrl 不是 anime/{id} 格式时）。
     */
    private suspend fun getAnimeDetailFromHtml(detailUrl: String): AnimeDetailBean {
        val document = fetchDocument("$baseUrl/$detailUrl")
        val detailInfo = document.select("div.detail-info")
        val title = detailInfo.select("h3").text()
        val desc = document.select("div#height_limit").text()
        val imgUrl = document.select("div.detail-pic > img").attr("data-src")

        val tags = detailInfo.select("span.slide-info-remarks").map { it.text() }

        val episodes = getAnimeEpisodes(document)
        val relatedAnimes = getAnimeList(document)
        return AnimeDetailBean(title, imgUrl, desc, tags, relatedAnimes, episodes)
    }

    private fun mapArea(area: String?): Region {
        return when {
            area.isNullOrBlank() -> Region.JP
            area.contains("日本", ignoreCase = true) -> Region.JP
            area.contains("中国", ignoreCase = true) -> Region.CN
            area.contains("国漫", ignoreCase = true) -> Region.CN
            area.contains("欧美", ignoreCase = true) -> Region.US
            else -> Region.JP
        }
    }

    private fun mapType(state: String?): AnimeType {
        return when {
            state.isNullOrBlank() -> AnimeType.SERIES
            state.contains("Movie", ignoreCase = true) -> AnimeType.OVA
            state.contains("OVA", ignoreCase = true) -> AnimeType.OVA
            state.contains("TV", ignoreCase = true) -> AnimeType.SERIES
            state.contains("Donghua", ignoreCase = true) -> AnimeType.DONGHUA
            state.contains("国漫", ignoreCase = true) -> AnimeType.DONGHUA
            else -> AnimeType.SERIES
        }
    }

    private fun getAnimeEpisodes(
        document: Document,
        action: (String) -> Unit = {}
    ): List<EpisodeBean> {
        return document.select("div.anthology-list")
            .select("li").map {
                if (it.select("em").isNotEmpty()) {
                    action(it.text())
                }
                EpisodeBean(it.text(), it.select("a").attr("href"))
            }
    }

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val sectionId = episodeUrl.removePrefix("section/").toLongOrNull()
        return if (sectionId != null) {
            // 优先走接口：/api/v2/sections/{sectionId}/play-url
            val response = apiService.getPlayUrl(sectionId)
            if (!response.isSuccess()) {
                throw IllegalStateException("cycani play-url error: ${response.msg} (code=${response.code})")
            }
            val data = response.data
            if (data == null || data.url.isBlank()) {
                throw IllegalStateException("cycani play-url empty")
            }
            VideoBean(
                videoUrl = data.url,
                headers = mapOf(
                    "Referer" to "https://www.cycani.org/",
                    "User-Agent" to DefaultUserAgent,
                ),
            )
        } else {
            // 兜底：旧 HTML 剧集页走 WebView 拦截真实视频流
            val videoUrl = getVideoUrl("$baseUrl/$episodeUrl")
            VideoBean(videoUrl)
        }
    }

    private suspend fun getVideoUrl(url: String): String {
        return webViewUtil.interceptRequest(
            url = url,
            regex = "^(?!.*url=).*?(.mp4|.m3u8|obj).*\$",
        )
    }

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        // 优先走搜索 API（/api/videos/search），失败降级到 HTML 抓取。
        val apiResult = runCatching {
            val response = apiService.searchVideos(query, page)
            if (response.isSuccess()) {
                response.data?.list.orEmpty().mapNotNull { mapVideoDtoToAnimeBean(it) }
            } else emptyList()
        }.onFailure { it.log(TAG, "getSearchData API failed, fallback to HTML") }
            .getOrNull()
        if (!apiResult.isNullOrEmpty()) return apiResult

        // 降级：HTML 抓取
        val document = fetchDocument("${baseUrl}/search/wd/$query/page/$page.html")
        return getAnimeList(document)
    }

    override suspend fun getFilterData(
        zoneId: Int,
        tag: String?,
        year: Int?,
        orderBy: String?,
        page: Int,
    ): List<AnimeBean> {
        // 优先走筛选 API（/api/videos），失败降级到空集合，由 UI 提示。
        return runCatching {
            val response = apiService.filterVideos(
                zoneId = zoneId,
                tag = tag,
                year = year,
                orderBy = orderBy,
                page = page,
            )
            if (response.isSuccess()) {
                response.data?.list.orEmpty().mapNotNull { mapVideoDtoToAnimeBean(it) }
            } else emptyList()
        }.onFailure { it.log(TAG, "getFilterData API failed") }
            .getOrDefault(emptyList())
    }

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        // 优先走 /api/index/weekday?weekday=1..7，失败降级到 HTML 整页抓取。
        val apiResult = runCatching { fetchWeekDataFromApi() }
            .onFailure { it.log(TAG, "getWeekData API failed, fallback to HTML") }
            .getOrNull()
        if (!apiResult.isNullOrEmpty()) return apiResult

        val document = runCatching { fetchDocument(baseUrl) }.getOrNull() ?: return emptyMap()
        val weekMap = mutableMapOf<Int, List<AnimeBean>>()
        document.select("div#week-module-box")
            .select("div.public-r").forEachIndexed { index, element ->
                val dayList = getAnimeList(element)
                weekMap[index] = dayList
            }
        return weekMap
    }

    /**
     * 并行拉取 1..7 的排期接口，映射为 key=0..6（周一到周日）的 Map。
     */
    private suspend fun fetchWeekDataFromApi(): Map<Int, List<AnimeBean>> = coroutineScope {
        val responses = (1..7).map { day ->
            async { runCatching { apiService.getWeekday(day) }.getOrNull() }
        }.awaitAll()

        val weekMap = mutableMapOf<Int, MutableList<AnimeBean>>()
        responses.filterNotNull().forEach { response ->
            if (!response.isSuccess()) return@forEach
            response.data?.list.orEmpty().forEach { group ->
                val key = group.weekday - 1 // 0=周一 .. 6=周日
                if (key !in 0..6) return@forEach
                val list = weekMap.getOrPut(key) { mutableListOf() }
                group.videos.mapNotNull { mapVideoDtoToAnimeBean(it) }
                    .forEach { list.add(it) }
            }
        }
        weekMap.mapValues { it.value.toList() }
    }

    private fun mapVideoDtoToAnimeBean(dto: CycaniVideoDto): AnimeBean? {
        val title = dto.title.takeIf { it.isNotBlank() } ?: return null
        return AnimeBean(
            title = title,
            img = dto.coverUrl.orEmpty(),
            url = "anime/${dto.videoId}",
            episodeName = dto.remarks.orEmpty(),
        )
    }

    /**
     * 排行榜：走 cycani 接口 /api/ranks/1/videos。
     * 同一接口返回 TV / Movie 混合数据，按 version / categories 分流为 TV番组、剧场番组。
     * 未登录时会因 401 抛异常，上层 Repository 会降级到首页兜底。
     */
    override suspend fun getRanking(): Map<String, List<AnimeBean>> {
        return try {
            val tv = mutableListOf<AnimeBean>()
            val movie = mutableListOf<AnimeBean>()

            // TV番组：分区 1，使用公开筛选接口（/api/videos?zone_id=1，免登录、按 zone 过滤一次即可）
            // 比需登录的 /api/ranks/{zoneId}/videos 更稳，未登录也能拿到数据。
            runCatching {
                val tvResp = apiService.filterVideos(ZONE_ID_TV)
                if (tvResp.isSuccess()) {
                    tvResp.data?.list?.forEach { dto -> tv.add(toRankBean(dto)) }
                }
            }.onFailure { it.log(TAG, "getRanking TV(filter) failed") }

            // 剧场番组：分区 2，同样走公开筛选接口（/api/videos?zone_id=2）
            runCatching {
                val movieResp = apiService.filterVideos(ZONE_ID_MOVIE)
                if (movieResp.isSuccess()) {
                    movieResp.data?.list?.forEach { dto -> movie.add(toRankBean(dto)) }
                }
            }.onFailure { it.log(TAG, "getRanking Movie(filter) failed") }

            // 兜底：筛选接口异常（如网络失败）或分区无数据时返回空，会导致首页「TV番组 / 剧场番组」
            // 无资源。改用公开的 /api/index/recommend（含「TV番组」「剧场版」分组）填充，
            // 保证极端情况下首页分类仍有内容。
            if (tv.isEmpty() || movie.isEmpty()) {
                runCatching { fillRankingFromRecommend(tv, movie) }
                    .onFailure { it.log(TAG, "getRanking recommend fallback failed") }
            }

            mapOf("TV番组" to tv, "剧场番组" to movie)
        } catch (e: Exception) {
            e.log(TAG, "getRanking failed, fallback to empty")
            emptyMap()
        }
    }

    /**
     * 用首页推荐接口（公开、免登录）填充排行榜兜底数据。
     * - 推荐分组名含「剧场」的归入「剧场番组」；
     * - 其余分组（TV番组 / 热门 / 新番等）统一归入「TV番组」，确保电视番组分类始终有资源。
     * 兜底路径不回查视频详情，避免额外网络请求。
     */
    private suspend fun fillRankingFromRecommend(
        tv: MutableList<AnimeBean>,
        movie: MutableList<AnimeBean>,
    ) {
        val resp = apiService.getRecommend()
        if (!resp.isSuccess()) return
        resp.data?.list.orEmpty().forEach { group ->
            val beans = group.videos.mapNotNull { dto -> recommendDtoToBean(dto) }
            if (beans.isEmpty()) return@forEach
            if (group.name.contains("剧场", ignoreCase = true)) {
                movie.addAll(beans)
            } else {
                tv.addAll(beans)
            }
        }
    }

    /** 推荐视频 DTO → AnimeBean（兜底路径，不回查详情，避免额外请求）。 */
    private fun recommendDtoToBean(dto: CycaniVideoDto): AnimeBean? {
        val title = dto.title.takeIf { it.isNotBlank() } ?: return null
        return AnimeBean(
            title = title,
            img = dto.coverUrl?.takeIf { it.isNotBlank() } ?: "",
            url = "anime/${dto.videoId}",
            episodeName = dto.remarks.orEmpty(),
        )
    }

    private fun toRankBean(dto: CycaniVideoDto): AnimeBean = AnimeBean(
        title = dto.title,
        img = dto.coverUrl.orEmpty(),
        url = "anime/${dto.videoId}",
        episodeName = dto.remarks.orEmpty(),
        tags = dto.tags,
        categories = dto.categories,
        year = dto.year,
        zoneId = dto.zoneId,
        hits = dto.hits,
        score = dto.score,
    )

    fun getAnimeList(parent: Element): List<AnimeBean> {
        val animeList = mutableListOf<AnimeBean>()
        // 新版卡片：<a href="/anime/{id}" class="… rounded-lg …"> 内含
        //   img[src]（封面）、div.line-clamp-1（标题）、span[class*=bottom-2]（更新徽标，如「10|周四22:05后」）
        // 通过 class 含 rounded-lg 排除顶部 hero 大图浮层链接。
        parent.select("a[href^=/anime/]").forEach { a ->
            if (!a.className().contains("rounded-lg", ignoreCase = true)) return@forEach
            val rawUrl = a.attr("href").removePrefix("/")
            // 过滤占位链接（"播放"按钮 / 未解锁剧集常为 javascript:），避免脏数据流入列表/历史
            if (rawUrl.isBlank() || rawUrl.startsWith("javascript:", ignoreCase = true)) return@forEach
            val imgUrl = a.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() } ?: ""
            val title = a.selectFirst("div.line-clamp-1")?.text()
                ?: a.selectFirst("img")?.attr("alt")
                ?: ""
            val episodeName = a.selectFirst("span[class*=bottom-2]")?.text() ?: ""
            animeList.add(AnimeBean(title = title, img = imgUrl, url = rawUrl, episodeName = episodeName))
        }
        return animeList
    }

    /**
     * 统一拦截器：附加 cycani 要求的固定请求头，并在已登录时带上 Authorization。
     */
    private class CycaniHeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val original: Request = chain.request()
            val builder = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "*/*")
                // 注意：不要手动设置 Accept-Encoding。OkHttp 的桥接拦截器会自动加
                // Accept-Encoding: gzip 并自动解压；一旦手动设置，OkHttp 会认为调用方
                // 自行处理编码，从而把 gzip 原始字节直接交给 Gson，导致 MalformedJsonException。
                .header("Connection", "keep-alive")
                .header("x-app-name", "cyc_web")
                .header("x-app-version", "cycweb")
                .header("x-time-zone", "Asia/Hong_Kong")
                .header("referer", "https://www.cycani.org/")

            CycaniAuthManager.token?.let { token ->
                builder.header("Authorization", token)
            }

            CycaniAuthManager.cookies?.let { cookies ->
                builder.header("Cookie", cookies)
            }

            return chain.proceed(builder.build())
        }
    }
}
