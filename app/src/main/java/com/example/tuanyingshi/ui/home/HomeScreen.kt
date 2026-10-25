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
import com.example.tuanyingshi.data.remote.backend.BackendAccount
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.HomeBanner
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.ui.components.effectiveHomeColumns
import com.example.tuanyingshi.ui.home.components.BannerCarousel
import com.example.tuanyingshi.ui.home.components.CategoryBrowse
import com.example.tuanyingshi.ui.home.components.CategoryTabs
import com.example.tuanyingshi.ui.home.components.HotAnimeCard
import com.example.tuanyingshi.ui.home.components.SectionHeader
import com.example.tuanyingshi.ui.home.components.TopHeader
import com.example.tuanyingshi.ui.home.hanime1.Hanime1HomeContent
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.util.SourceMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onAvatarClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val categories = HomeCategory.values().toList()
    val scope = rememberCoroutineScope()

    // 顶栏头像：后端模式且已登录时展示后端头像，点击打开左侧「快捷设置」抽屉
    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()
    val account by BackendAccount.userState.collectAsStateWithLifecycle()
    val avatarUrl = if (backendMode) BackendPrefs.absoluteUrl(account?.avatar) else null

    // 布局设置里的首页列表列数（推荐 / 热播 / 分类浏览共用）；开启自适应后按屏幕宽度实时计算
    val homeColumns = effectiveHomeColumns()

    // 数据源切换后，主动重新拉取首页数据；同源重复进入不重复加载（去重在 ViewModel 内完成）。
    val sourceId by viewModel.currentSourceId.collectAsStateWithLifecycle()
    LaunchedEffect(sourceId) { viewModel.loadIfSourceChanged() }

    // 选中 Hanime1 主数据源时，整页切换到参考 Han1meViewer 的「Hero 头图 + 横版卡片行」展示方式。
    val isHanime1 = sourceId == SourceMode.Hanime1

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
                avatarUrl = avatarUrl,
                onAvatarClick = onAvatarClick,
            )
                if (!isHanime1) {
                    CategoryTabs(
                        categories = categories,
                        selectedIndex = pagerState.currentPage,
                        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    )
                }
            }
        }
    ) { inner ->
        if (isHanime1) {
            // Hanime1 风格首页：Hero 轮播 + 横向分类卡片行（与现有竖版海报网格完全独立）。
            Hanime1HomeContent(
                navController = navController,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )
            return@Scaffold
        }
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
                                columns = homeColumns,
                            )
                            HomeCategory.TV -> CategoryBrowse(
                                zone = "TV番组",
                                categoryMap = state.categoryMap,
                                allAnime = state.allAnime,
                                navController = navController,
                                columns = homeColumns,
                                modifier = Modifier.fillMaxSize(),
                            )
                            HomeCategory.THEATER -> CategoryBrowse(
                                zone = "剧场番组",
                                categoryMap = state.categoryMap,
                                allAnime = state.allAnime,
                                navController = navController,
                                columns = homeColumns,
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
    columns: Int,
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
            columns = columns,
            horizontalPadding = 12.dp,
            spacing = 12.dp,
        ) { anime, cellModifier ->
            // TV番组与剧场版使用同款卡片（3:4 竖版海报 + 右下角标）
            HotAnimeCard(
                anime = anime,
                onClick = { navController.navigate(Screen.Detail.create(anime.detailUrl)) },
                modifier = cellModifier,
                // 首页各区块均为当前数据源的单源内容，不展示来源徽标，
                // 也避免窄列下源徽标与集数徽标压字（「Girigiri 3集 / 22:30」串字）
                showSource = false,
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
            columns = columns,
            horizontalPadding = 12.dp,
            spacing = 10.dp,
        ) { anime, cellModifier ->
            HotAnimeCard(
                anime = anime,
                onClick = { navController.navigate(Screen.Detail.create(anime.detailUrl)) },
                modifier = cellModifier,
                // 首页各区块均为当前数据源的单源内容，不展示来源徽标，
                // 也避免窄列下源徽标与集数徽标压字（「Girigiri 3集 / 22:30」串字）
                showSource = false,
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
    // 列数直接取「布局设置」中的值（2-6，默认 4），不再按屏幕尺寸二次放大，保证设置所见即所得
    val actualColumns = columns
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
