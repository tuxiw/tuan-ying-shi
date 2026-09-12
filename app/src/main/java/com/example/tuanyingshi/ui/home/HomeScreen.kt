package com.example.tuanyingshi.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.HomeBanner
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.ui.components.adaptiveColumns
import com.example.tuanyingshi.ui.home.components.BannerCarousel
import com.example.tuanyingshi.ui.home.components.CategoryBrowse
import com.example.tuanyingshi.ui.home.components.CategoryTabs
import com.example.tuanyingshi.ui.home.components.HotAnimeCard
import com.example.tuanyingshi.ui.home.components.HotRecommendCard
import com.example.tuanyingshi.ui.home.components.SectionHeader
import com.example.tuanyingshi.ui.home.components.TopHeader
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.ContentPrefs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val categories = HomeCategory.values().toList()
    val scope = rememberCoroutineScope()

    // 数据源切换后，主动重新拉取首页数据；同源重复进入不重复加载（去重在 ViewModel 内完成）。
    val sourceId by viewModel.currentSourceId.collectAsStateWithLifecycle()
    LaunchedEffect(sourceId) { viewModel.loadIfSourceChanged() }

    val pagerState = rememberPagerState(
        initialPage = state.selectedCategory.ordinal,
        pageCount = { categories.size },
    )

    // 滑动 / 点击切换页面后，把当前页同步回 ViewModel，驱动标签高亮与内容选择。
    LaunchedEffect(pagerState.currentPage) {
        val cat = categories[pagerState.currentPage]
        if (cat != state.selectedCategory) viewModel.onCategorySelected(cat)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
            TopHeader(
                query = "",
                onQueryChange = {},
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onDownloadClick = { navController.navigate(Screen.Downloads.route) },
            )
                CategoryTabs(
                    categories = categories,
                    selectedIndex = pagerState.currentPage,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
        }
    ) { inner ->
        val pullState = rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .pullRefresh(pullState),
        ) {
            when {
                state.loading -> CenteredMessage("加载中…", Modifier.fillMaxSize())
                state.error != null -> CenteredMessage(state.error!!, Modifier.fillMaxSize())
                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (categories[page]) {
                            HomeCategory.RECOMMEND -> RecommendContent(
                                state = state,
                                navController = navController,
                            )
                            HomeCategory.TV -> CategoryBrowse(
                                zone = "TV番组",
                                categoryMap = state.categoryMap,
                                allAnime = state.allAnime,
                                navController = navController,
                                modifier = Modifier.fillMaxSize(),
                            )
                            HomeCategory.THEATER -> CategoryBrowse(
                                zone = "剧场番组",
                                categoryMap = state.categoryMap,
                                allAnime = state.allAnime,
                                navController = navController,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 「推荐」分类页内容：顶部轮播图 + 「TV番组」2x2 网格 + 「剧场版」3 列网格。
 */
@Composable
private fun RecommendContent(
    state: HomeUiState,
    navController: NavController,
) {
    val hideHomeBanner by ContentPrefs.hideHomeBanner.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 移除首页轮播图：开启后隐藏顶部轮播 Banner，内容更紧凑
        if (!hideHomeBanner) {
            BannerCarousel(
                banners = state.banners,
                onBannerClick = { banner ->
                    val videoId = banner.actionValue?.takeIf { it.isNotBlank() }
                    if (videoId != null) {
                        navController.navigate(Screen.Detail.create("anime/$videoId"))
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        SectionHeader(
            title = state.hotRecommendationsTitle,
            emoji = "💕",
            rightText = "实时热度榜",
        )
        GridSection(
            items = state.hotRecommendations,
            columns = 2,
            horizontalPadding = 12.dp,
            spacing = 12.dp,
        ) { anime, cellModifier ->
            HotRecommendCard(
                anime = anime,
                onClick = { navController.navigate(Screen.Detail.create(anime.detailUrl)) },
                modifier = cellModifier,
            )
        }
        Spacer(Modifier.height(8.dp))

        SectionHeader(
            title = state.hotAnimeTitle,
            emoji = "🤭",
            rightText = "更多",
        )
        GridSection(
            items = state.hotAnime,
            columns = 3,
            horizontalPadding = 12.dp,
            spacing = 10.dp,
        ) { anime, cellModifier ->
            HotAnimeCard(
                anime = anime,
                onClick = { navController.navigate(Screen.Detail.create(anime.detailUrl)) },
                modifier = cellModifier,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * 通用网格区块：按列数自动换行，每行等分。
 * - 奇数末行用空 Spacer 填位避免拉伸。
 */
@Composable
private fun GridSection(
    items: List<Anime>,
    columns: Int,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    cell: @Composable (Anime, Modifier) -> Unit,
) {
    val actualColumns = adaptiveColumns(phoneBase = columns, tabletBase = columns + 2, expandedBase = columns + 3)
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
        ) {
            CenteredMessage("暂无内容")
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items.chunked(actualColumns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowItems.forEach { anime ->
                    cell(anime, Modifier.weight(1f))
                }
                // 末行不足 columns 个时补 Spacer，防止 weight 拉伸
                repeat(actualColumns - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
