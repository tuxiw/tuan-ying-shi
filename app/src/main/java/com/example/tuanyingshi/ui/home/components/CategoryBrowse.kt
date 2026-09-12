package com.example.tuanyingshi.ui.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import com.example.tuanyingshi.ui.components.adaptiveMinCardWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import coil.compose.AsyncImage
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.episodeBadge
import com.example.tuanyingshi.ui.components.AbandonDialog
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.ui.components.LocalUserStatusRepository
import com.example.tuanyingshi.ui.home.FilterViewModel
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tuanyingshi.util.CategoryFilterState
import androidx.compose.runtime.snapshotFlow
import com.example.tuanyingshi.util.ContentPrefs

// ── 筛选数据 ──────────────────────────────────────────────

/** 题材（首项为默认）。标签文案即接口 tag 取值。 */
private val genreOptions = listOf(
    "全部", "原创", "漫画改", "小说改", "游戏改",
    "异世界", "特摄", "热血", "穿越", "奇幻", "战斗",
    "搞笑", "日常", "科幻", "治愈", "校园", "泡面",
    "恋爱", "后宫", "少女", "百合", "魔法",
    "冒险", "历史", "架空", "机战", "运动", "励志",
    "音乐", "推理", "社团", "智斗", "催泪", "美食",
    "偶像", "乙女", "职场",
)

/** 年份（首项为默认）。 */
private val yearOptions = (2026 downTo 1980).map { it.toString() }.toMutableList().apply { add(0, "全部") }

/** 排序选项（首项为默认，对应接口不传 order_by）。 */
private val sortOptions = listOf("更新时间", "热度", "评分")

// ── 选中色：品红底白字，统一收归主题 primary ──────────────

private val ChipUnselectedText = Color(0xFFBBBBBB)

/** 分区名 → 接口 zone_id（仅 cycani 接口使用）。 */
private fun zoneToId(zone: String): Int = when (zone) {
    "剧场番组" -> 2
    else -> 1 // TV番组 及其它默认 1
}

// ── 主组件 ───────────────────────────────────────────────

/**
 * 「分类浏览」内容区（TV番组 / 剧场番组 Tab 共用）。
 *
 * 筛选栏三行，每行 **横向滑动** 的 chip 行（fanjuxq 风格）：
 * 1. 题材（35 项）
 * 2. 年份（1980–2026）
 * 3. 排序（3 项）
 *
 * 数据源策略：
 * - **cycani 资源**：走真实在线筛选接口（/api/videos，FilterViewModel 分页），支持下滑加载更多。
 * - **其它资源**：该接口无效，降级为本地内存过滤（复用首页已加载的 categoryMap / allAnime）。
 */
@Composable
fun CategoryBrowse(
    zone: String,
    categoryMap: Map<String, List<Anime>>,
    allAnime: List<Anime>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val isCycani = SourceHolder.currentSourceMode == SourceMode.Cycanime

    // 筛选持久策略：filterResetOnExit=true（默认）时跨导航保留筛选，仅在退出/切源时重置；
    // 否则每次进入分类页都重置为默认（局部 remember 行为）。
    val persistFilter by ContentPrefs.filterResetOnExit.collectAsStateWithLifecycle()

    var selectedGenre by remember(persistFilter) {
        mutableStateOf(if (persistFilter) CategoryFilterState.genre.value else genreOptions.first())
    }
    var selectedYear by remember(persistFilter) {
        mutableStateOf(if (persistFilter) CategoryFilterState.year.value else yearOptions.first())
    }
    var selectedSort by remember(persistFilter) {
        mutableStateOf(if (persistFilter) CategoryFilterState.sort.value else sortOptions.first())
    }

    Column(modifier = modifier.fillMaxSize()) {
        /* ═══ 三行筛选（横向滑动）═══ */
        FilterRow(label = "题材", options = genreOptions, selected = selectedGenre, onSelected = {
            selectedGenre = it
            if (persistFilter) CategoryFilterState.genre.value = it
        })
        FilterRow(label = "年份", options = yearOptions, selected = selectedYear, onSelected = {
            selectedYear = it
            if (persistFilter) CategoryFilterState.year.value = it
        })
        FilterRow(label = "排序", options = sortOptions, selected = selectedSort, onSelected = {
            selectedSort = it
            if (persistFilter) CategoryFilterState.sort.value = it
        })

        /* ═══ 分割线 ═══ */
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        )

        /* ═══ 内容网格 ═══ */
        Box(modifier = Modifier.weight(1f)) {
            if (isCycani) {
                CategoryBrowseRemote(
                    zone = zone,
                    genre = selectedGenre,
                    year = selectedYear,
                    sort = selectedSort,
                    navController = navController,
                )
            } else {
                CategoryBrowseLocal(
                    zone = zone,
                    categoryMap = categoryMap,
                    allAnime = allAnime,
                    genre = selectedGenre,
                    year = selectedYear,
                    sort = selectedSort,
                    navController = navController,
                )
            }
        }
    }
}

/**
 * cycani 资源：在线筛选接口分页流。
 */
