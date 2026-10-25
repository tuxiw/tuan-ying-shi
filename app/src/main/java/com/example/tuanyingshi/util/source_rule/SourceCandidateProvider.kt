package com.example.tuanyingshi.util.source_rule

import com.example.tuanyingshi.domain.model.SourceCandidate
import com.example.tuanyingshi.domain.model.SourceQueryState
import com.example.tuanyingshi.domain.model.SourceQueryStatus
import com.example.tuanyingshi.util.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

private const val LOG_TAG = "SourceCandidate"

/**
 * 为一个番剧标题构建「所有可用 CSS 规则源」的候选列表（animeko MediaSelector 的 WEB 列）：
 * 每个 [SourceCandidate] = 一个源 + 它的全部线路（channels）。
 * 用于详情页「数据源」与播放器「换源」选择器——让用户可以像 animeko 那样，
 * 在同一部番的不同 CSS 源之间切换并挑选线路。
 */
object SourceCandidateProvider {
    private val cache = ConcurrentHashMap<String, Cached>()
    private data class Cached(
        val value: List<SourceCandidate>,
        val states: List<SourceQueryState>,
        val queried: Int,
        val total: Int,
    )

    /** 同标题查询去重：查询进行中再次打开选择器，等待复用同一次查询而非并发重查。 */
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * 缓存策略：**进程内长期有效**（同一标题不重复搜索；「切换番剧」= 标题变化 = 自然重新搜索）。
     * 仅在用户主动刷新（刷新按钮 / 编辑查询请求）时通过 [invalidate] 失效。
     */
    /** 单源搜索+详情+取线路的超时（避免某个慢源/卡死的 WebView 拖垮整个选择器）。 */
    private const val PER_SOURCE_TIMEOUT_MS = 20_000L
    /** 同时并发查询的 CSS 源数量上限（避免一次性起太多 WebView 拖垮设备）。 */
    private const val BUILD_MAX_CONCURRENCY = 6

