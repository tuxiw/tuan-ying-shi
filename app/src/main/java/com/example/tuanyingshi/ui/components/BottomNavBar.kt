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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.UiPrefs

data class Tab(val route: String, val label: String, val icon: ImageVector)

val tabs = listOf(
    Tab(Screen.Home.route, "首页", Icons.Filled.Home),
    Tab(Screen.Schedule.route, "排期表", Icons.Filled.DateRange),
    Tab(Screen.Ranking.route, "排行榜", Icons.Filled.Leaderboard),
    Tab(Screen.Mine.route, "我的", Icons.Filled.Person),
)

@Composable
fun BottomNavBar(navController: NavController, currentRoute: String?) {
    val showLabels by UiPrefs.navLabelsVisible.collectAsStateWithLifecycle()
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