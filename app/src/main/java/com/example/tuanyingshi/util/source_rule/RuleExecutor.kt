package com.example.tuanyingshi.util.source_rule

import android.net.Uri
import com.example.tuanyingshi.data.remote.dto.AnimeBean
import org.seimicrawler.xpath.JXDocument
import org.seimicrawler.xpath.JXNode
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.EpisodeBean
import com.example.tuanyingshi.data.remote.parse.util.WebViewUtil
import com.example.tuanyingshi.util.DownloadManager
import com.example.tuanyingshi.util.applyProxy
import com.example.tuanyingshi.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val LOG_TAG = "RuleExecutor"

/** 源标识：name[id]，方便日志里区分每一个 CSS 源。 */
private fun srcTag(rule: SourceRule): String =
    buildString { append(rule.name.ifBlank { "未命名" }); append("["); append(rule.id.take(8)); append("]") }

/** 本项目运行在 Android，播放器为 ExoPlayer（animeko `onlySupportsPlayers` 的取值之一）。 */
private const val CURRENT_PLAYER_ID = "exoplayer"

/**
 * 抓取兜底用的「宽松」OkHttpClient：信任所有证书 + 关闭主机名校验。
 *
 * 仅用于 WebView 抓取失败后的 OkHttp 兜底。某些站点（WAF / 自签 / 证书链不全）
 * 会让 WebView 的 TLS 握手被重置（net_error -101），而 OkHttp 走另一套 TLS 栈常能成功。
 * 抓取场景对证书真实性不敏感，故放宽为 trust-all。
 */
private val lenientOkHttp: OkHttpClient by lazy {
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustAll), SecureRandom())
    }
    OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .sslSocketFactory(sslContext.socketFactory, trustAll)
        .hostnameVerifier(HostnameVerifier { _, _ -> true })
        .applyProxy()
        .build()
}

/**
 * 通用规则执行器。
 *
 * 按 [SourceRule] 中用户配置的 CSS Selector、正则、URL 模板执行三步抓取：
 * 1. [searchEntries]：用关键词搜索候选番剧条目（[SubjectFormat] 决定如何解析）；
 * 2. [getAnimeDetail] / [searchEpisodes]：进入条目详情页，按规则提取详情与剧集
 *    （[ChannelFormat] 决定线路 / 剧集的解析形态）；
 * 3. [matchVideo]：进入剧集播放页，按 [VideoMode] 获取真实视频 URL。
 *
 * 抓取与解析的分工对齐 animeko 的 `SelectorMediaSourceEngine`：
 * 抓取负责拿 HTML，解析（select*）是纯函数，便于测试与复用。
 *
 * 所有方法返回可空/空集合，调用方负责降级或提示。
 */
object RuleExecutor {

    /**
     * animeko `SelectorChannelFormat.DEFAULT_MATCH_EPISODE_SORT_FROM_NAME`。
     * 规则未配置序号正则时的兜底。
     */
    const val DEFAULT_MATCH_EPISODE_SORT_FROM_NAME = """第\s*(?<ep>.+)\s*[话集]"""

    /** 搜索条目结果 */
    data class SearchResult(
        val title: String,
        val url: String,
        /** 封面图 URL（订阅源 web 选择器通常不提供封面选择器，故从条目 DOM 启发式抽取）。 */
        val cover: String = "",
    )

    /** 一个线路 / 分组下的剧集列表 */
    data class EpisodeGroup(
        val lineName: String,
        val episodes: List<EpisodeItem>,
    )

    data class EpisodeItem(
        val name: String,
        val url: String,
        val number: Int? = null,
    )

    /** 视频匹配结果（含播放所需请求头，如 Referer / Cookie）。 */
    data class VideoMatch(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    )

    // ───────────────────────── 步骤 1：搜索条目 ─────────────────────────

    /**
     * 搜索条目（单名称）。等价于 animeko `SelectorMediaSource.search` 的一次迭代。
     *
     * @return 候选条目列表（url 已按 [subjectListBaseUrl] 解析为绝对地址，对应 animeko `fullUrl`）；
     *   失败或被拦返回空集合。
     */
    suspend fun searchEntries(rule: SourceRule, keyword: String): List<SearchResult> =
        testSearchEntries(rule, keyword).entries

    /**
     * 搜索条目（测试用），额外返回「是否被拦」的原因。对齐 animeko 测试器的
     * `searchSubject`：页面加载失败 / 被 WAF 拦截时，把 [BlockReason] 回传给 UI 区分「被拦」与「无结果」。
     *
     * 生产搜索 [searchEntries] 委托于此，只取 [SearchTestResult.entries]。
     */
    suspend fun testSearchEntries(rule: SourceRule, keyword: String): SearchTestResult {
        val tag = srcTag(rule)
        if (!supportsCurrentPlayer(rule)) {
            "$tag testSearchEntries: skip onlySupportsPlayers=${rule.onlySupportsPlayers}".log(LOG_TAG, "testSearchEntries")
            return SearchTestResult(emptyList(), BlockReason.WAF)
        }
        val query = prepareKeyword(rule, keyword)
        val searchUrl = rule.search.searchUrl.replace("{keyword}", query)
        "$tag testSearchUrl: keyword=$keyword query=$query".log(LOG_TAG, "testSearchEntries")
        delayUntilNextAllowedSearch(rule)
        val (html, block) = fetchSearchHtml(searchUrl, rule, tag)
        if (html == null) {
            "$tag testSearchEntries: html=null block=${block ?: "null"}".log(LOG_TAG, "testSearchEntries")
            return SearchTestResult(emptyList(), block ?: BlockReason.WAF)
        }
        if (block != null) {
            "$tag testSearchEntries: blocked by $block htmlLen=${html.length}".log(LOG_TAG, "testSearchEntries")
            return SearchTestResult(emptyList(), block)
        }
        val entries = selectSubjects(html, rule)
        "$tag testSearchEntries: htmlLen=${html.length} parsed=${entries.size}".log(LOG_TAG, "testSearchEntries")
        return SearchTestResult(entries, null)
    }

    /** [testSearchEntries] 的返回：条目列表 + 被拦原因（null 表示成功加载）。 */
    data class SearchTestResult(
        val entries: List<SearchResult>,
        val blockReason: BlockReason?,
    )

    /**
     * 用多个条目名依次搜索并合并结果。对应 animeko `SelectorMediaSource.fetch`：
     * `subjectNames.take(searchUseSubjectNamesCount)` → 每个名字一次搜索 →
     * `flattenConcat(requestInterval)` 串联。
     *
     * 两次搜索之间等待 [SourceRule.SearchStep.requestIntervalMs]；由于
     * [delayUntilNextAllowedSearch] 会先「占位」，两个等待不会叠加。
     * 结果按 URL 去重，保留首次出现（即第一个名称的命中优先）。
     *
     * @param names 条目名称候选（主名 / 原名 / 别名），实际使用前
     *   [SourceRule.SearchStep.alternateNameCount] 个
     */
    suspend fun searchSubjects(rule: SourceRule, names: List<String>): List<SearchResult> {
        if (!supportsCurrentPlayer(rule)) {
            "skip: onlySupportsPlayers=${rule.onlySupportsPlayers}".log(LOG_TAG, "searchSubjects")
            return emptyList()
        }
        val queries = names.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(rule.search.alternateNameCount.coerceAtLeast(1))
        if (queries.isEmpty()) return emptyList()

        // LinkedHashMap 保持首次出现顺序，同时按 url 去重
        val merged = LinkedHashMap<String, SearchResult>()
        queries.forEachIndexed { index, query ->
            if (index > 0) {
                val gap = rule.search.requestIntervalMs.coerceAtLeast(0L)
                if (gap > 0) delay(gap)
            }
            // 与 animeko 一致：各名称的结果全部合并，不因首个名称命中就跳过后面的
            // （主名搜不到时，用原名 / 别名往往能搜到）
            searchOnce(rule, query).forEach { merged.putIfAbsent(it.url, it) }
        }
        return merged.values.toList()
    }

    private suspend fun searchOnce(rule: SourceRule, keyword: String): List<SearchResult> {
        // 引擎路由：Kazumi 风格 XPath / API 走各自实现；其余（animeko CSS）走原生逻辑。
        return when (rule.engine) {
            RuleEngineKind.Xpath -> searchOnceXpath(rule, keyword)
            RuleEngineKind.Api -> searchOnceApi(rule, keyword)
            RuleEngineKind.Css -> searchOnceCss(rule, keyword)
        }
    }

    private suspend fun searchOnceCss(rule: SourceRule, keyword: String): List<SearchResult> {
        val tag = srcTag(rule)
        val t0 = System.currentTimeMillis()
        val query = prepareKeyword(rule, keyword)
        if (query.isBlank()) {
            "$tag searchOnce: query 为空，跳过".log(LOG_TAG, "searchOnce")
            return emptyList()
        }
        val searchUrl = rule.search.searchUrl.replace("{keyword}", query)
        "$tag searchOnce: keyword=$keyword query=$query".log(LOG_TAG, "searchOnce")
        searchUrl.log(LOG_TAG, "searchUrl")
        delayUntilNextAllowedSearch(rule)
        // 抓搜索页：OkHttp 优先、WebView 兜底（见 [fetchSearchHtml]）。appleCMS 多为服务端渲染，OkHttp 直抓即可。
        val (html, block) = fetchSearchHtml(searchUrl, rule, tag)
        val cost = System.currentTimeMillis() - t0
        if (html == null) {
            "$tag searchOnce: html=null (抓取失败) cost=${cost}ms block=$block".log(LOG_TAG, "searchOnce")
            return emptyList()
        }
        if (block != null) {
            "$tag searchOnce: 命中被拦=$block cost=${cost}ms htmlLen=${html.length}".log(LOG_TAG, "searchOnce")
            return emptyList()
        }
        // 反爬：若抓到的是验证页，尝试用 WebView 过一次验证，拿回可直接解析的 HTML
        val finalHtml = if (rule.antiCrawler.enabled) {
            AntiCrawlerGate.maybeVerifySearch(rule, keyword, html) ?: html
        } else html
        val results = selectSubjects(finalHtml, rule)
        "$tag searchOnce: 成功 htmlLen=${finalHtml.length} cost=${cost}ms found=${results.size}".log(LOG_TAG, "searchOnce")
        return results
    }

    /**
     * animeko `onlySupportsPlayers` 检查：桌面端匹配 `mpv`/`vlc`，Android 匹配 `exoplayer`。
     * 本项目只跑 Android，因此只认 `exoplayer`；列表为空表示不限。
     */
    fun supportsCurrentPlayer(rule: SourceRule): Boolean =
        rule.onlySupportsPlayers.isEmpty() ||
            rule.onlySupportsPlayers.any { it.equals(CURRENT_PLAYER_ID, ignoreCase = true) }

