package com.example.tuanyingshi.ui.player

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.download.DownloadItemUi
import com.example.tuanyingshi.ui.download.DownloadState
import com.example.tuanyingshi.ui.download.DownloadViewModel
import com.example.tuanyingshi.util.isInAppPrivateDir
import com.example.tuanyingshi.util.PlaybackPrefs
import com.example.tuanyingshi.util.rememberStorageAccessRequester
import com.example.tuanyingshi.util.shouldRequestStorageAccess
import com.example.tuanyingshi.util.openDownloadDir
import com.lanlinju.videoplayer.VideoPlayer
import com.lanlinju.videoplayer.VideoPlayerControl
import com.lanlinju.videoplayer.rememberVideoPlayerState
import java.io.File

/**
 * 本地文件播放页：下载完成后离线观看合并好的 .ts 视频文件。
 *
 * 若该文件属于某个已下载番剧组（detailUrl 非空），底部会列出同组其它已下载集数，
 * 点击即可在同一页面内切换播放（满足「组内不同集数同时加载并可选择」）。
 *
 * 平板端适配：非全屏时左侧为播放器，右侧为信息面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlayerScreen(
    navController: NavController,
    filePath: String,
    detailUrl: String = "",
    episodeUrl: String = "",
) {
    val context = LocalContext.current
    val downloadViewModel: DownloadViewModel = hiltViewModel()
    val items by downloadViewModel.items.collectAsStateWithLifecycle()

    // 当前正在播放的集：默认由导航参数决定，选集时就地切换（不再打开新页面）
    var playingPath by remember(filePath, episodeUrl) { mutableStateOf(filePath) }
    var playingEpisodeUrl by remember(filePath, episodeUrl) { mutableStateOf(episodeUrl) }

    // 同组已下载集数（仅当来自某个番剧组时展示）
    val groupItems = if (detailUrl.isNotBlank()) {
        items.filter { it.detailUrl == detailUrl }
    } else {
        emptyList()
    }
    val currentFile = File(playingPath)
    val currentEpisode = groupItems.firstOrNull { it.episodeUrl == playingEpisodeUrl }
    val title = currentEpisode?.let { "${it.animeTitle} - ${it.episodeName}" }
        ?: currentFile.name.removeSuffix(".ts").ifBlank { currentFile.name }

    if (!currentFile.exists()) {
        // 文件可能被系统清理或用户手动删除
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SimpleBlackTopBar(title = title, onBack = { navController.popBackStack() })
            CenteredMessageLocal(
                "文件不存在或已被删除\n${currentFile.absolutePath}",
                Modifier.fillMaxSize().padding(16.dp),
            )
        }
        return
    }

    // 播放用户指定下载位置的视频需要文件访问权限（Android 11+ 为「所有文件访问」）。
    // 缺失且文件不在应用私有目录时，先弹出授权门，避免直接播放因无权限而失败。
    val storageRequester = rememberStorageAccessRequester { /* 授权状态变化会触发重组 */ }
    val needsStorageAccess = shouldRequestStorageAccess(context) && !isInAppPrivateDir(context, currentFile)
    if (needsStorageAccess && !storageRequester.granted) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SimpleBlackTopBar(title = title, onBack = { navController.popBackStack() })
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "需要文件访问权限",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "播放该下载位置的视频需要授予「所有文件访问」权限。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = currentFile.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { storageRequester.request() }) {
                        Text("授予文件访问权限")
                    }
                }
            }
        }
        return
    }

    val playerState = rememberVideoPlayerState(isAutoOrientation = false)
    // player 生命周期由本屏幕掌管：跨平板两栏/全屏分支切换时复用同一 player，
    // 不在 VideoPlayer 自身 dispose 时释放（releaseOnDispose = false），
    // 屏幕整体退出时在此统一释放，避免分支切换导致 player 被释放后新实例复用已死 player
    // （表现为平板黑屏 / 死线程 / 无法播放本地缓存视频）。
    DisposableEffect(Unit) {
        onDispose { playerState.player.release() }
    }
    val isFullscreen by playerState.isFullscreen

    // 仅进入时按开关初始化一次全屏状态（与 PlayerScreen 保持一致）。
    // 平板端由「平板端进入播放页自动全屏」控制，手机端由「进入播放页自动全屏」控制；
    // 开关关闭时不自动全屏（即便横屏也保持普通播放），仅用户手动点全屏按钮才进入全屏。
    val autoFullscreenOnEnter = if (isTablet()) PlaybackPrefs.isAutoFullscreenTablet() else PlaybackPrefs.isAutoFullscreen()
    LaunchedEffect(Unit) { playerState.control.setFullscreen(autoFullscreenOnEnter) }

    fun handleBack() {
        if (isFullscreen) playerState.control.setFullscreen(false)
        else navController.popBackStack()
    }

    // 全屏时隐藏系统栏；退出播放恢复（与 PlayerScreen 相同的范式）
    DisposableEffect(isFullscreen) {
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val w = (context as? Activity)?.window
            if (w != null) {
                WindowCompat.setDecorFitsSystemWindows(w, false)
                WindowCompat.getInsetsController(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val infoPanel: @Composable () -> Unit = {
        LocalPlayerInfoPanel(
            title = title,
            currentFile = currentFile,
            groupItems = groupItems,
            playingEpisodeUrl = playingEpisodeUrl,
            onEpisodeClick = { ep ->
                playingPath = File(ep.savePath, ep.saveName).absolutePath
                playingEpisodeUrl = ep.episodeUrl
            },
        )
    }

    // 全屏切换时务必让 VideoSurface（即 VideoPlayer）停留在组合树的同一槽位：
    // 否则平板下会从 Row 分支切到 else(Column) 分支，导致 VideoPlayer 被重挂，
    // 而本页未设 releaseOnDispose=false，重挂时旧 player 被释放、新实例复用已死 player
    // —— 表现为「平板无法播放本地缓存视频 / 切全屏黑屏」。
    // 故平板永远用 Row、手机永远用 Column；全屏只改播放器尺寸与信息面板显隐。
    @Composable
    fun VideoSurface(modifier: Modifier) {
        Box(modifier.background(Color.Black)) {
            VideoPlayer(
                modifier = Modifier.fillMaxSize(),
                url = currentFile.absolutePath,
                videoPosition = 0L,
                playerState = playerState,
                releaseOnDispose = false,
                onBackPress = { handleBack() },
                controller = {
                    VideoPlayerControl(
                        state = playerState,
                        title = title,
                        danmakuEnabled = false,
                        onBackClick = { handleBack() },
                    )
                }
            )
        }
    }

    val isTabletDevice = isTablet()
    if (isTabletDevice) {
        // 平板：左右分栏；全屏时播放器占满整行、隐藏信息面板（VideoSurface 始终在 Row 同一槽位）。
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            VideoSurface(
                Modifier
                    .weight(if (isFullscreen) 1f else 0.6f)
                    .fillMaxHeight(),
            )
            if (!isFullscreen) {
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    infoPanel()
                }
            }
        }
    } else {
        // 手机：竖排；全屏时播放器占满、隐藏信息面板（VideoSurface 始终在 Column 同一槽位）。
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(
                Modifier
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
            )

            if (!isFullscreen) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    infoPanel()
                }
            }
        }
    }
}

