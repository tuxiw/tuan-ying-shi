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
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.CategoryFilterState
import com.example.tuanyingshi.util.SavedScroll
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.util.DedicatedBackendPrefs
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.data.remote.backend.FilterOptionsVO
import androidx.compose.runtime.snapshotFlow

// ── 筛选数据 ──────────────────────────────────────────────

/** 单个筛选选项：展示文案 + 实际提交给接口的 value（空串代表「全部」）。 */
private data class FilterOption(val label: String, val value: String)

/** 题材（本地兜底，首项为「全部」）。标签文案即接口 tag 取值。 */
private val genreOptionsFallback = listOf(
    FilterOption("全部", ""),
    FilterOption("原创", "原创"), FilterOption("漫画改", "漫画改"), FilterOption("小说改", "小说改"),
    FilterOption("异世界", "异世界"), FilterOption("特摄", "特摄"), FilterOption("热血", "热血"), FilterOption("穿越", "穿越"),
    FilterOption("奇幻", "奇幻"), FilterOption("战斗", "战斗"),
    FilterOption("搞笑", "搞笑"), FilterOption("日常", "日常"), FilterOption("科幻", "科幻"), FilterOption("治愈", "治愈"),
    FilterOption("校园", "校园"), FilterOption("泡面", "泡面"),
    FilterOption("恋爱", "恋爱"), FilterOption("后宫", "后宫"), FilterOption("少女", "少女"), FilterOption("百合", "百合"),
    FilterOption("魔法", "魔法"),
    FilterOption("冒险", "冒险"), FilterOption("历史", "历史"), FilterOption("架空", "架空"), FilterOption("机战", "机战"),
    FilterOption("运动", "运动"), FilterOption("励志", "励志"),
    FilterOption("音乐", "音乐"), FilterOption("推理", "推理"), FilterOption("社团", "社团"), FilterOption("智斗", "智斗"),
    FilterOption("催泪", "催泪"), FilterOption("美食", "美食"),
    FilterOption("偶像", "偶像"), FilterOption("乙女", "乙女"), FilterOption("职场", "职场"),
)

/** 年份（本地兜底，首项为「全部」）。 */
private val yearOptionsFallback = listOf(FilterOption("全部", "")) +
    (2026 downTo 1980).map { FilterOption(it.toString(), it.toString()) }

/** 排序（本地兜底，首项为「全部」= 更新时间）。 */
private val sortOptionsFallback = listOf(
    FilterOption("全部", ""),
    FilterOption("热度", "hits"),
    FilterOption("评分", "score"),
)

// ── 选中色：品红底白字，统一收归主题 primary ──────────────

private val ChipUnselectedText = Color(0xFFBBBBBB)

/** 分区名 → 接口 zone_id（仅 cycani / 后端接口使用）。 */
private fun zoneToId(zone: String): Int = when (zone) {
    "剧场番组" -> 2
    else -> 1 // TV番组 及其它默认 1
}

// ── 主组件 ───────────────────────────────────────────────

/**
 * 「分类浏览」内容区（TV番组 / 剧场番组 Tab 共用）。
 *
 * 筛选栏多行，每行 **横向滑动** 的 chip 行（fanjuxq 风格）：
 * 1. 题材
 * 2. 年份
 * 3. 地区 / 状态（仅后端模式，后端接口支持，选项来自后端）
 * 4. 排序
 *
 * 数据源策略：
 * - **后端模式 / 次元城 / 专属后端**：走真实在线筛选接口（FilterViewModel 分页），支持下滑加载更多；
 *   后端模式下筛选选项（题材/年份/地区/状态/排序）由后端 `/filter/options` 下发，与后端保持一致。
 * - **其它本地源**：在线筛选接口不可用，降级为本地内存过滤（复用首页已加载的 categoryMap / allAnime）。
 */