    /**
     * 条目名与搜索词的匹配率（0..100），基于 animeko `StringMatcher` +
     * `MediaListFilters.removeSpecials`。用于「哪个搜索结果最像目标条目」的排序。
     */
    fun matchRate(title: String, subjectName: String): Int =
        MediaListFilters.matchRate(title, subjectName)

    /**
     * 用 animeko `MediaListFilters.ContainsAnyEpisodeInfo` 的语义过滤剧集：
     * 序号匹配 → 特殊剧集按名称匹配 → 季度内集数匹配。
     */
    fun filterEpisodes(
        groups: List<EpisodeGroup>,
        episodeNumber: Int?,
        episodeEp: Int? = null,
        episodeName: String? = null,
    ): List<EpisodeGroup> {
        if (episodeNumber == null && episodeEp == null && episodeName.isNullOrBlank()) return groups
        val context = SubjectFilterContext(
            subjectNames = emptySet(),
            episodeNumber = episodeNumber,
            episodeEp = episodeEp,
            episodeName = episodeName,
        )
        return groups.mapNotNull { group ->
            val kept = group.episodes.filter { episode ->
                MediaListFilters.applyAll(MediaListFilters.ContainsAnyEpisodeInfo, context, episode.asCandidate())
            }
            if (kept.isEmpty()) null else group.copy(episodes = kept)
        }
    }

    private fun EpisodeItem.asCandidate(): MediaListFilter.Candidate {
        val item = this
        return object : MediaListFilter.Candidate {
            override val originalTitle: String get() = item.name
            override val episodeNumber: Int? get() = item.number
            override val episodeName: String? get() = item.name
        }
    }

    /**
     * 解析搜索结果页，返回该页的全部条目。纯函数（对应 animeko `selectSubjects`）。
     *
     * 由 [SourceRule.SearchStep.format] 决定解析格式，与订阅 JSON 的 `subjectFormatId` 一致。
     */
    fun selectSubjects(html: String, rule: SourceRule): List<SearchResult> {
        val baseUrl = subjectListBaseUrl(rule)
        val fmt = rule.search.format()
        val result = when (fmt) {
            SubjectFormat.A -> selectSubjectsA(html, rule, baseUrl)
            SubjectFormat.Indexed -> selectSubjectsIndexed(html, rule, baseUrl)
            SubjectFormat.JsonPathIndexed -> selectSubjectsJsonPath(html, rule, baseUrl)
        }
        "selectSubjects: fmt=$fmt baseUrl=$baseUrl htmlLen=${html.length} → ${result.size} 条".log(LOG_TAG, "selectSubjects")
        result.take(10).forEachIndexed { i, r -> "  [$i] ${r.title} -> ${r.url} | cover=${if (r.cover.isBlank()) "∅" else r.cover}".log(LOG_TAG, "selectSubjects") }
        return if (rule.search.preferShorterName) result.sortedBy { it.title.length } else result
    }

    /** 把搜索结果映射为仓库层 AnimeBean（详情页/搜索列表通用）。封面优先用搜索页抽取的 [SearchResult.cover]。 */
    fun toAnimeBeans(results: List<SearchResult>): List<AnimeBean> =
        results.map { AnimeBean(title = it.title, img = it.cover, url = it.url) }

    // ───────────────────────── 步骤 2：详情 + 剧集 ─────────────────────────

    /**
     * 获取详情（标题 / 封面 / 简介 / 标签 + 多线路剧集）。
     * @return [AnimeDetailBean]；失败返回 null（调用方应回退原解析器）。
     */
    suspend fun getAnimeDetail(rule: SourceRule, detailUrl: String): AnimeDetailBean? {
        // 剧集引擎路由：Kazumi XPath / API 走各自实现（搜索页 detailUrl 即条目/章节入口）
        return when (rule.chapterEngine) {
            RuleEngineKind.Api -> getAnimeDetailApi(rule, detailUrl)
            RuleEngineKind.Xpath -> getAnimeDetailXpath(rule, detailUrl)
            RuleEngineKind.Css -> getAnimeDetailCss(rule, detailUrl)
        }
    }

    private suspend fun getAnimeDetailCss(rule: SourceRule, detailUrl: String): AnimeDetailBean? {
        val absoluteUrl = resolveUrl(rule, detailUrl)
        val tag = srcTag(rule)
        val html = fetchHtml(absoluteUrl, rule) ?: return null
        val document = Jsoup.parse(html)
        val meta = extractDetailMeta(document, rule.detail, absoluteUrl)
        "$tag getAnimeDetail: title=${meta.title.ifBlank { "∅" }} imgLen=${meta.img.length} descLen=${meta.desc.length} tags=${meta.tags.size}".log(LOG_TAG, "getAnimeDetail")
        if (meta.title.isBlank()) return null
        // 复用剧集缓存：详情页与「搜索剧集」会命中同一个条目页，避免重复抓取
        val groups = readEpisodeCache(rule, absoluteUrl) ?: selectEpisodes(document, rule, absoluteUrl)
            .also { writeEpisodeCache(rule, absoluteUrl, it) }
        val channels = groups.mapIndexed { i, g -> i to g.episodes.map { EpisodeBean(it.name, it.url) } }.toMap()
        return AnimeDetailBean(
            title = meta.title,
            imgUrl = meta.img,
            desc = meta.desc,
            tags = meta.tags,
            relatedAnimes = emptyList(),
            channels = channels,
        )
    }

    /**
     * 步骤 2 搜索剧集的返回。
     * - [loaded]：详情页是否成功抓到可解析的 HTML；
     * - [blockReason]：当 [loaded] 为 false 时，被拦的具体原因（对齐 animeko `BlockReason`）；
     *   用于区分「加载失败 / WAF 拦截」与「页面正常但选择器未匹配」（后者见 [episodeIssue] 在 VM 层）。
     */
    data class EpisodeFetchResult(
        val loaded: Boolean,
        val groups: List<EpisodeGroup>,
        val blockReason: BlockReason? = null,
    )

    /**
     * 步骤 2：搜索剧集。
     * @param entryUrl 条目详情页 URL（相对/绝对均可）
     * @param episodeNumber 可选，若提供则优先返回匹配该序号的剧集；仍返回全部分组供 UI 展示
     * @return [EpisodeFetchResult]；[EpisodeFetchResult.loaded] 为 false 表示详情页加载失败或**被 WAF 拦截**
     *   （[EpisodeFetchResult.blockReason] 非 null），否则页面已加载。
     */
    suspend fun searchEpisodes(
        rule: SourceRule,
        entryUrl: String,
        episodeNumber: Int? = null,
    ): EpisodeFetchResult {
        val absoluteUrl = resolveUrl(rule, entryUrl)

        // animeko `searchFromCacheOrNull`：命中缓存就不再发起任何网络请求。
        readEpisodeCache(rule, absoluteUrl)?.let { cached ->
            "cache hit ${cached.size} groups".log(LOG_TAG, "searchEpisodes")
            return EpisodeFetchResult(loaded = true, groups = applyPreferShortest(cached, rule, episodeNumber))
        }

        // Kazumi API 模式：章节来自 HTTP+JSON 接口，不走通用 HTML 抓取
        if (rule.chapterEngine == RuleEngineKind.Api) {
            val groups = runCatching { selectEpisodesApi(absoluteUrl, rule) }.getOrNull()
            if (groups == null) {
                return EpisodeFetchResult(loaded = false, groups = emptyList(), blockReason = BlockReason.WAF)
            }
            writeEpisodeCache(rule, absoluteUrl, groups)
            return EpisodeFetchResult(loaded = true, groups = applyPreferShortest(groups, rule, episodeNumber))
        }

        val (html, block) = fetchHtmlWithRetry(absoluteUrl, rule, rule.search.requestIntervalMs)
        if (html == null) {
            // 加载失败（超时 / 网络）或被拦：用检测原因兜底（无法从内容识别时按 WAF 处理，最常见）
            return EpisodeFetchResult(loaded = false, groups = emptyList(), blockReason = block ?: BlockReason.WAF)
        }
        if (block != null) {
            // 页面回来了，但其实是 WAF 挑战页（选择器自然解析不到内容）
            return EpisodeFetchResult(loaded = false, groups = emptyList(), blockReason = block)
        }
        val groups = if (rule.chapterEngine == RuleEngineKind.Xpath) {
            selectEpisodesXpath(html, rule, absoluteUrl)
        } else {
            val document = Jsoup.parse(html)
            selectEpisodes(document, rule, absoluteUrl)
        }
        // animeko `addCache`：真实搜索后把该条目页的全部剧集写入缓存，切集时直接复用。
        writeEpisodeCache(rule, absoluteUrl, groups)
        return EpisodeFetchResult(loaded = true, groups = applyPreferShortest(groups, rule, episodeNumber))
    }

    private fun applyPreferShortest(
        groups: List<EpisodeGroup>,
        rule: SourceRule,
        episodeNumber: Int?,
    ): List<EpisodeGroup> = if (episodeNumber != null && rule.episodes.preferShortestTitle) {
        groups.filter { group -> group.episodes.any { it.number == episodeNumber } }
            .takeIf { it.isNotEmpty() } ?: groups
    } else {
        groups
    }

    // ───────────────────────── 剧集缓存（animeko searchCacheTtl） ─────────────────────────

    private data class EpisodeCacheEntry(
        val groups: List<EpisodeGroup>,
        val expireAtMillis: Long,
    )

    private val episodeCache = java.util.concurrent.ConcurrentHashMap<String, EpisodeCacheEntry>()

    private fun cacheKey(rule: SourceRule, subjectUrl: String) = "${rule.id}|$subjectUrl"

    private fun readEpisodeCache(rule: SourceRule, subjectUrl: String): List<EpisodeGroup>? {
        val ttlMinutes = rule.search.cacheMinutes
        if (ttlMinutes <= 0) return null
        return episodeCache[cacheKey(rule, subjectUrl)]
            ?.takeIf { it.expireAtMillis > System.currentTimeMillis() }
            ?.groups
    }

    private fun writeEpisodeCache(rule: SourceRule, subjectUrl: String, groups: List<EpisodeGroup>) {
        val ttlMinutes = rule.search.cacheMinutes
        if (ttlMinutes <= 0 || groups.isEmpty()) return
        episodeCache[cacheKey(rule, subjectUrl)] = EpisodeCacheEntry(
            groups = groups,
            expireAtMillis = System.currentTimeMillis() + ttlMinutes * 60_000L,
        )
    }

    /** 清空剧集缓存（规则被修改 / 手动刷新时用）。 */
    fun clearEpisodeCache(ruleId: String? = null) {
        if (ruleId == null) {
            episodeCache.clear()
            return
        }
        episodeCache.keys.removeIf { it.startsWith("$ruleId|") }
    }

