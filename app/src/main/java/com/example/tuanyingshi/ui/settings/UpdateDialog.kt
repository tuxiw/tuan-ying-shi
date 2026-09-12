package com.example.tuanyingshi.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tuanyingshi.util.UpdateChecker
import com.example.tuanyingshi.util.UpdateDialogState
import com.example.tuanyingshi.util.UpdateDownloader
import com.example.tuanyingshi.util.UpdateInfo
import com.example.tuanyingshi.util.UpdatePrefs

/**
 * 更新检查对话框宿主：根据 [state] 渲染「有更新 / 已是最新 / 失败」三类弹窗。
 * 在 [AppNavigation] 顶部挂载一次即可全局响应。
 */
@Composable
fun UpdateDialogHost(
    state: UpdateDialogState?,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateDialogState.Available -> UpdateAvailableDialog(state.info, onDismiss)
        is UpdateDialogState.NoUpdate -> UpdateInfoDialog(
            title = "已是最新版本",
            message = "当前已是最新版本 ${UpdateChecker.formatVersion(state.versionName)}",
            onDismiss = onDismiss,
        )
        is UpdateDialogState.Failed -> UpdateInfoDialog(
            title = "检查更新失败",
            message = state.message,
            onDismiss = onDismiss,
        )
        null -> { /* 不渲染 */ }
    }
}

/** 发现新版本弹窗：展示更新说明，可「立即更新」跳转下载，或「稍后再说」（强制更新时不可关闭）。 */
@Composable
private fun UpdateAvailableDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 点击「立即更新」后弹出渠道选择（发布页 / GitHub），不再直接打开 APK 直链。
    var showChannelPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!info.isForceUpdate) onDismiss() },
        confirmButton = {
            TextButton(onClick = {
                if (UpdatePrefs.inAppUpdate.value) {
                    // 应用内更新：后台下载 + 通知栏进度（不受本弹窗关闭影响）
                    val target = UpdateChecker.pickInAppUpdate(info, context)
                    if (target != null) {
                        UpdateDownloader.startBackgroundDownload(
                            context,
                            target.url,
                            target.fileName,
                            target.channel,
                            UpdateChecker.formatVersion(info.version),
                        )
                    } else {
                        // 应用内无对应安装包：回退到网页下载页
                        val web = UpdateChecker.pickFallbackWebUrl(info)
                        if (web != null) openUrl(context, web)
                    }
                } else {
                    showChannelPicker = true
                }
                onDismiss()
            }) { Text("立即更新") }
        },
        dismissButton = if (info.isForceUpdate) {
            null
        } else {
            { TextButton(onClick = onDismiss) { Text("稍后再说") } }
        },
        title = { Text("发现新版本 ${UpdateChecker.formatVersion(info.version)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                info.updateNote?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                val features = info.changelog?.features.orEmpty()
                val fixes = info.changelog?.fixes.orEmpty()
                if (features.isNotEmpty()) {
                    Text(
                        "新功能",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    features.forEach {
                        Text("· $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (fixes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "修复",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    fixes.forEach {
                        Text("· $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )

    if (showChannelPicker) {
        ChannelPickerDialog(
            website = info.website,
            github = info.github,
            onPick = { url ->
                openUrl(context, url)
                showChannelPicker = false
                onDismiss()
            },
            onDismiss = { showChannelPicker = false },
        )
    }
}

/**
 * 「立即更新」二级弹窗：让用户选择去发布页下载还是 GitHub 下载。
 * 两个渠道地址来自 [website] / [github]（服务端配置），为空则该渠道不展示。
 */
@Composable
private fun ChannelPickerDialog(
    website: String?,
    github: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("选择下载渠道") },
        text = {
            val siteUrl = website?.takeIf { it.isNotBlank() }
            val ghUrl = github?.takeIf { it.isNotBlank() }
            Column {
                if (siteUrl != null) {
                    TextButton(onClick = { onPick(siteUrl) }) {
                        Text("发布页下载")
                    }
                }
                if (ghUrl != null) {
                    TextButton(onClick = { onPick(ghUrl) }) {
                        Text("GitHub 下载")
                    }
                }
                if (siteUrl == null && ghUrl == null) {
                    Text(
                        "暂未配置任何下载渠道，请联系开发者。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** 普通信息弹窗（已是最新 / 失败）。 */
@Composable
private fun UpdateInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        title = { Text(title) },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** 在浏览器中打开指定链接（失败则提示）。 */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        android.widget.Toast.makeText(context, "无法打开链接：$url", android.widget.Toast.LENGTH_SHORT).show()
    }
}
