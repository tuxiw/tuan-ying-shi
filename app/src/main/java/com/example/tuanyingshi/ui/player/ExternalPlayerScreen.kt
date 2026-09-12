package com.example.tuanyingshi.ui.player

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.util.contentUriToFilePath
import com.example.tuanyingshi.util.copyContentUriToCache
import com.example.tuanyingshi.util.isInAppPrivateDir
import com.example.tuanyingshi.util.rememberStorageAccessRequester
import com.example.tuanyingshi.util.shouldRequestStorageAccess
import com.lanlinju.videoplayer.VideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lanlinju.videoplayer.VideoPlayerControl
import com.lanlinju.videoplayer.rememberVideoPlayerState
import java.io.File

/**
 * 外部视频播放页：用户在系统文件管理器 / 浏览器中「用本 App 打开」视频文件或链接时进入。
 *
 * 与 [LocalPlayerScreen]（仅本地 .ts 文件绝对路径）不同，这里接收的是更通用的 [uri]：
 * - content://（文件管理器共享，最常见）
 * - file://（部分管理器直传路径）
 * - http(s)://（.m3u8 直播/点播链接等）
 *
 * 底层复用同一套 [VideoPlayer]，直接把 uri 交给 ExoPlayer 即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalPlayerScreen(
    navController: NavController,
    uri: String,
    title: String?,
) {
    val context = LocalContext.current
    val parsedUri = runCatching { Uri.parse(uri) }.getOrNull()

    if (parsedUri == null) {
        SimpleTopBar(title = title ?: "视频", onBack = { navController.popBackStack() })
        CenteredMessage("无效的视频地址：\n$uri")
        return
    }

    // ── 解析可播放目标 ──────────────────────────────────────────────
    // content:// 直接交给 ExoPlayer 会因 provider 未导出/未授权而抛 SecurityException。
    // 优先把能映射为真实文件的 content:// 解析成文件路径（走「所有文件访问」权限门）；
    // 无法映射的 content://（云盘等）再兜底拷贝到应用缓存播放。
    val scheme = parsedUri.scheme
    val resolvedFile: File? = when (scheme) {
        "file" -> parsedUri.path?.let { File(it) }
        "content" -> contentUriToFilePath(context, parsedUri)?.let { File(it) }
        else -> null
    }

    val needsStorageAccess = resolvedFile != null &&
        shouldRequestStorageAccess(context) &&
        !isInAppPrivateDir(context, resolvedFile)

    val storageRequester = rememberStorageAccessRequester { }
    if (needsStorageAccess && !storageRequester.granted) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SimpleTopBar(title = title ?: "视频", onBack = { navController.popBackStack() })
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
                        text = "播放该本地视频文件需要授予「所有文件访问」权限。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = resolvedFile!!.absolutePath,
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

    // 纯 content:// 且无法映射为真实文件：复制到缓存（需共享方授予的临时 URI 权限）
    var cacheFile by remember(parsedUri) { mutableStateOf<File?>(null) }
    var cacheError by remember(parsedUri) { mutableStateOf<String?>(null) }
    LaunchedEffect(parsedUri) {
        if (scheme == "content" && resolvedFile == null) {
            withContext(Dispatchers.IO) {
                runCatching { copyContentUriToCache(context, parsedUri) }
                    .onSuccess { cacheFile = it }
                    .onFailure { cacheError = it.message ?: "无法读取该视频" }
            }
        }
    }

    val playUrl: String? = when {
        resolvedFile != null -> resolvedFile.absolutePath
        scheme == "content" -> cacheFile?.absolutePath
        else -> uri // http(s)://
    }

    // 纯 content:// 仍在准备或已失败
    if (scheme == "content" && resolvedFile == null) {
        if (cacheError != null) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SimpleTopBar(title = title ?: "视频", onBack = { navController.popBackStack() })
                CenteredMessage("无法播放该视频：\n$cacheError")
            }
            return
        }
        if (cacheFile == null) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SimpleTopBar(title = title ?: "视频", onBack = { navController.popBackStack() })
                CenteredMessage("正在准备播放…")
            }
            return
        }
    }

    // 展示名与大小（content:// 走 ContentResolver，其余回退到路径片段 / 文件名）
    val nameFallback = title ?: resolvedFile?.name ?: cacheFile?.name
    val (displayName, sizeBytes) = rememberContentInfo(context, parsedUri, nameFallback)

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val playerState = rememberVideoPlayerState(isAutoOrientation = false)
    // player 生命周期由本屏幕掌管：跨全屏切换 / 旋屏重组时复用同一 player，
    // 不在 VideoPlayer 自身 dispose 时释放（releaseOnDispose = false），
    // 屏幕整体退出时在此统一释放，避免平板等因重挂/重建导致 player 被释放后复用已死 player
    // （表现为无法播放 / 死线程 / 黑屏）。
    DisposableEffect(Unit) {
        onDispose { playerState.player.release() }
    }
    val isFullscreen by playerState.isFullscreen

    LaunchedEffect(Unit) { playerState.control.setFullscreen(isLandscape) }

    fun handleBack() {
        if (isFullscreen) playerState.control.setFullscreen(false)
        else navController.popBackStack()
    }

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

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black),
        ) {
            VideoPlayer(
                modifier = Modifier.fillMaxSize(),
                url = playUrl ?: uri, // 已解析为真实文件路径或缓存文件；content/http(s)/m3u8 均可
                videoPosition = 0L,
                playerState = playerState,
                releaseOnDispose = false,
                onBackPress = { handleBack() },
                controller = {
                    VideoPlayerControl(
                        state = playerState,
                        title = displayName,
                        danmakuEnabled = false,
                        onBackClick = { handleBack() },
                    )
                }
            )
        }

        if (!isFullscreen) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = displayName,
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
                        text = if (sizeBytes >= 0) formatFileSize(sizeBytes) else "外部视频",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = uri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 从 content:// 读取显示名与大小；非 content 则回退到路径片段去掉扩展名。 */
@Composable
private fun rememberContentInfo(
    context: Context,
    uri: Uri,
    fallbackTitle: String?,
): Pair<String, Long> {
    // 用 remember 缓存，避免每次重组重复查询
    val cached = androidx.compose.runtime.remember(uri) {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            queryContentInfo(context, uri) ?: (fallbackTitle ?: uri.lastPathSegment ?: "视频") to -1L
        } else {
            val seg = uri.lastPathSegment ?: fallbackTitle ?: "视频"
            seg.substringBeforeLast('.') to -1L
        }
    }
    return cached
}

private fun queryContentInfo(context: Context, uri: Uri): Pair<String, Long>? {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0) ?: uri.lastPathSegment ?: "视频"
                val size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
                name to size
            } else {
                null
            }
        }
    }.getOrNull()
}

/** 简易顶栏（错误页用）。 */
@Composable
private fun SimpleTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
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