    /**
     * 测试诊断用：判断播放页是否可加载、是否被拦。
     *
     * 对齐 animeko 步骤 3 的「加载失败」判定：返回 [BlockReason] 表示页面被 WAF/限流/验证码挡住；
     * 返回 null 表示页面正常加载（此时未匹配到视频属于「选择器/配置问题」，由 VM 用 [TestIssue.InvalidConfig] 表达）。
     */
    suspend fun pageBlockReason(rule: SourceRule, url: String): BlockReason? {
        val absoluteUrl = resolveUrl(rule, url)
        val (html, block) = fetchHtmlWithRetry(absoluteUrl, rule, rule.search.requestIntervalMs)
        if (html == null) return block ?: BlockReason.WAF
        return detectBlockReason(html)
    }

    // ───────────────────────── 剧集匹配 ─────────────────────────

    /**
     * 从剧集列表中找出目标剧集。对应 animeko `findMatchingEpisodeOrNull`，优先级：
     * 1. 序号匹配 [episodeNumber]；
     * 2. 特殊剧集（序号解析不出来）按名称包含匹配 [episodeName]；
     * 3. 序号匹配季度内集数 [episodeEp]。
     */
    fun findMatchingEpisode(
        episodes: List<EpisodeItem>,
        episodeNumber: Int?,
        episodeEp: Int? = null,
        episodeName: String? = null,
    ): EpisodeItem? {
        episodeNumber?.let { sort ->
            episodes.firstOrNull { it.number == sort }?.let { return it }
        }
        if (!episodeName.isNullOrBlank()) {
            episodes.firstOrNull { it.number == null && MediaListFilters.specialContains(it.name, episodeName) }
                ?.let { return it }
        }
        episodeEp?.let { ep ->
            episodes.firstOrNull { it.number == ep }?.let { return it }
        }
        return null
    }

    // ───────────────────────── 步骤 3：匹配视频 ─────────────────────────

    /** 匹配视频，仅返回直链字符串（编辑器测试用）。 */
    suspend fun matchVideo(rule: SourceRule, episodeUrl: String): String =
        matchVideoDetailed(rule, episodeUrl).url

    /** 匹配视频，返回直链 + 播放请求头。 */
    suspend fun matchVideoDetailed(rule: SourceRule, episodeUrl: String): VideoMatch {
        val absoluteUrl = resolveUrl(rule, episodeUrl)
        val headers = buildPlayHeaders(rule)
        "[matchVideoDetailed] videoMode=${rule.video.videoMode} episodeUrl=${episodeUrl.take(80)} → absoluteUrl=${absoluteUrl.take(140)}".log(LOG_TAG)
        "[matchVideoDetailed] playHeaders=${headers.entries.joinToString(", ") { "${it.key}=${it.value.take(24)}${if (it.value.length > 24) "…" else ""}" }}".log(LOG_TAG)
        return when (rule.video.videoMode) {
            VideoMode.INTERCEPT -> VideoMatch(matchIntercept(rule, absoluteUrl), headers)
            VideoMode.SILISILI_POST -> VideoMatch(matchSilisiliPost(rule, absoluteUrl), headers)
            VideoMode.GIRIGIRI_B64 -> VideoMatch(matchGirigiriB64(absoluteUrl, rule.headers.userAgent, referer = headers["Referer"] ?: ""), headers)
        }
    }

    // ───────────────────────── 内部解析辅助 ─────────────────────────

    /**
     * [SubjectFormat.A]：一个选择器选出若干 `<a>`，text（或 title 属性）为名称，`href` 为链接。
     */
    private fun selectSubjectsA(html: String, rule: SourceRule, baseUrl: String): List<SearchResult> {
        val document = Jsoup.parse(html)
        val elements = selectOrEmpty(document, rule.search.titleSelector)
        return elements.mapNotNull { a ->
            val name = a.attr("title").takeIf { it.isNotBlank() } ?: a.text()
            val href = a.attr("href")
            val cover = extractCoverFromSubject(a, baseUrl)
            buildSubject(name, href, baseUrl, cover)
        }
    }

    /**
     * [SubjectFormat.Indexed]：两个选择器分别选出名称列表与链接列表，按索引一一对应。
     * 链接选择器通常选中 `<a>` 元素，可顺带从同条目容器内抽封面。
     */
    private fun selectSubjectsIndexed(html: String, rule: SourceRule, baseUrl: String): List<SearchResult> {
        val step = rule.search
        val document = Jsoup.parse(html)
        val names = selectOrEmpty(document, step.titleSelector).map { it.text() }
        val linkEls = selectOrEmpty(document, step.linkSelector)
        val links = linkEls.map { it.attr("href") }
        val covers = linkEls.map { extractCoverFromSubject(it, baseUrl) }
        // 与 animeko fastZipNotNullToMutable 一致：按较短的一侧截断，跳过无效项
        return names.zip(links).zip(covers).mapNotNull { (pair, cover) ->
            val (name, href) = pair
            buildSubject(name, href, baseUrl, cover)
        }
    }

    /**
     * [SubjectFormat.JsonPathIndexed]：搜索接口返回 JSON，用 JsonPath 取名称与链接。
     * 此类接口（多为 API JSON）无 DOM，无法抽取封面，封面留空。
     */
    private fun selectSubjectsJsonPath(raw: String, rule: SourceRule, baseUrl: String): List<SearchResult> {
        val step = rule.search
        val json = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val names = MiniJsonPath.resolve(json, step.titleSelector)?.mapNotNull { MiniJsonPath.firstString(it) }
            ?: return emptyList()
        val links = MiniJsonPath.resolve(json, step.linkSelector)?.mapNotNull { MiniJsonPath.firstString(it) }
            ?: return emptyList()
        return names.zip(links).mapNotNull { (name, href) -> buildSubject(name, href, baseUrl) }
    }

    private fun buildSubject(name: String, href: String, baseUrl: String, cover: String = ""): SearchResult? {
        val title = name.trim()
        val raw = href.trim()
        if (title.isBlank() || raw.isBlank()) return null
        if (raw.startsWith("javascript:", ignoreCase = true)) return null
        return SearchResult(title, computeAbsoluteUrl(baseUrl, raw), cover)
    }

    /**
     * 从搜索结果条目元素中启发式抽取封面图。
     *
     * 订阅源 web 选择器（animeko web-selector）**不提供**搜索封面选择器（其 `selectorSubjectFormatA`
     * 仅含 `selectLists`/`preferShorterName`）。但苹果CMS 等搜索结果页里，封面 `<img>` 与标题 `<a>`
     * 同属一个条目容器，只是不在同一元素内——典型结构：
     * `<li.module-card-item><a.poster><img data-original></a> ... <div.title><a>标题</a></div>`。
     *
     * 策略：先看链接元素自身是否内嵌 `<img>`；否则向上最多爬 4 层祖先，取最近含 `<img>` 的容器，
     * 用 [pickImgAttr]（优先 `data-original`/`data-src`/`src`，过滤占位图）取出并补全相对地址。
     */
    private fun extractCoverFromSubject(a: Element, baseUrl: String): String {
        a.selectFirst("img")?.let { direct ->
            val u = pickImgAttr(direct)
            if (u.isNotBlank() && !isPlaceholder(u)) return resolveIfRelative(u, baseUrl)
        }
        var cur: Element? = a.parent()
        repeat(4) {
            val el = cur ?: return ""
            cur = el.parent()
            val img = el.selectFirst("img")
            if (img != null) {
                val u = pickImgAttr(img)
                if (u.isNotBlank() && !isPlaceholder(u)) return resolveIfRelative(u, baseUrl)
            }
        }
        return ""
    }

    /** 空白 / 非法选择器视为零命中，不抛异常。 */
    private fun selectOrEmpty(root: Element, selector: String): List<Element> =
        if (selector.isBlank()) emptyList() else runCatching { root.select(selector) }.getOrDefault(emptyList())

    /**
     * 抽取详情元数据（标题 / 封面 / 简介 / 标签）。
     *
     * 订阅类 web 源（animeko web-selector）**不提供** `detail` 选择器——animeko 的详情标题来自
     * 搜索结果 / bangumi，封面与简介来自 ani 元数据，web 源只负责剧集。但本 App 是独立 App，
     * 没有独立的元数据来源，必须自己从详情页把标题 / 封面 / 简介抽出来。
     *
     * 因此：当 [SourceRule.DetailStep] 各选择器为空时，退化为 DOM 启发式兜底
     * （`<h1>` / `og:title` / `<title>` 标题；`data-original` / `data-src` / `src` 封面；
     * `.module-info-introduction` 等简介容器）。这样既兼容内置源（Silisili/Girigiri 自带
     * `detail` 选择器、优先用显式选择器），又能让订阅源详情页正常出数据。
     */
    private fun extractDetailMeta(root: Element, step: SourceRule.DetailStep, pageUrl: String = ""): DetailMeta {
        val title = if (step.titleSelector.isNotBlank()) {
            runCatching { root.select(step.titleSelector).firstOrNull()?.text()?.trim() }.getOrDefault("") ?: ""
        } else {
            root.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: root.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
                ?: root.selectFirst("title")?.text()?.trim()?.substringBefore(" - ")?.trim()?.takeIf { it.isNotBlank() }
                ?: ""
        }
        val img = if (step.imgSelector.isNotBlank()) {
            runCatching {
                val el = root.select(step.imgSelector).firstOrNull() ?: return@runCatching ""
                el.attr(step.imgAttr).trim()
            }.getOrDefault("")
        } else {
            bestImageUrl(root, pageUrl)
        }
        val desc = if (step.descSelector.isNotBlank()) {
            runCatching { root.select(step.descSelector).firstOrNull()?.text()?.trim() }.getOrDefault("") ?: ""
        } else {
            root.selectFirst(
                ".module-info-introduction, .content-desc, .module-info-desc, .detail-desc, " +
                    ".vod-desc, .introduction, .contain-sketch, .sketch, .desc, .module-info-content",
            )?.text()?.trim() ?: ""
        }
        val tags = if (step.tagSelector.isNotBlank()) {
            runCatching {
                root.select(step.tagSelector).map { it.text().trim().uppercase() }.filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
        } else {
            root.select(".module-info-tag a, .vod-tag a, .tag a, .module-info-tag span, .vod-tag span")
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it != "|" }
        }
        return DetailMeta(title, img, desc, tags)
    }

