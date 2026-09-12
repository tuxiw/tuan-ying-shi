package com.example.tuanyingshi.data.remote.parse

import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.EpisodeBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.data.remote.dandanplay.DandanplayApi
import com.example.tuanyingshi.data.remote.dandanplay.DanmakuItem
import com.example.tuanyingshi.domain.model.AnimeStatus
import com.example.tuanyingshi.domain.model.AnimeType
import com.example.tuanyingshi.domain.model.Region
import com.example.tuanyingshi.util.dandanplay.DandanplayConfig
import com.example.tuanyingshi.util.log
import com.example.tuanyingshi.util.source_rule.RuleExecutor
import com.example.tuanyingshi.util.source_rule.SourceRule
import com.example.tuanyingshi.util.source_rule.SourceRuleRepository
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 「外部资源」混合源（弹弹play 开放弹幕网络 API v2）。
 *
 * 设计（与用户决策一致）：**找番 + 弹幕 + 元数据 + 排行 + 新番全部走弹弹play**；
 * **视频播放仍用现有 CSS 规则源**——弹弹play 开放 API 不提供视频直链，因此播放时按
 * 「番名 + 集名」在 CSS 规则源里匹配出可播链接（复用 [RuleExecutor]）。
 *
 * 集 URL 约定：`dandanplay://{bangumiId}/{episodeId}/{encode(番名)}/{encode(集名)}`
 * ——既承载弹幕所需 episodeId，也承载 CSS 视频匹配所需的番名/集名。
 */
object DandanplaySource : AnimeSource {

    private const val TAG = "DandanplaySource"
    private const val SCHEME = "dandanplay://"

    // 外部资源无独立域名：baseUrl 始终用弹弹play 配置地址，拒绝被聚合空域名覆盖。
    override val DEFAULT_DOMAIN: String
        get() = DandanplayConfig.baseUrl
    override var baseUrl: String
        get() = DandanplayConfig.baseUrl
        set(_) {}
    override var WEB_URL: String = "api.dandanplay.net"

    // ── 集 URL 编解码 ──

    private fun encodeEpisode(
        bangumiId: String,
        episodeId: Long,
        animeTitle: String,
        episodeTitle: String,
    ): String =
        "$SCHEME$bangumiId/$episodeId/${enc(animeTitle)}/${enc(episodeTitle)}"

    private fun decodeEpisode(url: String): DandanplayEpisodeRef? {
        if (!url.startsWith(SCHEME)) return null
        val parts = url.split("/")
        if (parts.size < 6) return null
        return DandanplayEpisodeRef(
            bangumiId = parts[2],
            episodeId = parts[3].toLongOrNull() ?: 0,
            animeTitle = dec(parts[4]),
            episodeTitle = dec(parts[5]),
        )
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    // ───────────────────────── 浏览类：弹弹play ─────────────────────────

    override suspend fun getHomeData(): List<HomeBean> {
        val hot = runCatching { DandanplayApi.getTrendingHot("week") }.getOrDefault(emptyList())
        val shin = runCatching { DandanplayApi.getShin() }.getOrDefault(emptyList())
        "getHomeData: hot=${hot.size}, shin=${shin.size}".log(TAG)
        val sections = mutableListOf<HomeBean>()
        if (hot.isNotEmpty()) {
            sections.add(HomeBean(title = "热播推荐", animes = hot.mapNotNull { it.toAnimeBean() }))
        }
        if (shin.isNotEmpty()) {
            sections.add(HomeBean(title = "新番速递", animes = shin.mapNotNull { it.toAnimeBean() }))
        }
        return sections
    }

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        // 弹弹play 无「按星期排期」接口；此处用新番列表按 bangumiId 稳定散列到周一~周日，
        // 仅作占位，让周表页有内容。后续可改为逐番拉 airDate 精确分桶。
        val shin = runCatching { DandanplayApi.getShin() }.getOrDefault(emptyList())
        if (shin.isEmpty()) return emptyMap()
        val map = mutableMapOf<Int, MutableList<AnimeBean>>()
        shin.forEach { s ->
            val day = (((s.bangumiId.toLongOrNull() ?: 0) % 7) + 1).toInt().coerceIn(1, 7)
            map.getOrPut(day) { mutableListOf() }.add(s.toAnimeBean() ?: return@forEach)
        }
        "getWeekData: ${shin.size} 部 -> ${map.size} 天有内容".log(TAG)
        return map
    }

