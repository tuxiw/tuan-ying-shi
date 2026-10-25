package com.example.tuanyingshi.ui.search.hanime1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Api
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Filter
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Video
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.util.log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hanime1 高级搜索 ViewModel（参考 Han1meViewer 的 HAdvancedSearch 体系）。
 *
 * - query 走 350ms 防抖；筛选条件（genre/sort/date/duration/tags/brands/broad）变化立即重搜；
 * - 搜索/翻页都直连 [Hanime1Api]（站点 /search 接口），不走通用多源聚合；
 * - 新搜索会取消在途请求，避免旧结果覆盖新结果。
 */
@HiltViewModel
@OptIn(FlowPreview::class)
class Hanime1SearchViewModel @Inject constructor() : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(Hanime1Filter.DEFAULT)
    val filter: StateFlow<Hanime1Filter> = _filter.asStateFlow()

    private val _results = MutableStateFlow<List<Hanime1Video>>(emptyList())
    val results: StateFlow<List<Hanime1Video>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * NSFW 内容屏蔽开关（Hanime1 全站成人内容）：开启时本页不发起任何搜索、清空结果，
     * 由 UI 展示屏蔽提示；关闭后自动按当前关键词/筛选重搜。
     */
    private val _blocked = MutableStateFlow(ContentPrefs.isNsfwBlocked())
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()

    private var page = 1
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            ContentPrefs.nsfwBlock.collect { blocked ->
                _blocked.value = blocked
                if (blocked) {
                    searchJob?.cancel()
                    _results.value = emptyList()
                    _hasMore.value = false
                    _isLoading.value = false
                    _error.value = null
                } else {
                    search(_query.value, _filter.value)
                }
            }
        }
        viewModelScope.launch {
            combine(
                _query.debounce(350),
                _filter,
            ) { q, f -> q to f }
                .collect { (q, f) -> if (!_blocked.value) search(q, f) }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }

    fun onFilterChange(f: Hanime1Filter) {
        _filter.value = f
    }

    /** 「加载更多」：追加下一页。 */
    fun loadMore() {
        val f = _filter.value
        if (_isLoadingMore.value || !_hasMore.value || _blocked.value) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            val nextPage = page + 1
            val items = runCatching {
                Hanime1Api.search(
                    query = _query.value,
                    page = nextPage,
                    genre = f.genre,
                    sort = f.sort,
                    broad = f.broad,
                    date = f.date,
                    duration = f.duration,
                    tags = f.tags,
                    brands = f.brands,
                )
            }.onFailure { it.log(TAG, "loadMore failed") }.getOrDefault(emptyList())
            page = nextPage
            _results.value = (_results.value + items).distinctBy { it.videoCode }
            _hasMore.value = items.size >= PAGE_SIZE
            _isLoadingMore.value = false
        }
    }

    private fun search(query: String, filter: Hanime1Filter) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            page = 1
            val items = runCatching {
                Hanime1Api.search(
                    query = query,
                    page = 1,
                    genre = filter.genre,
                    sort = filter.sort,
                    broad = filter.broad,
                    date = filter.date,
                    duration = filter.duration,
                    tags = filter.tags,
                    brands = filter.brands,
                )
            }.onFailure {
                it.log(TAG, "search failed")
                _error.value = "搜索失败：${it.message}"
            }.getOrDefault(emptyList())
            _results.value = items.distinctBy { it.videoCode }
            _hasMore.value = items.size >= PAGE_SIZE
            _isLoading.value = false
        }
    }

    companion object {
        private const val TAG = "Hanime1SearchVM"

        /** 站点每页条数（用于「还有更多」判定）。 */
        private const val PAGE_SIZE = 20
    }
}
