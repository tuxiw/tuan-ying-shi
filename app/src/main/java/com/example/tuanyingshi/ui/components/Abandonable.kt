package com.example.tuanyingshi.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tuanyingshi.data.repository.UserStatusRepository
import kotlinx.coroutines.launch

/**
 * 当前用户状态仓储（抛弃等），通过 CompositionLocal 在组件树中共享，
 * 默认 null（未提供时卡片不显示长按抛弃菜单）。在 MainActivity 根处注入。
 */
val LocalUserStatusRepository = staticCompositionLocalOf<UserStatusRepository?> { null }

/**
 * 「抛弃 / 取消抛弃」确认对话框。
 * - 未抛弃：提示抛弃，确认后写入状态表（从列表隐藏）。
 * - 已抛弃：提示取消抛弃，确认后移除状态。
 */
@Composable
fun AbandonDialog(
    detailUrl: String,
    title: String,
    imgUrl: String,
    onDismiss: () -> Unit,
) {
    val repo = LocalUserStatusRepository.current ?: return
    val isAbandoned by repo.observeAbandoned(detailUrl).collectAsStateWithLifecycle(initialValue = false)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAbandoned) "取消抛弃" else "抛弃《${title}》") },
        text = {
            Text(
                if (isAbandoned) {
                    "确定将该番剧从「抛弃」列表移除？移除后将在列表中恢复显示。"
                } else {
                    "确定抛弃《${title}》？抛弃后将从所有列表隐藏（关闭「不显示看过和抛弃的条目」后可恢复）。"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (isAbandoned) {
                        repo.removeAbandoned(detailUrl)
                    } else {
                        repo.setAbandoned(detailUrl, title, imgUrl)
                    }
                }
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
