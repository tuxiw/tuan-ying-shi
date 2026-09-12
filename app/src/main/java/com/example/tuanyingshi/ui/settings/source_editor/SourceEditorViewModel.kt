package com.example.tuanyingshi.ui.settings.source_editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.util.source_rule.BlockReason
import com.example.tuanyingshi.util.source_rule.MatchTag
import com.example.tuanyingshi.util.source_rule.RuleEngineKind
import com.example.tuanyingshi.util.source_rule.RuleExecutor
import com.example.tuanyingshi.util.source_rule.SourceRule
import com.example.tuanyingshi.util.source_rule.SourceRuleRepository
import com.example.tuanyingshi.util.source_rule.TestIssue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class SourceEditorViewModel @Inject constructor() : ViewModel() {

    private val _rule = MutableStateFlow(SourceRule())
    val rule: StateFlow<SourceRule> = _rule.asStateFlow()

    private val _testKeyword = MutableStateFlow("樱 Trick")
    val testKeyword: StateFlow<String> = _testKeyword.asStateFlow()

    private val _testEpisode = MutableStateFlow("1")
    val testEpisode: StateFlow<String> = _testEpisode.asStateFlow()

    private val _testResults = MutableStateFlow<TestResults?>(null)
    val testResults: StateFlow<TestResults?> = _testResults.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /**
     * 当前测试任务的 token（单调递增）。对齐 animeko `SelectorMediaSourceTester` 的
     * `mapLatest` / `FlowRestarter`：再次点击「运行测试」时取消上一轮尚未完成的测试，
     * 只让最新一轮更新 UI。否则重复点击会让多个 WebView 抓取的协程在主线程叠加，互相干扰。
     */
    private val testToken = AtomicInteger(0)
    private var testJob: Job? = null

    fun loadRule(id: String?, defaultEngine: RuleEngineKind? = null) {
        if (id == null) {
            // 新增：按入口指定的默认引擎（如从「添加 XPATH 源」进入时默认 XPath）
            _rule.value = SourceRule(
                engine = defaultEngine ?: RuleEngineKind.Css,
                chapterEngine = defaultEngine ?: RuleEngineKind.Css,
            )
            return
        }
        SourceRuleRepository.getById(id)?.let { _rule.value = it }
    }

    fun updateName(value: String) = _rule.update { it.copy(name = value) }
    fun updateIconUrl(value: String) = _rule.update { it.copy(iconUrl = value) }

    /** 切换解析引擎（搜索 + 剧集一并跟随），仅在新增 / 编辑界面手动设置时调用。 */
    fun updateEngine(kind: RuleEngineKind) =
        _rule.update { it.copy(engine = kind, chapterEngine = kind) }

    fun updateSearch(block: SourceRule.SearchStep.() -> SourceRule.SearchStep) =
        _rule.update { it.copy(search = block(it.search)) }

    fun updateEpisodes(block: SourceRule.EpisodesStep.() -> SourceRule.EpisodesStep) =
        _rule.update { it.copy(episodes = block(it.episodes)) }

    fun updateVideo(block: SourceRule.VideoStep.() -> SourceRule.VideoStep) =
        _rule.update { it.copy(video = block(it.video)) }

    fun updateFilters(block: SourceRule.FilterStep.() -> SourceRule.FilterStep) =
        _rule.update { it.copy(filters = block(it.filters)) }

    fun updateMarks(block: SourceRule.MarkStep.() -> SourceRule.MarkStep) =
        _rule.update { it.copy(marks = block(it.marks)) }

    fun updateHeaders(block: SourceRule.HeaderStep.() -> SourceRule.HeaderStep) =
        _rule.update { it.copy(headers = block(it.headers)) }

    fun updateXpath(block: SourceRule.XpathStep.() -> SourceRule.XpathStep) =
        _rule.update { it.copy(xpath = block(it.xpath)) }

    fun updateTestKeyword(value: String) { _testKeyword.value = value }
    fun updateTestEpisode(value: String) { _testEpisode.value = value }

    fun save(): Boolean {
        val current = _rule.value
        val success = if (SourceRuleRepository.getById(current.id) != null) {
            SourceRuleRepository.update(current)
        } else {
            val added = SourceRuleRepository.add(current)
            _rule.value = added
            true
        }
        _saved.value = success
        return success
    }

    fun runTest() {
        // 取消上一轮未完成的测试，避免主线程上多个 WebView 抓取协程叠加（animeko mapLatest 语义）
        testJob?.cancel()
        val token = testToken.incrementAndGet()
        testJob = viewModelScope.launch {
            _isTesting.value = true
            _testResults.value = null
            try {
                val current = _rule.value
                val keyword = _testKeyword.value
                val episodeNumber = _testEpisode.value.toIntOrNull()

                // ── 步骤 1：搜索条目（对齐 animeko 测试器，区分「被拦」与「无结果」）──
                val sr = RuleExecutor.testSearchEntries(current, keyword)
                val entries = sr.entries.map { result ->
                    EntryPresentation(
                        result = result,
                        tags = listOf(
                            // 标题匹配度（0..100），≥50 视为命中；与 animeko 的 matchRate 阈值一致
                            MatchTag("标题", isMatch = RuleExecutor.matchRate(result.title, keyword) >= 50),
                            MatchTag("链接", isMissing = result.url.isBlank()),
                        ),
                    )
                }
                // 被拦 = 网络 / WAF 侧问题；空结果（无 blockReason）只是「站点没这部番」，不算错误
                val entryIssue: TestIssue? = sr.blockReason?.let { reason ->
                    TestIssue.Blocked(
                        reason,
                        "搜索页加载失败或被拦截（${blockHint(reason)}）。请确认「Base URL / 搜索链接」正确，且站点未触发 WAF；必要时调大「请求间隔」后重试。",
                    )
                }

                val targetEntry = sr.entries.firstOrNull()

                // ── 步骤 2：搜索剧集（Blocked vs InvalidConfig 二分）──
                var episodeIssue: TestIssue? = null
                val groups = if (targetEntry != null) {
                    val res = RuleExecutor.searchEpisodes(current, targetEntry.url, episodeNumber)
                    when {
                        !res.loaded || res.blockReason != null -> {
                            val reason = res.blockReason ?: BlockReason.WAF
                            episodeIssue = TestIssue.Blocked(
                                reason,
                                "详情页加载失败或被拦截（${blockHint(reason)}）。请确认「Base URL」「User-Agent / Referer / Cookie」填写正确。",
                            )
                            emptyList()
                        }
                        res.groups.isEmpty() -> {
                            episodeIssue = TestIssue.InvalidConfig(
                                "详情页已加载，但剧集选择器未匹配到任何元素：请检查「线路分组」「线路名称 Selector」「剧集面板 Selector」「剧集列表 Selector」是否与站点 DOM 一致。",
                            )
                            emptyList()
                        }
                        else -> res.groups
                    }
                } else emptyList()

                val targetEpisode = groups.firstOrNull()?.episodes?.firstOrNull()
                // 逐条目的字段级校验标签（对齐 animeko MatchTag）
                val episodeTags = targetEpisode?.let { ep ->
                    buildList {
                        if (ep.number == null) add(MatchTag("EP 序号", isMissing = true))
                        else add(MatchTag("EP 序号", isMatch = true))
                        when {
                            ep.url.isBlank() -> add(MatchTag("播放地址", isMissing = true))
                            !ep.url.startsWith("http") -> add(MatchTag("播放地址", isMatch = false))
                            else -> add(MatchTag("播放地址", isMatch = true))
                        }
                    }
                }.orEmpty()

                // ── 步骤 3：匹配视频 ──
                var videoUrl = ""
                var videoIssue: TestIssue? = null
                if (targetEpisode != null) {
                    val block = RuleExecutor.pageBlockReason(current, targetEpisode.url)
                    if (block != null) {
                        videoIssue = TestIssue.Blocked(
                            block,
                            "播放页加载失败或被拦截（${blockHint(block)}）。请检查「播放时 HTTP 头」（Referer / Cookie）是否正确。",
                        )
                    } else {
                        videoUrl = RuleExecutor.matchVideo(current, targetEpisode.url)
                        if (videoUrl.isBlank()) {
                            videoIssue = TestIssue.InvalidConfig(
                                "播放页已加载，但未能拦截到视频直链：请检查「匹配视频链接正则」「启用嵌套链接」「视频匹配策略」。",
                            )
                        }
                    }
                } else {
                    videoIssue = TestIssue.InvalidConfig("无剧集，跳过视频匹配。")
                }

                // 仅最新一轮（token 未变）才回写 UI，被取消的旧轮直接丢弃
                if (token == testToken.get()) {
                    _testResults.value = TestResults(
                        entries = entries,
                        selectedEntry = targetEntry,
                        entryIssue = entryIssue,
                        groups = groups,
                        selectedEpisode = targetEpisode,
                        episodeIssue = episodeIssue,
                        episodeTags = episodeTags,
                        videoUrl = videoUrl,
                        videoIssue = videoIssue,
                    )
                }
            } catch (e: CancellationException) {
                return@launch // 被新一轮测试取消，不更新 UI
            } catch (e: Exception) {
                if (token == testToken.get()) {
                    _testResults.value = TestResults(error = e.message ?: "测试失败")
                }
            } finally {
                if (token == testToken.get()) _isTesting.value = false
            }
        }
    }

    /** [BlockReason] → 中文简述，用于测试结果的失败原因。 */
    private fun blockHint(reason: BlockReason): String = when (reason) {
        BlockReason.WAF -> "反爬 / WAF 拦截"
        BlockReason.RATE_LIMITED -> "请求过于频繁，触发限流"
        BlockReason.FORBIDDEN -> "403 禁止访问"
        BlockReason.NOT_FOUND -> "404 页面不存在"
        BlockReason.CAPTCHA -> "需要人机验证"
    }

    data class TestResults(
        val entries: List<EntryPresentation> = emptyList(),
        val selectedEntry: RuleExecutor.SearchResult? = null,
        /** 步骤 1 验证问题（null = 成功 / 仅无结果）；区分「搜索页被拦」与「无结果」。 */
        val entryIssue: TestIssue? = null,
        val groups: List<RuleExecutor.EpisodeGroup> = emptyList(),
        val selectedEpisode: RuleExecutor.EpisodeItem? = null,
        /** 步骤 2 验证问题（null = 成功）。 */
        val episodeIssue: TestIssue? = null,
        /** 选中剧集的字段级校验标签。 */
        val episodeTags: List<MatchTag> = emptyList(),
        val videoUrl: String = "",
        /** 步骤 3 验证问题（null = 成功）。 */
        val videoIssue: TestIssue? = null,
        val error: String? = null,
    )

    /** 搜索条目的展示包装：原始结果 + 字段级校验标签（对齐 animeko 的 Presentation）。 */
    data class EntryPresentation(
        val result: RuleExecutor.SearchResult,
        val tags: List<MatchTag>,
    )

}
