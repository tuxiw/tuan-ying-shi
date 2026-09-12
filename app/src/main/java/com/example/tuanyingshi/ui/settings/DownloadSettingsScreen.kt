package com.example.tuanyingshi.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.util.DownloadPrefs
import com.example.tuanyingshi.util.downloadLocationDisplayName
import com.example.tuanyingshi.util.openDownloadDir
import com.example.tuanyingshi.util.setDownloadLocationUri
import com.example.tuanyingshi.util.shouldRequestStorageAccess
import com.example.tuanyingshi.util.rememberStorageAccessRequester

private const val MIN_VALUE = 1f
private const val MAX_VALUE = 10f

/**
 * 下载设置页（设置 → 通用 → 下载设置）：
 * - 下载位置：SAF 选择目录，未选择时用应用私有 Movies 目录；
 * - 同时下载：并发下载任务数（1..10）；
 * - 下载线程数：单个任务的分片并发数（1..10）；
 * - 打开下载目录：跳转到系统文件管理器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val concurrent by DownloadPrefs.concurrentDownloads.collectAsStateWithLifecycle()
    val threads by DownloadPrefs.threadPerTask.collectAsStateWithLifecycle()

    var locationName by remember { mutableStateOf(downloadLocationDisplayName(context)) }

    val storageRequester = rememberStorageAccessRequester { }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            setDownloadLocationUri(context, it.toString())
            locationName = downloadLocationDisplayName(context)
            // 选了自定义（共享存储）目录后，申请文件访问权限，否则后续写入/播放会失败
            if (shouldRequestStorageAccess(context)) storageRequester.request()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("下载设置", color = MaterialTheme.colorScheme.onBackground) },
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingItem(
                icon = Icons.Filled.Folder,
                title = "下载位置",
                trailing = {
                    Text(
                        text = abbreviateLeadingPath(locationName),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                },
                onClick = { treeLauncher.launch(null) },
            )
            DividerItem()

            // 缺少文件访问权限时（自定义下载位置需要），提示用户授权，否则写入/播放会失败
            if (shouldRequestStorageAccess(context) && !storageRequester.granted) {
                StoragePermissionBanner(onGrant = { storageRequester.request() })
                DividerItem()
            }

            SettingItem(
                icon = Icons.Filled.FolderOpen,
                title = "打开下载目录",
                onClick = { openDownloadDir(context) },
            )
            DividerItem()

            SliderSettingItem(
                title = "同时下载",
                desc = "同时进行的下载任务数量（下一个任务生效）",
                value = concurrent.toFloat(),
                onValueChange = { DownloadPrefs.setConcurrentDownloads(it.toInt()) },
            )
            DividerItem()

            SliderSettingItem(
                title = "下载线程数",
                desc = "单个任务的分片并发数：支持分片的视频按线程数分块并发，" +
                    "m3u8 同时拉取多个分片；源站不支持分片续传时自动退化为单线程（下一个下载任务生效）",
                value = threads.toFloat(),
                onValueChange = { DownloadPrefs.setThreadPerTask(it.toInt()) },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 缺少文件访问权限时的提示横幅（自定义下载位置需要）。 */
@Composable
private fun StoragePermissionBanner(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "需要文件访问权限",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "自定义下载位置位于共享存储，播放/写入其中的视频需授予「所有文件访问」权限。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
            Text("授予文件访问权限")
        }
    }
}

/** 滑动条设置项：标题 + 当前值 + Slider(1..10) + 说明文案。 */
@Composable
private fun SliderSettingItem(
    title: String,
    desc: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    // 拖动过程中本地保存位置，松手时再写入偏好，避免高频写 SharedPreferences
    var sliderPosition by remember(value) { mutableFloatStateOf(value) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${sliderPosition.toInt()}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onValueChange(sliderPosition) },
            valueRange = MIN_VALUE..MAX_VALUE,
            // 1..10 共 10 个整数档位，去掉首尾后需要 8 个中间步进点
            steps = (MAX_VALUE - MIN_VALUE).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = MIN_VALUE.toInt().toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MAX_VALUE.toInt().toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
