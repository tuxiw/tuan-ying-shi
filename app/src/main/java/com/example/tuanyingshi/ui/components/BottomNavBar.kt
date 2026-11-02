package com.example.tuanyingshi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.ui.theme.LocalThemePackAssets
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.UiPrefs

data class Tab(val route: String, val label: String, val icon: ImageVector)

/** 主 Tab 清单（手机底栏与平板侧栏共用；Hanime1 模式下由调用方过滤掉排期表）。 */
val baseTabs = listOf(
    Tab(Screen.Home.route, "首页", Icons.Filled.Home),
    Tab(Screen.Schedule.route, "排期表", Icons.Filled.DateRange),
    Tab(Screen.Ranking.route, "排行榜", Icons.Filled.Leaderboard),
    Tab(Screen.Mine.route, "我的", Icons.Filled.Person),
)

@Composable
fun BottomNavBar(navController: NavController, currentRoute: String?) {
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
    // 主题套装：底部栏背景图（tail_bg）与标签配色（tail_color / tail_color_selected）
    val navBg = assets?.bottomNavBg
    val tailColor = assets?.tailColor
    val tailColorSelected = assets?.tailColorSelected

    // ⚠️ 这里的 Box 只能由 NavigationBar 决定尺寸：背景图必须用 matchParentSize()，
    // 不能写 fillMaxSize()。Scaffold 给 bottomBar 的是松约束（minHeight=0、maxHeight=全屏），
    // 若背景图用 fillMaxSize() 会把底栏撑到全屏高度，进而把内容区压成 0 高度
    // （表现为：内容消失 + 4 个 Tab 被顶到屏幕顶部 + tail_bg 铺满全屏）。
    Box(Modifier.fillMaxWidth()) {
        // 主题套装底部栏背景图；无则不画，由 NavigationBar 自身 surface 兜底
        if (navBg != null) {
            AsyncImage(
                model = navBg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        NavigationBar(
            containerColor = navBg?.let { Color.Transparent } ?: MaterialTheme.colorScheme.surface,
            tonalElevation = if (navBg != null) 0.dp else 8.dp,
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                // 首页 / 我的 在套装里有专属图标；其余 Tab 用内置矢量图标
                val pair = assets?.icons?.get(tab.route)
                val themeIcon = if (selected) pair?.selected else pair?.normal
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            navController.navigate(tab.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        if (themeIcon != null) {
                            // 套装图标是已上色的 PNG，不额外着色
                            AsyncImage(
                                model = themeIcon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) {
                                    tailColorSelected ?: MaterialTheme.colorScheme.primary
                                } else {
                                    tailColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    label = if (showLabels) {
                        { Text(tab.label) }
                    } else {
                        null
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = tailColorSelected
                            ?: MaterialTheme.colorScheme.primary,
                        unselectedTextColor = tailColor
                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = tailColorSelected?.copy(alpha = 0.18f)
                            ?: MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }
}
