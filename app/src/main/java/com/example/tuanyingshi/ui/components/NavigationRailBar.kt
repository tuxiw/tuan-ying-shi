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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.UiPrefs

/**
 * 平板 / 折叠屏使用的左侧导航栏（替代手机端的底部导航栏）。
 *
 * 结构：顶部「搜索」入口、底部「设置」入口、中间为原来的 4 个主 Tab。
 * 是否显示文字标签由 [UiPrefs.navLabelsVisible] 控制（默认显示）：
 * - 显示文字时 Rail 加宽、图标下方展示标签；
 * - 隐藏文字时仅显示图标（窄 Rail）。
 */
@Composable
fun NavigationRailBar(navController: NavController, currentRoute: String?) {
    val showLabels by UiPrefs.navLabelsVisible.collectAsStateWithLifecycle()
    val railWidth = if (showLabels) 104.dp else 64.dp

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(MaterialTheme.colorScheme.surface)
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
                RailItem(
                    icon = tab.icon,
                    label = tab.label,
                    selected = selected,
                    showLabel = showLabels,
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

/** Rail 中的单个条目：圆角选中背景 + 图标（+ 可选文字标签）。 */
@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
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
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
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
