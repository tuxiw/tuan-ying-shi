package com.example.tuanyingshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuanyingshi.domain.model.Episode
import com.example.tuanyingshi.domain.model.SourceCandidate
import com.example.tuanyingshi.domain.model.SourceQueryState
import com.example.tuanyingshi.domain.model.SourceQueryStatus

/**
 * 数据源选择器（对标 animeko MediaSelector / 参考第三方播放器的「选择数据源」面板）。
 *
 * - **简单模式**：每个源一行（图标 + 名称 + 验证警告），下方 FlowRow 线路 chips，点击即选中；
 * - **详细模式**：查询进度（已查询 x/x 数据源）、逐源统计 chips（图标 + 资源数），
 *   分辨率 / 字幕 / 字幕组筛选，以及按资源维度展开的列表；
 * - **编辑查询请求**：修改主搜索名 / 次要搜索名 / 集序号后重新查询（[onRequery]）。
 *
 * [queryStates] 为各源的查询状态快照（由 SourceCandidateProvider 的 onProgress 回传），
 * 为空表示当前调用方未接入进度上报（此时详细模式的进度区自动隐藏）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaSourceSheet(
    candidates: List<SourceCandidate>,
    currentSourceId: String,
    currentChannelIndex: Int,
    onSelect: (SourceCandidate, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    queryStates: List<SourceQueryState> = emptyList(),
    queriedCount: Int = -1,
    queryTotal: Int = -1,
    searchTitle: String = "",
    onRequery: (title: String, aliases: List<String>) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
) {
    var detailedMode by remember { mutableStateOf(false) }
    var showEditQuery by remember { mutableStateOf(false) }
    // 「集序号」筛选（编辑查询请求里设置）：过滤资源列表，只留对应序号的集
    var episodeFilter by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            // ── 标题行：选择数据源 + 编辑查询请求 + 关闭 ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "选择数据源",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showEditQuery = true }) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑查询请求",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ── 简单模式 / 详细模式 分段切换 ──
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                SegmentedButton(
                    selected = !detailedMode,
                    onClick = { detailedMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("简单模式") },
                    icon = {
                        if (!detailedMode) {
                            Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp))
                        }
                    },
                )
                SegmentedButton(
                    selected = detailedMode,
                    onClick = { detailedMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("详细模式") },
                    icon = {
                        if (detailedMode) {
                            Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp))
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))

            if (candidates.isEmpty() && queryStates.isEmpty()) {
                // 查询进行中（还没回任何源）时显示进度，不要直接判成「没有数据源」
                if (queryTotal > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "正在查询数据源 $queriedCount/$queryTotal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "当前没有可用的 CSS 数据源",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else if (!detailedMode) {
                // ───────────── 简单模式：源 + 线路 chips ─────────────
                LazyColumn(Modifier.fillMaxWidth()) {
                    itemsIndexed(candidates, key = { _, c -> c.sourceId }) { _, candidate ->
                        val state = queryStates.firstOrNull { it.ruleId == candidate.sourceId }
                        SimpleSourceItem(
                            candidate = candidate,
                            isCurrentSource = candidate.sourceId == currentSourceId,
                            needsVerify = state?.status == SourceQueryStatus.NeedsVerify,
                            currentChannelIndex = currentChannelIndex,
                            onSelect = onSelect,
                        )
                    }
                }
            } else {
                // ───────────── 详细模式 ─────────────
                val entries = remember(candidates) { buildResourceEntries(candidates) }
                var resFilter by remember { mutableStateOf<String?>(null) }
                var subFilter by remember { mutableStateOf<String?>(null) }
                var groupFilter by remember { mutableStateOf<String?>(null) }
                // 按数据源筛选（点击「在线」区的源 chip 切换）
                var sourceFilter by remember { mutableStateOf<String?>(null) }
                val filtered = entries.filter { e ->
                    (resFilter == null || e.resolution == resFilter) &&
                        (subFilter == null || e.subtitle == subFilter) &&
                        (groupFilter == null || e.group == groupFilter) &&
                        (episodeFilter == null || e.epNumber == episodeFilter) &&
                        (sourceFilter == null || e.candidate.sourceId == sourceFilter)
                }
                DetailedModeContent(
                    candidates = candidates,
                    entries = entries,
                    filtered = filtered,
                    queryStates = queryStates,
                    queriedCount = queriedCount,
                    queryTotal = queryTotal,
                    currentSourceId = currentSourceId,
                    sourceFilter = sourceFilter, onSourceFilter = { sourceFilter = it },
                    resFilter = resFilter, onResFilter = { resFilter = it },
                    subFilter = subFilter, onSubFilter = { subFilter = it },
                    groupFilter = groupFilter, onGroupFilter = { groupFilter = it },
                    episodeFilter = episodeFilter, onEpisodeFilter = { episodeFilter = it },
                    onRefresh = onRefresh,
                    onSelect = onSelect,
                )
            }
        }
    }

    if (showEditQuery) {
        EditQueryDialog(
            initialTitle = searchTitle,
            initialEpisode = episodeFilter,
            onDismiss = { showEditQuery = false },
            onSave = { title, aliases, ep ->
                showEditQuery = false
                episodeFilter = ep
                onRequery(title, aliases)
            },
        )
    }
}

// ───────────────────────── 简单模式 ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimpleSourceItem(
    candidate: SourceCandidate,
    isCurrentSource: Boolean,
    needsVerify: Boolean,
    currentChannelIndex: Int,
    onSelect: (SourceCandidate, Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceIcon(iconUrl = candidate.iconUrl, size = 26.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                candidate.sourceName.ifBlank { "未命名数据源" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (needsVerify) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "需要处理验证",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                )
            }
            if (isCurrentSource) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "当前",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // 线路 chips：FlowRow 自动换行（对齐参考 UI），点击某线路即选中「该源 + 该线路」
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            candidate.channelNames.forEachIndexed { index, name ->
                val selected = isCurrentSource && index == currentChannelIndex
                Box(
                    modifier = Modifier
                        .clipChip()
                        .clickable { onSelect(candidate, index) }
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        name,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

// ───────────────────────── 详细模式 ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailedModeContent(
    candidates: List<SourceCandidate>,
    entries: List<ResourceEntry>,
    filtered: List<ResourceEntry>,
    queryStates: List<SourceQueryState>,
    queriedCount: Int,
    queryTotal: Int,
    currentSourceId: String,
    sourceFilter: String?,
    onSourceFilter: (String?) -> Unit,
    resFilter: String?,
    onResFilter: (String?) -> Unit,
    subFilter: String?,
    onSubFilter: (String?) -> Unit,
    groupFilter: String?,
    onGroupFilter: (String?) -> Unit,
    episodeFilter: Int?,
    onEpisodeFilter: (Int?) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (SourceCandidate, Int) -> Unit,
) {
    // 当前展开的下拉筛选（res/sub/group），null = 全部收起
    var openDropdown by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxWidth()) {
        // ── 查询进度 ──
        if (queryTotal > 0) {
            item(key = "progress") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已查询 $queriedCount/$queryTotal 数据源",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (queriedCount < queryTotal) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "重新查询",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // ── 逐源统计 chips（图标 + 资源数 / 状态），点击按该源筛选资源列表 ──
        item(key = "sources") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "在线",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    queryStates.forEach { state ->
                        SourceStatChip(
                            state = state,
                            isCurrent = state.ruleId == currentSourceId,
                            isSelected = state.ruleId == sourceFilter,
                            onClick = {
                                onSourceFilter(if (sourceFilter == state.ruleId) null else state.ruleId)
                            },
                        )
                    }
                    // 无进度上报时回退：从候选列表统计
                    if (queryStates.isEmpty()) {
                        candidates.forEach { c ->
                            val count = c.channels.values.sumOf { it.size }
                            SourceStatChip(
                                state = SourceQueryState(
                                    ruleId = c.sourceId,
                                    name = c.sourceName,
                                    iconUrl = c.iconUrl,
                                    status = SourceQueryStatus.Ok,
                                    episodeCount = count,
                                ),
                                isCurrent = c.sourceId == currentSourceId,
                                isSelected = c.sourceId == sourceFilter,
                                onClick = {
                                    onSourceFilter(if (sourceFilter == c.sourceId) null else c.sourceId)
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── 筛选 ──
        item(key = "filters") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "筛选到 ${filtered.size}/${entries.size} 条资源",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                // 筛选 chips 一行横排（FlowRow 溢出自动换行）
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sourceFilter != null) {
                        FilterChip(
                            selected = true,
                            onClick = { onSourceFilter(null) },
                            label = {
                                Text(
                                    queryStates.firstOrNull { it.ruleId == sourceFilter }?.name
                                        ?: candidates.firstOrNull { it.sourceId == sourceFilter }?.sourceName
                                        ?: sourceFilter,
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(16.dp))
                            },
                        )
                    }
                    FilterChip(
                        selected = resFilter != null,
                        onClick = { openDropdown = if (openDropdown == "res") null else "res" },
                        label = { Text(resFilter ?: "分辨率") },
                        trailingIcon = {
                            Icon(
                                if (openDropdown == "res") Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                Modifier.size(18.dp),
                            )
                        },
                    )
                    FilterChip(
                        selected = subFilter != null,
                        onClick = { openDropdown = if (openDropdown == "sub") null else "sub" },
                        label = { Text(subFilter ?: "字幕") },
                        trailingIcon = {
                            Icon(
                                if (openDropdown == "sub") Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                Modifier.size(18.dp),
                            )
                        },
                    )
                    FilterChip(
                        selected = groupFilter != null,
                        onClick = { openDropdown = if (openDropdown == "group") null else "group" },
                        label = { Text(groupFilter ?: "字幕组") },
                        trailingIcon = {
                            Icon(
                                if (openDropdown == "group") Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                Modifier.size(18.dp),
                            )
                        },
                    )
                    if (episodeFilter != null) {
                        FilterChip(
                            selected = true,
                            onClick = { onEpisodeFilter(null) },
                            label = { Text("第 $episodeFilter 集 ×") },
                        )
                    }
                }
                // 内联展开：点开的下拉选项铺满一行，显示在 chips 下方（不用 DropdownMenu——
                // 其 Popup 层级低于 ModalBottomSheet 会被面板盖住）
                when (openDropdown) {
                    "res" -> DropdownOptionsRow(
                        label = "分辨率",
                        options = distinctNonNull(entries) { it.resolution },
                        selected = resFilter,
                        onSelect = {
                            onResFilter(it)
                            openDropdown = null
                        },
                    )
                    "sub" -> DropdownOptionsRow(
                        label = "字幕",
                        options = distinctNonNull(entries) { it.subtitle },
                        selected = subFilter,
                        onSelect = {
                            onSubFilter(it)
                            openDropdown = null
                        },
                    )
                    "group" -> DropdownOptionsRow(
                        label = "字幕组",
                        options = distinctNonNull(entries) { it.group },
                        selected = groupFilter,
                        onSelect = {
                            onGroupFilter(it)
                            openDropdown = null
                        },
                    )
                }
            }
        }

        // ── 资源列表 ──
        itemsIndexed(filtered, key = { _, e -> "${e.candidate.sourceId}-${e.lineIndex}-${e.index}-${e.episode.url}" }) { _, e ->
            ResourceRow(
                entry = e,
                isCurrent = e.candidate.sourceId == currentSourceId &&
                    e.lineIndex == currentChannelIndexFor(e),
                onClick = { onSelect(e.candidate, e.lineIndex) },
            )
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                Text(
                    "没有符合筛选条件的资源",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** 当前线路高亮：资源行自身的线路即其所在线路，无需额外映射。 */
