package com.example.tuanyingshi.util.source_rule

import com.example.tuanyingshi.domain.model.SourceCandidate
import com.example.tuanyingshi.util.log
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
    private data class Cached(val value: List<SourceCandidate>, val time: Long)

    /** 候选结果缓存时长：5 分钟（同一番剧短时间内重复打开选择器无需重新搜索全部源）。 */
    private const val TTL_MS = 5 * 60_000L

    /** 单源搜索+详情+取线路的超时（避免某个慢源/卡死的 WebView 拖垮整个选择器）。 */
    private const val PER_SOURCE_TIMEOUT_MS = 20_000L

    /**
     * 为标题构建所有可用 CSS 源候选（animeko MediaSelector 的 WEB 列）。
     *
     * 设计要点（修复「换源/数据源选择器为空」）：
     * 1. **当前源兜底**：[current] 为详情/播放页已成功加载的源，已知可用，直接放入，
     *    保证选择器**至少**有 1 个源，不会因其它源全失败而显示「没有可用的 CSS 数据源」。
     * 2. **渐进式填充**：每搜到 1 个源就通过 [onUpdate] 立即回传，列表边搜边显示，
     *    不再等全部 17 个源跑完才一次性赋值（旧实现会卡在空列表很久）。
     * 3. **单源容错**：每个源用独立 try/catch + 超时包裹，单个源异常/卡死不影响其余源。
     *
     * @param title 番剧标题（搜索各源用）
     * @param current 当前正在使用的源（已知可用）。为空表示当前非 CSS 源，仅依赖搜索补全。
     * @param onUpdate 每新增/刷新候选时回调（直接写入 StateFlow 即可），默认无操作。
     * @return 全部候选（已按 tier 排序），供调用方直接使用。
     */
    suspend fun buildFor(
        title: String,
        current: SourceCandidate? = null,
        onUpdate: (List<SourceCandidate>) -> Unit = {},
    ): List<SourceCandidate> {
        if (title.isBlank()) {
            val fallback = current?.takeIf { it.sourceId.isNotBlank() && it.channels.isNotEmpty() }
                ?.let { listOf(it) } ?: emptyList()
            onUpdate(fallback)
            return fallback
        }
        val now = System.currentTimeMillis()
        cache[title]?.let { if (now - it.time < TTL_MS) { onUpdate(it.value); return it.value } }

        // 当前源优先放入：详情/播放页已加载成功，是「确定可用」的源
        val collected = mutableListOf<SourceCandidate>()
        current?.takeIf { it.sourceId.isNotBlank() && it.channels.isNotEmpty() }?.let {
            collected.add(it)
            "buildFor: 放入当前源 ${it.sourceName}(${it.sourceId}) 线路=${it.channels.size}".log(LOG_TAG, "buildFor")
        }
        onUpdate(collected.toList())

        val rules = SourceRuleRepository.getAll().filter { RuleExecutor.supportsCurrentPlayer(it) }
        val tiers = rules.associate { it.id to (it.tier ?: Int.MAX_VALUE) }
        "buildFor: title=$title 候选源数=${rules.size} 已置当前源=${collected.size}".log(LOG_TAG, "buildFor")

        rules.forEach { rule ->
            if (rule.id == current?.sourceId) return@forEach // 当前源已放入，跳过重复搜索
            try {
                withTimeout(PER_SOURCE_TIMEOUT_MS) {
                    val entry = RuleExecutor.searchSubjects(rule, listOf(title))
                        .firstOrNull { it.title.isNotBlank() }
                        ?: run {
                            "buildFor: [${rule.name}] 搜索无结果".log(LOG_TAG, "buildFor")
                            return@withTimeout
                        }
                    val bean = RuleExecutor.getAnimeDetail(rule, entry.url)
                        ?: run {
                            "buildFor: [${rule.name}] 详情为 null".log(LOG_TAG, "buildFor")
                            return@withTimeout
                        }
                    if (bean.title.isBlank()) {
                        "buildFor: [${rule.name}] 标题为空，跳过".log(LOG_TAG, "buildFor")
                        return@withTimeout
                    }
                    val detail = bean.toAnimeDetail()
                        .copy(sourceId = rule.id, sourceName = rule.name, iconUrl = rule.iconUrl)
                    if (detail.channels.isEmpty()) {
                        "buildFor: [${rule.name}] 无线路，跳过".log(LOG_TAG, "buildFor")
                        return@withTimeout
                    }
                    collected.add(
                        SourceCandidate(
                            sourceId = detail.sourceId,
                            sourceName = detail.sourceName,
                            iconUrl = detail.iconUrl,
                            channels = detail.channels,
                            channelNames = detail.channelNames,
                        ),
                    )
                    onUpdate(collected.toList())
                    "buildFor: [${rule.name}] +1 线路=${detail.channels.size}".log(LOG_TAG, "buildFor")
                }
            } catch (e: Exception) {
                "buildFor: [${rule.name}] 异常>${e.message}".log(LOG_TAG, "buildFor")
            }
        }

        // 按 animeko MediaSourceTier 排序：tier 小者优先（与设置页顺序一致）
        val sorted = collected.sortedBy { tiers[it.sourceId] ?: Int.MAX_VALUE }
        cache[title] = Cached(sorted, now)
        onUpdate(sorted)
        "buildFor: 完成 共 ${sorted.size} 个候选源".log(LOG_TAG, "buildFor")
        return sorted
    }

    /** 主动失效某个标题的缓存（切源成功后调用，保证下次打开看到最新线路）。 */
    fun invalidate(title: String) {
        cache.remove(title)
    }
}
