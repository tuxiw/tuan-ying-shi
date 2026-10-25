package com.example.tuanyingshi.ui.search.hanime1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Filter
import com.example.tuanyingshi.data.remote.hanime1.Hanime1SearchOptions
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.ui.components.NsfwBlockedHint
import com.example.tuanyingshi.ui.home.hanime1.Hanime1VideoCard
import com.example.tuanyingshi.ui.navigation.Screen

/**
 * Hanime1 搜索页（参考 Han1meViewer 的高级搜索）：
 * 顶部「返回 + 关键词输入 + 筛选按钮」+ 结果横版卡片网格 + 加载更多。
 * 筛选面板覆盖全部站点搜索参数：类型 / 排序 / 发布时间 / 时长 / 标签（多选）/ 品牌（多选）/ 宽泛匹配。
 *
 * 仅在主数据源 = [com.example.tuanyingshi.util.SourceMode.Hanime1] 时由 [com.example.tuanyingshi.ui.search.SearchScreen] 整体委托渲染。
 */
@Composable
fun Hanime1SearchContent(
    navController: NavController,
    viewModel: Hanime1SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    // NSFW 屏蔽开启：Hanime1 全站成人内容不搜索、不展示
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()

    var showFilter by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = {
                            Text("搜索番剧名称", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { }),
                        shape = MaterialTheme.shapes.small,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                    )
                    // 筛选入口：有非默认条件时按钮加徽标点
                    Box {
                        IconButton(onClick = { showFilter = true }) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "筛选",
                                tint = if (filter.isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        if (filter.isActive) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxSize(),
                                ) {}
                            }
                        }
                    }
                }
            }
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            when {
                blocked -> NsfwBlockedHint(Modifier.fillMaxSize())

                isLoading && results.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                error != null && results.isEmpty() -> CenteredMessage(error!!)

                results.isEmpty() -> CenteredMessage(
                    if (filter.isActive || query.isNotBlank()) "没有找到相关内容，试试调整筛选条件"
                    else "输入关键词或点击右上角筛选浏览番剧"
                )

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            items = results,
                            key = { index, v -> "${index}_${v.videoCode}" },
                            span = { _, _ -> GridItemSpan(1) },
                        ) { _, video ->
                            Hanime1VideoCard(
                                video = video,
                                onClick = {
                                    navController.navigate(Screen.Detail.create("video/${video.videoCode}"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (hasMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                OutlinedButton(
                                    onClick = viewModel::loadMore,
                                    enabled = !isLoadingMore,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text(if (isLoadingMore) "加载中…" else "加载更多")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        Hanime1FilterDialog(
            filter = filter,
            onDismiss = { showFilter = false },
            onApply = {
                viewModel.onFilterChange(it)
                showFilter = false
            },
        )
    }
}

/**
 * Hanime1 高级筛选面板（对齐 Han1meViewer AdvancedSearchSheet）：
 * 类型 / 排序 / 发布时间 / 时长为单选；标签 / 品牌为多选；另有「宽泛匹配」开关。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hanime1FilterDialog(
    filter: Hanime1Filter,
    onDismiss: () -> Unit,
    onApply: (Hanime1Filter) -> Unit,
) {
    var genre by remember { mutableStateOf(filter.genre) }
    var sort by remember { mutableStateOf(filter.sort) }
    var date by remember { mutableStateOf(filter.date) }
    var duration by remember { mutableStateOf(filter.duration) }
    var tags by remember { mutableStateOf(filter.tags) }
    var brands by remember { mutableStateOf(filter.brands) }
    var broad by remember { mutableStateOf(filter.broad) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "筛选",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SingleChipSection(
                        title = "类型",
                        options = Hanime1SearchOptions.genres,
                        selected = genre,
                        onSelect = { genre = it },
                    )
                    SingleChipSection(
                        title = "排序",
                        options = Hanime1SearchOptions.sorts,
                        selected = sort,
                        onSelect = { sort = it },
                    )
                    SingleChipSection(
                        title = "发布时间",
                        options = Hanime1SearchOptions.dates,
                        selected = date,
                        onSelect = { date = it },
                    )
                    SingleChipSection(
                        title = "时长",
                        options = Hanime1SearchOptions.durations,
                        selected = duration,
                        onSelect = { duration = it },
                    )
                    MultiChipSection(
                        title = "标签",
                        options = Hanime1SearchOptions.tags,
                        selected = tags,
                        onToggle = { key ->
                            tags = if (key in tags) tags - key else tags + key
                        },
                    )
                    // 宽泛匹配开关（站点 broad=on）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("宽泛匹配", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "搜索范围更广，可能包含不相关的结果",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = broad, onCheckedChange = { broad = it })
                    }
                    MultiChipSection(
                        title = "品牌",
                        options = Hanime1SearchOptions.brands,
                        selected = brands,
                        onToggle = { key ->
                            brands = if (key in brands) brands - key else brands + key
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            genre = null; sort = null; date = null; duration = null
                            tags = emptySet(); brands = emptySet(); broad = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("重置")
                    }
                    Button(
                        onClick = {
                            onApply(
                                Hanime1Filter(
                                    genre = genre,
                                    sort = sort,
                                    date = date,
                                    duration = duration,
                                    tags = tags,
                                    brands = brands,
                                    broad = broad,
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("应用")
                    }
                }
            }
        }
    }
}

/** 单选 chip 分区（null 选项 = 全部）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleChipSection(
    title: String,
    options: List<com.example.tuanyingshi.data.remote.hanime1.Hanime1Option>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt.searchKey == selected,
                    onClick = { onSelect(opt.searchKey) },
                    label = { Text(opt.label) },
                )
            }
        }
    }
}

/** 多选 chip 分区。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiChipSection(
    title: String,
    options: List<com.example.tuanyingshi.data.remote.hanime1.Hanime1Option>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            title + if (selected.isNotEmpty()) "（已选 ${selected.size}）" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { opt ->
                val key = opt.searchKey ?: return@forEach
                val checked = key in selected
                AssistChip(
                    onClick = { onToggle(key) },
                    label = { Text(opt.label) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }
        }
    }
}
