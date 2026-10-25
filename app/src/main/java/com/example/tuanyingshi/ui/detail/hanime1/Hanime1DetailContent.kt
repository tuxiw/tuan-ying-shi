package com.example.tuanyingshi.ui.detail.hanime1

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Api
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Detail
import com.example.tuanyingshi.data.remote.hanime1.Hanime1Video
import com.example.tuanyingshi.ui.components.ExpandableText
import com.example.tuanyingshi.ui.components.NsfwBlockedHint
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.components.maxContentWidth
import com.example.tuanyingshi.ui.home.hanime1.Hanime1VideoCard
import com.example.tuanyingshi.ui.home.hanime1.hanime1CardWidth
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 平板双栏时左栏（主信息）占比；右栏 = 1 - 该值，与通用详情页 0.38/0.62 比例一致。 */
private const val TABLET_MAIN_FRACTION = 0.38f

/**
 * Hanime1 风格番剧详情页内容（参考 Han1meViewer 的 VideoIntroductionScreen）。
 *
 * 仅在主数据源 = [com.example.tuanyingshi.util.SourceMode.Hanime1] 时由 [DetailScreen] 委托渲染：
 * 横版大封面 + 标题/中文标题 + 作者 + 标签 + 简介 + 播放/收藏 + 系列视频 + 相关推荐。
 * 顶栏（返回 / 抛弃）仍由 [DetailScreen] 的 Scaffold 提供。
 *
 * 平板适配：双栏布局（左 38% 封面+标题+作者+标签+操作，右 62% 简介+系列+推荐，各栏独立滚动），
 * 内容整体限宽（[maxContentWidth]）居中；手机为单栏竖滑。
 */
@Composable
fun Hanime1DetailContent(
    navController: NavController,
    detailUrl: String,
    modifier: Modifier = Modifier,
) {
    val code = detailUrl.substringAfterLast("/").substringBefore("?")
    var detail by remember { mutableStateOf<Hanime1Detail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var favorited by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Hanime1 全站成人内容：NSFW 屏蔽开启时不加载详情，直接展示屏蔽提示
    val nsfwBlock by ContentPrefs.nsfwBlock.collectAsStateWithLifecycle()

    LaunchedEffect(code, nsfwBlock) {
        if (nsfwBlock) {
            detail = null
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        detail = withContext(Dispatchers.IO) {
            runCatching { Hanime1Api.video(code) }.onFailure {
                it.log("Hanime1DetailContent")
                error = "加载失败：${it.message}"
            }.getOrNull()
        }
        loading = false
    }

    val d = detail
    when {
        nsfwBlock -> NsfwBlockedHint(modifier.fillMaxSize())
        loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        error != null -> Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        d != null -> Hanime1DetailScroll(
            detail = d,
            favorited = favorited,
            onToggleFavorite = {
                favorited = !favorited
                // 服务端收藏需要登录态（Cloudflare 过盾 + 会话 Cookie）；未登录会失败，仅做本地标记 + 提示。
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Hanime1Api.favorite(code, favorited) }
                    if (!ok) android.widget.Toast.makeText(
                        context, if (favorited) "收藏需登录 Hanime1" else "已取消收藏（本地）",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onPlay = {
                navController.navigate(Screen.Player.create("video/$code", "video/$code", "builtin_hanime1"))
            },
            onOpenVideo = { vc -> navController.navigate(Screen.Detail.create("video/$vc")) },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hanime1DetailScroll(
    detail: Hanime1Detail,
    favorited: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isTablet()) {
        // 平板双栏：左 38% 主信息 / 右 62% 简介与推荐，两栏各自独立滚动，互不影响。
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = maxContentWidth),
            ) {
                Column(
                    modifier = Modifier
                        .weight(TABLET_MAIN_FRACTION)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Hanime1MainInfo(detail, favorited, onToggleFavorite, onPlay)
                }
                Column(
                    modifier = Modifier
                        .weight(1f - TABLET_MAIN_FRACTION)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Hanime1DetailExtras(detail, onOpenVideo)
                }
            }
        }
    } else {
        // 手机：单栏竖滑。
        Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Hanime1MainInfo(detail, favorited, onToggleFavorite, onPlay)
            Hanime1DetailExtras(detail, onOpenVideo)
        }
    }
}

/** 主信息列：横版大封面 + 标题/中文标题 + 作者 + 标签 + 播放/收藏 + 元信息。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hanime1MainInfo(
    detail: Hanime1Detail,
    favorited: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
) {
    // 横版大封面
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(12.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        AsyncImage(
            model = detail.coverUrl,
            contentDescription = detail.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(detail.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        detail.chineseTitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 作者
        if (detail.artistName != null || detail.artistGenre != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                detail.artistAvatar?.let { avatar ->
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    detail.artistName?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    }
                    detail.artistGenre?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 标签
        if (detail.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                detail.tags.forEach { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50),
                        tonalElevation = 1.dp,
                    ) {
                        Text(
                            "#$tag",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        // 操作行：播放 + 收藏
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("播放")
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(
                    imageVector = if (favorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (favorited) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 元信息：观看次数 / 上传时间
        Row(Modifier.padding(vertical = 6.dp)) {
            detail.uploadTime?.let {
                Text("上传：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            detail.views?.let {
                Spacer(Modifier.width(12.dp))
                Text("播放：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 附加内容列：简介 + 系列视频 + 相关推荐。 */
@Composable
private fun Hanime1DetailExtras(
    detail: Hanime1Detail,
    onOpenVideo: (String) -> Unit,
) {
    // 简介
    if (!detail.introduction.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(
            "简介",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ExpandableText(
            text = detail.introduction,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    // 系列视频
    if (detail.playlist.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SectionTitle("系列 / 同系列影片")
        Hanime1VideoRow(detail.playlist, onOpenVideo)
    }

    // 相关推荐
    if (detail.related.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SectionTitle("相关推荐")
        Hanime1VideoRow(detail.related, onOpenVideo)
    }

    Spacer(Modifier.height(28.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun Hanime1VideoRow(videos: List<Hanime1Video>, onOpenVideo: (String) -> Unit) {
    val cardWidth = hanime1CardWidth(padding = 16.dp)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        // key 加 index 前缀：站点数据可能出现重复 videoCode，裸 code 作 key 会崩溃
        itemsIndexed(videos, key = { index, v -> "${index}_${v.videoCode}" }) { _, v ->
            Hanime1VideoCard(
                video = v,
                onClick = { onOpenVideo(v.videoCode) },
                modifier = Modifier.width(cardWidth),
            )
        }
    }
}
