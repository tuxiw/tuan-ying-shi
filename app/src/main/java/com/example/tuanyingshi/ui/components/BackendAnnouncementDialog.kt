package com.example.tuanyingshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.tuanyingshi.data.remote.backend.AnnouncementVO

/**
 * 后端公告弹窗（对接 `GET /ops/announcement/popup`）。
 *
 * - 展示标题 / 类型 / 正文；正文可滚动，超长公告也能完整阅读；
 * - 公告带 [AnnouncementVO.linkUrl] 时额外提供「查看详情」跳浏览器；
 * - 需主动点击「我知道了」关闭，禁止点外部或返回键直接关掉，避免重要公告被跳过。
 *
 * 「是否重复弹出」由调用方按 `forceShow` 与已读记录决定，组件本身不关心。
 */
@Composable
fun BackendAnnouncementDialog(
    announcement: AnnouncementVO,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Dialog(onDismissRequest = { /* 必须主动确认，禁止点外部关闭 */ }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.72f),
        ) {
            Column(Modifier.fillMaxHeight()) {
                Text(
                    text = announcement.title?.takeIf { it.isNotBlank() } ?: "公告",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
                )

                val tag = typeLabel(announcement.type)
                if (tag.isNotBlank()) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }

                AnnounceDivider()

                // 正文（后端为 Markdown 文本，这里按纯文本展示并保留换行）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Text(
                        text = announcement.content.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                val link = announcement.linkUrl?.takeIf { it.isNotBlank() }
                if (link != null) {
                    TextButton(
                        onClick = { runCatching { uriHandler.openUri(link) } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        Text("查看详情")
                    }
                }

                AnnounceDivider()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("我知道了")
                    }
                }
            }
        }
    }
}

/** 公告类型转中文标签；未知类型原样返回。 */
private fun typeLabel(type: String?): String = when (type) {
    "NOTICE" -> "通知"
    "UPDATE" -> "更新"
    "ACTIVITY" -> "活动"
    else -> type.orEmpty()
}

/** 公告弹窗内的细分隔线。 */
@Composable
private fun AnnounceDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
    )
}
