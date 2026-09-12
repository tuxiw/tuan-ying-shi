package com.example.tuanyingshi.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.OutlinedTextField
import com.example.tuanyingshi.util.sync.DirectSync
import com.example.tuanyingshi.util.sync.WebDavSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.tuanyingshi.util.AutoBackup
import com.example.tuanyingshi.util.ConfigBackup
import com.example.tuanyingshi.util.preferences
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份与恢复页（设置 → 数据 → 备份与恢复）：
 * - 导出：勾选要备份的分类（个性化 / 通知与提醒 / 数据源与订阅 / 账号会话），经 SAF 另存为 JSON。
 * - 恢复：经 SAF 选取文件，先校验合法性并展示范围/时间，确认后再写回并重启。
 * - 自动备份：开启后按间隔在应用私有 Download 目录生成全量备份，可一键从历史恢复。
 * - 跨设备同步 (WebDAV)：对接 WebDAV 服务器，手机/平板之间通过 newer-wins 双向同步配置。
 *
 * 手机端用 [BackupRestoreScreen]（带独立顶栏）；平板端左右布局时直接复用 [BackupRestoreContent]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(navController: NavController) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("备份与恢复", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            BackupRestoreContent()
        }
    }
}

/** 备份与恢复页内容区（供手机页 TopAppBar 与平板左右布局右侧共用）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupRestoreContent() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }

    // 选择性导出：默认全选
    var selectedScopes by remember { mutableStateOf(ConfigBackup.BackupScope.entries.toSet()) }

    // 从文件恢复的确认弹窗
    var showImportConfirm by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf<String?>(null) }
    var importMeta by remember { mutableStateOf<ConfigBackup.BackupMeta?>(null) }

    // 自动备份偏好
    var autoEnabled by remember { mutableStateOf(AutoBackup.isEnabled()) }
    var intervalHours by remember { mutableStateOf(AutoBackup.getIntervalHours()) }
    var lastBackupMs by remember { mutableStateOf(AutoBackup.getLastBackupMs()) }
    var history by remember { mutableStateOf(AutoBackup.listHistory(context)) }

    // 从历史恢复的确认弹窗
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var restoreMeta by remember { mutableStateOf<ConfigBackup.BackupMeta?>(null) }
    var showRestoreConfirmProxy by remember { mutableStateOf(false) }

    // 跨设备同步 (WebDAV) 状态
    var davEnabled by remember { mutableStateOf(WebDavSync.isEnabled()) }
    var davUrl by remember { mutableStateOf(WebDavSync.getUrl()) }
    var davUser by remember { mutableStateOf(WebDavSync.getUser()) }
    var davPass by remember { mutableStateOf(WebDavSync.getPass()) }
    var davFile by remember { mutableStateOf(WebDavSync.getRemoteFile()) }
    var davAutoLaunch by remember { mutableStateOf(WebDavSync.isAutoLaunch()) }
    var davLastSync by remember { mutableStateOf(WebDavSync.getLastSyncMs()) }
    var davWorking by remember { mutableStateOf(false) }
    var davPassReveal by remember { mutableStateOf(false) }

    // 设备直连同步状态
    var directMode by remember { mutableStateOf("host") } // "host"=接收方 / "join"=连接方
    var directRunning by remember { mutableStateOf(DirectSync.Server.isRunning) }
    var directCode by remember { mutableStateOf(DirectSync.generateCode()) }
    var directHostCats by remember { mutableStateOf(DirectSync.SyncCategory.entries.toSet()) }
    var directAddress by remember { mutableStateOf("") }
    var directJoinCode by remember { mutableStateOf("") }
    var directPeerInfo by remember { mutableStateOf<DirectSync.DeviceInfo?>(null) }
    var directJoinCats by remember { mutableStateOf<Set<DirectSync.SyncCategory>>(emptySet()) }
    var directDirection by remember { mutableStateOf("pull") } // "pull"=拉取 / "push"=推送
    var directWorking by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    /** 在 IO 线程执行 WebDAV 操作，回到主线程更新状态；返回 (提示文案, 是否需要重启)。 */
    fun runDav(block: suspend () -> Pair<String, Boolean>) {
        scope.launch {
            davWorking = true
            val result = withContext(Dispatchers.IO) {
                runCatching { block() }.fold(
                    onSuccess = { it },
                    onFailure = { "操作失败：${it.message}" to false },
                )
            }
            davWorking = false
            davLastSync = WebDavSync.getLastSyncMs()
            status = result.first
            Toast.makeText(context, result.first, Toast.LENGTH_SHORT).show()
            if (result.second) ConfigBackup.restartApp(context)
        }
    }

    /** 连接方：按所选分类与方向，依次与对方同步；拉取设置成功时结束后重启本机。 */
    fun startDirectSync() {
        val addr = directAddress.trim()
        val code = directJoinCode.trim()
        val dir = directDirection
        val cats = directJoinCats
        if (addr.isBlank() || code.isBlank()) {
            Toast.makeText(context, "请填写对方地址与配对码", Toast.LENGTH_SHORT).show()
            return
        }
        if (cats.isEmpty()) {
            Toast.makeText(context, "请至少选择一个要同步的分类", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            directWorking = true
            val (log, needRestart) = withContext(Dispatchers.IO) {
                val lines = mutableListOf<String>()
                var restart = false
                for (cat in cats) {
                    val o = when (cat) {
                        DirectSync.SyncCategory.SETTINGS ->
                            if (dir == "pull") {
                                DirectSync.Client.pullSettings(addr, code)
                            } else {
                                DirectSync.Client.pushSettings(addr, code)
                            }
                        DirectSync.SyncCategory.HISTORY ->
                            if (dir == "pull") {
                                DirectSync.Client.pullHistory(addr, code)
                            } else {
                                DirectSync.Client.pushHistory(addr, code)
                            }
                        DirectSync.SyncCategory.DOWNLOADS ->
                            if (dir == "pull") {
                                DirectSync.Client.pullDownloads(addr, code)
                            } else {
                                DirectSync.Client.pushDownloads(addr, code)
                            }
                    }
                    lines.add("${cat.label}：${o.detail}")
                    if (o.success && cat == DirectSync.SyncCategory.SETTINGS && dir == "pull") restart = true
                }
                lines.joinToString("\n") to restart
            }
            directWorking = false
            status = log
            Toast.makeText(
                context,
                if (log.length > 40) "同步完成" else log,
                Toast.LENGTH_LONG,
            ).show()
            if (needRestart) ConfigBackup.restartApp(context)
        }
    }

    fun refreshAutoBackupState() {        autoEnabled = AutoBackup.isEnabled()
        intervalHours = AutoBackup.getIntervalHours()
        lastBackupMs = AutoBackup.getLastBackupMs()
        history = AutoBackup.listHistory(context)
    }

    // 导出：让用户选择保存位置与文件名
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                ConfigBackup.exportScoped(
                    context.preferences,
                    os.writer(StandardCharsets.UTF_8),
                    selectedScopes,
                )
            }
            val counts = ConfigBackup.scopeCounts(context.preferences)
            val detail = selectedScopes.joinToString("、") { s -> "${s.label} ${counts[s] ?: 0} 项" }
            status = "导出成功：$detail"
            Toast.makeText(context, "配置已导出", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            status = "导出失败：${e.message}"
            Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 导入（恢复）：选文件后先校验，再弹确认框
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)
                ?.use { it.reader(StandardCharsets.UTF_8).readText() } ?: return@rememberLauncherForActivityResult
            val meta = ConfigBackup.parseMeta(text)
            importText = text
            importMeta = meta
            showImportConfirm = true
        } catch (e: Exception) {
            status = "文件校验失败：${e.message}"
            Toast.makeText(context, "不是有效的团影视备份文件", Toast.LENGTH_LONG).show()
        }
    }

    if (status != null) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = status!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    // ===== 导出 =====
    SectionTitle("导出配置")
    Text(
        text = "将选中的配置分类导出为 JSON 文件，可分享或留存。「通知与提醒」包含下载通知与离线模式提示开关；" +
            "「账号会话」包含登录令牌与代理密码等敏感信息。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    val exportCounts = remember(selectedScopes) { ConfigBackup.scopeCounts(context.preferences) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConfigBackup.BackupScope.entries.forEach { scope ->
            val checked = selectedScopes.contains(scope)
            FilterChip(
                selected = checked,
                onClick = {
                    selectedScopes = if (checked) {
                        selectedScopes - scope
                    } else {
                        selectedScopes + scope
                    }
                },
                label = { Text("${scope.label} (${exportCounts[scope] ?: 0})") },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = {
            if (selectedScopes.isEmpty()) {
                Toast.makeText(context, "请至少选择一个分类", Toast.LENGTH_SHORT).show()
                return@OutlinedButton
            }
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            exportLauncher.launch("tuanyingshi-config-$ts.json")
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.FileUpload, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("导出选中配置")
    }

    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(24.dp))

    // ===== 从文件恢复 =====
    SectionTitle("从文件恢复")
    Text(
        text = "选择先前导出的 JSON 文件恢复配置。恢复会覆盖备份中包含的配置，并重启应用使更改生效。" +
            "兼容 v1 ~ v${ConfigBackup.CURRENT_EXPORT_VERSION} 备份；旧版备份中缺少的项（如新增的通知开关）将保持当前设置不变。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.FileDownload, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("选择备份文件恢复")
    }

    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(24.dp))

    // ===== 自动备份 =====
    SectionTitle("自动备份")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "自动备份到本地",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (autoEnabled) {
                    "上次备份：${if (lastBackupMs > 0) formatTime(lastBackupMs) else "暂无"}"
                } else {
                    "已关闭"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = autoEnabled,
            onCheckedChange = {
                AutoBackup.setEnabled(it)
                refreshAutoBackupState()
            },
        )
    }
    if (autoEnabled) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "备份间隔",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(12 to "12 小时", 24 to "24 小时", 48 to "48 小时", 168 to "7 天").forEach { (h, label) ->
                FilterChip(
                    selected = intervalHours == h,
                    onClick = {
                        AutoBackup.setIntervalHours(h)
                        refreshAutoBackupState()
                    },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val f = AutoBackup.backupNow(context)
                refreshAutoBackupState()
                if (f != null) {
                    status = "已创建自动备份：${formatTime(f.lastModified())}"
                    Toast.makeText(context, "已创建备份", Toast.LENGTH_SHORT).show()
                } else {
                    status = "备份失败：无法写入文件"
                    Toast.makeText(context, "备份失败", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.FileUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("立即备份")
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("备份历史（${history.size}）")
        if (history.isEmpty()) {
            Text(
                text = "暂无自动备份。开启自动备份并运行一段时间后，会在此处生成历史版本。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    history.forEachIndexed { index, entry ->
                                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    restoreFile = entry.file
                                    restoreMeta = runCatching {
                                        ConfigBackup.parseMeta(entry.file.readText())
                                    }.getOrNull()
                                    showRestoreConfirmProxy = true
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatTime(entry.exportedAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = entry.scopes.joinToString("、") { it.label } +
                                        " · ${formatSize(entry.size)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (index < history.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
            }
        }
    }

    // ===== 跨设备同步 (WebDAV) =====
    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(24.dp))
    SectionTitle("跨设备同步 (WebDAV)")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "手机 / 平板 互相同步",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (davEnabled) {
                    "上次同步：${if (davLastSync > 0) formatTime(davLastSync) else "暂无"}"
                } else {
                    "已关闭"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = davEnabled,
            onCheckedChange = {
                davEnabled = it
                WebDavSync.setEnabled(it)
            },
        )
    }
    if (davEnabled) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "填写 WebDAV 地址（含目录，如 https://cloud.example.com/remote.php/dav/files/用户/）与账号密码，对接 Nextcloud / NAS 等。建议仅使用 HTTPS。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        DavTextField(
            value = davUrl,
            label = "服务器地址",
            onValueChange = { davUrl = it; WebDavSync.setUrl(it) },
        )
        Spacer(Modifier.height(10.dp))
        DavTextField(
            value = davUser,
            label = "账号",
            onValueChange = { davUser = it; WebDavSync.setUser(it) },
        )
        Spacer(Modifier.height(10.dp))
        DavTextField(
            value = davPass,
            label = "密码",
            password = true,
            reveal = davPassReveal,
            onReveal = { davPassReveal = it },
            onValueChange = { davPass = it; WebDavSync.setPass(it) },
        )
        Spacer(Modifier.height(10.dp))
        DavTextField(
            value = davFile,
            label = "远端文件名",
            onValueChange = { davFile = it; WebDavSync.setRemoteFile(it) },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    runDav {
                        WebDavSync.testConnection()
                        "连接成功" to false
                    }
                },
                enabled = !davWorking && WebDavSync.isConfigured(),
            ) { Text("测试连接") }
            OutlinedButton(
                onClick = {
                    runDav {
                        val o = WebDavSync.syncNow(context).getOrThrow()
                        val msg = if (o.pulled) {
                            "同步完成：已从云端拉取 ${o.importedCount} 项并上传本机配置，即将重启…"
                        } else {
                            "同步完成：已上传本机配置到云端"
                        }
                        msg to o.pulled
                    }
                },
                enabled = !davWorking && WebDavSync.isConfigured(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("立即同步")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    runDav {
                        WebDavSync.upload(context).getOrThrow()
                        "已上传本机配置到云端" to false
                    }
                },
                enabled = !davWorking && WebDavSync.isConfigured(),
                modifier = Modifier.weight(1f),
            ) { Text("仅上传") }
            OutlinedButton(
                onClick = {
                    runDav {
                        val r = WebDavSync.downloadAndImport(context).getOrThrow()
                        "已从云端恢复 ${r.importedCount} 项，即将重启…" to true
                    }
                },
                enabled = !davWorking && WebDavSync.isConfigured(),
                modifier = Modifier.weight(1f),
            ) { Text("仅下载") }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "应用启动时自动同步",
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = davAutoLaunch,
                onCheckedChange = {
                    davAutoLaunch = it
                    WebDavSync.setAutoLaunch(it)
                },
            )
        }
        if (!WebDavSync.isConfigured()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "请先填写服务器地址与账号后再操作。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    // ===== 设备直连同步 =====
    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(24.dp))
    SectionTitle("设备直连同步")
    Text(
        text = "两部设备连同一 WiFi 或热点，一方作为「接收方」生成配对码，另一方输入对方显示的地址与配对码即可互传。可自由勾选要同步的内容。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = directMode == "host",
            onClick = { directMode = "host" },
            label = { Text("我是接收方") },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = directMode == "join",
            onClick = { directMode = "join" },
            label = { Text("我要连接对方") },
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(Modifier.height(16.dp))
    if (directMode == "host") {
        Text(
            text = "选择本机愿意共享的分类：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DirectSync.SyncCategory.entries.forEach { cat ->
                val checked = directHostCats.contains(cat)
                FilterChip(
                    selected = checked,
                    onClick = {
                        directHostCats = if (checked) directHostCats - cat else directHostCats + cat
                    },
                    label = { Text(cat.label) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!directRunning) {
            OutlinedButton(
                onClick = {
                    if (directHostCats.isEmpty()) {
                        Toast.makeText(context, "请至少选择一个共享分类", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    DirectSync.Server.start(context, directCode, directHostCats)
                    directRunning = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("开始接收（生成配对码）")
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "正在等待对方连接…",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "本机地址：${DirectSync.Server.listenAddress}:${DirectSync.Server.listenPort}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "配对码：$directCode",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { directCode = DirectSync.generateCode() },
                        ) { Text("重新生成码") }
                        OutlinedButton(
                            onClick = {
                                DirectSync.Server.stop()
                                directRunning = false
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("停止接收") }
                    }
                }
            }
        }
    } else {
        DavTextField(
            value = directAddress,
            label = "对方地址（IP:端口，如 192.168.1.5:8787）",
            onValueChange = { directAddress = it },
        )
        Spacer(Modifier.height(10.dp))
        DavTextField(
            value = directJoinCode,
            label = "配对码",
            onValueChange = { directJoinCode = it },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val addr = directAddress.trim()
                val code = directJoinCode.trim()
                if (addr.isBlank() || code.isBlank()) {
                    Toast.makeText(context, "请填写对方地址与配对码", Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                scope.launch {
                    directWorking = true
                    try {
                        val info = DirectSync.Client.fetchInfo(addr, code)
                        directPeerInfo = info
                        directJoinCats = info.categories.toSet()
                        status = "已连接到「${info.name}」，请选择要同步的内容"
                    } catch (e: Exception) {
                        directPeerInfo = null
                        status = "连接失败：${e.message}"
                        Toast.makeText(context, "连接失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                    directWorking = false
                }
            },
            enabled = !directWorking,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("连接对方") }

        if (directPeerInfo != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "对方设备：${directPeerInfo!!.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "选择要同步的分类：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                directPeerInfo!!.categories.forEach { cat ->
                    val checked = directJoinCats.contains(cat)
                    FilterChip(
                        selected = checked,
                        onClick = {
                            directJoinCats = if (checked) directJoinCats - cat else directJoinCats + cat
                        },
                        label = { Text(cat.label) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = directDirection == "pull",
                    onClick = { directDirection = "pull" },
                    label = { Text("拉取（从对方获取）") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = directDirection == "push",
                    onClick = { directDirection = "push" },
                    label = { Text("推送（发送给对方）") },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { startDirectSync() },
                enabled = !directWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (directWorking) "同步中…" else "开始同步")
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(
        text = "注意：导出文件包含账号会话、代理密码等敏感信息，请妥善保管，避免泄露。自动备份保存在应用私有 Download 目录，无需存储权限。WebDAV 同步文件同样包含完整配置，请确保服务器可信并使用 HTTPS。设备直连同步仅在同一局域网内有效，配对码用于防止陌生设备误连。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // 从文件恢复确认弹窗
    if (showImportConfirm && importMeta != null) {
        RestoreConfirmDialog(
            meta = importMeta!!,
            title = "恢复配置",
            message = "即将恢复以下分类的配置，并重启应用使配置生效。仅覆盖备份中包含的配置，其余保持不变。",
            onDismiss = { showImportConfirm = false },
            onConfirm = {
                showImportConfirm = false
                val text = importText ?: return@RestoreConfirmDialog
                try {
                    val res = ConfigBackup.import(context.preferences, text)
                    status = "已恢复 ${res.importedCount} 项配置，正在重启…"
                    Toast.makeText(context, "恢复成功，即将重启", Toast.LENGTH_SHORT).show()
                    ConfigBackup.restartApp(context)
                } catch (e: Exception) {
                    status = "恢复失败：${e.message}"
                    Toast.makeText(context, "恢复失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    // 从历史恢复确认弹窗
    if (showRestoreConfirmProxy && restoreFile != null && restoreMeta != null) {
        RestoreConfirmDialog(
            meta = restoreMeta!!,
            title = "从历史备份恢复",
            message = "即将用该历史备份恢复配置，并重启应用。仅覆盖备份中包含的配置，其余保持不变。",
            onDismiss = { showRestoreConfirmProxy = false },
            onConfirm = {
                showRestoreConfirmProxy = false
                val file = restoreFile ?: return@RestoreConfirmDialog
                try {
                    val res = AutoBackup.restore(context, file)
                    status = "已恢复 ${res.importedCount} 项配置，正在重启…"
                    Toast.makeText(context, "恢复成功，即将重启", Toast.LENGTH_SHORT).show()
                    ConfigBackup.restartApp(context)
                } catch (e: Exception) {
                    status = "恢复失败：${e.message}"
                    Toast.makeText(context, "恢复失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

/** 恢复确认弹窗：展示备份范围与导出时间。 */
@Composable
private fun RestoreConfirmDialog(
    meta: ConfigBackup.BackupMeta,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(Modifier.height(10.dp))
                meta.scopes.forEach { s ->
                    Text("• ${s.label}", color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(10.dp))
                val time = if (meta.exportedAt > 0) formatTime(meta.exportedAt) else "未知"
                Text(
                    "导出时间：$time${if (meta.keyCount >= 0) " · 包含 ${meta.keyCount} 项配置" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("恢复并重启") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** WebDAV 配置输入框；密码框支持显示/隐藏切换。 */
@Composable
private fun DavTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    reveal: Boolean = false,
    onReveal: ((Boolean) -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password && onReveal != null) {
            {
                TextButton(onClick = { onReveal(!reveal) }) {
                    Text(if (reveal) "隐藏" else "显示")
                }
            }
        } else null,
    )
}

private fun formatTime(ms: Long): String =
    if (ms <= 0) "未知" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
