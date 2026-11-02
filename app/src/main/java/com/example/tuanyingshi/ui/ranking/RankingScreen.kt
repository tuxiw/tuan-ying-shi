package com.example.tuanyingshi.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.ui.components.HeadBgSection
import com.example.tuanyingshi.ui.components.NsfwBlockedHint
import com.example.tuanyingshi.ui.components.RankingRowCard
import com.example.tuanyingshi.ui.components.isExpanded
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.ui.ranking.components.RankingTabs
import com.example.tuanyingshi.ui.theme.LocalThemePackAssets
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.util.SourceMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RankingScreen(
    navController: NavController,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val category by viewModel.category.collectAsStateWithLifecycle()
    val categories by viewModel.visibleCategories.collectAsStateWithLifecycle()
    val ranking by viewModel.ranking.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 数据源切换后，主动重新拉取榜单数据；同源重复进入不重复加载（去重在 ViewModel 内完成）。
    val sourceId by viewModel.currentSourceId.collectAsStateWithLifecycle()
    LaunchedEffect(sourceId) { viewModel.loadIfSourceChanged() }

    // 主题套装顶头图（head_bg）：与首页头图区保持一致；未应用套装时为 null。
    val headBg = LocalThemePackAssets.current?.headBg
    val overImage = headBg != null

    // Hanime1 全站成人内容：NSFW 屏蔽开启时榜单被 filterContent 清空，空态给出屏蔽提示
    val nsfwBlock by ContentPrefs.nsfwBlock.collectAsStateWithLifecycle()
    val isHanime1 = sourceId == SourceMode.Hanime1

    // 分类集合随数据源变化（Hanime1 = 里番/泡面番）：当前分类不在新集合时回退到第一项。
    LaunchedEffect(categories, category) {
        if (category !in categories) {
            viewModel.setCategory(categories.first())
            return@LaunchedEffect
        }
    }

    val pagerState = rememberPagerState(
        initialPage = categories.indexOf(category).coerceAtLeast(0),
        pageCount = { categories.size },
    )

    // 滑动 / 点击切换后，把当前页同步回 ViewModel，驱动标签高亮与初始页。
    LaunchedEffect(pagerState.currentPage) {
        val c = categories[pagerState.currentPage]
        if (c != category) viewModel.setCategory(c)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val pullState = rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullState),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            // 顶部头图区（与首页一致）：标题 + 分类 tab 垫在 head_bg 之上，
            // edge-to-edge 铺到状态栏之下；未应用套装时无背景图，外观与之前完全一致。
            HeadBgSection(headBg = headBg) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 本路由不吃 NavHost 的状态栏 top padding，故标题必须自己 statusBarsPadding 避让，
                    // 否则会被状态栏压住。
                    Text(
                        text = "排行榜",
                        color = if (overImage) Color.White else MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 先避让状态栏，再留出一段间距：部分机型前置摄像头挖孔较大，
                            // 仅靠状态栏高度不足以让标题避开挖孔区。
                            .statusBarsPadding()
                            .padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                    )

                    // 分类 tab（按当前数据源过滤：Hanime1 = 里番/泡面番，其余源 = TV番组/剧场番组）
                    RankingTabs(
                        categories = categories,
                        selectedIndex = pagerState.currentPage,
                        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        overImage = overImage,
                    )
                }
            }

            // 副标题（参考图："根据当前热度整理，不定时更新"）
            Text(
                text = "根据当前热度整理，不定时更新",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val cat = categories[page]
                val items = remember(ranking, cat) { RankingViewModel.itemsFor(cat, ranking) }
                if (items.isEmpty()) {
                    if (isHanime1 && nsfwBlock) {
                        NsfwBlockedHint(Modifier.fillMaxSize())
                    } else {
                        CenteredMessage("暂无排行数据", Modifier.fillMaxSize())
                    }
                } else {
                    val columns = when {
                        isExpanded() -> 3
                        isTablet() -> 2
                        else -> 1
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(items, key = { index, anime -> "${index}_${anime.detailUrl}" }) { _, anime ->
                            RankingRowCard(anime = anime) {
                                navController.navigate(Screen.Detail.create(anime.detailUrl))
                            }
                        }
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
