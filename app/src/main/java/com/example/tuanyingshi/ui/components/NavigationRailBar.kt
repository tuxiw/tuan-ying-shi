package com.example.tuanyingshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.ui.theme.LocalThemePackAssets
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.UiPrefs
import java.io.File

/**
 * 平板 / 折叠屏使用的左侧导航栏（替代手机端的底部导航栏）。
 *
 * 结构：顶部「搜索」入口、底部「设置」入口、中间为原来的 4 个主 Tab。
 * 是否显示文字标签由 [UiPrefs.navLabelsVisible] 控制（默认显示）：
 * - 显示文字时 Rail 加宽、图标下方展示标签；
 * - 隐藏文字时仅显示图标（窄 Rail）。
 *
 * 主题套装：侧栏背景渲染 [LocalThemePackAssets.sideBg]（side_bg），无则回退 surface；
 * 首页 / 我的 用套装专属图标，其余用内置矢量；标签与图标着色跟随 tail_color / tail_color_selected。
 */
@Composable
fun NavigationRailBar(navController: NavController, currentRoute: String?) {
    val assets = LocalThemePackAssets.current
    val showLabels by UiPrefs.navLabelsVisible.collectAsStateWithLifecycle()
    // Hanime1 主数据源没有周更排期概念（站点无周榜单数据），隐藏「排期表」入口。
    val isHanime1 by SourceHolder.sourceModeFlow.collectAsStateWithLifecycle()
    val tabs = remember(isHanime1) {
        if (isHanime1 == SourceMode.Hanime1) {
            baseTabs.filter { it.route != Screen.Schedule.route }
        } else {
            baseTabs
        }
    }
    val railWidth = if (showLabels) 104.dp else 64.dp
    // 主题套装：侧栏背景图（side_bg）与配色
    val sideBg = assets?.sideBg
    val tailColor = assets?.tailColor
    val tailColorSelected = assets?.tailColorSelected

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(railWidth),
    ) {
        // 主题套装侧栏背景图；无则不画，由 Column 的 surface 兜底。
        // 用 matchParentSize()：尺寸由外层 Box（fillMaxHeight + railWidth）决定，
        // 背景图不参与测量，保证图片与侧栏控件严格一致（不可写 fillMaxSize，会反向撑大容器）。
        if (sideBg != null) {
            AsyncImage(
                model = sideBg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(railWidth)
                .background(if (sideBg == null) MaterialTheme.colorScheme.surface else Color.Transparent)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部留白，让搜索入口往下挪一段
            Spacer(Modifier.height(24.dp))

            // 顶部：搜索入口
            RailItem(
                icon = Icons.Filled.Search,
                label = "搜索",
                selected = currentRoute == Screen.Search.route,
                showLabel = showLabels,
                themeIcon = null,
                tint = barTint(currentRoute == Screen.Search.route, tailColor, tailColorSelected),
                onClick = {
                    if (currentRoute != Screen.Search.route) {
                        navController.navigate(Screen.Search.route) {
                            launchSingleTop = true
                        }
                    }
                },
            )

            // 中间：原来的 4 个主 Tab（垂直居中分布）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    // 首页 / 我的 在套装里有专属图标；其余 Tab 用内置矢量图标
                    val pair = assets?.icons?.get(tab.route)
                    val themeIcon = if (selected) pair?.selected else pair?.normal
                    RailItem(
                        icon = tab.icon,
                        label = tab.label,
                        selected = selected,
                        showLabel = showLabels,
                        themeIcon = themeIcon,
                        tint = barTint(selected, tailColor, tailColorSelected),
                        onClick = {
                            if (!selected) {
                                navController.navigate(tab.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }

            // 底部：设置入口
            RailItem(
                icon = Icons.Filled.Settings,
                label = "设置",
                selected = currentRoute == Screen.Settings.route,
                showLabel = showLabels,
                themeIcon = null,
                tint = barTint(currentRoute == Screen.Settings.route, tailColor, tailColorSelected),
                onClick = {
                    if (currentRoute != Screen.Settings.route) {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
    }
}

/** 侧栏条目的着色：选中取 tailColorSelected，否则 tailColor；套装未提供时回退内置容器色上的字色。 */
@Composable
private fun barTint(selected: Boolean, tailColor: Color?, tailColorSelected: Color?): Color =
    if (selected) {
        tailColorSelected ?: MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        tailColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    }

/** Rail 中的单个条目：圆角选中背景 + 图标（+ 可选文字标签）。 */
@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    showLabel: Boolean,
    themeIcon: File?,
    tint: Color,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (themeIcon != null) {
            // 套装图标是已上色的 PNG，不额外着色
            AsyncImage(
                model = themeIcon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        if (showLabel) {
            androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