@Composable
fun CategoryBrowse(
    zone: String,
    categoryMap: Map<String, List<Anime>>,
    allAnime: List<Anime>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val isOnlineFilter = SourceHolder.currentSourceMode == SourceMode.Cycanime ||
        BackendPrefs.isBackendMode() ||
        DedicatedBackendPrefs.isEnabled()
    val isBackend = BackendPrefs.isBackendMode()

    // 筛选持久策略：filterResetOnExit=true（默认）时跨导航保留筛选，仅在退出/切源时重置；
    // 否则每次进入分类页都重置为默认（局部 remember 行为）。
    val persistFilter by ContentPrefs.filterResetOnExit.collectAsStateWithLifecycle()

    // 后端筛选选项（仅后端模式从接口拉取；拉取失败时回退本地硬编码列表）
    var backendOptions by remember { mutableStateOf<FilterOptionsVO?>(null) }
    LaunchedEffect(Unit) {
        if (isBackend) {
            backendOptions = try {
                BackendClient.api.filterOptions().data
            } catch (e: Exception) {
                null
            }
        }
    }

    val genreOptions: List<FilterOption> = remember(backendOptions) {
        backendOptions?.genres?.takeIf { it.isNotEmpty() }
            ?.map { FilterOption(it.label ?: it.value ?: "全部", it.value ?: "") }
            ?: genreOptionsFallback
    }
    val yearOptions: List<FilterOption> = remember(backendOptions) {
        backendOptions?.years?.takeIf { it.isNotEmpty() }
            ?.map { FilterOption(it.label ?: it.value ?: "", it.value ?: "") }
            ?: yearOptionsFallback
    }
    val sortOptions: List<FilterOption> = remember(backendOptions) {
        backendOptions?.orders?.takeIf { it.isNotEmpty() }
            ?.map { FilterOption(it.label ?: it.value ?: "", it.value ?: "") }
            ?: sortOptionsFallback
    }
    // 地区 / 状态：仅后端接口提供，本地兜底为空（即不展示这些筛选行）。
    // 说明：原生「类型」（TV番组/剧场番组）已由顶部页签表达，筛选面板不再重复提供，避免自相矛盾。
    val regionOptions: List<FilterOption> = remember(backendOptions) {
        backendOptions?.regions?.map { FilterOption(it.label ?: it.value ?: "", it.value ?: "") }
            ?: emptyList()
    }
    val statusOptions: List<FilterOption> = remember(backendOptions) {
        backendOptions?.statuses?.map { FilterOption(it.label ?: it.value ?: "", it.value ?: "") }
            ?: emptyList()
    }

    var selectedGenre by remember(persistFilter) {
        mutableStateOf(if (persistFilter) CategoryFilterState.genre.value else "")
    }
    var selectedYear by remember(persistFilter) {
        mutableStateOf(if (persistFilter) CategoryFilterState.year.value else "")
    }
    var selectedSort by remember(persistFilter) {
        mutableStateOf(if (persistFilter) CategoryFilterState.sort.value else "")
    }
    var selectedRegion by remember(persistFilter) {
        mutableStateOf(if (persistFilter) CategoryFilterState.region.value else "")
    }
    var selectedStatus by remember(persistFilter) {
        mutableStateOf(if (persistFilter) CategoryFilterState.status.value else "")
    }
    // 筛选区展开/收起：跨导航记忆（写回 CategoryFilterState.expanded）
    var filtersExpanded by CategoryFilterState.expanded

    Column(modifier = modifier.fillMaxSize()) {
        /* ═══ 筛选区折叠头 ═══ */
        val activeCount = listOf(
            selectedGenre, selectedYear, selectedRegion,
            selectedStatus, selectedSort,
        ).count { it.isNotBlank() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { filtersExpanded = !filtersExpanded }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "筛选",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (!filtersExpanded && activeCount > 0) {
                Text(
                    text = "已选 $activeCount 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (filtersExpanded) "收起" else "展开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = if (filtersExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (filtersExpanded) "收起筛选" else "展开筛选",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        /* ═══ 筛选行（横向滑动，可折叠）═══ */
        AnimatedVisibility(
            visible = filtersExpanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column {
                FilterRow(label = "题材", options = genreOptions, selected = selectedGenre, onSelected = {
                    selectedGenre = it
                    if (persistFilter) CategoryFilterState.genre.value = it
                })
                FilterRow(label = "年份", options = yearOptions, selected = selectedYear, onSelected = {
                    selectedYear = it
                    if (persistFilter) CategoryFilterState.year.value = it
                })
                if (isBackend) {
                    FilterRow(label = "地区", options = regionOptions, selected = selectedRegion, onSelected = {
                        selectedRegion = it
                        if (persistFilter) CategoryFilterState.region.value = it
                    })
                    FilterRow(label = "状态", options = statusOptions, selected = selectedStatus, onSelected = {
                        selectedStatus = it
                        if (persistFilter) CategoryFilterState.status.value = it
                    })
                }
                FilterRow(label = "排序", options = sortOptions, selected = selectedSort, onSelected = {
                    selectedSort = it
                    if (persistFilter) CategoryFilterState.sort.value = it
                })
            }
        }

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
            if (isOnlineFilter) {
                CategoryBrowseRemote(
                    zone = zone,
                    genre = selectedGenre,
                    year = selectedYear,
                    sort = selectedSort,
                    region = selectedRegion,
                    status = selectedStatus,
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
 * 在线筛选接口分页流（后端 / 次元城 / 专属后端）。
 */
@Composable
private fun CategoryBrowseRemote(
    zone: String,
    genre: String,
    year: String,
    sort: String,
    region: String,
    status: String,
    navController: NavController,
) {
    val viewModel: FilterViewModel = hiltViewModel(key = zone)

    LaunchedEffect(zone) {
        viewModel.setZone(zoneToId(zone))
        // 兜底首加载：TV番组 的参数（zoneId=1 + 全部分类）与 FilterViewModel 默认值完全相同，
        // setZone / setFilters 都会因「参数未变」提前 return 而不触发拉取，导致该分类永远空白。
        viewModel.ensureLoaded()
    }

    // 滚动位置记忆：仅当筛选签名与上次一致时才恢复，否则从顶部开始。
    val signature0 = "$genre|$year|$sort|$region|$status"
    var currentSignature by remember { mutableStateOf(signature0) }
    val savedScroll = CategoryFilterState.scrollPositions[zone]
    val initialIndex = if (savedScroll?.signature == signature0) savedScroll.index else 0
    val initialOffset = if (savedScroll?.signature == signature0) savedScroll.offset else 0
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset,
    )

    LaunchedEffect(genre, year, sort, region, status) {
        val newSig = "$genre|$year|$sort|$region|$status"
        currentSignature = newSig
        viewModel.setFilters(
            tag = genre.ifBlank { null },
            year = year.ifBlank { null }?.toIntOrNull(),
            orderBy = sort.ifBlank { null },
            region = region.ifBlank { null },
            status = status.ifBlank { null },
        )
        // 筛选签名发生变化（真实改了筛选，或从详情返回时签名未变则不动）→ 回到顶部
        val prev = CategoryFilterState.scrollPositions[zone]
        if (prev == null || prev.signature != newSig) {
            CategoryFilterState.scrollPositions[zone] = SavedScroll(0, 0, newSig)
            gridState.scrollToItem(0)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

    // 持续记录滚动位置（普通 HashMap，不触发重组）；签名取最新值，筛选变化后不会误写旧签名。
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (idx, off) ->
            CategoryFilterState.scrollPositions[zone] = SavedScroll(idx, off, currentSignature)
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
        if (genre.isNotBlank()) {
            result = result.filter { anime ->
                anime.tags.any { it.contains(genre, ignoreCase = true) } ||
                    anime.categories.any { it.contains(genre, ignoreCase = true) }
            }
        }

        // 年份
        if (year.isNotBlank()) {
            val y = year.toIntOrNull()
            result = result.filter { it.year == y }
        }

        // 排序
        result = when (sort) {
            "hits" -> result.sortedByDescending { it.hits ?: -1L }
            "score" -> result.sortedByDescending { it.score ?: -1.0 }
            else -> result // 更新时间（默认）保持原序
        }

        result
    }

    // 滚动位置记忆（本地模式）：仅筛选签名一致时恢复，筛选变化回顶部。
    val signature0 = "$genre|$year|$sort"
    var currentSignature by remember { mutableStateOf(signature0) }
    val savedScroll = CategoryFilterState.scrollPositions[zone]
    val initialIndex = if (savedScroll?.signature == signature0) savedScroll.index else 0
    val initialOffset = if (savedScroll?.signature == signature0) savedScroll.offset else 0
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset,
    )

    LaunchedEffect(genre, year, sort) {
        val newSig = "$genre|$year|$sort"
        currentSignature = newSig
        val prev = CategoryFilterState.scrollPositions[zone]
        if (prev == null || prev.signature != newSig) {
            CategoryFilterState.scrollPositions[zone] = SavedScroll(0, 0, newSig)
            gridState.scrollToItem(0)
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (idx, off) ->
            CategoryFilterState.scrollPositions[zone] = SavedScroll(idx, off, currentSignature)
        }
    }

    if (filtered.isEmpty()) {
        CenteredMessage("暂无内容")
    } else {
        LazyVerticalGrid(
            state = gridState,
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
 * 第一个 chip 为默认项（value 为空串=全部）；左右滑动浏览更多，点选即高亮（品红底白字）。
 */
@Composable
private fun FilterRow(
    label: String,
    options: List<FilterOption>,
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
                    text = option.label,
                    selected = option.value == selected,
                    onClick = { onSelected(option.value) },
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
