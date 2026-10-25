package com.example.tuanyingshi.ui.home.hanime1

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Api
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Category
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Home
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Video
import com.example.tuanyingshi.ui.components.NsfwBlockedHint
import com.example.tuanyingshi.ui.components.currentWidthDp
import com.example.tuanyingshi.ui.components.isExpanded
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.components.maxContentWidth
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hanime1 风格首页内容（参考 Han1meViewer）。
 *
 * 仅在主数据源 = [com.example.tuanyingshi.util.SourceMode.Hanime1] 时由 [HomeScreen] 渲染：
 * 多个横向分类视频行（16:9 横版封面 + 标题 + 播放量/时长/日期）。
 * 顶栏（搜索 / 头像）仍复用 [HomeScreen] 的 Scaffold TopBar。
 *
 * 平板适配：
 * - 内容整体限宽（[maxContentWidth]）并居中，避免超宽屏拉曝；
 * - 分类行卡片宽度按屏幕反推（[hanime1CardWidth]），平板首屏铺满更多张。
 */
@Composable
fun Hanime1HomeContent(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    var home by remember { mutableStateOf<Hanime1Home?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Hanime1 全站成人内容：NSFW 屏蔽开启时不拉取、直接展示屏蔽提示（开关切换即时生效）
    val nsfwBlock by ContentPrefs.nsfwBlock.collectAsStateWithLifecycle()

    LaunchedEffect(nsfwBlock) {
        if (nsfwBlock) {
            home = null
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        home = withContext(Dispatchers.IO) {
            runCatching { Hanime1Api.home() }.onFailure {
                it.log("Hanime1HomeContent")
                error = "Hanime1 加载失败：${it.message}"
            }.getOrNull()
        }
        loading = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            nsfwBlock -> NsfwBlockedHint(Modifier.fillMaxSize())
            loading -> CenteredProgress()
            error != null -> CenteredMessage(error!!)
            home != null -> Hanime1HomeScroll(home!!, navController)
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CenteredMessage(msg: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun Hanime1HomeScroll(home: Hanime1Home, navController: NavController) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = maxContentWidth)
                .verticalScroll(scroll),
        ) {
            home.categories.forEach { cat ->
                Hanime1CategoryRow(cat = cat, navController = navController)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Hanime1 横滑卡片宽度自适应（首页 / 详情 / 播放页横滑行共用）。
 *
 * 手机固定 150dp；平板按「首屏铺满 N 张」反推（N = 大平板 5 / 平板 4），
 * 避免平板上卡片过小、一行浪费空间。
 *
 * @param padding 该横滑行的左右 contentPadding
 */
@Composable
fun hanime1CardWidth(padding: Dp = 12.dp): Dp {
    if (!isTablet()) return 150.dp
    val perRow = if (isExpanded()) 5 else 4
    val screenWidth = currentWidthDp().toFloat().coerceAtMost(maxContentWidth.value)
    val usable = (screenWidth - padding.value * 2f).coerceAtLeast(0f)
    return ((usable - 10f * (perRow - 1)) / perRow).coerceAtLeast(150f).dp
}

/** 单个横向分类行（标题 + 横滑卡片）。 */
@Composable
fun Hanime1CategoryRow(cat: Hanime1Category, navController: NavController) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                cat.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (cat.videos.isEmpty()) {
            Text(
                "暂无内容",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            return
        }
        val cardWidth = hanime1CardWidth()
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            // key 加 index 前缀：站点数据可能出现重复 videoCode，裸 code 作 key 会崩溃
            itemsIndexed(cat.videos, key = { index, video -> "${index}_${video.videoCode}" }) { _, video ->
                Hanime1VideoCard(
                    video = video,
                    onClick = { navController.navigate(Screen.Detail.create("video/${video.videoCode}")) },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

/**
 * 横版视频卡片（Han1meViewer 风格）：16:9 封面 + 底部播放量/时长遮罩 + 标题 + 副标题。
 */
@Composable
fun Hanime1VideoCard(
    video: Hanime1Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            ) {
                val ctx = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(video.coverUrl)
                        .crossfade(true).build(),
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // 底部遮罩：播放量 + 时长
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    if (video.views.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.PlayCircleOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(video.views, color = Color.White, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (video.duration.isNotBlank()) {
                        Text(video.duration, color = Color.White, fontSize = 10.sp)
                    }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = buildString {
                    if (video.artistName.isNotBlank()) append(video.artistName)
                    if (video.uploadTime.isNotBlank()) {
                        if (isNotEmpty()) append("  ·  ")
                        append(video.uploadTime)
                    }
                }
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