private fun currentChannelIndexFor(e: ResourceEntry): Int = e.lineIndex

/**
 * 逐源统计 chip：图标 + 资源数；查询中转圈，无资源 ×，需验证橙色「验」。
 * 点击按该源筛选资源列表（再次点击取消），选中态高亮描边。
 */
@Composable
private fun SourceStatChip(
    state: SourceQueryState,
    isCurrent: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .clipChip()
            .border(
                1.dp,
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                },
                RoundedCornerShape(16.dp),
            )
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Unspecified
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceIcon(iconUrl = state.iconUrl, size = 18.dp)
        Spacer(Modifier.width(5.dp))
        when (state.status) {
            SourceQueryStatus.Querying, SourceQueryStatus.Pending ->
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
            SourceQueryStatus.Ok -> Text("${state.episodeCount}", style = MaterialTheme.typography.labelMedium)
            SourceQueryStatus.Empty, SourceQueryStatus.Failed -> Text(
                "×",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            SourceQueryStatus.Stopped -> Text(
                "停",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            SourceQueryStatus.NeedsVerify -> Text(
                "验",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** 资源行：标题 + 属性 chips + 来源行（图标 + 源名 + 线路名）。 */
@Composable
private fun ResourceRow(entry: ResourceEntry, isCurrent: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clipChip()
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            entry.episode.name.ifBlank { "第 ${entry.index + 1} 集" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.resolution != null || entry.subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.resolution?.let {
                    MiniTag(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                entry.subtitle?.let {
                    MiniTag(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                entry.group?.let {
                    MiniTag(it, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceIcon(iconUrl = entry.candidate.iconUrl, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                entry.candidate.sourceName.ifBlank { "未命名数据源" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.lineName.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    entry.lineName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCurrent) {
                Spacer(Modifier.weight(1f))
                Text(
                    "当前",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MiniTag(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 下拉筛选的选项行（分辨率 / 字幕 / 字幕组）：铺满一行的 FlowRow chips。
 *
 * 注意：**不使用 DropdownMenu** —— 它的 Popup 窗口层级低于 ModalBottomSheet 的独立窗口，
 * 菜单会被面板盖住导致「点击无效果」。改为内联展开：点击筛选 chip 后选项铺在其下方。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DropdownOptionsRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        if (selected != null) {
            FilterChip(
                selected = false,
                onClick = { onSelect(null) },
                label = { Text("全部 $label") },
            )
        }
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(opt) },
            )
        }
    }
}

// ───────────────────────── 编辑查询请求（图4） ─────────────────────────

/**
 * 编辑查询请求：主搜索名（多数源使用）、次要搜索名（逗号/空格分隔，可选）、
 * 集序号（可选，过滤资源列表）。保存后触发 [onSave] 重新查询。
 */
@Composable
private fun EditQueryDialog(
    initialTitle: String,
    initialEpisode: Int?,
    onDismiss: () -> Unit,
    onSave: (title: String, aliases: List<String>, episode: Int?) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var aliases by remember { mutableStateOf("") }
    var episode by remember { mutableStateOf(initialEpisode?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑查询请求") },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = title.trim()
                    if (t.isEmpty()) return@TextButton
                    val aliasList = aliases.split(',', '，', ' ', '\n')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    val ep = episode.trim().toIntOrNull()
                    onSave(t, aliasList, ep)
                },
                enabled = title.trim().isNotEmpty(),
            ) { Text("保存并刷新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("主搜索名") },
                    supportingText = { Text("大多数数据源只使用此名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = aliases,
                    onValueChange = { aliases = it },
                    label = { Text("次要搜索名（可选，逗号分隔）") },
                    supportingText = { Text("用于主搜索名查不到资源时的备用关键词") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = episode,
                    onValueChange = { episode = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("集序号（可选）") },
                    supportingText = { Text("筛选资源列表，只显示对应序号的集，例如 01") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

// ───────────────────────── 资源解析工具 ─────────────────────────

/** 圆角容器修饰：资源行 / 统计 chip / 线路 chip 共用。 */
private fun Modifier.clipChip(shape: RoundedCornerShape = RoundedCornerShape(10.dp)): Modifier = clip(shape)

/** 详细模式资源条目：某源某线路下的一集，附从名称解析出的属性。 */
private data class ResourceEntry(
    val candidate: SourceCandidate,
    val lineIndex: Int,
    val lineName: String,
    val episode: Episode,
    val index: Int,
    val resolution: String?,
    val subtitle: String?,
    val group: String?,
    /** 解析出的集序号（第 x 集/话）；解析不到用位置序号。 */
    val epNumber: Int?,
)

/** 把全部候选展开为资源条目列表（详细模式数据源）。 */
private fun buildResourceEntries(candidates: List<SourceCandidate>): List<ResourceEntry> {
    val list = mutableListOf<ResourceEntry>()
    candidates.forEach { c ->
        c.channels.keys.sorted().forEach { line ->
            val eps = c.channels[line].orEmpty()
            val lineName = c.channelNames.getOrNull(c.channels.keys.sorted().indexOf(line)) ?: ""
            eps.forEachIndexed { idx, ep ->
                val text = "${c.channelNames.getOrNull(c.channels.keys.sorted().indexOf(line)).orEmpty()} ${ep.name}"
                list.add(
                    ResourceEntry(
                        candidate = c,
                        lineIndex = line,
                        lineName = lineName,
                        episode = ep,
                        index = idx,
                        resolution = parseResolution(text),
                        subtitle = parseSubtitle(text),
                        group = parseFansub(text),
                        epNumber = parseEpisodeNumber(ep.name) ?: (idx + 1),
                    ),
                )
            }
        }
    }
    return list
}

private fun distinctNonNull(entries: List<ResourceEntry>, selector: (ResourceEntry) -> String?): List<String> =
    entries.mapNotNull(selector).distinct().sorted()

private val RESOLUTION_RE = Regex("(2160P|1080[PI]|1080P|720P|480P|360P|4K|蓝光|高清|超清|原画)", RegexOption.IGNORE_CASE)

/** 从线路/集名解析分辨率标签（统一为大写规范形，2160P 归一为 4K）。 */
private fun parseResolution(text: String): String? {
    val m = RESOLUTION_RE.find(text) ?: return null
    val v = m.value.uppercase().replace("1080I", "1080P")
    return if (v == "2160P") "4K" else v
}

private val SUBTITLE_RE = Regex("简中|简体中字|简体|简日|繁中|繁体|中字|中文字幕|双语|官方中字")

/** 从线路/集名解析字幕语言标签（归一为 简中 / 繁中 / 中字）。 */
private fun parseSubtitle(text: String): String? {
    val m = SUBTITLE_RE.find(text) ?: return null
    return when {
        m.value.startsWith("简") -> "简中"
        m.value.startsWith("繁") -> "繁中"
        else -> "中字"
    }
}

private val FANSUB_RE = Regex("[\\u4e00-\\u9fa5A-Za-z0-9]{1,12}(?:字幕组|汉化组)")

/** 从线路/集名解析字幕组名（如「千夏字幕组」）。 */
private fun parseFansub(text: String): String? = FANSUB_RE.find(text)?.value

private val EP_NUM_RE = Regex("第?\\s*(\\d{1,4})\\s*[集话話期]")

/** 从集名解析集序号（「第 03 话」→ 3）；解析不到返回 null（调用方回退位置序号）。 */
private fun parseEpisodeNumber(name: String): Int? =
    EP_NUM_RE.find(name)?.groupValues?.get(1)?.toIntOrNull()
        ?: Regex("^\\s*(\\d{1,4})$").find(name.trim())?.groupValues?.get(1)?.toIntOrNull()

/** 数据源图标：有 iconUrl 用圆形远程图，否则用 Language 占位（与设置页一致）。 */
@Composable
fun SourceIcon(iconUrl: String, size: androidx.compose.ui.unit.Dp) {
    if (iconUrl.isNotBlank()) {
        coil.compose.AsyncImage(
            model = iconUrl,
            contentDescription = null,
            modifier = Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}
