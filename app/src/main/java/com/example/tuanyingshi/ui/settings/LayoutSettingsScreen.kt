package com.example.tuanyingshi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.components.effectiveDetailColumns
import com.example.tuanyingshi.ui.components.effectiveHomeColumns
import com.example.tuanyingshi.util.COLUMNS_MAX
import com.example.tuanyingshi.util.COLUMNS_MIN
import com.example.tuanyingshi.util.UiPrefs

/**
 * 布局设置页：调整首页列表与详情页列表的每行列数。
 *
 * 两种模式：
 * - **自动（默认）**：按当前屏幕宽度与横竖屏实时算列数，手机约 3 列，平板 / 横屏更多（上限 8 列）；
 * - **手动**：钉死 2-6 列中的某一档，任何屏幕都不再变化。
 *
 * 设置项写入 [UiPrefs]，首页 / 详情页会实时跟随变化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutSettingsScreen(navController: NavController) {
    val homeColumns by UiPrefs.homeColumns.collectAsStateWithLifecycle()
    val detailColumns by UiPrefs.detailColumns.collectAsStateWithLifecycle()
    val homeAuto by UiPrefs.homeColumnsAuto.collectAsStateWithLifecycle()
    val detailAuto by UiPrefs.detailColumnsAuto.collectAsStateWithLifecycle()

    // 预览与文案统一展示「最终生效值」：自动档时按当前屏幕宽度算出来的实际列数
    val homeResolved = effectiveHomeColumns()
    val detailResolved = effectiveDetailColumns()

    val optionRange = (COLUMNS_MIN..COLUMNS_MAX).toList()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("布局设置", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            ColumnHeader(
                icon = Icons.Filled.GridOn,
                title = "首页列表",
                desc = "「推荐」页的热播推荐 / 热播日漫，以及分类浏览（TV 番组 / 剧场番组）的卡片每行列数。" +
                    "自动档按屏幕宽度与横竖屏决定（当前 $homeResolved 列）",
            )
            ColumnOptionRow(
                auto = homeAuto,
                selected = homeColumns,
                options = optionRange,
                onSelectAuto = { UiPrefs.setHomeColumnsAuto() },
                onSelect = { UiPrefs.setHomeColumns(it) },
            )
            PosterPreview(columns = homeResolved)

            Spacer(Modifier.height(16.dp))

            ColumnHeader(
                icon = Icons.Filled.GridView,
                title = "详情页列表",
                desc = "番剧详情页「选集」按钮每行排列的列数。" +
                    "自动档按可用宽度决定（当前 $detailResolved 列）",
            )
            ColumnOptionRow(
                auto = detailAuto,
                selected = detailColumns,
                options = optionRange,
                onSelectAuto = { UiPrefs.setDetailColumnsAuto() },
                onSelect = { UiPrefs.setDetailColumns(it) },
            )
            EpisodePreview(columns = detailResolved)

            Spacer(Modifier.height(20.dp))
            Text(
                text = "「自动」会根据屏幕宽度、折叠状态与横竖屏实时调整列数；手动档则是固定列数。" +
                    "列数仅影响本机展示，不会同步到账号；重启应用后依然有效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/** 分组标题（图标 + 标题 + 说明）。 */
@Composable
private fun ColumnHeader(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 「自动」+ 2-6 列的选项行。
 * 多一个「自动」后窄屏一行放不下，整体改为横向可滚动。
 */
@Composable
private fun ColumnOptionRow(
    auto: Boolean,
    selected: Int,
    options: List<Int>,
    onSelectAuto: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChip(
            selected = auto,
            onClick = onSelectAuto,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            label = { Text("自动") },
            shape = RoundedCornerShape(10.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        options.forEach { cols ->
            FilterChip(
                selected = !auto && cols == selected,
                onClick = { onSelect(cols) },
                label = { Text("$cols 列") },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/** 首页预览：按当前列数铺一行迷你海报卡（海报占位 + 标题条）。 */
@Composable
private fun PosterPreview(columns: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(columns) { index ->
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%02d".format(index + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(5.dp))
                // 模拟标题文字条
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index % 2 == 0) 0.9f else 0.7f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
                )
            }
        }
    }
}

/** 详情页预览：按当前列数铺一行「选集」按钮（描边胶囊，与真实样式一致）。 */
@Composable
private fun EpisodePreview(columns: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(columns) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "第%02d集".format(index + 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
