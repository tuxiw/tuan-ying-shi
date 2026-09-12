package com.example.tuanyingshi.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import coil.annotation.ExperimentalCoilApi
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.imageLoader
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.ui.player.formatFileSize
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.util.DebugPrefs
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FilterList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 通用设置页：承载下载位置、清理缓存等通用项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    navController: NavController,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("通用设置", color = MaterialTheme.colorScheme.onBackground) },
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
            GeneralSettingsContent(navController)
        }
    }
}

/**
 * 通用设置内容区（供手机页与平板左右布局右侧共用）。
 */
@Composable
fun GeneralSettingsContent(
    navController: NavController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheSizeText by remember { mutableStateOf("计算中…") }

    val nsfwBlock by ContentPrefs.nsfwBlock.collectAsStateWithLifecycle()
    val hideWatched by ContentPrefs.hideWatched.collectAsStateWithLifecycle()
    val hideHomeBanner by ContentPrefs.hideHomeBanner.collectAsStateWithLifecycle()
    val filterResetOnExit by ContentPrefs.filterResetOnExit.collectAsStateWithLifecycle()
    val exitConfirm by ContentPrefs.exitConfirm.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        cacheSizeText = withContext(Dispatchers.IO) { formatFileSize(dirSize(context.cacheDir)) }
    }

    // NSFW 内容屏蔽：默认开启，隐藏含成人 / 肉番 / NSFW / R18 标签的番剧
    SwitchRow(
        icon = Icons.Filled.Block,
        title = "NSFW 内容屏蔽",
        desc = "隐藏含成人 / 肉番 / NSFW / R18 标签的番剧",
        checked = nsfwBlock,
        onCheckedChange = { ContentPrefs.setNsfwBlock(it) },
    )
    // 不显示看过和抛弃的条目：默认关闭（显示）；开启后隐藏浏览/播放过与已抛弃的番剧
    SwitchRow(
        icon = Icons.Filled.VisibilityOff,
        title = "不显示看过和抛弃的条目",
        desc = "从列表隐藏「浏览/播放过」与「已抛弃」的番剧",
        checked = hideWatched,
        onCheckedChange = { ContentPrefs.setHideWatched(it) },
    )
    // 移除首页轮播图：默认关闭（显示）；开启后隐藏「推荐」页顶部的轮播 Banner
    SwitchRow(
        icon = Icons.Filled.VisibilityOff,
        title = "移除首页轮播图",
        desc = "隐藏「推荐」页顶部的轮播 Banner，内容更紧凑",
        checked = hideHomeBanner,
        onCheckedChange = { ContentPrefs.setHideHomeBanner(it) },
    )
    // 分类浏览筛选重置策略：默认开启，仅退出 App / 切换数据源时重置；关闭则每次进入分类页都重置
    SwitchRow(
        icon = Icons.Filled.FilterList,
        title = "筛选仅在退出或切换资源时重置",
        desc = "开启后分类浏览的筛选条件在导航间保留，仅退出或切换数据源时清空",
        checked = filterResetOnExit,
        onCheckedChange = { ContentPrefs.setFilterResetOnExit(it) },
    )
    // 二级退出确认：默认开启，连按两次返回键才退出 App
    SwitchRow(
        icon = Icons.Filled.ExitToApp,
        title = "二级退出确认",
        desc = "连按两次返回键才退出应用",
        checked = exitConfirm,
        onCheckedChange = { ContentPrefs.setExitConfirm(it) },
    )
    DividerItem()

    // 下载位置 / 同时下载 / 下载线程数 已迁移到「下载设置」页
    SettingItem(
        icon = Icons.Filled.Download,
        title = "下载设置",
        trailing = {
            Text(
                text = "位置 · 并发 · 线程",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        },
        onClick = { navController.navigate(Screen.DownloadSettings.route) },
    )
    DividerItem()

    // 播放设置：自动换集等播放相关开关
    SettingItem(
        icon = Icons.Filled.PlayCircle,
        title = "播放设置",
        trailing = {
            Text(
                text = "自动换集 · 自动换源",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        },
        onClick = { navController.navigate(Screen.PlaybackSettings.route) },
    )
    DividerItem()

    SettingItem(
        icon = Icons.Filled.CleanHands,
        title = "清理缓存",
        trailing = {
            Text(
                text = "已使用 $cacheSizeText",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        onClick = {
            scope.launch {
                val freed = withContext(Dispatchers.IO) { clearAppCache(context) }
                cacheSizeText = withContext(Dispatchers.IO) { formatFileSize(dirSize(context.cacheDir)) }
                Toast.makeText(context, "已清理 ${formatFileSize(freed)}", Toast.LENGTH_SHORT).show()
            }
        },
    )

    Spacer(Modifier.height(24.dp))
}

/** 单个设置项：图标 + 标题 + (可选尾部) + 右箭头。 */
@Composable
internal fun SettingItem(
    icon: ImageVector,
    title: String,
    trailing: @Composable (() -> Unit)? = null,
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
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 组间分隔线（极细，与背景同色系）。 */
@Composable
internal fun DividerItem() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(8.dp),
    )
}

/**
 * 长路径省略前面（保留末尾几个目录段），避免设置行右侧显示不下。
 * 例：`存储目录: /storage/emulated/0/Download/xxx` → `…/Download/xxx`
 */
internal fun abbreviateLeadingPath(path: String, maxSegments: Int = 3): String {
    if (path.length <= 24) return path
    val segs = path.split("/").filter { it.isNotBlank() }
    val tail = segs.takeLast(maxSegments).joinToString("/")
    return "…/$tail"
}

/** 递归统计目录大小（含子目录）。 */
internal fun dirSize(dir: File): Long =
    if (!dir.exists()) 0L
    else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

/**
 * 清理应用缓存：清空 cacheDir（含 Coil / WebView / OkHttp 等子缓存）+
 * Coil 内存与磁盘图片缓存。返回释放的字节数。
 */
@OptIn(ExperimentalCoilApi::class)
internal fun clearAppCache(context: android.content.Context): Long {
    val cacheDir = context.cacheDir
    val size = dirSize(cacheDir)
    runCatching { cacheDir.deleteRecursively() }
    runCatching { cacheDir.mkdirs() }
    // Coil 图片缓存（TuanyingApp 自定义 ImageLoader）
    runCatching { context.imageLoader.memoryCache?.clear() }
    runCatching { context.imageLoader.diskCache?.clear() }
    return size
}