@Composable
private fun LocalPlayerInfoPanel(
    title: String,
    currentFile: File,
    groupItems: List<DownloadItemUi>,
    playingEpisodeUrl: String,
    onEpisodeClick: (DownloadItemUi) -> Unit,
) {
    val context = LocalContext.current

    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.VideoFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = formatFileSize(currentFile.length()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // 同组其它已下载集数：点击切换播放
    if (groupItems.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "本番剧下载集数（${groupItems.count { it.status == DownloadState.DONE }}/${groupItems.size}）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(groupItems, key = { it.episodeUrl }) { ep ->
                val selected = ep.episodeUrl == playingEpisodeUrl
                val playable = ep.status == DownloadState.DONE
                val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .then(if (playable) Modifier.clickable { onEpisodeClick(ep) } else Modifier)
                        .background(if (playable) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(1.dp, borderColor, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        ep.episodeName,
                        color = if (playable) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = currentFile.absolutePath,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = { openDownloadDir(context) }) {
        Icon(
            Icons.Filled.FolderOpen,
            contentDescription = null,
            modifier = Modifier.height(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("打开所在目录")
    }
    Spacer(Modifier.height(24.dp))
}

/** 简易顶栏（文件缺失时的错误页用）。 */
@Composable
private fun SimpleBlackTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CenteredMessageLocal(message: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 字节数 → 可读大小。 */
internal fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
