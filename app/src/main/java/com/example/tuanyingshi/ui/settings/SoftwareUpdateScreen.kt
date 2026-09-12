package com.example.tuanyingshi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.BuildConfig
import com.example.tuanyingshi.R
import com.example.tuanyingshi.util.UpdateChecker
import com.example.tuanyingshi.util.UpdatePrefs
import com.example.tuanyingshi.util.UpdateResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 软件更新页（设置 → 软件更新）：
 * - 顶部展示当前版本与实时状态徽标；
 * - 一键「检查更新」；
 * - 更新通道 / 版本号 / 上次检查时间；
 * - 「自动检查更新」开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftwareUpdateScreen(navController: NavController) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("软件更新", color = MaterialTheme.colorScheme.onBackground) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            SoftwareUpdateContent()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 软件更新页内容区（供手机页与平板左右布局右侧共用）。 */
@Composable
fun SoftwareUpdateContent() {
    val checking by UpdateChecker.checking.collectAsStateWithLifecycle()
    val lastResult by UpdateChecker.lastResult.collectAsStateWithLifecycle()
    val lastCheckedAt by UpdateChecker.lastCheckedAt.collectAsStateWithLifecycle()
    val autoCheck by UpdatePrefs.autoCheckUpdate.collectAsStateWithLifecycle()
    val inApp by UpdatePrefs.inAppUpdate.collectAsStateWithLifecycle()

    val versionName = UpdateChecker.currentVersionName
    val versionCode = UpdateChecker.currentVersionCode

    // 状态徽标：检查中优先，其次展示最近一次检查结果
    val status: UpdateUiStatus = if (checking) {
        UpdateUiStatus.Checking
    } else {
        when (lastResult) {
            is UpdateResult.Available -> UpdateUiStatus.Available
            is UpdateResult.UpToDate -> UpdateUiStatus.UpToDate
            is UpdateResult.Failed -> UpdateUiStatus.Failed((lastResult as UpdateResult.Failed).message)
            UpdateResult.Idle -> UpdateUiStatus.Idle
        }
    }

    // 顶部 Hero 卡片
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_image),
                contentDescription = "团影视",
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "团影视",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "当前版本 v$versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            StatusPill(status = status)
        }
    }

    Spacer(Modifier.height(16.dp))

    // 检查更新按钮
    Button(
        onClick = { UpdateChecker.checkAsync(manual = true) },
        enabled = !checking,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = if (checking) Icons.Filled.Refresh else Icons.Filled.SystemUpdate,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (checking) "检查中…" else "检查更新",
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    Spacer(Modifier.height(20.dp))

    // 信息卡片：更新通道 / 版本号 / 上次检查
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            InfoRow(
                icon = Icons.Filled.CloudDownload,
                label = "更新通道",
                value = UpdateChecker.UPDATE_URL,
            )
            DividerLine()
            InfoRow(
                icon = Icons.Filled.SystemUpdate,
                label = "版本号",
                value = "v$versionName (code $versionCode)",
            )
            DividerLine()
            InfoRow(
                icon = Icons.Filled.Info,
                label = "上次检查",
                value = formatLastChecked(lastCheckedAt),
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // 自动检查更新开关
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        SwitchRow(
            icon = Icons.Filled.Refresh,
            title = "自动检查更新",
            desc = "每次启动应用时自动检查新版本",
            checked = autoCheck,
            onCheckedChange = { UpdatePrefs.setAutoCheckUpdate(it) },
        )
        DividerLine()
        SwitchRow(
            icon = Icons.Filled.CloudDownload,
            title = "应用内更新",
            desc = "开启后点「立即下载」会在应用内直接下载安装对应安装包；关闭则跳转发布页/GitHub",
            checked = inApp,
            onCheckedChange = { UpdatePrefs.setInAppUpdate(it) },
        )
    }

    Spacer(Modifier.height(14.dp))
    Text(
        "仅在「团影视」官方发布通道检查更新，不会收集任何个人信息。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** 更新状态（仅用于 UI 展示）。 */
private sealed class UpdateUiStatus {
    data object Checking : UpdateUiStatus()
    data object UpToDate : UpdateUiStatus()
    data object Available : UpdateUiStatus()
    data object Idle : UpdateUiStatus()
    data class Failed(val message: String) : UpdateUiStatus()
}

/** 状态徽标（胶囊样式）。 */
@Composable
private fun StatusPill(status: UpdateUiStatus) {
    val scheme = MaterialTheme.colorScheme
    val (icon, label, tint, bg) = when (status) {
        UpdateUiStatus.Checking -> PillStyle(
            Icons.Filled.Refresh, "检查中",
            scheme.onSurfaceVariant, scheme.surfaceVariant.copy(alpha = 0.65f),
        )
        UpdateUiStatus.UpToDate -> PillStyle(
            Icons.Filled.CheckCircle, "已是最新",
            Color(0xFF2E9E5B), Color(0xFF2E9E5B).copy(alpha = 0.14f),
        )
        UpdateUiStatus.Available -> PillStyle(
            Icons.Filled.NewReleases, "发现新版本",
            scheme.primary, scheme.primary.copy(alpha = 0.14f),
        )
        UpdateUiStatus.Idle -> PillStyle(
            Icons.Filled.SystemUpdate, "未检查",
            scheme.onSurfaceVariant, scheme.surfaceVariant.copy(alpha = 0.65f),
        )
        is UpdateUiStatus.Failed -> PillStyle(
            Icons.Filled.Info, "检查失败",
            scheme.error, scheme.error.copy(alpha = 0.14f),
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 徽标样式打包。 */
private data class PillStyle(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val bg: Color,
)

/** 信息行（图标 + 标签 + 值）。 */
@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
            )
        }
    }
}

/** 信息卡片内的细分隔线。 */
@Composable
private fun DividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
    )
}

/** 将上次检查时间戳格式化为「刚刚 / x 分钟前 / x 小时前 / 日期」。 */
private fun formatLastChecked(ts: Long?): String {
    if (ts == null) return "从未检查"
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }
}
