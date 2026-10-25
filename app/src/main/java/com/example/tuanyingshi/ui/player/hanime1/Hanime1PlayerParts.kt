package com.example.tuanyingshi.ui.player.hanime1

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Api
import com.example.tuanyingshi.ui.home.hanime1.Hanime1VideoCard
import com.example.tuanyingshi.ui.home.hanime1.hanime1CardWidth
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 播放页评论区（Hanime1 风格）：拉取 [Hanime1Api.comment] 并渲染头像 / 用户名 / 时间 / 正文 / 赞数。
 * 仅在主数据源 = [com.example.tuanyingshi.util.SourceMode.Hanime1] 时由播放页 COMMENTS Tab 渲染。
 */
@Composable
fun Hanime1CommentsBlock(videoCode: String) {
    var loading by remember { mutableStateOf(true) }
    var comments by remember { mutableStateOf<List<com.example.tuanyingshi.data.remote.hanime1.Hanime1Comment>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoCode) {
        loading = true
        error = null
        comments = withContext(Dispatchers.IO) {
            runCatching { Hanime1Api.comment(videoCode, 1).comments }.onFailure {
                it.log("Hanime1CommentsBlock")
                error = it.message
            }.getOrDefault(emptyList())
        }
        loading = false
    }

    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("评论（Hanime1）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            }
            error != null -> Text("评论加载失败：$error", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            comments.isEmpty() -> Text("暂无评论", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                comments.forEach { c -> Hanime1CommentRow(c) }
            }
        }
    }
}

@Composable
private fun Hanime1CommentRow(comment: com.example.tuanyingshi.data.remote.hanime1.Hanime1Comment) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = comment.avatar?.let {
                ImageRequest.Builder(LocalContext.current).data(it).crossfade(true).build()
            },
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.username ?: "匿名", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                comment.time?.let {
                    Text("  ·  $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(comment.text, style = MaterialTheme.typography.bodySmall)
            comment.likes?.let {
                Text("👍 $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 播放页相关推荐行（Hanime1 风格）：拉取该视频的「系列 + 相关推荐」并横向展示，复用首页同款视频卡片。
 * 仅在主数据源 = [com.example.tuanyingshi.util.SourceMode.Hanime1] 时由播放页简介区渲染。
 */
@Composable
fun Hanime1RelatedRow(videoCode: String, navController: NavController) {
    var loading by remember { mutableStateOf(true) }
    var videos by remember { mutableStateOf<List<com.example.tuanyingshi.data.remote.hanime1.Hanime1Video>>(emptyList()) }

    LaunchedEffect(videoCode) {
        loading = false
        videos = withContext(Dispatchers.IO) {
            runCatching {
                val d = Hanime1Api.video(videoCode)
                // ⚠️ 站点会把同一视频同时放进 playlist 与 related，直接合并会出现重复 videoCode，
                // LazyRow 用 videoCode 作 key 会抛「Key xxx was already used」崩溃 → 必须去重。
                ((d?.playlist ?: emptyList()) + (d?.related ?: emptyList()))
                    .distinctBy { it.videoCode }
            }.onFailure { it.log("Hanime1RelatedRow") }.getOrDefault(emptyList())
        }
    }

    if (!loading && videos.isNotEmpty()) {
        Column {
            Text("系列 / 相关推荐", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            val cardWidth = hanime1CardWidth()
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                itemsIndexed(videos, key = { index, v -> "${index}_${v.videoCode}" }) { _, v ->
                    Hanime1VideoCard(
                        video = v,
                        onClick = { navController.navigate(Screen.Detail.create("video/${v.videoCode}")) },
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
        }
    }
}
