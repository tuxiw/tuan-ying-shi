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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
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
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.openDownloadDir
import java.io.File

/**
 * 下载番剧组详情页：展示该番剧下每集的下载状态与进度，
 * 已完成的集可直接点击离线播放；支持单集删除与整组删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadGroupScreen(
    navController: NavController,
    detailUrl: String,
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 二级确认弹窗：删除整组 / 删除单集
    var pendingDeleteGroup by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<DownloadItemUi?>(null) }
    val groupItems = items.filter { it.detailUrl == detailUrl }
    val head = groupItems.firstOrNull()
    val title = head?.animeTitle ?: "下载详情"
    val img = head?.animeImg ?: ""

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { openDownloadDir(context) }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "打开目录")
                    }
                    IconButton(onClick = { pendingDeleteGroup = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "删除整组")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            )
        }
    ) { inner ->
        if (groupItems.isEmpty()) {
            CenteredMessage("该番剧没有下载任务", Modifier.fillMaxSize().padding(inner))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (img.isNotBlank()) {
                    item {
                        AsyncImage(
                            model = img,
                            contentDescription = title,
                            modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(groupItems, key = { it.episodeUrl }) { item ->
                    GroupEpisodeRow(
                        item = item,
                        onPlay = {
                            val file = File(item.savePath, item.saveName)
                            navController.navigate(
                                Screen.LocalPlayer.create(file.absolutePath, item.detailUrl, item.episodeUrl),
                            )
                        },
                        onRetry = { viewModel.retry(item) },
                        onDelete = { pendingDeleteItem = item },
                    )
                }
            }
        }
    }

    if (pendingDeleteGroup) {
        ConfirmDialog(
            title = "删除下载",
            text = "确定删除《$title》的全部下载吗？该操作不可恢复。",
            onConfirm = { viewModel.removeGroup(detailUrl) },
            onDismiss = { pendingDeleteGroup = false },
        )
    }

    if (pendingDeleteItem != null) {
        val it = pendingDeleteItem!!
        ConfirmDialog(
            title = "删除下载",
            text = "确定删除《${it.episodeName}》的下载缓存吗？该操作不可恢复。",
            onConfirm = { viewModel.remove(it) },
            onDismiss = { pendingDeleteItem = null },
        )
    }
}

@Composable
private fun GroupEpisodeRow(
    item: DownloadItemUi,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val done = item.status == DownloadState.DONE
    val failed = item.status == DownloadState.FAILED || item.status == DownloadState.PAUSED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .then(
                when {
                    done -> Modifier.clickable(onClick = onPlay)
                    failed -> Modifier.clickable(onClick = onRetry)
                    else -> Modifier
                }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "播放",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.episodeName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            when (item.status) {
                DownloadState.DONE -> Text(
                    "已完成 · 点击播放",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                DownloadState.FAILED -> Text(
                    "下载失败 · 点击重试",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                DownloadState.PAUSED -> Text(
                    "已暂停 · 点击继续",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${item.progress}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (failed) {
            IconButton(onClick = onRetry) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "重试",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
