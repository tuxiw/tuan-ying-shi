package com.example.tuanyingshi.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.components.maxContentWidth
import com.example.tuanyingshi.ui.navigation.Screen
import java.io.File

/**
 * 我的下载页：按番剧分组展示（同一 detailUrl 为一组），
 * 点击组进入「组详情页」查看每集进度。
 * 顶部第一个按钮（下载图标）跳转到「下载设置」页（设置 → 通用 → 下载设置），
 * 在那里集中管理下载位置 / 同时下载 / 下载线程数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    navController: NavController,
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 二级确认弹窗：清理缓存 / 删除某个番剧组
    var pendingClear by remember { mutableStateOf(false) }
    var pendingDeleteGroup by remember { mutableStateOf<DownloadGroupUi?>(null) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("我的下载") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 第一个图标：进入下载设置（下载位置 / 同时下载 / 下载线程数）
                    IconButton(onClick = { navController.navigate(Screen.DownloadSettings.route) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "下载设置")
                    }
                    if (groups.any { g -> g.doneCount > 0 || g.failedCount > 0 }) {
                        IconButton(onClick = { pendingClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清空已完成")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            )
        }
    ) { inner ->
        if (groups.isEmpty()) {
            CenteredMessage("还没有下载任务", Modifier.fillMaxSize().padding(inner))
        } else if (isTablet()) {
            // 平板端：两列网格展示下载分组，充分利用横向空间
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().widthIn(max = maxContentWidth).padding(inner),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.detailUrl }) { group ->
                    DownloadGroupCard(
                        group = group,
                        onClick = { navController.navigate(Screen.DownloadGroup.create(group.detailUrl)) },
                        onPlay = { playFirstDone(group, navController) },
                        onDelete = { pendingDeleteGroup = group },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = maxContentWidth).padding(inner),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.detailUrl }) { group ->
                    DownloadGroupCard(
                        group = group,
                        onClick = { navController.navigate(Screen.DownloadGroup.create(group.detailUrl)) },
                        onPlay = { playFirstDone(group, navController) },
                        onDelete = { pendingDeleteGroup = group },
                    )
                }
            }
        }
    }

    if (pendingClear) {
        ConfirmDialog(
            title = "清理缓存",
            text = "确定清理所有已完成 / 已失败的下载缓存吗？该操作不可恢复。",
            confirmText = "清理",
            onConfirm = { viewModel.clearFinished() },
            onDismiss = { pendingClear = false },
        )
    }

    if (pendingDeleteGroup != null) {
        val g = pendingDeleteGroup!!
        ConfirmDialog(
            title = "删除下载",
            text = "确定删除《${g.animeTitle}》的全部下载（共 ${g.total} 集）吗？该操作不可恢复。",
            onConfirm = { viewModel.removeGroup(g.detailUrl) },
            onDismiss = { pendingDeleteGroup = null },
        )
    }
}

@Composable
private fun DownloadGroupCard(
    group: DownloadGroupUi,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面
        if (group.animeImg.isNotBlank()) {
            AsyncImage(
                model = group.animeImg,
                contentDescription = group.animeTitle,
                modifier = Modifier.size(56.dp, 75.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.size(56.dp, 75.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.animeTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (group.allDone) "已全部下载完成" else "${group.doneCount}/${group.total} 集 · ${group.overallProgress}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (group.overallProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.DeleteSweep,
                contentDescription = "删除组",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 有已完成的集时，显示播放按钮（离线本地播放第一集已完成的内容）
        if (group.doneCount > 0) {
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 播放分组内第一集已下载完成的视频（离线本地播放）。 */
private fun playFirstDone(group: DownloadGroupUi, navController: NavController) {
    val first = group.items.firstOrNull { it.status == DownloadState.DONE } ?: return
    val file = File(first.savePath, first.saveName)
    navController.navigate(Screen.LocalPlayer.create(file.absolutePath, first.detailUrl, first.episodeUrl))
}
