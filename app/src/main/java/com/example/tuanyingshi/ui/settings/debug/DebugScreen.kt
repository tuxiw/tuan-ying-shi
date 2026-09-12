package com.example.tuanyingshi.ui.settings.debug

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.components.maxContentWidth
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.ui.player.formatFileSize
import com.example.tuanyingshi.util.DebugLogCollector
import com.example.tuanyingshi.util.DebugLogLevel
import com.example.tuanyingshi.util.DebugLogLine
import com.example.tuanyingshi.util.DebugPrefs
import com.example.tuanyingshi.util.UpdateChecker
import com.example.tuanyingshi.util.UpdateDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LEVEL_COLOR = mapOf(
    DebugLogLevel.ERROR to Color(0xFFE53935),
    DebugLogLevel.WARN to Color(0xFFFFB300),
    DebugLogLevel.INFO to Color(0xFF42A5F5),
    DebugLogLevel.DEBUG to Color(0xFF9E9E9E),
    DebugLogLevel.VERBOSE to Color(0xFF789262),
)

private enum class LogFilter { ALL, ERROR, WEBVIEW }
private enum class LogTab { GENERAL, MATCH }

/**
 * 调试模式主页（默认隐藏，需关于页连点版本号开启）。
 * 包含：
 * - WebView 日志开关（开启后收集 WebView 控制台 / 网络 / 源层异常）；
 * - 实时日志 / 错误日志查看器（可按级别筛选、清空、复制）；
 * - 数据源诊断工具入口（仅在此处可达）；
 * - 更新功能测试：测试更新弹窗（预览真实更新 UI）+ 更新包下载测试（实拉 APK 验证应用内更新链路）。
 *
 * 平板适配：宽度 >= 600dp 时采用左右双栏（左=控制区，右=日志查看器），并限制最大内容宽度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(navController: NavController) {
    val tablet = isTablet()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("调试模式", color = MaterialTheme.colorScheme.onBackground) },
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
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentAlignment = if (tablet) Alignment.TopCenter else Alignment.TopStart,
        ) {
            if (tablet) {
                Row(
                    modifier = Modifier
                        .widthIn(max = maxContentWidth)
                        .fillMaxWidth()
                        .padding(16.dp, 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DebugControls(navController)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        DebugLogArea()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = maxContentWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp, 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DebugControls(navController)
                    DebugLogArea()
                }
            }
        }
    }
}

/** 调试页控制区：开关 + 诊断入口 + 更新功能测试。 */
@Composable
private fun DebugControls(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val debugModeEnabled by DebugPrefs.debugModeEnabled.collectAsStateWithLifecycle()
    val webViewLogEnabled by DebugPrefs.webViewLogEnabled.collectAsStateWithLifecycle()

    // 更新包下载测试状态
    var dlStatus by remember { mutableStateOf("未开始") }
    var dlProgress by remember { mutableStateOf(-1) }
    var dlPath by remember { mutableStateOf<String?>(null) }

    // ===== 关闭调试模式 =====
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "调试模式已开启",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "关闭后将隐藏「设置 → 调试模式」入口",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = debugModeEnabled,
            onCheckedChange = { enabled ->
                if (!enabled) {
                    DebugPrefs.setDebugModeEnabled(false)
                    navController.popBackStack()
                }
            },
        )
    }

    // ===== WebView 日志开关 =====
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "开启调试日志",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "收集 WebView 控制台、视频匹配、网络请求与源层异常（关闭后清空）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = webViewLogEnabled,
            onCheckedChange = { DebugPrefs.setWebViewLogEnabled(it) },
        )
    }

    // ===== 更新功能测试 =====
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
    ) {
        Text(
            text = "更新功能测试",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))

        // 测试更新弹窗：拉取远端 JSON 并强制弹出真实更新对话框（预览 UI / 测试应用内更新与渠道选择）
        Button(
            onClick = {
                scope.launch {
                    val info = UpdateChecker.fetchUpdateInfo()
                    if (info != null) {
                        UpdateChecker.showAvailable(info)
                    } else {
                        Toast.makeText(context, "拉取更新信息失败", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("测试更新弹窗")
        }

        Spacer(Modifier.height(8.dp))

        // 更新包下载测试：按架构/语言选包并实拉 APK，验证应用内更新链路
        Button(
            onClick = {
                dlPath = null
                dlProgress = -1
                dlStatus = "正在获取更新信息…"
                scope.launch {
                    val info = UpdateChecker.fetchUpdateInfo()
                    if (info == null) {
                        dlStatus = "获取更新信息失败"
                        return@launch
                    }
                    val target = UpdateChecker.pickInAppUpdate(info, context)
                    if (target == null) {
                        // 应用内无对应包：回退网页下载页
                        val web = UpdateChecker.pickFallbackWebUrl(info)
                        if (web != null) {
                            dlStatus = "无应用内安装包，已回退到网页下载：$web"
                            openUrl(context, web)
                        } else {
                            dlStatus = "无可用下载地址"
                        }
                        return@launch
                    }
                    dlStatus = "正在从 ${target.channel} 下载：${target.url}"
                    val file = UpdateDownloader.download(context, target.url, target.fileName) { p ->
                        dlProgress = p
                        dlStatus = if (p >= 0) "正在下载… $p%" else "正在下载…"
                    }
                    if (file == null) {
                        dlStatus = "下载失败，请检查网络或前往发布页下载"
                    } else {
                        dlStatus = "下载完成：${file.name}（${formatFileSize(file.length())}）"
                        dlPath = file.absolutePath
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("更新包下载测试")
        }

        Spacer(Modifier.height(10.dp))

        // 下载进度 / 状态展示
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(10.dp),
        ) {
            // 自定义进度条（兼容不同 Material3 版本）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
            ) {
                val frac = if (dlProgress < 0) 0.06f else (dlProgress / 100f).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = dlStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dlPath != null) {
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = "路径：$dlPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }

    // ===== 数据源诊断工具（仅此处可达） =====
    Button(
        onClick = { navController.navigate(Screen.Diagnostics.route) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Filled.BugReport,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text("数据源诊断（排错工具）")
    }
}

/**
 * 调试页日志区：顶部 Tab 切换「日志」与「视频匹配日志」，
 * 两个分区的日志均支持筛选、清空与导出。
 */
@Composable
private fun DebugLogArea() {
    var tab by remember { mutableStateOf(LogTab.GENERAL) }
    val webViewLogEnabled by DebugPrefs.webViewLogEnabled.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = tab == LogTab.GENERAL,
                onClick = { tab = LogTab.GENERAL },
                label = { Text("日志") },
            )
            FilterChip(
                selected = tab == LogTab.MATCH,
                onClick = { tab = LogTab.MATCH },
                label = { Text("视频匹配日志") },
            )
        }
        Spacer(Modifier.height(10.dp))
        if (tab == LogTab.GENERAL) {
            LogViewerCard(
                title = "日志 / 错误日志",
                logs = DebugLogCollector.logs.collectAsStateWithLifecycle().value,
                exportTitle = "调试",
                showWebViewFilter = true,
                enabled = webViewLogEnabled,
                onClear = { DebugLogCollector.clear() },
            )
        } else {
            LogViewerCard(
                title = "视频匹配日志",
                logs = DebugLogCollector.matchLogs.collectAsStateWithLifecycle().value,
                exportTitle = "视频匹配",
                showWebViewFilter = false,
                enabled = webViewLogEnabled,
                onClear = { DebugLogCollector.clearMatch() },
            )
        }
    }
}

/**
 * 通用日志卡片：可按级别筛选、清空、导出。
 * @param showWebViewFilter 是否显示「仅 WebView」筛选（视频匹配日志不需要）。
 * @param onClear 清空回调（通用日志 / 视频匹配日志分表清空）。
 */
@Composable
private fun LogViewerCard(
    title: String,
    logs: List<DebugLogLine>,
    exportTitle: String,
    showWebViewFilter: Boolean,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(LogFilter.ALL) }

    val filtered = remember(logs, filter) {
        when (filter) {
            LogFilter.ALL -> logs
            LogFilter.ERROR -> logs.filter { it.level == DebugLogLevel.ERROR || it.level == DebugLogLevel.WARN }
            LogFilter.WEBVIEW -> logs.filter { it.source == "WebView" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { exportLogs(context, logs, exportTitle) }) {
                Text("导出")
            }
            TextButton(onClick = onClear) {
                Text("清空")
            }
        }

        Spacer(Modifier.height(8.dp))

        // 筛选 chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == LogFilter.ALL,
                onClick = { filter = LogFilter.ALL },
                label = { Text("全部 (${logs.size})") },
            )
            FilterChip(
                selected = filter == LogFilter.ERROR,
                onClick = { filter = LogFilter.ERROR },
                label = { Text("错误/警告") },
            )
            if (showWebViewFilter) {
                FilterChip(
                    selected = filter == LogFilter.WEBVIEW,
                    onClick = { filter = LogFilter.WEBVIEW },
                    label = { Text("仅 WebView") },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (enabled) "暂无日志，操作 App 后会在此显示" else "请先开启「调试日志」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    filtered.forEach { line ->
                        val color = LEVEL_COLOR[line.level] ?: MaterialTheme.colorScheme.onSurfaceVariant
                        Text(
                            text = formatLine(line),
                            color = color,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 单行日志文本：时间 + 级别 + 来源/标签 + 内容。 */
private fun formatLine(line: DebugLogLine): String {
    val lvl = when (line.level) {
        DebugLogLevel.ERROR -> "E"
        DebugLogLevel.WARN -> "W"
        DebugLogLevel.INFO -> "I"
        DebugLogLevel.DEBUG -> "D"
        DebugLogLevel.VERBOSE -> "V"
    }
    return "${line.timeText} $lvl ${line.source}/${line.tag}: ${line.message}"
}

/**
 * 导出调试日志：把当前分区全部日志写入应用私有 Download 目录的 txt 文件，
 * 并通过系统分享（Chooser）交给用户保存 / 发送。两个分区的日志都走这里，互不影响。
 */
private fun exportLogs(context: android.content.Context, lines: List<DebugLogLine>, exportTitle: String) {
    if (lines.isEmpty()) {
        Toast.makeText(context, "暂无日志可导出", Toast.LENGTH_SHORT).show()
        return
    }
    CoroutineScope(Dispatchers.IO).launch {
        val result = runCatching {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            dir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "tuanyingshi_${exportTitle}_$ts.txt")
            file.bufferedWriter().use { w ->
                w.write("团影视 ${exportTitle} 日志导出 @ $ts\n")
                w.write("共 ${lines.size} 条\n")
                w.write("================================\n")
                lines.forEach { w.write(formatLine(it) + "\n") }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "团影视 ${exportTitle} 日志")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "导出 ${exportTitle} 日志")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                Toast.makeText(context, "已生成日志文件并打开分享", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "导出失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** 在浏览器中打开指定链接（失败则 Toast 提示）。 */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "无法打开链接：$url", Toast.LENGTH_SHORT).show()
    }
}
