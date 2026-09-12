package com.example.tuanyingshi.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.ui.components.RankingRowCard
import com.example.tuanyingshi.ui.components.isExpanded
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.ui.schedule.components.WeekdayBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ScheduleScreen(
    navController: NavController,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val weekday by viewModel.selectedWeekday.collectAsStateWithLifecycle()
    val all by viewModel.allSchedules.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 数据源切换后，主动重新拉取排期数据；同源重复进入不重复加载（去重在 ViewModel 内完成）。
    val sourceId by viewModel.currentSourceId.collectAsStateWithLifecycle()
    LaunchedEffect(sourceId) { viewModel.loadIfSourceChanged() }

    // 7 个星期：索引 0..6 对应 weekday 1..7
    val pagerState = rememberPagerState(
        initialPage = weekday - 1,
        pageCount = { 7 },
    )

    // 滑动 / 点击切换后，把当前页同步回 ViewModel，驱动标签高亮与初始页。
    LaunchedEffect(pagerState.currentPage) {
        val w = pagerState.currentPage + 1
        if (w != weekday) viewModel.setWeekday(w)
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
            // 顶部置顶标题（页面已在 NavHost 的 padding(innerPadding) 内，
            // innerPadding 已含状态栏高度，无需再 statusBarsPadding，否则叠加翻倍）
            Text(
                text = "排期表",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                textAlign = TextAlign.Center,
            )

            WeekdayBar(
                selectedIndex = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val items = all.getOrNull(page).orEmpty()
                if (items.isEmpty()) {
                    CenteredMessage("今日暂无更新", Modifier.fillMaxSize())
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