    override suspend fun getFilterData(
        zoneId: Int,
        tag: String?,
        year: Int?,
        orderBy: String?,
        page: Int,
    ): List<AnimeBean> {
        // 弹弹play 高级搜索（search/adv）字段较多，这里用 tag / 年份拼一个关键词走 search/anime 兜底。
        val query = tag?.takeIf { it.isNotBlank() } ?: year?.toString() ?: ""
        if (query.isBlank()) {
            "getFilterData: query 为空，跳过".log(TAG)
            return emptyList()
        }
        val list = runCatching { DandanplayApi.searchAnime(query, page) }.getOrDefault(emptyList())
        val beans = list.mapNotNull { it.toAnimeBean() }
        "getFilterData('$query', page=$page) -> ${beans.size} 条".log(TAG)
        return beans
    }

    override suspend fun getRanking(): Map<String, List<AnimeBean>> {
        val hot = runCatching { DandanplayApi.getTrendingHot("week") }.getOrDefault(emptyList())
        val rising = runCatching { DandanplayApi.getTrendingRising("week") }.getOrDefault(emptyList())
        val newAnime = runCatching { DandanplayApi.getNewAnimeHot("week") }.getOrDefault(emptyList())
        "getRanking: hot=${hot.size}, rising=${rising.size}, newAnime=${newAnime.size}".log(TAG)
        return mapOf(
            "TV番组" to hot.mapNotNull { it.toAnimeBean() },
            "剧场番组" to rising.mapNotNull { it.toAnimeBean() },
            "新番热播" to newAnime.mapNotNull { it.toAnimeBean() },
        )
    }

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        val list = runCatching { DandanplayApi.searchAnime(query, page) }.getOrDefault(emptyList())
        val beans = list.mapNotNull { it.toAnimeBean() }
        "getSearchData('$query', page=$page) -> ${beans.size} 条".log(TAG)
        return beans
    }

    // ───────────────────────── 详情 ─────────────────────────

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val bangumiId = detailUrl.substringAfterLast("/").toLongOrNull()
        if (bangumiId == null || bangumiId == 0L) {
            "getAnimeDetail: 无效 url='$detailUrl'".log(TAG)
            return AnimeDetailBean(title = "", imgUrl = "", desc = "", relatedAnimes = emptyList())
        }
        val d = runCatching { DandanplayApi.getBangumi(bangumiId.toString()) }.getOrNull()
            ?: run {
                "getAnimeDetail($bangumiId): detail null".log(TAG)
                return AnimeDetailBean(title = "", imgUrl = "", desc = "", relatedAnimes = emptyList())
            }
        val eps = d.episodeList
        "getAnimeDetail('${d.animeTitle}'): episodes=${eps.size}".log(TAG)
        val episodes = eps.map { ep ->
            EpisodeBean(
                name = ep.episodeTitle.ifBlank { "第${ep.episodeId}话" },
                url = encodeEpisode(d.bangumiId, ep.episodeId, d.animeTitle, ep.episodeTitle),
            )
        }
        return AnimeDetailBean(
            title = d.animeTitle,
            imgUrl = d.imageUrl,
            desc = d.summary,
            tags = d.tags?.map { it.name }.orEmpty(),
            relatedAnimes = emptyList(),
            episodes = episodes,
            channels = emptyMap(),
            region = Region.JP,
            type = mapType(d.type),
            status = AnimeStatus.ONGOING,
            year = d.airDate.take(4).toIntOrNull() ?: 2026,
            rating = d.rating.toDoubleOrNull() ?: 0.0,
            totalEpisodes = eps.size,
            latestEpisodeLabel = eps.lastOrNull()?.episodeTitle ?: "",
            sourceId = "dandanplay",
            sourceName = "弹弹play",
            iconUrl = "",
        )
    }

    // ───────────────────────── 播放：视频仍走 CSS 规则源 ─────────────────────────

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val ref = decodeEpisode(episodeUrl) ?: return VideoBean(videoUrl = "", headers = emptyMap())
        return resolveVideoFromCss(ref.animeTitle, ref.episodeTitle)
    }

    /**
     * 按「番名 + 集名」在全部 CSS 规则源里匹配可播链接（弹弹play 无视频直链，借现有 CSS 源）。
     * 对齐 RuleAggregateAnimeSource：跨源按标题相似度 + tier 排序，逐个下钻详情匹配集名。
     */
    private suspend fun resolveVideoFromCss(animeTitle: String, episodeTitle: String): VideoBean {
        if (animeTitle.isBlank()) return VideoBean(videoUrl = "", headers = emptyMap())
        val rules = SourceRuleRepository.getAll().filter { RuleExecutor.supportsCurrentPlayer(it) }
        if (rules.isEmpty()) return VideoBean(videoUrl = "", headers = emptyMap())

        val candidates = rules.flatMap { rule ->
            RuleExecutor.searchSubjects(rule, listOf(animeTitle)).map { rule to it }
        }.filter { it.second.title.isNotBlank() }

        val ranked = candidates
            .map { (rule, entry) -> Triple(rule, entry, RuleExecutor.matchRate(entry.title, animeTitle)) }
            .sortedWith(
                compareByDescending<Triple<SourceRule, RuleExecutor.SearchResult, Int>> { it.third }
                    .thenBy { it.first.tier },
            )
            .take(5)

        for ((rule, entry) in ranked) {
            val detail = runCatching { RuleExecutor.getAnimeDetail(rule, entry.url) }.getOrNull() ?: continue
            if (detail.title.isBlank() || detail.episodes.isEmpty()) continue
            val ep = detail.episodes.firstOrNull { RuleExecutor.matchRate(it.name, episodeTitle) >= 70 }
                ?: detail.episodes.firstOrNull {
                    it.name.contains(episodeTitle, ignoreCase = true) || episodeTitle.contains(it.name, ignoreCase = true)
                }
                ?: continue
            val m = RuleExecutor.matchVideoDetailed(rule, ep.url)
            if (m.url.isNotBlank()) {
                return VideoBean(
                    videoUrl = m.url,
                    headers = m.headers,
                    sourceId = rule.id,
                    sourceName = rule.name,
                    iconUrl = rule.iconUrl,
                )
            }
        }
        return VideoBean(videoUrl = "", headers = emptyMap())
    }

    // ───────────────────────── 弹幕 ─────────────────────────

    /**
     * 一次弹幕拉取的结果：弹幕列表 + 命中的番剧/集元信息（供播放页展示「当前匹配的弹幕番剧/集」，并支持手动切换）。
     */
    data class DanmakuResult(
        val items: List<DanmakuItem>,
        val animeTitle: String,
        val episodeTitle: String,
        val bangumiId: String,
        val episodeId: Long,
    )

    /** 弹幕搜索候选：一个具体的「番剧 + 集」，用户可手动选定为当前集的弹幕来源。 */
    data class DanmakuSearchEntry(
        val animeTitle: String,
        val episodeTitle: String,
        val bangumiId: String,
        val episodeId: Long,
    )

    /**
     * 拉取某集弹幕：优先用 episodeId 直拉；episodeId 缺失时退而用「番名 集名」[DandanplayApi.match] 反查。
     * 返回 [DanmakuResult]，含命中的番剧/集信息（即使匹配失败也带上原始番名/集名，便于 UI 展示）。
     */
    suspend fun getDanmaku(episodeUrl: String): DanmakuResult {
        val ref = decodeEpisode(episodeUrl) ?: run {
            "getDanmaku: 无法解码 episodeUrl='$episodeUrl'".log(TAG)
            return DanmakuResult(emptyList(), "", "", "", 0)
        }
        "getDanmaku: bangumiId=${ref.bangumiId}, episodeId=${ref.episodeId}, '${ref.animeTitle}'".log(TAG)
        val resolved = if (ref.episodeId > 0) {
            ResolvedEpisode(ref.episodeId, ref.bangumiId, ref.animeTitle, ref.episodeTitle)
        } else {
            resolveEpisode(ref.animeTitle, ref.episodeTitle, ref.bangumiId.takeIf { it.isNotBlank() })
        }
        val items = if (resolved.episodeId > 0) getComments(resolved.episodeId, "getDanmaku") else emptyList()
        return DanmakuResult(items, resolved.animeTitle, resolved.episodeTitle, resolved.bangumiId, resolved.episodeId)
    }

    /**
     * 按「番名 + 集名」匹配并拉取弹幕，供**非弹弹play 源的剧集**复用弹弹play 弹幕库
     * （次元城 / CSS 规则源等默认数据源的集没有 `dandanplay://` 编码，需靠番名+集名反查）。
     *
     * 解析方案（已对官方 API 实测验证）：弹弹play 的 `/api/v2/match` 是**文件识别**接口，
     * 依赖真实文件的 hash（前 16MB 的 MD5），无法用纯「番名+集名」文本稳定匹配；
     * 而 `/api/v2/search/anime` + `/api/v2/bangumi/{id}` 能稳定拿到每集的 `episodeId`。
     * 故本方法改用 **search/anime → getBangumi → 按集名匹配 episodeId** 的管线。
     *
     * @param animeTitle 当前播放番剧标题（来自详情页元信息）
     * @param episodeTitle 当前集名称（如「第1集」/「EP01」）
     */
    suspend fun getDanmakuByTitle(animeTitle: String, episodeTitle: String): DanmakuResult {
        if (animeTitle.isBlank()) {
            "getDanmakuByTitle: animeTitle 为空，跳过".log(TAG)
            return DanmakuResult(emptyList(), "", "", "", 0)
        }
        "getDanmakuByTitle: 番名='$animeTitle' 集名='$episodeTitle'".log(TAG)
        val resolved = resolveEpisode(animeTitle, episodeTitle, null)
        val items = if (resolved.episodeId > 0) getComments(resolved.episodeId, "getDanmakuByTitle") else emptyList()
        return DanmakuResult(items, resolved.animeTitle, resolved.episodeTitle, resolved.bangumiId, resolved.episodeId)
    }

    /** 按用户手动选定的候选拉取弹幕（搜索面板选择后调用）。 */
    suspend fun getDanmakuByEntry(entry: DanmakuSearchEntry): DanmakuResult {
        val items = if (entry.episodeId > 0) getComments(entry.episodeId, "getDanmakuByEntry") else emptyList()
        return DanmakuResult(items, entry.animeTitle, entry.episodeTitle, entry.bangumiId, entry.episodeId)
    }

    /**
     * 按关键词搜索弹幕候选（番剧 + 集）。用 [DandanplayApi.searchAnime] 取得候选番剧，
     * 再逐一下钻 [DandanplayApi.getBangumi] 取出选集，展开成扁平的「番名 / 集名」候选，
     * 供用户在播放页手动选择当前集对应的弹幕来源。
     */
    suspend fun searchDanmaku(keyword: String): List<DanmakuSearchEntry> {
        if (keyword.isBlank()) return emptyList()
        val animes = runCatching { DandanplayApi.searchAnime(keyword) }.getOrDefault(emptyList())
        if (animes.isEmpty()) return emptyList()
        val entries = mutableListOf<DanmakuSearchEntry>()
        // 取匹配度最高的前 3 个番，逐一下钻选集（手动搜索，请求量可控）。
        for (anime in animes.take(3)) {
            val detail = runCatching { DandanplayApi.getBangumi(anime.bangumiId) }.getOrNull() ?: continue
            val eps = detail.episodeList
            if (eps.isEmpty()) continue
            for (ep in eps) {
                if (ep.episodeId > 0 && ep.episodeTitle.isNotBlank()) {
                    entries.add(
                        DanmakuSearchEntry(
                            animeTitle = detail.animeTitle.ifBlank { anime.animeTitle },
                            episodeTitle = ep.episodeTitle,
                            bangumiId = anime.bangumiId,
                            episodeId = ep.episodeId,
                        )
                    )
                }
            }
        }
        return entries.also { "searchDanmaku('$keyword') -> ${it.size} 条候选".log(TAG) }
    }

    /**
     * 反查「番剧 + 集」对应的 [episodeId]：
     * - 已知 bangumiId（弹弹play 源精确匹配）时直接下钻选集；
     * - 否则先 [DandanplayApi.searchAnime] 取最匹配番剧，再 [DandanplayApi.getBangumi] 拿选集；
     * - 最后按集名/集数在选集中定位目标集。
     * 返回 episodeId=0 表示匹配失败（UI 据此提示用户手动匹配）。
     *
     * 注：返回番名/集名统一用弹弹play 官方命名，便于展示与二次检索。
     */
    private suspend fun resolveEpisode(
        animeTitle: String,
        episodeTitle: String,
        restrictBangumiId: String?,
    ): ResolvedEpisode {
        val bangumiId = restrictBangumiId?.takeIf { it.isNotBlank() } ?: run {
            val animes = runCatching { DandanplayApi.searchAnime(animeTitle.trim()) }.getOrDefault(emptyList())
            if (animes.isEmpty()) return@run null
            // 弹弹play 的 search/anime 已按相关度排序，第一个结果即最可能为用户所指番。
            // 即便存在别名 / 译名差异（如「链锯人」→ 排首的「电锯人」），后端也会把正确条目置顶，
            // 应信任其排序而非用本地 matchRate 重排（后者会因「链锯人 蕾塞篇」字面更接近而选错番）。
            // 仅当首个结果与查询相似度过低（可能误搜到无关项）时，才回退取标题最相似者。
            val top = animes.first()
            val topRate = RuleExecutor.matchRate(top.animeTitle, animeTitle)
            val chosen = if (topRate >= 45) {
                top
            } else {
                animes.maxByOrNull { RuleExecutor.matchRate(it.animeTitle, animeTitle) } ?: top
            }
            val chosenRate = RuleExecutor.matchRate(chosen.animeTitle, animeTitle)
            if (chosenRate < 30) null else chosen.bangumiId
        } ?: return ResolvedEpisode(0, "", animeTitle, episodeTitle)

        val detail = runCatching { DandanplayApi.getBangumi(bangumiId) }.getOrNull()
            ?: return ResolvedEpisode(0, bangumiId, animeTitle, episodeTitle)
        val eps = detail.episodeList
        if (eps.isEmpty()) return ResolvedEpisode(0, bangumiId, animeTitle, episodeTitle)

        val ep = findEpisode(eps, episodeTitle) ?: eps.firstOrNull()
        return if (ep != null && ep.episodeId > 0) {
            ResolvedEpisode(
                episodeId = ep.episodeId,
                bangumiId = bangumiId,
                animeTitle = detail.animeTitle.ifBlank { animeTitle },
                episodeTitle = ep.episodeTitle.ifBlank { episodeTitle },
            )
        } else {
            ResolvedEpisode(0, bangumiId, animeTitle, episodeTitle)
        }
    }

    /**
     * 在选集中定位目标集：依次尝试「完全相等 → 高相似度(>=75) → 互包含 → 集数数字一致」。
     * 全部失败返回 null（调用方兜底取首集或提示手动匹配）。
     */
    private fun findEpisode(eps: List<DandanplayApi.Episode>, episodeTitle: String): DandanplayApi.Episode? {
        if (episodeTitle.isBlank()) return null
        val norm = episodeTitle.trim()
        return eps.firstOrNull { it.episodeTitle.equals(norm, ignoreCase = true) }
            ?: eps.firstOrNull { RuleExecutor.matchRate(it.episodeTitle, norm) >= 75 }
            ?: eps.firstOrNull {
                it.episodeTitle.isNotBlank() &&
                    (it.episodeTitle.contains(norm, ignoreCase = true) || norm.contains(it.episodeTitle, ignoreCase = true))
            }
            ?: run {
                val n = extractEpisodeNumber(norm)
                if (n != null) eps.firstOrNull { extractEpisodeNumber(it.episodeTitle) == n } else null
            }
    }

    /** 从集名中提取集数数字（第N话 / EP N / N 话 / 话N 等），提取不到返回 null。 */
    private fun extractEpisodeNumber(title: String): Int? {
        if (title.isBlank()) return null
        val patterns = listOf(
            Regex("第\\s*(\\d{1,4})\\s*[话集]"),
            Regex("(?i)EP\\s*(\\d{1,4})"),
            Regex("[\\-\\s]\\s*(\\d{1,4})\\s*[话集]"),
            Regex("(\\d{1,4})\\s*[话集]"),
        )
        for (p in patterns) {
            p.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private data class ResolvedEpisode(
        val episodeId: Long,
        val bangumiId: String,
        val animeTitle: String,
        val episodeTitle: String,
    )

    private suspend fun getComments(episodeId: Long, caller: String): List<DanmakuItem> {
        return runCatching { DandanplayApi.getComment(episodeId, withRelated = true) }
            .getOrDefault(emptyList())
            .map { DanmakuItem(time = it.time, text = it.text, color = it.color, mode = it.mode) }
            .also { "getComments($caller): episodeId=$episodeId -> ${it.size} 条弹幕".log(TAG) }
    }

    // ───────────────────────── 映射工具 ─────────────────────────

    private fun DandanplayApi.AnimeSummary.toAnimeBean(): AnimeBean? {
        if (animeTitle.isBlank()) return null
        return AnimeBean(
            title = animeTitle,
            img = imageUrl,
            url = "bangumi/$bangumiId",
            episodeName = "",
            tags = tags.orEmpty(),
            year = year.toIntOrNull(),
            sourceName = "弹弹play",
            sourceId = "dandanplay",
        )
    }

    private fun mapType(type: String): AnimeType = when {
        type.equals("movie", ignoreCase = true) -> AnimeType.OVA
        type.equals("ova", ignoreCase = true) -> AnimeType.OVA
        type.equals("tvsp", ignoreCase = true) -> AnimeType.OVA
        type.equals("donghua", ignoreCase = true) -> AnimeType.DONGHUA
        else -> AnimeType.SERIES
    }

    private data class DandanplayEpisodeRef(
        val bangumiId: String,
        val episodeId: Long,
        val animeTitle: String,
        val episodeTitle: String,
    )
}
