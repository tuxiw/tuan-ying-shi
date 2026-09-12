package com.example.tuanyingshi.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.repository.AnimeRepository
import com.example.tuanyingshi.data.repository.ContentFilterRepository
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.filterContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RankingViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val contentFilter: ContentFilterRepository,
) : ViewModel() {

    /** 排行榜当前分类（仅用于标签高亮与初始页）。 */
    private val _category = MutableStateFlow(RankingCategory.TV)
    val category: StateFlow<RankingCategory> = _category.asStateFlow()

    /** 全量榜单（原始，未过滤），按分类名映射（如 TV番组 / 剧场番组）。 */
    private val _ranking = MutableStateFlow<Map<String, List<Anime>>>(emptyMap())

    /** 榜单（已按 NSFW 屏蔽 / 隐藏看过与抛弃 过滤）。 */
    val ranking: StateFlow<Map<String, List<Anime>>> = combine(
        _ranking,
        contentFilter.nsfwBlock,
        contentFilter.hiddenDetailUrls,
    ) { map, block, hidden ->
        map.mapValues { (_, list) -> list.filterContent(block, hidden) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 当前数据源（StateFlow），供页面在切源后触发按需刷新。 */
    val currentSourceId: StateFlow<SourceMode> = SourceHolder.sourceModeFlow

    /** 下拉刷新进行中（不覆盖已有数据，仅驱动 RefreshIndicator）。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 上一次已加载数据的源；用于切源后按需刷新、避免同源重复加载。 */
    private var loadedMode: SourceMode? = null

    init {
        loadedMode = SourceHolder.currentSourceMode
        load()
    }

    /** 仅当当前数据源与上次加载的不同时才重新拉取（供页面在 sourceMode 变化时调用）。 */
    fun loadIfSourceChanged() {
        val current = SourceHolder.currentSourceMode
        if (loadedMode == current) return
        loadedMode = current
        load()
    }

    fun setCategory(c: RankingCategory) { _category.value = c }

    fun load() {
        viewModelScope.launch { fetchRanking() }
    }

    /** 下拉刷新：保留当前数据，仅显示刷新指示器，完成后收起。 */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            fetchRanking()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchRanking() {
        runCatching { animeRepository.getRanking(20) }
            .onSuccess { _ranking.value = it }
            .onFailure { _ranking.value = emptyMap() }
    }

    companion object {
        /** 按分类名取对应榜单，默认取前 12 条。 */
        fun itemsFor(cat: RankingCategory, all: Map<String, List<Anime>>, limit: Int = 12): List<Anime> {
            return all[cat.label].orEmpty().take(limit)
        }
    }
}

/**
 * 排行榜分类 tab：TV番组 / 剧场番组。
 */
enum class RankingCategory(val label: String) {
    TV("TV番组"),
    MOVIE("剧场番组"),
}
