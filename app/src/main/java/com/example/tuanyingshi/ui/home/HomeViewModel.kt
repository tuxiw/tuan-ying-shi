package com.example.tuanyingshi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.HomeBanner
import com.example.tuanyingshi.domain.repository.AnimeRepository
import com.example.tuanyingshi.data.repository.ContentFilterRepository
import com.example.tuanyingshi.util.Resource
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页 UI 状态（单一对象，简化 Compose 端消费）。
 *
 * - loading/error 互斥；其余为业务字段。
 * - selectedCategory 不参与后端请求，仅做前端 Tab 切换。
 * - 首页数据来自 getHomeData()：每个 Home 区块按序映射为
 *   「热播推荐」2x2（首个区块）/「热播日漫推荐」3 列（次个区块）/ 全量 allAnime（分类浏览）。
 */
data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val selectedCategory: HomeCategory = HomeCategory.RECOMMEND,
    val banners: List<HomeBanner> = emptyList(),
    val hotRecommendations: List<Anime> = emptyList(),
    val hotRecommendationsTitle: String = "TV番组",
    val hotAnime: List<Anime> = emptyList(),
    val hotAnimeTitle: String = "剧场版",
    val allAnime: List<Anime> = emptyList(),
    /** 分类页数据源：key = "TV番组" / "剧场番组"，来自排行榜接口。 */
    val categoryMap: Map<String, List<Anime>> = emptyMap(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val contentFilter: ContentFilterRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    /** 首页状态（已按 NSFW 屏蔽 / 隐藏看过与抛弃 过滤各列表）。 */
    val uiState: StateFlow<HomeUiState> = combine(
        _uiState,
        contentFilter.nsfwBlock,
        contentFilter.hiddenDetailUrls,
    ) { s, block, hidden ->
        s.copy(
            hotRecommendations = s.hotRecommendations.filterContent(block, hidden),
            hotAnime = s.hotAnime.filterContent(block, hidden),
            allAnime = s.allAnime.filterContent(block, hidden),
            categoryMap = s.categoryMap.mapValues { (_, list) -> list.filterContent(block, hidden) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /** 下拉刷新进行中（不覆盖已有数据，仅驱动 RefreshIndicator）。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 当前数据源（StateFlow），供页面在切源后触发按需刷新。 */
    val currentSourceId: StateFlow<SourceMode> = SourceHolder.sourceModeFlow

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

    fun load() {
        viewModelScope.launch { fetch(showLoading = true) }
    }

    /** 下拉刷新：保留当前数据，仅显示刷新指示器，完成后收起。 */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch { fetch(showLoading = false) }
    }

    private suspend fun fetch(showLoading: Boolean) {
        if (showLoading) _uiState.update { it.copy(loading = true, error = null) }

        coroutineScope {
            val homeDeferred = async { animeRepository.getHomeData() }
            val rankingDeferred = async {
                runCatching { animeRepository.getRanking(100) }
                    .onFailure { it.printStackTrace() }
                    .getOrDefault(emptyMap())
            }

            val homeRes = homeDeferred.await()
            val rankingMap = rankingDeferred.await()

            when (homeRes) {
                is Resource.Success -> {
                    val homes = homeRes.data.orEmpty()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = null,
                            banners = homes.firstOrNull()?.banners.orEmpty(),
                            hotRecommendations = homes.getOrNull(0)?.animeList.orEmpty(),
                            hotRecommendationsTitle = homes.getOrNull(0)?.title ?: "TV番组",
                            hotAnime = homes.getOrNull(1)?.animeList.orEmpty(),
                            hotAnimeTitle = homes.getOrNull(1)?.title ?: "剧场版",
                            allAnime = homes.flatMap { home -> home.animeList }
                                .distinctBy { anime -> anime.detailUrl },
                            categoryMap = rankingMap,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = homeRes.error?.message ?: "加载失败",
                            categoryMap = rankingMap,
                        )
                    }
                }
                is Resource.Loading -> if (showLoading) _uiState.update { it.copy(loading = true) }
            }
        }
        if (!showLoading) _isRefreshing.value = false
    }

    fun onCategorySelected(category: HomeCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }
}
