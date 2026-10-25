package com.example.tuanyingshi.ui.personalization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.components.effectiveDetailColumns
import com.example.tuanyingshi.ui.components.effectiveHomeColumns
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.ui.settings.SwitchRow
import com.example.tuanyingshi.util.UiPrefs

/**
 * 个性化功能页（设置 → 个性功能）。
 * 目前提供「开屏封面」入口，点击进入开屏封面设置页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(navController: NavController) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("个性功能", color = MaterialTheme.colorScheme.onBackground) },
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PersonalizationSettingsContent(navController)
        }
    }
}

/**
 * 个性化设置内容区（供手机页与平板左右布局右侧共用）。
 */
@Composable
fun PersonalizationSettingsContent(navController: NavController) {
    val navLabelsVisible by UiPrefs.navLabelsVisible.collectAsStateWithLifecycle()

    // 导航文字标签开关：默认显示；关闭后底部 / 侧边导航仅显示图标。
    SwitchRow(
        icon = Icons.AutoMirrored.Filled.LabelOff,
        title = "隐藏导航文字",
        desc = "关闭后手机底部与平板侧边导航仅显示图标",
        checked = !navLabelsVisible,
        onCheckedChange = { UiPrefs.setNavLabelsVisible(!it) },
    )
    PersonalizationItem(
        icon = Icons.Filled.Image,
        title = "开屏封面",
        subtitle = "自定义启动图、显示时长与封面来源",
        onClick = { navController.navigate(Screen.SplashCoverSettings.route) },
    )
    PersonalizationItem(
        icon = Icons.Filled.Palette,
        title = "主题与色彩",
        subtitle = "深浅色模式、动态色彩与强调色调色板",
        onClick = { navController.navigate(Screen.Theme.route) },
    )
    // 列表布局：首页 / 详情页列表每行列数。默认按屏幕自适应，手动档则显示固定列数
    val homeAuto by UiPrefs.homeColumnsAuto.collectAsStateWithLifecycle()
    val detailAuto by UiPrefs.detailColumnsAuto.collectAsStateWithLifecycle()
    val homeColumns = effectiveHomeColumns()
    val detailColumns = effectiveDetailColumns()
    val homeLabel = if (homeAuto) "首页 自适应 $homeColumns 列" else "首页 $homeColumns 列"
    val detailLabel = if (detailAuto) "详情页 自适应 $detailColumns 列" else "详情页 $detailColumns 列"
    PersonalizationItem(
        icon = Icons.Filled.GridOn,
        title = "列表布局",
        subtitle = "$homeLabel · $detailLabel",
        onClick = { navController.navigate(Screen.LayoutSettings.route) },
    )
}

/** 单个个性化入口项：图标 + 标题 + 副标题 + 右箭头。 */
@Composable
internal fun PersonalizationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
