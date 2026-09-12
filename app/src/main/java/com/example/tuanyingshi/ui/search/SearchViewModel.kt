package com.example.tuanyingshi.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.repository.ContentFilterRepository
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.repository.AnimeRepository
import com.example.tuanyingshi.util.SearchStreamState
import com.example.tuanyingshi.util.SEARCH_PAGE_SIZE
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.isNsfw
import com.example.tuanyingshi.util.source_rule.RuleExecutor
import com.example.tuanyingshi.util.source_rule.SourceRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页搜索 ViewModel —— 流式逐源显示（对齐 animeko）。
 *
 * - query 经 [debounce](350ms) + 回车强制信号([onSearch]) 双入口驱动搜索；新词会取消在途搜索（flatMapLatest）。
 * - 搜索走 [animeRepository.getSearchStream]：各数据源（聚合源=各 CSS 规则）逐源返回，
 *   **先到的源立刻显示**，慢源（WebView 冷启动）完成后再补，不再等最慢源拖住整页。
 * - [contentFilter] 在最终 UI 状态上合并（NSFW / 隐藏看过）。
 * - API 类源（次元城 / 嘶哩 / Girigiri）支持「加载更多」翻页，[loadMore] 追加下一页（带 TTL 缓存）。
 */
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val contentFilter: ContentFilterRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    /** 回车 / IME 搜索键的强制搜索信号：与输入即搜并存，但绕过 350ms 防抖、立即触发。 */
    private val _forceSearch = MutableStateFlow("")

    /** 内部原始状态（未应用内容过滤），与 [contentFilter] combine 后产出 [uiState]。 */
    private val _rawState = MutableStateFlow(RawSearchState())

    /** 当前 API 翻页页码（仅 API 类源有）。 */
    private var apiPage = 1

    /** 最终呈现给 UI 的状态（已应用内容过滤）。 */
    val uiState: StateFlow<SearchUiState> = combine(_rawState, contentFilter.contentFilterFlow) { raw, filter ->
        val (blockNsfw, hidden) = filter
        val results = raw.rawResults.filter { !(blockNsfw && it.isNsfw()) && it.detailUrl !in hidden }
        SearchUiState(
            query = raw.query,
            results = results,
            pending = raw.pending,
            isSearching = raw.isSearching,
            page1Loaded = raw.page1Loaded,
            canLoadMore = raw.canLoadMore,
            loadedAll = raw.loadedAll,
            isLoadingMore = raw.isLoadingMore,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    init {
        viewModelScope.launch {
            merge(_query.debounce(350), _forceSearch)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .flatMapLatest { q -> runSearch(q) }
                .collect { _rawState.value = it }
        }
    }

    private fun runSearch(query: String): Flow<RawSearchState> = flow {
        val mode = SourceHolder.currentSourceMode
        val expected = expectedSourceNames(mode)
        apiPage = 1
        // 立即清空旧结果并展示「正在搜索」的源清单，不等任何网络
        emit(RawSearchState(query = query, pending = expected, isSearching = true))
        animeRepository.getSearchStream(query, mode).collect { snap: SearchStreamState ->
            val results = snap.items.map { it.toAnime() }
            val pendingNow = snap.pending
            emit(
                RawSearchState(
                    query = query,
                    rawResults = results,
                    pending = pendingNow,
                    isSearching = pendingNow.isNotEmpty(),
                    page1Loaded = pendingNow.isEmpty(),
                    canLoadMore = mode in API_PAGINATED_MODES && pendingNow.isEmpty(),
                )
            )
        }
    }

    /** 「加载更多」：仅 API 类源有效，追加下一页（带 TTL 缓存）。CSS / 聚合源无此能力。 */
    fun loadMore() {
        val cur = _rawState.value
        if (!cur.canLoadMore || cur.isLoadingMore || cur.query.isBlank()) return
        viewModelScope.launch {
            _rawState.update { it.copy(isLoadingMore = true) }
            val mode = SourceHolder.currentSourceMode
            val nextPage = apiPage + 1
            val pageItems = runCatching {
                animeRepository.getSearchPage(cur.query, nextPage, mode)
            }.getOrDefault(emptyList())
            apiPage = nextPage
            _rawState.update { s ->
                val merged = (s.rawResults + pageItems).distinctBy { it.detailUrl }
                s.copy(
                    rawResults = merged,
                    isLoadingMore = false,
                    canLoadMore = pageItems.size >= SEARCH_PAGE_SIZE,
                    loadedAll = pageItems.size < SEARCH_PAGE_SIZE,
                )
            }
        }
    }

    /** 当前搜索涉及的源名清单（用于「正在搜索」提示）。聚合源=各 CSS 规则名。 */
    private fun expectedSourceNames(mode: SourceMode): List<String> {
        return if (mode == SourceMode.Rule && SourceHolder.currentSourceId == SourceHolder.EXTERNAL_CSS_SOURCE_ID) {
            SourceRuleRepository.getAll()
                .filter { RuleExecutor.supportsCurrentPlayer(it) }
                .map { it.name.ifBlank { it.search.baseUrl } }
        } else {
            listOf(SourceHolder.getResourceSource(SourceHolder.currentSourceId)?.name ?: mode.name)
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }

    /** 回车 / IME 搜索键：立即触发搜索（绕过防抖）。键盘收起由 TextField 的 keyboardActions 处理。 */
    fun onSearch() {
        _forceSearch.value = _query.value.trim()
    }

    companion object {
        /** 支持翻页（「加载更多」）的数据源类型。 */
        private val API_PAGINATED_MODES = setOf(SourceMode.Cycanime, SourceMode.Silisili, SourceMode.Girigiri)
    }
}

/** 内部原始搜索状态（未应用内容过滤）。 */
private data class RawSearchState(
    val query: String = "",
    val rawResults: List<Anime> = emptyList(),
    val pending: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val page1Loaded: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadedAll: Boolean = false,
    val isLoadingMore: Boolean = false,
)

/** 最终呈现给 UI 的搜索状态（已应用内容过滤）。 */
data class SearchUiState(
    val query: String = "",
    val results: List<Anime> = emptyList(),
    val pending: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val page1Loaded: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadedAll: Boolean = false,
    val isLoadingMore: Boolean = false,
)