@Composable
private fun CategoryBrowseRemote(
    zone: String,
    genre: String,
    year: String,
    sort: String,
    navController: NavController,
) {
    val viewModel: FilterViewModel = hiltViewModel(key = zone)

    LaunchedEffect(zone) {
        viewModel.setZone(zoneToId(zone))
        // 兜底首加载：TV番组 的参数（zoneId=1 + 全部分类）与 FilterViewModel 默认值完全相同，
        // setZone / setFilters 都会因「参数未变」提前 return 而不触发拉取，导致该分类永远空白。
        viewModel.ensureLoaded()
    }
    LaunchedEffect(genre, year, sort) {
        viewModel.setFilters(
            tag = if (genre == genreOptions.first()) null else genre,
            year = if (year == yearOptions.first()) null else year.toIntOrNull(),
            orderBy = when (sort) {
                "热度" -> "hits"
                "评分" -> "score"
                else -> null // 更新时间（默认）不传
            },
        )
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // 滚动接近底部时自动加载下一页（snapshotFlow 仅在布局信息变化时重算，
    // 读取 uiState.value 取最新快照，避免额外订阅）。
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            lastVisible to total
        }.collect { (lastVisible, total) ->
            val s = viewModel.uiState.value
            if (total > 0 && lastVisible >= total - 4 &&
                s.canLoadMore && !s.isLoadingMore && !s.isLoading
            ) {
                viewModel.loadMore()
            }
        }
    }

    when {
        uiState.isLoading -> CenteredMessage("加载中…")
        uiState.isError -> CenteredMessage("加载失败，请稍后重试")
        uiState.isEmpty -> CenteredMessage("暂无内容")
        else -> {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = adaptiveMinCardWidth),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.items) { anime ->
                    CategoryAnimeCard(anime = anime) {
                        navController.navigate(Screen.Detail.create(anime.detailUrl))
                    }
                }
                // 底部加载更多指示（跨满整行）
                if (uiState.canLoadMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (uiState.isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(22.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = "上拉加载更多",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.clickable { viewModel.loadMore() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 其它资源：在线筛选接口不可用，降级为本地内存过滤（复用首页已加载数据）。
 */
@Composable
private fun CategoryBrowseLocal(
    zone: String,
    categoryMap: Map<String, List<Anime>>,
    allAnime: List<Anime>,
    genre: String,
    year: String,
    sort: String,
    navController: NavController,
) {
    val baseList = remember(zone, categoryMap, allAnime) {
        (categoryMap[zone] ?: allAnime).distinctBy { it.detailUrl }
    }

    val filtered = remember(baseList, genre, year, sort) {
        var result = baseList

        // 题材：tags / categories 模糊匹配
        if (genre != genreOptions.first()) {
            result = result.filter { anime ->
                anime.tags.any { it.contains(genre, ignoreCase = true) } ||
                    anime.categories.any { it.contains(genre, ignoreCase = true) }
            }
        }

        // 年份
        if (year != yearOptions.first()) {
            val y = year.toIntOrNull()
            result = result.filter { it.year == y }
        }

        // 排序
        result = when (sort) {
            "热度" -> result.sortedByDescending { it.hits ?: -1L }
            "评分" -> result.sortedByDescending { it.score ?: -1.0 }
            else -> result // 更新时间（默认）保持原序
        }

        result
    }

    if (filtered.isEmpty()) {
        CenteredMessage("暂无内容")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = adaptiveMinCardWidth),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered) { anime ->
                CategoryAnimeCard(anime = anime) {
                    navController.navigate(Screen.Detail.create(anime.detailUrl))
                }
            }
        }
    }
}

// ── 筛选行组件 ───────────────────────────────────────────

/**
 * 单行筛选：「标签名」+ 横向滑动的 chip 行，**同行展示**。
 * 第一个 chip 为默认项；左右滑动浏览更多，点选即高亮（品红底白字）。
 */
@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧标签（固定宽度，不换行）
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = 12.dp, end = 8.dp)
                .width(40.dp),
        )
        // 右侧横向滑动 chip 行
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            lazyItems(options) { option ->
                FilterChip(
                    text = option,
                    selected = option == selected,
                    onClick = { onSelected(option) },
                )
            }
        }
    }
}

/**
 * 单个筛选药丸 chip（fanjuxq 风格）。
 * - 选中：品红背景 + 白字
 * - 未选中：透明背景 + 灰色文字
 */
@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (selected) Color.White else ChipUnselectedText

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── 动漫卡片 ─────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryAnimeCard(anime: Anime, onClick: () -> Unit) {
    var showAbandon by remember { mutableStateOf(false) }
    val canAbandon = LocalUserStatusRepository.current != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (canAbandon) showAbandon = true },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = anime.img,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = anime.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val epBadge = anime.episodeBadge
        if (epBadge.isNotBlank()) {
            Text(
                text = epBadge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showAbandon) {
        AbandonDialog(
            detailUrl = anime.detailUrl,
            title = anime.title,
            imgUrl = anime.img,
            onDismiss = { showAbandon = false },
        )
    }
}
