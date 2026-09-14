package com.example.tuanyingshi.data.remote.parse

import com.example.tuanyingshi.data.remote.FilterPage
import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.util.source_rule.RuleExecutor
import com.example.tuanyingshi.util.source_rule.SourceRule
import com.example.tuanyingshi.util.source_rule.SourceRuleRepository
import com.example.tuanyingshi.util.log
import com.example.tuanyingshi.util.SearchChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

/**
 * 「弹弹play（API 源）」聚合源：选中它作为默认数据源时，搜索 / 选集 / 播放统一走
 * [SourceRuleRepository] 中的**全部** CSS 规则源；浏览（首页 / 周表 / 分类 / 排行榜）
 * 与详情元数据仍走次元城 API。
 *
 * 性能与可见性设计（针对"全部一起太慢 / 看不到数据"）：
 * - 搜索 / 选集 / 播放对全部规则**并发**执行，而非串行累加；
 * - 单个 CSS 源通过 [PER_SOURCE_TIMEOUT_MS] 超时包裹，坏源/慢源被直接跳过，不拖垮整体；
 * - 并发数受 [MAX_CONCURRENCY] 限制，平衡速度与 WebView 资源占用；
 * - 搜索结果去重并标注 [AnimeBean.sourceName]，让用户明确看到数据来自哪个 CSS 源；
 * - 详情页对规则站 URL 按 host 精准匹配对应规则直达，避免点进详情空白。
 */
class RuleAggregateAnimeSource : AnimeSource {

    override val DEFAULT_DOMAIN: String = ""
    override var baseUrl: String = ""
    override var WEB_URL: String = ""

    private val allRules: List<SourceRule>
        get() = SourceRuleRepository.getAll()

    companion object {
        /** 单个 CSS 源抓取的最长等待时间，超时直接跳过，避免一个坏源拖垮整体。 */
        private const val PER_SOURCE_TIMEOUT_MS = 7_000L
        /**
         * 搜索场景的超时：WebView 冷启动首屏常 >7s，若仍套用 [PER_SOURCE_TIMEOUT_MS]
         * 会被直接取消、返回空集合——表现为「数据页测试有数据、聚合搜索却空」。
         * 这里对齐数据页测试的 30s，保证单源能完整加载。
         */
        private const val PER_SOURCE_SEARCH_TIMEOUT_MS = 30_000L
        /** 同时并发的 CSS 源数量上限，平衡速度与 WebView 资源占用。 */
        private const val MAX_CONCURRENCY = 4
    }

    // ── 浏览类：始终次元城 API ──
    override suspend fun getHomeData(): List<HomeBean> = CycanimeSource.getHomeData()
    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> = CycanimeSource.getWeekData()
    override suspend fun getFilterData(
        zoneId: Int,
        tag: String?,
        year: Int?,
        orderBy: String?,
        region: String?,
        type: String?,
        status: String?,
        page: Int,
    ): FilterPage<AnimeBean> = CycanimeSource.getFilterData(zoneId, tag, year, orderBy, region, type, status, page)
    override suspend fun getRanking(): Map<String, List<AnimeBean>> = CycanimeSource.getRanking()