    /** 从 DOM 中挑出最佳封面图 URL（苹果CMS 等常把真图放在 `data-original` / `data-src` 懒加载属性上）。 */
    private fun bestImageUrl(root: Element, pageUrl: String): String {
        val candidates = root.select(
            ".module-item-pic img, .module-info-pic img, .videopic img, .module-poster img, " +
                ".module-info-img img, img.lazyload, img[data-original], img[data-src]",
        )
        for (el in candidates) {
            val u = pickImgAttr(el)
            if (u.isNotBlank() && !isPlaceholder(u)) return resolveIfRelative(u, pageUrl)
        }
        for (el in root.select("img")) {
            val u = pickImgAttr(el)
            if (u.isNotBlank() && !isPlaceholder(u)) return resolveIfRelative(u, pageUrl)
        }
        return ""
    }

    private fun pickImgAttr(el: Element): String {
        return el.attr("data-original").takeIf { it.isNotBlank() }
            ?: el.attr("data-src").takeIf { it.isNotBlank() }
            ?: el.attr("data-lazy-src").takeIf { it.isNotBlank() }
            ?: el.attr("src").takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun isPlaceholder(u: String): Boolean {
        val s = u.lowercase()
        return s.contains("load.gif") || s.contains("errorpic") || s.contains("placeholder") ||
            s.endsWith("/") || s.startsWith("data:")
    }

    private fun resolveIfRelative(u: String, pageUrl: String): String {
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        if (pageUrl.isBlank()) return u
        return runCatching {
            val uri = URI(pageUrl)
            URI("${uri.scheme}://${uri.authority}/").resolve(u).toString()
        }.getOrDefault(u)
    }

    private data class DetailMeta(
        val title: String,
        val img: String,
        val desc: String,
        val tags: List<String>,
    )

    /**
     * 解析条目详情页的线路 / 剧集。纯函数（对应 animeko `selectEpisodes` → `SelectorChannelFormat.select`）。
     *
     * @param subjectUrl 该详情页的完整 URL。剧集相对链接以此为基准（取「去掉最后一段路径」的目录），
     *   而不是站点根——站点常把资源放在 `/vod/`、`/video/` 等前缀下。
     */
    fun selectEpisodes(page: Element, rule: SourceRule, subjectUrl: String): List<EpisodeGroup> {
        val baseUrl = episodeBaseUrl(subjectUrl)
        return when (rule.episodes.format()) {
            ChannelFormat.IndexGrouped -> selectIndexGrouped(page, rule, baseUrl)
            ChannelFormat.NoChannel -> selectNoChannel(page, rule, baseUrl)
        }
    }

    /**
     * [ChannelFormat.IndexGrouped]：线路名称列表与剧集面板列表按索引一一对应。
     *
     * 严格模式下（与 animeko 一致）「配置了名称正则却没匹配上」的线路会被整体丢弃；
     * 若严格模式一个线路都没剩下，再退化为用元素完整文本命名跑一遍——
     * 订阅里的正则往往是照着作者当时的 DOM 写的，站点改版后可能全部失配，
     * 此时丢弃整页剧集不如给出「原始文本」命名的线路。
     */
    private fun selectIndexGrouped(page: Element, rule: SourceRule, baseUrl: String): List<EpisodeGroup> {
        val strict = selectIndexGroupedImpl(page, rule, baseUrl, strict = true)
        if (strict.isNotEmpty()) return strict
        return selectIndexGroupedImpl(page, rule, baseUrl, strict = false)
    }

    private fun selectIndexGroupedImpl(
        page: Element,
        rule: SourceRule,
        baseUrl: String,
        strict: Boolean,
    ): List<EpisodeGroup> {
        val step = rule.episodes

        // null = 该线路不可用（文本为空 / 正则非法 / 正则未命中）
        val channelNames: List<String?> = selectOrEmpty(page, step.lineNameSelector).map { element ->
            val text = element.text().trim().takeIf { it.isNotBlank() } ?: return@map null
            if (step.lineNameRegex.isBlank()) return@map text
            val regex = runCatching { step.lineNameRegex.toRegex() }.getOrNull() ?: return@map if (strict) null else text
            val match = regex.find(text) ?: return@map if (strict) null else text
            match.groupOrNull("ch")?.value?.trim()?.takeIf { it.isNotBlank() } ?: text
        }

        val lists = selectOrEmpty(page, step.episodePanelSelector)
        val groups = mutableListOf<EpisodeGroup>()
        lists.forEachIndexed { index, list ->
            val name = channelNames.getOrNull(index)
            // 配置了线路选择器却对不上（数量不足或正则未命中）→ 丢弃；
            // 完全没配置线路选择器 → 退化为「线路 N」命名，保留剧集。
            if (name == null && channelNames.isNotEmpty()) return@forEachIndexed
            val episodes = extractEpisodesFrom(list, rule, baseUrl, step.episodeListSelector)
            if (episodes.isNotEmpty()) groups.add(EpisodeGroup(name ?: "线路 ${index + 1}", episodes))
        }
        return groups
    }

    /** [ChannelFormat.NoChannel]：整页只有一个剧集列表。 */
    private fun selectNoChannel(page: Element, rule: SourceRule, baseUrl: String): List<EpisodeGroup> {
        val episodes = extractEpisodesFrom(page, rule, baseUrl, rule.episodes.episodeListSelector)
        return if (episodes.isEmpty()) emptyList() else listOf(EpisodeGroup("默认", episodes))
    }

    /**
     * 从 [scope] 中选出剧集元素并提取名称 / 链接 / 序号。
     *
     * [listSelector] 选出的元素若本身是 `<a>` 则直接用其 href；否则用 [SourceRule.EpisodesStep.episodeLinkSelector]
     * 在 [scope] 内单独选出链接列表，按索引与剧集元素对齐（animeko `selectLinksOrNull`）。
     */
    private fun extractEpisodesFrom(
        scope: Element,
        rule: SourceRule,
        baseUrl: String,
        listSelector: String,
    ): List<EpisodeItem> {
        val step = rule.episodes
        val elements = selectOrEmpty(scope, listSelector)
        val links = step.episodeLinkSelector.takeIf { it.isNotBlank() }?.let { selector ->
            selectOrEmpty(scope, selector).map { it.attr("href") }
        }
        return elements.mapIndexedNotNull { index, element ->
            val name = element.text().trim()
            val href = links?.getOrNull(index) ?: element.attr("href")
            if (name.isBlank() || href.isBlank()) return@mapIndexedNotNull null
            EpisodeItem(name, computeAbsoluteUrl(baseUrl, href), extractEpisodeNumber(name, step.episodeNumberRegex))
        }
    }

    /**
     * 从剧集名称解析集数，对齐 animeko `SelectorChannelFormat.convertSpecialEpisodes` +
     * `findGroupOrFullText`：
     * - 正则命中且含 `ep` 分组 → 用该分组；命中但无分组 → 用整个文本；
     * - 正则未命中 → 用整个文本；
     * - 解析不出数字，但名称像电影（含 1080P/4K 等或为「正片」「高清版」）→ 视为第 1 集。
     */
    private fun extractEpisodeNumber(name: String, regexStr: String): Int? {
        val expr = regexStr.ifBlank { DEFAULT_MATCH_EPISODE_SORT_FROM_NAME }
        val regex = runCatching { expr.toRegex() }.getOrNull()
        val raw = if (regex == null) {
            name
        } else {
            val match = regex.find(name) ?: return if (isPossiblyMovie(name)) 1 else parseEpisodeNumberOrNull(name)
            match.groupOrNull("ep")?.value?.trim()?.takeIf { it.isNotBlank() } ?: name
        }
        return parseEpisodeNumberOrNull(raw) ?: if (isPossiblyMovie(name)) 1 else null
    }

    /** animeko `EpisodeSort(String)`：整数或 `.5` 的浮点才认为是合法集数。 */
    private fun parseEpisodeNumberOrNull(raw: String): Int? {
        val float = raw.trim().toFloatOrNull() ?: return null
        if (float < 0) return null
        if (float.toInt().toFloat() != float && float % 0.5f != 0f) return null
        return float.toInt()
    }

    /** animeko `SelectorChannelFormat.isPossiblyMovie`。 */
    private fun isPossiblyMovie(title: String): Boolean {
        if (title == "正片" || title == "高清版") return true
        return "2160P" in title || "1440P" in title || "2K" in title || "4K" in title || "1080P" in title || "720P" in title
    }

    private fun MatchResult.groupOrNull(name: String): MatchGroup? =
        runCatching { groups[name] }.getOrNull()

    /**
     * 生成填入 `{keyword}` 的搜索词（已做 URL 路径段编码）。
     *
     * 1:1 对齐 animeko `MediaSourceEngineHelpers.getSearchKeyword` + `encodeUrlSegment`：
     * - 去特殊字符走 `MediaListFilters.removeSpecials(removeMarkers = true)`：先删「电影 / 剧场版 /
     *   OVA / OAD / 总集篇」等标记（否则「剧场版 命运石之门」会变「剧场版」），保留空白（供 getFirstWord 分词）；
     * - 仅用第一个词时按空格取首段；
     * - 编码用**路径段**语义（空格 → `%20`，与 animeko `URLBuilder().appendPathSegments().encodedPathSegments.first()`
     *   一致），而非表单编码——表单编码会把 `/ & = ?` 等也编码，污染 query 串、与订阅站点的预期不符。
     */
    private fun prepareKeyword(rule: SourceRule, keyword: String): String {
        val kw = getSearchKeyword(keyword, rule.search.removeSpecialChars, rule.search.useFirstWordOnly)
        return encodeUrlSegment(kw)
    }

    /** 对齐 animeko `MediaSourceEngineHelpers.getSearchKeyword`。 */
    private fun getSearchKeyword(subjectName: String, removeSpecial: Boolean, useOnlyFirstWord: Boolean): String {
        val finalName = if (removeSpecial) {
            MediaListFilters.removeSpecials(
                subjectName,
                removeWhitespace = false, // 保留空白，供 getFirstWord 分词
                replaceNumbers = false,
                removeMarkers = true,
            )
        } else {
            subjectName
        }
        return if (useOnlyFirstWord) getFirstWord(finalName) else finalName
    }

    private fun getFirstWord(string: String): String {
        if (!string.contains(' ')) return string
        return string.substringBefore(' ').ifBlank { string }
    }

    /**
     * URL 路径段编码（对齐 animeko `MediaSourceEngineHelpers.encodeUrlSegment`）。
     *
     * 优先用自实现的路径段编码（空格→%20，保留字母/数字/`-_.~`，其余按 UTF-8 百分号编码）；
     * 该实现等价于 ktor 的 `URLBuilder().appendPathSegments(value).encodedPathSegments.first()`。
     * 仅在结果为空等异常时回退到表单编码（`+` → `%20`）。
     */
    private fun encodeUrlSegment(value: String): String =
        encodePathSegment(value).ifBlank {
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        }

    /** 复刻 ktor `encodePathSegment`：对路径段做最小必要的百分号编码。 */
    private fun encodePathSegment(value: String): String {
        if (value.isEmpty()) return ""
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when {
                ch == ' ' -> sb.append("%20")
                ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                    ch == '-' || ch == '_' || ch == '.' || ch == '~' -> sb.append(ch)
                ch.code < 0x80 -> sb.append(String.format("%%%02X", ch.code))
                else -> {
                    for (b in ch.toString().toByteArray(Charsets.UTF_8)) {
                        sb.append(String.format("%%%02X", b.toInt() and 0xFF))
                    }
                }
            }
        }
        return sb.toString()
    }

