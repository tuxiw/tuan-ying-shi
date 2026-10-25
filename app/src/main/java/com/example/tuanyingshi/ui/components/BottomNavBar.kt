package com.example.tuanyingshi.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.navigation.Screen
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
    NavigationBar {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
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
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = if (showLabels) {
                    { Text(tab.label) }
                } else {
                    null
                },
            )
        }
    }
}