    // ── 详情页 ──
    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val isCycani = detailUrl.contains("cycani", ignoreCase = true)
        if (isCycani) {
            val cyMeta = runCatching { CycanimeSource.getAnimeDetail(detailUrl) }.getOrNull()
            val cssResult = findCssDetailByTitle(cyMeta?.title ?: "")
            val cssDetail = cssResult?.first
            val cssRule = cssResult?.second
            return when {
                cyMeta != null && cssDetail != null && cssRule != null ->
                    cyMeta.copy(channels = cssDetail.channels, sourceId = cssRule.id, sourceName = cssRule.name, iconUrl = cssRule.iconUrl)
                cyMeta != null -> cyMeta
                cssDetail != null -> cssDetail // 已带 source 字段
                else -> AnimeDetailBean(title = cyMeta?.title ?: "", imgUrl = "", desc = "", relatedAnimes = emptyList())
            }
        }
        // 规则站 URL（来自某 CSS 搜索结果）：优先按 host 精准匹配对应规则直达，最准最快；
        // 匹配到的规则若解析失败（返回空/标题为空），继续并发兜底其他规则，避免直接空白
        val host = hostOf(detailUrl)
        val byHost = allRules.firstOrNull { hostOf(it) == host }
        if (byHost != null) {
            val detail = RuleExecutor.getAnimeDetail(byHost, detailUrl)
            if (detail != null && detail.title.isNotBlank())
                return detail.copy(sourceId = byHost.id, sourceName = byHost.name, iconUrl = byHost.iconUrl)
        }
        // 并发尝试所有规则，取首个能解析出有效标题的详情
        "getAnimeDetail(聚合): detailUrl host=$host byHost=${byHost?.name ?: "∅"} → 回退并发查全部 CSS 规则(${allRules.size})".log("RuleAggregate", "getAnimeDetail")
        return runParallelFirstNotNull(allRules) { rule ->
            RuleExecutor.getAnimeDetail(rule, detailUrl)?.takeIf { it.title.isNotBlank() }?.let { it to rule }
        }?.let { (bean, rule) ->
            bean.copy(sourceId = rule.id, sourceName = rule.name, iconUrl = rule.iconUrl)
        } ?: AnimeDetailBean(title = "", imgUrl = "", desc = "", relatedAnimes = emptyList())
    }

    // ── 搜索：并发聚合全部 CSS 规则 ──
    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        // CSS 聚合源不翻页：首屏即全部结果。page>1 视为已到底，直接返回空，
        // 避免 SearchPagingSource 因忽略 page 而无限重复同一批结果（分页 bug 修复）。
        if (page > 1) return emptyList()
        val rules = allRules
        // animeko：onlySupportsPlayers 不含当前平台的源直接跳过，省一次无谓请求
        val usable = rules.filter { RuleExecutor.supportsCurrentPlayer(it) }
        val collected = runParallel(usable, PER_SOURCE_SEARCH_TIMEOUT_MS) { rule ->
            runCatching {
                RuleExecutor.toAnimeBeans(RuleExecutor.searchSubjects(rule, listOf(query)))
                    .map { it.copy(sourceName = rule.name, sourceId = rule.id) }
            }.getOrDefault(emptyList())
        }.flatten()
        // 去重：仅按标准化标题，同番跨源(URL 各异)合并为一张卡片，保留首次出现者(含其 sourceId)
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<AnimeBean>()
        for (bean in collected) {
            if (bean.title.isBlank()) continue
            val key = normalizeKey(bean.title)
            if (seen.add(key)) out.add(bean)
        }
        return out
    }

    /**
     * 流式搜索（对标 animeko「逐源到齐即显示」）：对全部 CSS 规则各自发起一条 chunk 流，
     * 规则完成即发射其 [SearchChunk]，由上游 [merge] 合并成「谁先回谁先显示」的快照。
     * 单条规则失败/超时只跳过自身（done 仍置 true、结果为空），不影响其它源。
     */
    fun searchChunks(query: String): Flow<SearchChunk> {
        val rules = allRules.filter { RuleExecutor.supportsCurrentPlayer(it) }
        val ruleFlows = rules.map { rule -> ruleChunkFlow(rule, query) }
        return merge(*ruleFlows.toTypedArray())
    }

    private fun ruleChunkFlow(rule: SourceRule, query: String): Flow<SearchChunk> = flow {
        val name = rule.name.ifBlank { rule.search.baseUrl }
        emit(SearchChunk(name, emptyList(), done = false))
        val items = runCatching {
            withTimeoutOrNull(PER_SOURCE_SEARCH_TIMEOUT_MS) {
                RuleExecutor.toAnimeBeans(RuleExecutor.searchSubjects(rule, listOf(query)))
                    .map { it.copy(sourceName = rule.name, sourceId = rule.id) }
            } ?: emptyList()
        }.getOrDefault(emptyList())
        emit(SearchChunk(name, items, done = true))
    }

    // ── 播放源：并发遍历全部 CSS 规则，首个成功匹配者胜出 ──
    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val rules = allRules
        if (rules.isEmpty()) return VideoBean(videoUrl = "", headers = emptyMap())
        // 优先把 host 命中的源排前面（最可能是该集所属源），其余兜底
        val host = hostOf(episodeUrl)
        val ordered = if (host != null) {
            rules.filter { hostOf(it) == host } + rules.filter { hostOf(it) != host }
        } else {
            rules
        }
        val result = runParallelFirstNotNull(ordered) { rule ->
            val m = RuleExecutor.matchVideoDetailed(rule, episodeUrl)
            m.takeIf { it.url.isNotBlank() }?.let { rule to it }
        }
        return if (result != null) {
            val (rule, m) = result
            VideoBean(
                videoUrl = m.url,
                headers = m.headers,
                sourceId = rule.id,
                sourceName = rule.name,
                iconUrl = rule.iconUrl,
            )
        } else VideoBean(videoUrl = "", headers = emptyMap())
    }

    /**
     * 在所有 CSS 规则中按标题搜索并取最匹配条目，返回其详情（含剧集）。
     *
     * 对齐 animeko web 源：**不按标题相似度硬筛**（`createFiltersForSubject` 返回空）。
     * 跨规则聚合时按匹配度降序 + [SourceRule.tier] 升序排序，逐个尝试下钻详情，
     * 直到拿到有效结果——不再设 ≥80/≥50 的门槛，避免正确条目因相似度偏低被丢弃。
     */
    private suspend fun findCssDetailByTitle(title: String): Pair<AnimeDetailBean, SourceRule>? {
        if (title.isBlank()) return null
        val rules = allRules.filter { RuleExecutor.supportsCurrentPlayer(it) }
        if (rules.isEmpty()) return null

        val candidates = runParallel(rules) { rule ->
            RuleExecutor.searchSubjects(rule, listOf(title)).map { entry -> rule to entry }
        }.flatten().filter { it.second.title.isNotBlank() }
        if (candidates.isEmpty()) return null

        val ranked = candidates
            .map { (rule, entry) -> Triple(rule, entry, RuleExecutor.matchRate(entry.title, title)) }
            .sortedWith(compareByDescending<Triple<SourceRule, RuleExecutor.SearchResult, Int>> { it.third }
                .thenBy { it.first.tier })
            .take(5)
        for ((rule, entry) in ranked) {
            val detail = runCatching { RuleExecutor.getAnimeDetail(rule, entry.url) }.getOrNull()
            if (detail != null && detail.title.isNotBlank()) return detail to rule
        }
        return null
    }

    // ── 并发工具 ──

    /**
     * 并发执行 [block] 对 [items]，单任务超时/异常自动跳过，返回所有成功结果（顺序展开）。
     * [MAX_CONCURRENCY] 限制同时进行的协程数，[PER_SOURCE_TIMEOUT_MS] 限制单任务耗时。
     */
    private suspend fun <T, R> runParallel(
        items: List<T>,
        timeoutMs: Long = PER_SOURCE_TIMEOUT_MS,
        block: suspend (T) -> R,
    ): List<R> =
        coroutineScope {
            val sem = Semaphore(MAX_CONCURRENCY)
            items.map { item ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        withTimeoutOrNull(timeoutMs) { block(item) }
                    }
                }
            }.awaitAll().filterNotNull()
        }

    /** 并发执行，返回首个非 null 结果（超时/异常视为 null，未命中任务被取消）。 */
    private suspend fun <T, R> runParallelFirstNotNull(items: List<T>, block: suspend (T) -> R?): R? =
        coroutineScope {
            val sem = Semaphore(MAX_CONCURRENCY)
            items.map { item ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) { block(item) }
                    }
                }
            }.firstNotNullOfOrNull { it.await() }
        }

    private fun hostOf(rule: SourceRule): String? =
        hostOf(rule.search.baseUrl) ?: hostOf(rule.search.searchUrl)

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.lowercase()

    private fun normalizeKey(title: String): String =
        title.lowercase().replace(Regex("\\s+"), "")

    /** 简易编辑距离，用于标题相似度匹配。 */
    private fun levenshtein(lhs: String, rhs: String): Int {
        if (lhs == rhs) return 0
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length
        val dp = IntArray(rhs.length + 1) { it }
        for (i in 1..lhs.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..rhs.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (lhs[i - 1] == rhs[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[rhs.length]
    }
}