    // ───────────────────────── URL 基准 ─────────────────────────

    /**
     * 搜索结果页的相对链接基准：`scheme://host/`。
     *
     * 对齐 animeko `SelectorSearchConfig.finalBaseUrl`（`rawBaseUrl` 为空时由 `searchUrl` 猜根域名）。
     */
    fun subjectListBaseUrl(rule: SourceRule): String {
        val raw = rule.search.baseUrl.takeIf { it.isNotBlank() } ?: rule.search.searchUrl
        return guessBaseUrl(raw)
    }

    /**
     * 剧集相对链接基准：条目详情页 URL 去掉最后一段路径后的目录。
     *
     * 对齐 animeko `selectEpisodesImpl`：`URLBuilder(subjectUrl).pathSegments.dropLast(1)`。
     * 站点常把资源放在 `/vod/`、`/video/` 等前缀下，用站点根拼接会 404。
     */
    fun episodeBaseUrl(subjectUrl: String): String {
        val sanitized = subjectUrl.replace("{keyword}", "")
        return runCatching {
            val uri = URI(sanitized)
            val path = uri.rawPath.orEmpty().ifBlank { "/" }
            val dir = if (path.endsWith("/")) path else path.substringBeforeLast('/', "") + "/"
            "${uri.scheme}://${uri.authority}$dir"
        }.getOrDefault(sanitized)
    }

    /** 从任意 URL 猜站点根（animeko `guessBaseUrl`），失败时按字符串兜底。 */
    private fun guessBaseUrl(url: String): String {
        val sanitized = url.replace("{keyword}", "")
        return runCatching {
            val uri = URI(sanitized)
            "${uri.scheme}://${uri.authority}/"
        }.getOrDefault(sanitized.removeSuffix("/") + "/")
    }