    /**
     * 为标题构建所有可用 CSS 源候选（animeko MediaSelector 的 WEB 列）。
     *
     * 设计要点（修复「换源/数据源选择器为空」）：
     * 1. **当前源兜底**：[current] 为详情/播放页已成功加载的源，已知可用，直接放入，
     *    保证选择器**至少**有 1 个源，不会因其它源全失败而显示「没有可用的 CSS 数据源」。
     * 2. **渐进式填充**：每搜到 1 个源就通过 [onUpdate] 立即回传，列表边搜边显示，
     *    不再等全部 17 个源跑完才一次性赋值（旧实现会卡在空列表很久）。
     * 3. **单源容错**：每个源用独立 try/catch + 超时包裹，单个源异常/卡死不影响其余源。
     * 4. **查询进度**：每个源的状态变化通过 [onProgress] 回传（已查询数/总数 + 各源状态），
     *    供「详细模式」展示「已查询 x/x 数据源」与逐源统计、验证警告。
     *
     * @param title 主搜索名
     * @param current 当前正在使用的源（已知可用）。为空表示当前非 CSS 源，仅依赖搜索补全。
     * @param aliases 次要搜索名（可选，与主搜索名一起传给各源搜索；源侧按匹配率取最优）。
     * @param onUpdate 每新增/刷新候选时回调（直接写入 StateFlow 即可），默认无操作。
     * @param onProgress 查询进度回调（states=全部源状态快照，queried=已完成数，total=总数）。
     * @return 全部候选（已按 tier 排序），供调用方直接使用。
     */
    suspend fun buildFor(
        title: String,
        current: SourceCandidate? = null,
        aliases: List<String> = emptyList(),
        onUpdate: (List<SourceCandidate>) -> Unit = {},
        onProgress: (states: List<SourceQueryState>, queried: Int, total: Int) -> Unit = { _, _, _ -> },
        /** 找到足够可用数据（当前源 + 至少 1 个可解析的 CSS 源）即结束扫描，避免慢源拖累（播放器自动换源用）。 */
        stopWhenEnough: Boolean = false,
    ): List<SourceCandidate> {
        if (title.isBlank()) {
            val fallback = current?.takeIf { it.sourceId.isNotBlank() && it.channels.isNotEmpty() }
                ?.let { listOf(it) } ?: emptyList()
            onUpdate(fallback)
            return fallback
        }
        val lock = locks.computeIfAbsent(title) { Mutex() }
        return lock.withLock {
            // 命中缓存：直接回放候选与查询进度，不重新搜索（同番剧重复打开选择器零开销）
            cache[title]?.let { cached ->
                onUpdate(cached.value)
                onProgress(cached.states, cached.queried, cached.total)
                return@withLock cached.value
            }

            val keywords = (listOf(title) + aliases).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        // 当前源优先放入：详情/播放页已加载成功，是「确定可用」的源
        val collected = mutableListOf<SourceCandidate>()
        current?.takeIf { it.sourceId.isNotBlank() && it.channels.isNotEmpty() }?.let {
            collected.add(it)
            "buildFor: 放入当前源 ${it.sourceName}(${it.sourceId}) 线路=${it.channels.size}".log(LOG_TAG, "buildFor")
        }
        onUpdate(collected.toList())

        val rules = SourceRuleRepository.getVisible().filter { RuleExecutor.supportsCurrentPlayer(it) }
        val tiers = rules.associate { it.id to (it.tier ?: Int.MAX_VALUE) }
        "buildFor: title=$title 候选源数=${rules.size} 已置当前源=${collected.size}".log(LOG_TAG, "buildFor")

        // 查询状态表：当前源直接记为成功，其余初始化为「查询中」
        val states = mutableListOf<SourceQueryState>()
        current?.takeIf { it.sourceId.isNotBlank() }?.let {
            states.add(
                SourceQueryState(
                    ruleId = it.sourceId,
                    name = it.sourceName,
                    iconUrl = it.iconUrl,
                    status = SourceQueryStatus.Ok,
                    episodeCount = it.channels.values.sumOf { ch -> ch.size },
                ),
            )
        }
        rules.filter { it.id != current?.sourceId }.forEach { rule ->
            states.add(SourceQueryState(rule.id, rule.name, rule.iconUrl))
        }
        var queried = if (current?.sourceId.isNullOrBlank()) 0 else 1
        val total = states.size
        /** 并发场景下保护 states / collected 的修改（多源协程同时回写状态）。 */
        val dataLock = Mutex()
        fun report() = onProgress(states.toList(), queried, total)
        report()

        suspend fun setState(ruleId: String, update: (SourceQueryState) -> SourceQueryState) {
            dataLock.withLock {
                val idx = states.indexOfFirst { it.ruleId == ruleId }
                if (idx >= 0) {
                    states[idx] = update(states[idx])
                    report()
                }
            }
        }

        // 已有足够数据即可结束的判定：当前源在 + 至少 1 个可解析 CSS 源（或没有当前源时至少 1 个）。
        // 满足即取消其余在途查询，实现「有数据就直接结束」，避免慢源/空源把选择器拖到几十秒。
        suspend fun enoughData(): Boolean = dataLock.withLock {
            (current != null && collected.size >= 2) || (current == null && collected.size >= 1)
        }

        val sem = Semaphore(BUILD_MAX_CONCURRENCY)
        lateinit var parentScope: CoroutineScope
        try {
            coroutineScope {
                parentScope = this
                rules.filter { it.id != current?.sourceId }.map { rule ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            // 已在别处拿到足够数据 → 跳过本源查询
                            if (stopWhenEnough && enoughData()) return@withPermit
                            try {
                                withTimeout(PER_SOURCE_TIMEOUT_MS) {
                                    val entry = RuleExecutor.searchSubjects(rule, keywords)
                                        .firstOrNull { it.title.isNotBlank() }
                                    if (entry == null) {
                                        "buildFor: [${rule.name}] 搜索无结果".log(LOG_TAG, "buildFor")
                                        markUnreachable(rule, ::setState, SourceQueryStatus.Empty)
                                        return@withTimeout
                                    }
                                    val bean = RuleExecutor.getAnimeDetail(rule, entry.url)
                                    if (bean == null || bean.title.isBlank()) {
                                        "buildFor: [${rule.name}] 详情为空/标题为空，跳过".log(LOG_TAG, "buildFor")
                                        markUnreachable(rule, ::setState, SourceQueryStatus.Failed)
                                        return@withTimeout
                                    }
                                    val detail = bean.toAnimeDetail()
                                        .copy(sourceId = rule.id, sourceName = rule.name, iconUrl = rule.iconUrl)
                                    if (detail.channels.isEmpty()) {
                                        "buildFor: [${rule.name}] 无线路，跳过".log(LOG_TAG, "buildFor")
                                        setState(rule.id) { it.copy(status = SourceQueryStatus.Empty) }
                                        return@withTimeout
                                    }
                                    // 加锁写入候选并取快照回传（与 setState 分开加锁，避免 Mutex 不可重入死锁）
                                    val snapshot = dataLock.withLock {
                                        collected.add(
                                            SourceCandidate(
                                                sourceId = detail.sourceId,
                                                sourceName = detail.sourceName,
                                                iconUrl = detail.iconUrl,
                                                channels = detail.channels,
                                                channelNames = detail.channelNames,
                                            ),
                                        )
                                        collected.toList()
                                    }
                                    onUpdate(snapshot)
                                    setState(rule.id) {
                                        it.copy(
                                            status = SourceQueryStatus.Ok,
                                            episodeCount = detail.channels.values.sumOf { ch -> ch.size },
                                        )
                                    }
                                    "buildFor: [${rule.name}] +1 线路=${detail.channels.size}".log(LOG_TAG, "buildFor")
                                }
                            } catch (e: Exception) {
                                "buildFor: [${rule.name}] 异常>${e.message}".log(LOG_TAG, "buildFor")
                                markUnreachable(rule, ::setState, SourceQueryStatus.Failed)
                            } finally {
                                queried++
                                report()
                                // 有数据即提前结束：取消其余在途查询
                                if (stopWhenEnough && enoughData()) parentScope.cancel()
                            }
                        }
                    }
                }.awaitAll()
            }
        } catch (_: CancellationException) {
            // 提前结束：把仍在「查询中/未查」的源标记为「已停止」，避免 UI 永久转圈
            queried = total
            dataLock.withLock {
                states.forEachIndexed { i, s ->
                    if (s.status == SourceQueryStatus.Querying || s.status == SourceQueryStatus.Pending) {
                        states[i] = s.copy(status = SourceQueryStatus.Stopped)
                    }
                }
            }
            report()
        }

