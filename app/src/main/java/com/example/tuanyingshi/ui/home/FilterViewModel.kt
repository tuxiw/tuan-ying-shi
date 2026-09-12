package com.example.tuanyingshi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.repository.ContentFilterRepository
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.repository.AnimeRepository
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SEARCH_PAGE_SIZE
import com.example.tuanyingshi.util.isNsfw
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 分类浏览筛选 ViewModel（Flow 分页版，对齐 SearchViewModel）。
 *
 * - 不再使用 Paging3（其 `pageEventFlow` 在 content-filter 流重发时会被重复 collect，触发
 *   "Attempt to collect twice from pageEventFlow" 崩溃）；改为手动分页的 [StateFlow]，
 *   由 UI 滚动到底部触发 [loadMore]。
 * - 筛选参数（zoneId / tag / year / orderBy）变化即重置为第 1 页重新拉取。
 * - 数据源（SourceHolder）切换时自动重载。
 * - 内容过滤（NSFW / 隐藏看过）在产出 [uiState] 时合并，不触发网络重拉。
 */
@HiltViewModel
class FilterViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val contentFilter: ContentFilterRepository,
) : ViewModel() {

    private data class FilterParams(
        val zoneId: Int = 1,
        val tag: String? = null,
        val year: Int? = null,
        val orderBy: String? = null,
    )

    private data class LoadStatus(val isLoading: Boolean, val isError: Boolean)

    private val _params = MutableStateFlow(FilterParams())
    private val _items = MutableStateFlow<List<Anime>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _isError = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _canLoadMore = MutableStateFlow(true)

    private val _loadStatus = combine(_isLoading, _isError) { l, e -> LoadStatus(l, e) }

    private var page = 1
    private var reloadJob: Job? = null
    private var moreJob: Job? = null
    /** 是否已触发过首次加载（防止 ensureLoaded 与 setZone/setFilters 重复拉取）。 */
    private var hasLoaded = false

    val uiState: StateFlow<FilterUiState> = combine(
        _items, _loadStatus, _isLoadingMore, _canLoadMore, contentFilter.contentFilterFlow,
    ) { arr ->
        val items = arr[0] as List<Anime>
        val status = arr[1] as LoadStatus
        val isLoadingMore = arr[2] as Boolean
        val canLoadMore = arr[3] as Boolean
        val filter = arr[4] as Pair<Boolean, Set<String>>
        val (blockNsfw, hidden) = filter
        val filtered = items.filter { !(blockNsfw && it.isNsfw()) && it.detailUrl !in hidden }
        FilterUiState(
            items = filtered,
            isLoading = status.isLoading,
            isLoadingMore = isLoadingMore,
            canLoadMore = canLoadMore && !status.isLoading,
            isEmpty = !status.isLoading && filtered.isEmpty() && !status.isError,
            isError = status.isError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterUiState())

    init {
        // 数据源切换后自动重载（跳过构造时的初始值）。
        viewModelScope.launch {
            SourceHolder.currentSourceIdFlow.drop(1).collect { reload() }
        }
    }

    fun setZone(zoneId: Int) {
        if (_params.value.zoneId == zoneId) return
        _params.value = _params.value.copy(zoneId = zoneId)
        hasLoaded = true
        reload()
    }

    fun setFilters(tag: String?, year: Int?, orderBy: String?) {
        val cur = _params.value
        if (cur.tag == tag && cur.year == year && cur.orderBy == orderBy) return
        _params.value = cur.copy(tag = tag, year = year, orderBy = orderBy)
        hasLoaded = true
        reload()
    }

    /**
     * 首次进入分类页的兜底加载，必须显式调用一次。
     *
     * 原因：默认筛选参数（zoneId=1、全部分类）与 [FilterParams] 的初始值**完全相同**，
     * 于是 `setZone(1)` 与 `setFilters(null, null, null)` 都会命中「参数未变」而提前 return，
     * 不会触发 [reload] —— 表现就是「TV番组」永远空白（而「剧场番组」因 zoneId=2 与默认值
     * 不同能正常触发加载，所以只有 TV番组 复现）。
     *
     * 幂等：已成功加载过（含 setZone/setFilters 触发的加载）则直接返回，切回 Tab 不会重复拉取；
     * 仅当上次加载失败（[_isError]）时才允许再拉一次，避免偶发网络失败后回到该 Tab 一直空白。
     */
    fun ensureLoaded() {
        if (_isLoading.value) return
        if (hasLoaded && !_isError.value) return
        hasLoaded = true
        reload()
    }

    /** 重新拉取第 1 页（zone / 筛选 / 切源 变化时调用）。 */
    private fun reload() {
        reloadJob?.cancel()
        moreJob?.cancel()
        page = 1
        reloadJob = viewModelScope.launch {
            _isLoading.value = true
            _isError.value = false
            _canLoadMore.value = true
            _items.value = emptyList()
            val p = _params.value
            var failed = false
            val list = runCatching {
                animeRepository.getFilterPage(
                    zoneId = p.zoneId,
                    tag = p.tag,
                    year = p.year,
                    orderBy = p.orderBy,
                    page = 1,
                    mode = SourceHolder.currentSourceMode,
                )
            }.onFailure { failed = true }.getOrDefault(emptyList())
            page = 1
            _items.value = list
            _canLoadMore.value = list.isNotEmpty()
            _isError.value = failed && list.isEmpty()
            _isLoading.value = false
        }
    }

    /** 加载下一页（滚动到底部时调用），带防重入与末页判定。 */
    fun loadMore() {
        if (_isLoading.value || _isLoadingMore.value || !_canLoadMore.value) return
        val p = _params.value
        val nextPage = page + 1
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            _isLoadingMore.value = true
            var failed = false
            val list = runCatching {
                animeRepository.getFilterPage(
                    zoneId = p.zoneId,
                    tag = p.tag,
                    year = p.year,
                    orderBy = p.orderBy,
                    page = nextPage,
                    mode = SourceHolder.currentSourceMode,
                )
            }.onFailure { failed = true }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                page = nextPage
                _items.value = (_items.value + list).distinctBy { it.detailUrl }
                _canLoadMore.value = list.size >= SEARCH_PAGE_SIZE
            } else {
                // 空页：正常末页则停止，失败则保留可重试
                _canLoadMore.value = failed
            }
            _isLoadingMore.value = false
        }
    }
}

/** 分类浏览呈现给 UI 的状态（已应用内容过滤）。 */
data class FilterUiState(
    val items: List<Anime> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val isEmpty: Boolean = false,
    val isError: Boolean = false,
)
