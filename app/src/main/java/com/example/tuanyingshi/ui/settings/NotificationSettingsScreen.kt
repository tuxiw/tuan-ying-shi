package com.example.tuanyingshi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WifiOff
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
import com.example.tuanyingshi.util.NotifPrefs

/**
 * 消息通知设置页：控制各类系统通知与应用内提醒。
 * - 下载进度通知：关闭后下载番剧不再在状态栏弹进度 / 完成提醒。
 * - 离线模式提示：无网络时（启动即离线 / 使用中断网或恢复）弹 Toast 告知。
 * - 离线模式顶部提醒：无网络时在主界面顶部常驻红色横幅。
 * 三项默认全部开启。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    navController: NavController,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("消息通知", color = MaterialTheme.colorScheme.onBackground) },
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
            NotificationSettingsContent()
        }
    }
}

/**
 * 消息通知设置内容区（供手机页与平板左右布局右侧共用）。
 */
@Composable
fun NotificationSettingsContent() {
    val downloadNotifEnabled by NotifPrefs.downloadNotificationEnabled.collectAsStateWithLifecycle()
    val offlineToastEnabled by NotifPrefs.offlineToastEnabled.collectAsStateWithLifecycle()
    val offlineBannerEnabled by NotifPrefs.offlineBannerEnabled.collectAsStateWithLifecycle()

    GroupTitle("下载")
    SwitchRow(
        icon = Icons.Filled.Notifications,
        title = "下载进度通知",
        desc = "下载番剧时在状态栏显示进度与完成提醒",
        checked = downloadNotifEnabled,
        onCheckedChange = { NotifPrefs.setDownloadNotificationEnabled(it) },
    )

    GroupTitle("离线模式")
    SwitchRow(
        icon = Icons.Filled.CloudOff,
        title = "离线模式提示",
        desc = "断网进入离线模式或恢复联网时弹出提示",
        checked = offlineToastEnabled,
        onCheckedChange = { NotifPrefs.setOfflineToastEnabled(it) },
    )
    SwitchRow(
        icon = Icons.Filled.WifiOff,
        title = "离线模式顶部提醒",
        desc = "无网络时在界面顶部常驻显示离线横幅",
        checked = offlineBannerEnabled,
        onCheckedChange = { NotifPrefs.setOfflineBannerEnabled(it) },
    )

    Spacer(Modifier.height(24.dp))
}

/** 分组小标题。 */
@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** 带图标的开关行。 */
@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