        // 按 animeko MediaSourceTier 排序：tier 小者优先（与设置页顺序一致）
        val sorted = collected.sortedBy { tiers[it.sourceId] ?: Int.MAX_VALUE }
        // 空结果不缓存：可能是规则未就绪 / 网络异常导致的偶发空查，
        // 缓住会让选择器永远「秒空」；下次打开重新查询。
        if (sorted.isNotEmpty()) {
            cache[title] = Cached(sorted, states.toList(), total, total)
        }
        onUpdate(sorted)
        onProgress(states.toList(), total, total)
        "buildFor: 完成 共 ${sorted.size} 个候选源".log(LOG_TAG, "buildFor")
        sorted
        }
    }

    /**
     * 查询无结果 / 失败时的统一收口：若该源启用了反爬，再做一次轻量探测，
     * 命中验证页则标为「需要处理验证」（详细模式显示橙色警告，对齐参考 UI 文案），
     * 否则按 [fallback]（Empty / Failed）标记。
     */
    private suspend fun markUnreachable(
        rule: SourceRule,
        setState: suspend (String, (SourceQueryState) -> SourceQueryState) -> Unit,
        fallback: SourceQueryStatus,
    ) {
        val status = if (rule.antiCrawler.enabled &&
            runCatching { ResourceVerifyChecker.needsVerification(rule) }.getOrDefault(false)
        ) {
            SourceQueryStatus.NeedsVerify
        } else {
            fallback
        }
        setState(rule.id) { it.copy(status = status) }
    }

    /** 主动失效某个标题的缓存（切源成功后调用，保证下次打开看到最新线路）。 */
    fun invalidate(title: String) {
        cache.remove(title)
    }
}