    /** 把相对链接按 [baseUrl] 解析为绝对链接（animeko `UrlHelpers.computeAbsoluteUrl`）。 */
    fun computeAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        if (baseUrl.isBlank() || relativeUrl.isBlank()) return relativeUrl
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) return relativeUrl
        return runCatching { URI(baseUrl).resolve(relativeUrl).toString() }.getOrDefault(relativeUrl)
    }

    // ───────────────────────── 请求节流 ─────────────────────────

    /**
     * 同一规则两次搜索之间的最小间隔（animeko `SelectorMediaSource.delayUntilNextAllowedSearch`）。
     *
     * 实现对齐 animeko：每个规则一个独立的 [AtomicLong]，用 **CAS 循环**占位。
     * 这样：
     * 1. 对同一源的所有并发调用（如聚合搜索与详情测试同时命中某规则）都严格遵守最小间隔；
     * 2. 不同源各自独立，互不阻塞——旧实现用全局 `synchronized` + 共享 `mutableMapOf`，
     *    会让规则 A 的节流检查被规则 B 的锁串行化，毫无必要。
     *
     * 站点常在 `requestInterval` 内限流，密集请求会被判为爬虫。
     */
    private val lastSearchTime = ConcurrentHashMap<String, AtomicLong>()

    private suspend fun delayUntilNextAllowedSearch(rule: SourceRule) {
        val interval = rule.search.requestIntervalMs
        if (interval <= 0) return
        val slot = lastSearchTime.computeIfAbsent(rule.id) { AtomicLong(0L) }
        // 与 animeko 一致的 CAS 循环：算等待 → 等待 → 重试，直到成功占位
        while (true) {
            val now = System.currentTimeMillis()
            val last = slot.get()
            val wait = (last + interval) - now
            if (wait > 0) {
                delay(wait)
                continue
            }
            if (slot.compareAndSet(last, now)) return
            // CAS 失败：另一个并发调用刚占用了槽位，重试
        }
    }

    // ───────────────────────── 视频策略 ─────────────────────────

    private suspend fun matchIntercept(rule: SourceRule, absoluteUrl: String): String {
        val webViewUtil = WebViewUtil()
        val referer = rule.headers.referer.ifBlank { deriveRootUrl(rule) }
        "[matchIntercept] enter absoluteUrl=${absoluteUrl.take(140)} referer=${referer.take(90)} enableNested=${rule.video.enableNestedLinks} nestedRegex='${rule.video.nestedLinkRegex}' videoRegex='${rule.video.videoUrlRegex}' ua.len=${rule.headers.userAgent.length}".log(LOG_TAG)
        val result = try {
            if (rule.video.enableNestedLinks && rule.video.nestedLinkRegex.isNotBlank()) {
                "[matchIntercept] 嵌套模式：先拦截嵌套链接".log(LOG_TAG)
                val nested = webViewUtil.interceptRequest(
                    url = absoluteUrl,
                    regex = rule.video.nestedLinkRegex,
                    userAgent = rule.headers.userAgent,
                    referer = referer,
                )
                "[matchIntercept] 嵌套链接 = ${nested.take(140)}".log(LOG_TAG)
                val nestedUrl = resolveUrl(rule, nested)
                webViewUtil.interceptRequest(
                    url = nestedUrl,
                    regex = rule.video.videoUrlRegex.ifBlank { ".m3u8|.mp4|.mkv" },
                    userAgent = rule.headers.userAgent,
                    referer = referer,
                )
            } else {
                webViewUtil.interceptRequest(
                    url = absoluteUrl,
                    regex = rule.video.videoUrlRegex.ifBlank { ".m3u8|.mp4|.mkv" },
                    userAgent = rule.headers.userAgent,
                    referer = referer,
                )
            }
        } catch (e: Exception) {
            e.log(LOG_TAG, "matchIntercept failed")
            ""
        } finally {
            webViewUtil.clearWeb()
        }
        "[matchIntercept] → 返回 result=${result.take(140)}".log(LOG_TAG)
        return result
    }

    /**
     * 嘶哩嘶哩式：POST 表单后返回密文，前 9 字符作 MD5 盐得到 AES key/iv，解密后正则取 m3u8。
     */
    private fun matchSilisiliPost(rule: SourceRule, absoluteUrl: String): String {
        return runCatching {
            val client = OkHttpClient.Builder().applyProxy().build()
            val body = FormBody.Builder().add(rule.video.silisiliFormField, "sili").build()
            val request = Request.Builder().url(absoluteUrl).post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .also { rb ->
                    val cookie = rule.video.cookies.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                        .joinToString("; ")
                    if (cookie.isNotBlank()) rb.addHeader("Cookie", cookie)
                    val referer = rule.headers.referer.ifBlank { deriveRootUrl(rule) }
                    if (referer.isNotBlank()) rb.addHeader("Referer", referer)
                }
                .build()
            val response = client.newCall(request).execute()
            val encryptData = response.body!!.charStream().readText()
            if (encryptData.isBlank()) return@runCatching ""
            val params1 = encryptData.substring(0, 9)
            val params2 = encryptData.substring(9)
            val ivAndKey = md5Hex(params1)
            val iv = ivAndKey.substring(0, 16)
            val key = ivAndKey.substring(16)
            val decrypted = aesDecrypt(
                data = android.util.Base64.decode(params2, android.util.Base64.DEFAULT),
                key = key.toByteArray(),
                iv = iv.toByteArray(),
            ).decodeToString()
            val urlRegex = """"url":"(.*?)"""".toRegex()
            urlRegex.find(decrypted)?.groupValues?.get(1)?.replace("\\", "") ?: ""
        }.getOrElse { e ->
            e.log(LOG_TAG, "matchSilisiliPost failed")
            ""
        }
    }

    /**
     * Girigiri 式：页面脚本内 Base64 编码的视频直链，解码即得。
     */
    private suspend fun matchGirigiriB64(absoluteUrl: String, userAgent: String, referer: String = ""): String {
        return runCatching {
            val webViewUtil = WebViewUtil()
            val html = webViewUtil.getPageHtml(url = absoluteUrl, userAgent = userAgent, waitAfterLoadMs = 3000L, referer = referer)
                ?: DownloadManager.getHtml(absoluteUrl, URI(absoluteUrl).host ?: "", emptyMap())
            webViewUtil.clearWeb()
            val script = Jsoup.parse(html).select("div.player-box > div.player-left > script").firstOrNull()?.data()
                ?: return@runCatching ""
            val rawVideoUrl = """"url":"(.*?)","url_next"""".toRegex().find(script)?.groupValues?.get(1)
                ?: return@runCatching ""
            val encoded = android.util.Base64.decode(rawVideoUrl, android.util.Base64.DEFAULT)
            Uri.decode(String(encoded, Charsets.UTF_8))
        }.getOrElse { e ->
            e.log(LOG_TAG, "matchGirigiriB64 failed")
            ""
        }
    }

    private fun buildPlayHeaders(rule: SourceRule): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val referer = rule.headers.referer.ifBlank { deriveRootUrl(rule) }
        if (referer.isNotBlank()) headers["Referer"] = referer
        if (rule.video.cookies.isNotBlank()) headers["Cookie"] = rule.video.cookies.replace("\n", "; ")
        return headers
    }

    private fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun aesDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    /**
     * 抓取页面并在被拦时按 [retryDelayMs] 重试一次（对齐 animeko 测试器的 `fetchWithRetries`：
     * 限流 / 冷却页等待 `retryAfter` 后重试）。
     *
     * @return `Pair(html, blockReason)`：
     *   - 正常加载（或加载成功但页面本身是 WAF 挑战页）→ `html` 为页面内容，`blockReason` 由 [detectBlockReason] 给出（可能为 null）；
     *   - 加载失败（超时 / 网络错误）→ `html = null`，`blockReason` 为 [detectBlockReason] 判定（可能为 null，表示无法从内容识别）。
     *
     * 注意：WebView 拿不到 HTTP 状态码，[detectBlockReason] 只能靠内容启发式。
     */
    /**
     * 详情 / 剧集页抓取：OkHttp 优先、WebView 兜底。对齐搜索 [fetchSearchHtml] 的策略。
     *
     * 苹果CMS 详情页多为服务端渲染，OkHttp（宽松证书 + 站点根 Referer）直抓即可拿到
     * 完整 HTML 并解析出元数据 / 剧集，秒回且不走 WebView。这也是修复「详情页全空」的根因：
     * 旧实现 [fetchHtmlWithRetry] 走的是 WebView 优先的 [fetchOnce]，而 WebView 的
     * `evaluateJavascript` 回调在协程调度下不回来 → 每个详情页卡满 30s 超时。
     *
     * 判定「OkHttp 可用」放宽到 [BlockReason]==null 即可（不要求已解析出剧集）——用户侧
     * 明确「只要有详情页即可，不要求必须有播放地址」，空壳 / JS 站再退 WebView 兜底。
     */
    private suspend fun fetchDetailHtml(url: String, rule: SourceRule, tag: String): Pair<String?, BlockReason?> {
        val referer = runCatching { URI(url).let { "${it.scheme}://${it.authority}/" } }.getOrDefault("")
        val ua = rule.headers.userAgent
        "$tag fetchDetailHtml: referer=$referer".log(LOG_TAG, "fetchDetailHtml")
        val okHtml = fetchOkHttp(url, ua, referer, tag)
        if (okHtml != null) {
            val reason = detectBlockReason(okHtml)
            if (reason == null) {
                "$tag fetchDetailHtml: OkHttp 直抓成功 len=${okHtml.length}".log(LOG_TAG, "fetchDetailHtml")
                return okHtml to null
            }
            // OkHttp 拿到空壳 / 被拦 / JS 渲染站 → 退 WebView（受全局 webViewLock 串行化）
            "$tag fetchDetailHtml: OkHttp reason=$reason → 退 WebView".log(LOG_TAG, "fetchDetailHtml")
            val wv = fetchWebView(url, ua, referer, DEFAULT_WAIT_MS, tag, DETAIL_WEBVIEW_TIMEOUT_MS)
            if (wv != null) return wv to detectBlockReason(wv)
            return okHtml to reason
        }
        // OkHttp 直接失败 → 纯 WebView 兜底
        "$tag fetchDetailHtml: OkHttp 失败 → WebView 兜底".log(LOG_TAG, "fetchDetailHtml")
        val wv = fetchWebView(url, ua, referer, DEFAULT_WAIT_MS, tag, DETAIL_WEBVIEW_TIMEOUT_MS)
        return wv to (wv?.let { detectBlockReason(it) })
    }

    /** getPageHtml 默认「页面加载后等待」时长（与 WebViewUtil 默认一致）。 */
    private const val DEFAULT_WAIT_MS = 1200L

    /** 详情 / 剧集页 WebView 兜底的超时：JS 渲染站本就大概率抓不到（evaluateJavascript 不回调），
     *  没必要按 30s 硬等，8s 足够其完成首屏渲染；超时即放弃、退回 OkHttp 的结果。 */
    private const val DETAIL_WEBVIEW_TIMEOUT_MS = 8000L

    /** 详情 / 剧集页抓取（OkHttp 优先、WebView 兜底），带重试。返回 `(html, 被拦原因)`。 */
    private suspend fun fetchHtmlWithRetry(
        url: String,
        rule: SourceRule,
        retryDelayMs: Long = 0L,
    ): Pair<String?, BlockReason?> {
        val tag = srcTag(rule)
        val (html1, reason1) = fetchDetailHtml(url, rule, tag)
        if (reason1 == null) return html1 to null
        // 命中 WAF / 冷却 / 验证码：等待 requestInterval 后重试一次（与 animeko 一致，只重试一遍）
        if (retryDelayMs > 0) {
            delay(retryDelayMs.coerceAtLeast(1500L))
            val (html2, reason2) = fetchDetailHtml(url, rule, tag)
            if (reason2 == null) return html2 to null
            return null to (reason2 ?: reason1)
        }
        return null to reason1
    }

    /** 详情 / 剧集页抓取别名：不做重试、不暴露被拦原因。 */
    private suspend fun fetchHtml(url: String, rule: SourceRule): String? =
        fetchHtmlWithRetry(url, rule, 0).first

    /**
     * 搜索页抓取：WebView 优先，失败回退 OkHttp。
     *
     * 与 [fetchHtml] 的区别：搜索结果页多为 JS 渲染（SPA / AJAX 填充），
     * `onPageFinished` 触发时结果列表往往还没渲染完。这里延长「页面加载完成后的等待」
     * （[SEARCH_PAGE_WAIT_MS]），对齐 animeko 等待水合/动态区块稳定的行为，避免
     * 提前读到空列表——这正是「详情/资源页有数据、搜索空」的根因之一。
     *
     * 返回 `(html, 被拦原因)`；命中 WAF/限流时 html 仍为页面文本，交由调用方决定是否丢弃。
     */
    /**
     * 搜索页抓取：OkHttp 优先、WebView 兜底的说明见下方 [fetchSearchHtml]。
     * 注意：搜索结果只要求有详情页链接，不作「必须有播放地址」的过滤。
     */
    private val SEARCH_PAGE_WAIT_MS = 3500L
    private suspend fun fetchSearchHtml(url: String, rule: SourceRule, tag: String): Pair<String?, BlockReason?> {
        val referer = runCatching { URI(url).let { "${it.scheme}://${it.authority}/" } }.getOrDefault("")
        "$tag fetchSearchHtml: referer=$referer".log(LOG_TAG, "fetchSearchHtml")
        // 1. OkHttp 优先：苹果CMS 搜索结果多为服务端渲染，直抓即可拿到完整 HTML（秒回）。
        val okHtml = fetchOkHttp(url, rule.headers.userAgent, referer, tag)
        if (okHtml != null) {
            val reason = detectBlockReason(okHtml)
            val parsed = selectSubjects(okHtml, rule).also { "$tag fetchSearchHtml: OkHttp 命中解析 okHtmlLen=${okHtml.length} parsed=${it.size}".log(LOG_TAG, "fetchSearchHtml") }
            // 2. 正常且解析出条目 → 直接用（绝大多数站走这条）
            if (reason == null && parsed.isNotEmpty()) {
                "$tag fetchSearchHtml: 走 OkHttp 直抓成功（无需 WebView）".log(LOG_TAG, "fetchSearchHtml")
                return okHtml to null
            }
            // 3. OkHttp 拿到空壳/被拦/JS 渲染站 → 退 WebView（受全局 webViewLock 串行化）
            "$tag fetchSearchHtml: OkHttp 未直接可用(reason=$reason, parsed=${parsed.size}) → 退 WebView".log(LOG_TAG, "fetchSearchHtml")
            val wv = fetchWebView(url, rule.headers.userAgent, referer, SEARCH_PAGE_WAIT_MS, tag)
            if (wv != null) {
                "$tag fetchSearchHtml: WebView 兜底成功 wvLen=${wv.length}".log(LOG_TAG, "fetchSearchHtml")
                return wv to detectBlockReason(wv)
            }
            // 4. WebView 也失败 → 退回 OkHttp 的结果（可能为空，由调用方决定）
            "$tag fetchSearchHtml: WebView 兜底也失败，退回 OkHttp 结果".log(LOG_TAG, "fetchSearchHtml")
            return okHtml to reason
        }
        // OkHttp 直接失败 → 纯 WebView 兜底
        "$tag fetchSearchHtml: OkHttp 直接失败 → 纯 WebView 兜底".log(LOG_TAG, "fetchSearchHtml")
        val wv = fetchWebView(url, rule.headers.userAgent, referer, SEARCH_PAGE_WAIT_MS, tag)
        return wv to (wv?.let { detectBlockReason(it) })
    }

    /** lenient OkHttp 直抓页面（信任所有证书 + 带 UA / Host / Referer / Accept）。必须在 IO 线程执行，否则会抛 NetworkOnMainThreadException。 */
    private suspend fun fetchOkHttp(url: String, userAgent: String, referer: String, tag: String): String? {
        val t0 = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            runCatching {
                val host = runCatching { URI(url).host }.getOrNull() ?: ""
                // 反爬验证后：复用该 host 的 clearance cookie + 验证时 WebView 的真实 UA（对齐指纹），
                // 让后续 OkHttp 请求（详情/剧集）也能绕过 WAF。
                val antiCookie = SourceCookieManager.cookieHeaderForHost(host)
                val antiUa = SourceCookieManager.userAgentForHost(host) ?: userAgent
                val req = Request.Builder().url(url).get()
                    .addHeader("User-Agent", antiUa)
                    .addHeader("Host", host)
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .also { if (referer.isNotBlank()) it.addHeader("Referer", referer) }
                    .also { if (antiCookie != null) it.addHeader("Cookie", antiCookie) }
                    .build()
                val resp = lenientOkHttp.newCall(req).execute()
                val code = resp.code
                val body = resp.body?.charStream()?.readText()
                val cost = System.currentTimeMillis() - t0
                "$tag fetchOkHttp: code=$code len=${body?.length ?: 0} cost=${cost}ms".log(LOG_TAG, "fetchOkHttp")
                if (code !in 200..299) {
                    "$tag fetchOkHttp: 非 2xx，返回 null（code=$code）".log(LOG_TAG, "fetchOkHttp")
                    null
                } else body
            }.getOrElse { e ->
                val cost = System.currentTimeMillis() - t0
                "$tag fetchOkHttp: 异常 cost=${cost}ms ${e.javaClass.simpleName}: ${e.message}".log(LOG_TAG, "fetchOkHttp")
                null
            }
        }
    }

    /** WebView 抓页面（受全局 webViewLock 串行化，避免并发拖垮渲染进程）。 */
    private suspend fun fetchWebView(
        url: String,
        userAgent: String,
        referer: String,
        waitMs: Long,
        tag: String,
        timeoutMs: Long = 30_000L,
    ): String? {
        val t0 = System.currentTimeMillis()
        "$tag fetchWebView: 进入（等待 ${waitMs}ms / 超时 ${timeoutMs}ms）".log(LOG_TAG, "fetchWebView")
        return runCatching {
            val webViewUtil = WebViewUtil()
            val h = webViewUtil.getPageHtml(
                url,
                userAgent = userAgent,
                referer = referer,
                waitAfterLoadMs = waitMs,
                timeoutMs = timeoutMs,
            )
            webViewUtil.clearWeb()
            h
        }.getOrElse { e ->
            "$tag fetchWebView: 异常 ${e.javaClass.simpleName}: ${e.message}".log(LOG_TAG, "fetchWebView")
            null
        }.also { h ->
            val cost = System.currentTimeMillis() - t0
            "$tag fetchWebView: 完成 len=${h?.length ?: 0} cost=${cost}ms".log(LOG_TAG, "fetchWebView")
        }
    }

    /**
     * 内容启发式识别「页面被拦」原因。
     *
     * WebView 无法取得 HTTP 404 / 429 / 403 状态码，这里只能靠页面文本特征判定，
     * 对应 animeko `PageEvaluator` 第 3~6 步的启发式检测（验证码 / 冷却页 / 403 / WAF 挑战页）。
     *
     * 返回 null 表示「从内容看不出来被拦」（可能是正常页面，也可能是加载超时——超时由 [fetchOnce] 直接返回 null html）。
     */
    private fun detectBlockReason(html: String?): BlockReason? {
        if (html.isNullOrBlank()) return null
        return when {
            // 3. 站内冷却 / 限流页
            html.contains("访问频率过高") || html.contains("访问太频繁") || html.contains("请求过于频繁") ||
                html.contains("操作过于频繁") || html.contains("请稍后再试") || html.contains("请稍后重试") ->
                BlockReason.RATE_LIMITED
            // 5. 验证码 / 人机验证（Cloudflare 等）
            html.contains("人机验证") || html.contains("安全验证") || html.contains("滑动验证") ||
                html.contains("Just a moment") || html.contains("Checking your browser") ||
                html.contains("cf-chl") || html.contains("challenge-platform") || html.contains("请输入验证码") ->
                BlockReason.CAPTCHA
            // 6. 403 / 无特征被挡 / 通用 WAF（次元城等返回「不提供服务」）
            html.contains("不提供服务") || html.contains("访问验证") || html.contains("系统检测到异常") ||
                html.contains("DDoS") || html.contains("安全防护") || html.contains("权限不足") ||
                (html.contains("verify") && html.contains("browser")) ->
                BlockReason.WAF
            else -> null
        }
    }

    /**
     * 从 Base URL 或搜索链接推导站点根域名（scheme://host），供 Base URL / Referer 留空时兜底。
     * 这样用户不填也能自动用站点域名拼接详情页 URL、作为 Referer。
     */
    fun deriveRootUrl(rule: SourceRule): String {
        val candidate = rule.search.baseUrl.takeIf { it.isNotBlank() } ?: rule.search.searchUrl
        return runCatching { URI(candidate) }.getOrNull()?.let { uri ->
            if (uri.host.isNullOrBlank()) candidate else "${uri.scheme}://${uri.host}"
        } ?: ""
    }

    private fun resolveUrl(rule: SourceRule, url: String): String {
        if (url.isBlank()) return ""
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val base = deriveRootUrl(rule).takeIf { it.isNotBlank() }
        return if (base != null) {
            val sep = if (url.startsWith("/")) "" else "/"
            "$base$sep$url"
        } else {
            url
        }
    }

    // ───────────────────────── 调试诊断公开方法 ─────────────────────────
    // 下列方法仅供「CSS 数据源测试分析工具」(SourceAnalysisEngine) 使用，不影响生产路径。

    /**
     * 调试诊断：抓取页面原始 HTML（WebView 优先、失败回退 OkHttp），失败返回 null。
     * 供分析工具做「原始 HTML 片段预览」与选择器命中统计。
     */
    suspend fun fetchPageHtml(rule: SourceRule, url: String): String? {
        val absoluteUrl = resolveUrl(rule, url)
        return fetchHtml(absoluteUrl, rule)
    }

    /**
     * 调试诊断：生成填入关键词后的绝对搜索链接（含 URL 路径段编码），
     * 供分析工具复现抓取并展示给用户核对。
     */
    fun buildSearchUrl(rule: SourceRule, keyword: String): String {
        val query = prepareKeyword(rule, keyword)
        return when (rule.engine) {
            RuleEngineKind.Xpath -> rule.xpath.searchUrl.replace("@keyword", encodeKeywordKz(query))
            else -> rule.search.searchUrl.replace("{keyword}", query)
        }
    }

    /**
     * 调试诊断：对原始 HTML 做内容启发式「被拦」判定（公开包一层私有 [detectBlockReason]）。
     */
    fun blockReasonOf(html: String?): BlockReason? = detectBlockReason(html)

    // ───────────────────────── Kazumi XPath / API 引擎 ─────────────────────────

    private fun encodeKeywordKz(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun appendQueryKz(base: String, query: Map<String, String>): String {
        if (query.isEmpty()) return base
        val q = query.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        return if ("?" in base) "$base&$q" else "$base?$q"
    }

    private fun resolveKazumiUrl(baseUrl: String, href: String): String {
        if (href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true)) return href
        if (href.startsWith("//")) {
            val scheme = runCatching { URI(baseUrl).scheme }.getOrNull() ?: "https"
            return "$scheme:$href"
        }
        if (href.startsWith("/")) {
            val u = runCatching { URI(baseUrl) }.getOrNull()
            return if (u != null && u.host != null) "${u.scheme}://${u.authority}$href" else href
        }
        val dir = baseUrl.substringBeforeLast("/", "") + "/"
        return dir + href.removePrefix("./")
    }

    /** 模板变量替换：@name → URL 编码后的值，对齐 Kazumi `_renderTemplate(encode=true)`。 */
    private fun renderKzTemplate(template: String, vars: Map<String, String>): String {
        val re = Regex("(?<![A-Za-z0-9_])@([A-Za-z_][A-Za-z0-9_]*)")
        return re.replace(template) { m ->
            val name = m.groupValues[1]
            URLEncoder.encode(vars[name] ?: "", "UTF-8")
        }
    }

    /** 通用 HTTP 抓取（GET/POST，json/form/none 体），自动附带该 host 的 clearance cookie + 验证 UA。 */
    private suspend fun fetchHttpText(
        tag: String,
        url: String,
        method: String,
        userAgent: String,
        referer: String,
        headers: Map<String, String>,
        query: Map<String, String>,
        bodyType: String,
        body: String,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val host = runCatching { URI(url).host }.getOrNull() ?: ""
            val antiCookie = SourceCookieManager.cookieHeaderForHost(host)
            val antiUa = SourceCookieManager.userAgentForHost(host) ?: userAgent
            var fullUrl = url
            if (query.isNotEmpty() && !method.equals("POST", ignoreCase = true)) fullUrl = appendQueryKz(fullUrl, query)
            val reqBuilder = Request.Builder().url(fullUrl)
                .addHeader("User-Agent", antiUa)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .also { if (referer.isNotBlank()) it.addHeader("Referer", referer) }
                .also { if (antiCookie != null) it.addHeader("Cookie", antiCookie) }
                .also { headers.forEach { (k, v) -> it.addHeader(k, v) } }
            val req = if (method.equals("POST", ignoreCase = true)) {
                val rb: RequestBody = when (bodyType.lowercase()) {
                    "json" -> RequestBody.create("application/json; charset=utf-8".toMediaType(), body)
                    "form" -> {
                        val fb = FormBody.Builder()
                        runCatching { Json.parseToJsonElement(body) }.getOrNull()
                            ?.let { if (it is kotlinx.serialization.json.JsonObject) it.forEach { (k, v) -> fb.add(k, v.toString().trim('"')) } }
                        fb.build()
                    }
                    else -> RequestBody.create(null, "")
                }
                reqBuilder.post(rb).build()
            } else {
                reqBuilder.get().build()
            }
            val resp = lenientOkHttp.newCall(req).execute()
            val code = resp.code
            val text = resp.body?.charStream()?.readText()
            if (code !in 200..299) {
                "$tag fetchHttpText: 非 2xx code=$code".log(LOG_TAG, "fetchHttpText")
                null
            } else text
        }.getOrElse { e ->
            "$tag fetchHttpText: ${e.javaClass.simpleName}: ${e.message}".log(LOG_TAG, "fetchHttpText")
            null
        }
    }

    // ── XPath 搜索 ──

    private suspend fun searchOnceXpath(rule: SourceRule, keyword: String): List<SearchResult> {
        val tag = srcTag(rule)
        val t0 = System.currentTimeMillis()
        val query = prepareKeyword(rule, keyword)
        if (query.isBlank()) return emptyList()
        val searchUrl = rule.xpath.searchUrl.replace("@keyword", encodeKeywordKz(query))
        delayUntilNextAllowedSearch(rule)
        val referer = rule.xpath.referer.ifBlank {
            runCatching { URI(searchUrl).let { "${it.scheme}://${it.authority}/" } }.getOrDefault("")
        }
        val html = if (rule.xpath.usePost) {
            val postUrl = runCatching { URI(searchUrl).let { "${it.scheme}://${it.authority}${it.path}" } }.getOrDefault(searchUrl)
            val q = runCatching { URI(searchUrl).query }.getOrNull().orEmpty()
                .split("&").filter { it.isNotBlank() }
                .associate { it.substringBefore("=") to it.substringAfter("=") }
            val jsonBody = "{" + q.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" } + "}"
            fetchHttpText(tag, postUrl, "POST", rule.headers.userAgent, referer, emptyMap(), emptyMap(), "form", jsonBody)
        } else {
            fetchHttpText(tag, searchUrl, "GET", rule.headers.userAgent, referer, emptyMap(), emptyMap(), "none", "")
        }
        val cost = System.currentTimeMillis() - t0
        if (html == null) {
            "$tag searchOnceXpath: html=null cost=${cost}ms".log(LOG_TAG, "searchOnceXpath")
            return emptyList()
        }
        val finalHtml = if (rule.antiCrawler.enabled) AntiCrawlerGate.maybeVerifySearch(rule, keyword, html) ?: html else html
        val results = selectSubjectsXpath(finalHtml, rule)
        "$tag searchOnceXpath: 成功 htmlLen=${finalHtml.length} cost=${cost}ms found=${results.size}".log(LOG_TAG, "searchOnceXpath")
        return results
    }

    /** XPath 搜索结果解析（对应 Kazumi `parseSearch`）。 */
    fun selectSubjectsXpath(html: String, rule: SourceRule): List<SearchResult> {
        val baseUrl = rule.xpath.baseUrl.ifBlank { subjectListBaseUrl(rule) }
        if (rule.xpath.searchList.isBlank() || rule.xpath.searchName.isBlank() || rule.xpath.searchResult.isBlank()) return emptyList()
        val doc = runCatching { JXDocument.create(html) }.getOrNull() ?: return emptyList()
        val nodes = runCatching { doc.selN(rule.xpath.searchList) }.getOrDefault(emptyList())
        return nodes.mapNotNull { node ->
            val name = runCatching {
                node.sel(rule.xpath.searchName).firstOrNull()?.asElement()?.text()?.trim()
            }.getOrNull().orEmpty()
            val href = runCatching {
                val inner = node.sel(rule.xpath.searchResult).firstOrNull()
                inner?.asElement()?.attr("href") ?: inner?.asElement()?.attr("src") ?: inner?.asString()
            }.getOrNull().orEmpty()
            if (href.isBlank()) null
            else SearchResult(title = name, url = resolveKazumiUrl(baseUrl, href))
        }
    }

    // ── XPath 详情 / 剧集 ──

    private suspend fun getAnimeDetailXpath(rule: SourceRule, detailUrl: String): AnimeDetailBean? {
        val absoluteUrl = resolveUrl(rule, detailUrl)
        val tag = srcTag(rule)
        val (html, block) = fetchHtmlWithRetry(absoluteUrl, rule, rule.search.requestIntervalMs)
        if (html == null || block != null) {
            "$tag getAnimeDetailXpath: html=${if (html == null) "null" else "ok"} block=$block".log(LOG_TAG, "getAnimeDetailXpath")
            return null
        }
        val groups = selectEpisodesXpath(html, rule, absoluteUrl)
        val document = Jsoup.parse(html)
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: document.title().ifBlank { absoluteUrl.substringAfterLast("/").substringBefore("?") }
        val cover = document.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val desc = document.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
        val channels = groups.mapIndexed { i, g -> i to g.episodes.map { EpisodeBean(it.name, it.url) } }.toMap()
        return AnimeDetailBean(
            title = title,
            imgUrl = cover,
            desc = desc,
            tags = emptyList(),
            relatedAnimes = emptyList(),
            channels = channels,
        )
    }

    fun selectEpisodesXpath(html: String, rule: SourceRule, baseUrl: String): List<EpisodeGroup> {
        val bUrl = rule.xpath.baseUrl.ifBlank { baseUrl }
        val doc = runCatching { JXDocument.create(html) }.getOrNull() ?: return emptyList()
        if (rule.xpath.chapterRoads.isBlank()) {
            val eps = parseXpathEpisodes(doc, rule, bUrl)
            return if (eps.isEmpty()) emptyList() else listOf(EpisodeGroup("默认", eps))
        }
        val roads = runCatching { doc.selN(rule.xpath.chapterRoads) }.getOrDefault(emptyList())
        return roads.mapIndexed { idx, road ->
            EpisodeGroup("播放线路${idx + 1}", parseXpathEpisodes(road, rule, bUrl))
        }
    }

    private fun parseXpathEpisodes(ctx: JXNode, rule: SourceRule, baseUrl: String): List<EpisodeItem> {
        val nodes = runCatching { ctx.sel(rule.xpath.chapterResult) }.getOrDefault(emptyList())
        return nodes.mapNotNull { ep ->
            val el = ep.asElement()
            val name = el?.text()?.trim().orEmpty()
            val href = el?.attr("href") ?: ep.asString().orEmpty()
            if (name.isBlank() && href.isBlank()) return@mapNotNull null
            val finalName = name.ifBlank { href.substringAfterLast("/").substringBefore("?") }
            EpisodeItem(name = finalName, url = resolveKazumiUrl(baseUrl, href))
        }
    }

    private fun parseXpathEpisodes(doc: JXDocument, rule: SourceRule, baseUrl: String): List<EpisodeItem> {
        val nodes = runCatching { doc.selN(rule.xpath.chapterResult) }.getOrDefault(emptyList())
        return nodes.mapNotNull { ep ->
            val el = ep.asElement()
            val name = el?.text()?.trim().orEmpty()
            val href = el?.attr("href") ?: ep.asString().orEmpty()
            if (name.isBlank() && href.isBlank()) return@mapNotNull null
            val finalName = name.ifBlank { href.substringAfterLast("/").substringBefore("?") }
            EpisodeItem(name = finalName, url = resolveKazumiUrl(baseUrl, href))
        }
    }

    // ── API 搜索 ──

    private suspend fun searchOnceApi(rule: SourceRule, keyword: String): List<SearchResult> {
        val tag = srcTag(rule)
        val t0 = System.currentTimeMillis()
        val query = prepareKeyword(rule, keyword)
        if (query.isBlank()) return emptyList()
        val cfg = rule.apiSearch
        if (cfg.url.isBlank() || cfg.listPath.isBlank() || cfg.namePath.isBlank()) {
            "$tag searchOnceApi: apiSearch 配置不完整，跳过".log(LOG_TAG, "searchOnceApi")
            return emptyList()
        }
        val reqUrl = renderKzTemplate(cfg.url, mapOf("keyword" to query))
        delayUntilNextAllowedSearch(rule)
        val raw = fetchHttpText(tag, reqUrl, cfg.method, rule.headers.userAgent, rule.headers.referer, cfg.headers, cfg.query, cfg.bodyType, cfg.body)
        if (raw == null) {
            "$tag searchOnceApi: 响应为空".log(LOG_TAG, "searchOnceApi")
            return emptyList()
        }
        val root = runCatching { Json.parseToJsonElement(raw) }.getOrElse {
            "$tag searchOnceApi: 响应非 JSON".log(LOG_TAG, "searchOnceApi")
            return emptyList()
        }
        val list = MiniJsonPath.resolve(root, cfg.listPath) ?: emptyList()
        val results = list.mapNotNull { item ->
            val name = MiniJsonPath.resolve(item, cfg.namePath)?.firstOrNull()?.let { MiniJsonPath.firstString(it) }.orEmpty()
            val url = MiniJsonPath.resolve(item, cfg.sourcePath)?.firstOrNull()?.let { MiniJsonPath.firstString(it) }.orEmpty()
            if (url.isBlank()) null else SearchResult(title = name.ifBlank { url }, url = url)
        }
        val cost = System.currentTimeMillis() - t0
        "$tag searchOnceApi: 成功 found=${results.size} cost=${cost}ms".log(LOG_TAG, "searchOnceApi")
        return results
    }

    // ── API 详情 / 剧集 ──

    private suspend fun getAnimeDetailApi(rule: SourceRule, detailUrl: String): AnimeDetailBean? {
        val groups = runCatching { selectEpisodesApi(detailUrl, rule) }.getOrNull() ?: return null
        val title = detailUrl.substringAfterLast("/").substringBefore("?").ifBlank { "API 源" }
        val channels = groups.mapIndexed { i, g -> i to g.episodes.map { EpisodeBean(it.name, it.url) } }.toMap()
        return AnimeDetailBean(
            title = title,
            imgUrl = "",
            desc = "",
            tags = emptyList(),
            relatedAnimes = emptyList(),
            channels = channels,
        )
    }

    private suspend fun selectEpisodesApi(entryUrl: String, rule: SourceRule): List<EpisodeGroup> = withContext(Dispatchers.IO) {
        val cfg = rule.apiChapter
        val tag = srcTag(rule)
        if (cfg.url.isBlank()) {
            "$tag selectEpisodesApi: apiChapter.url 为空".log(LOG_TAG, "selectEpisodesApi")
            return@withContext emptyList()
        }
        val reqUrl = renderKzTemplate(cfg.url, mapOf("source" to entryUrl, "keyword" to ""))
        val raw = fetchHttpText(tag, reqUrl, cfg.method, rule.headers.userAgent, rule.headers.referer, cfg.headers, cfg.query, cfg.bodyType, cfg.body)
            ?: return@withContext emptyList()
        val root = runCatching { Json.parseToJsonElement(raw) }.getOrElse {
            "$tag selectEpisodesApi: 响应非 JSON".log(LOG_TAG, "selectEpisodesApi")
            return@withContext emptyList()
        }
        // 捕获 variables（命名 JSONPath，从响应根取值，供 episodePage 模板使用）
        val captured = cfg.variables.mapValues { (_, path) ->
            MiniJsonPath.resolve(root, path)?.firstOrNull()?.let { MiniJsonPath.firstString(it) } ?: ""
        }
        if (cfg.format == "delimited") parseApiDelimited(root, cfg, captured)
        else parseApiNested(root, cfg, captured)
    }

    private fun parseApiNested(root: JsonElement, cfg: SourceRule.ApiChapterStep, captured: Map<String, String>): List<EpisodeGroup> {
        val roads = if (cfg.roadsPath.isBlank()) listOf(root) else (MiniJsonPath.resolve(root, cfg.roadsPath) ?: emptyList())
        return roads.mapIndexed { idx, roadEl ->
            val roadName = if (cfg.roadNamePath.isBlank()) "播放线路${idx + 1}"
            else MiniJsonPath.resolve(roadEl, cfg.roadNamePath)?.firstOrNull()?.let { MiniJsonPath.firstString(it) }?.ifBlank { "播放线路${idx + 1}" }
                ?: "播放线路${idx + 1}"
            val eps = MiniJsonPath.resolve(roadEl, cfg.episodesPath) ?: emptyList()
            val items = eps.mapIndexed { epIdx, epEl ->
                val name = MiniJsonPath.resolve(epEl, cfg.episodeNamePath)?.firstOrNull()?.let { MiniJsonPath.firstString(it) }.orEmpty()
                    .ifBlank { "第${epIdx + 1}集" }
                val rawUrl = MiniJsonPath.resolve(epEl, cfg.episodeUrlPath)?.firstOrNull()?.let { MiniJsonPath.firstString(it) }.orEmpty()
                val finalUrl = if (rawUrl.isNotBlank()) rawUrl
                else if (cfg.episodePageUrl.isNotBlank()) renderKzTemplate(
                    cfg.episodePageUrl,
                    captured + mapOf(
                        "episodeUrl" to rawUrl,
                        "roadIndex" to idx.toString(),
                        "roadNumber" to (idx + 1).toString(),
                        "episodeIndex" to epIdx.toString(),
                        "episodeNumber" to (epIdx + 1).toString(),
                    ),
                ) else ""
                EpisodeItem(name = name, url = finalUrl)
            }.filter { it.url.isNotBlank() }
            EpisodeGroup(roadName, items)
        }
    }

    private fun parseApiDelimited(root: JsonElement, cfg: SourceRule.ApiChapterStep, captured: Map<String, String>): List<EpisodeGroup> {
        val namesRaw = MiniJsonPath.resolve(root, cfg.roadNamesPath)?.firstOrNull()?.let { MiniJsonPath.firstString(it) } ?: ""
        val epsRaw = MiniJsonPath.resolve(root, cfg.roadEpisodesPath)?.firstOrNull()?.let { MiniJsonPath.firstString(it) } ?: ""
        val roadNames = namesRaw.split(cfg.roadSeparator).map { it.trim() }.filter { it.isNotBlank() }
        val roadEps = epsRaw.split(cfg.roadSeparator).map { it.trim() }
        return roadEps.mapIndexed { idx, block ->
            val name = roadNames.getOrNull(idx)?.ifBlank { "播放线路${idx + 1}" } ?: "播放线路${idx + 1}"
            val items = block.split(cfg.episodeSeparator).map { it.trim() }.filter { it.isNotBlank() }.mapIndexed { epIdx, field ->
                val parts = field.split(cfg.fieldSeparator)
                val epName = parts.firstOrNull()?.trim() ?: "第${epIdx + 1}集"
                val epUrl = parts.getOrNull(1)?.trim() ?: ""
                EpisodeItem(name = epName, url = epUrl)
            }.filter { it.url.isNotBlank() }
            EpisodeGroup(name, items)
        }
    }
}
