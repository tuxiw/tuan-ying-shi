package com.example.tuanyingshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.tuanyingshi.ui.theme.DarkSurface
import com.example.tuanyingshi.ui.theme.OnDark

/**
 * 首次进入 App 的「免费说明 / 官方下载地址」公告弹窗。
 *
 * - 正文可滚动；底部「我知道了」按钮初始禁用，必须滚动到底部后才能点击关闭；
 * - 禁止点击外部关闭，必须主动阅读并确认后才能进入应用。
 */
@Suppress("DEPRECATION")
@Composable
fun AnnouncementDialog(onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    val canClose by remember { derivedStateOf { !scrollState.canScrollForward } }
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val body = remember { buildAnnouncementAnnotated(linkColor) }

    Dialog(onDismissRequest = { /* 必须阅读到底才能关闭，禁止点外部关闭 */ }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
        ) {
            Column(Modifier.fillMaxSize()) {
                // 标题
                Text(
                    text = "欢迎使用团影视",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnDark,
                    modifier = Modifier.padding(20.dp),
                )
                DividerLine()

                // 可滚动正文
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                ) {
                    ClickableText(
                        text = body,
                        onClick = { offset ->
                            body.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()
                                ?.let { runCatching { uriHandler.openUri(it.item) } }
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = OnDark,
                            lineHeight = 22.sp,
                        ),
                    )
                }

                DividerLine()

                // 底部确认按钮：未滚动到底时禁用
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = onDismiss,
                        enabled = canClose,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (canClose) "我知道了" else "请滑动阅读至底部",
                        )
                    }
                }
            }
        }
    }
}

/** 公告正文：含官方下载地址与反馈 issues 的可点击链接。 */
private fun buildAnnouncementAnnotated(linkColor: Color): AnnotatedString {
    val downloadUrl = "https://gitee.com/tuxiw/tuan-ying-shi"
    val feedbackUrl = "https://gitee.com/tuxiw/tuan-ying-shi/issues"
    return buildAnnotatedString {
        withStyle(SpanStyle(color = OnDark)) {
            append("感谢你下载并使用「团影视」。在开始使用前，请花一分钟阅读以下说明。\n\n")
            append("【软件性质】\n")
            append("团影视是一款完全免费的影视观看工具，不含任何付费会员、内购或广告变现项目。你无需为观看或下载支付任何费用。\n\n")
            append("【官方下载地址】\n")
            append("为保证安全，请务必通过官方渠道下载与更新应用：")
            withLink(downloadUrl, linkColor)
            append("\n你也可通过该地址获取最新版本、查看使用说明，并向我们反馈问题与建议。\n\n")
            append("【意见反馈与好评】\n")
            append("· 问题反馈 / 功能建议：")
            withLink(feedbackUrl, linkColor)
            append("\n· 如果觉得好用，欢迎前往项目主页打个好评：")
            withLink(downloadUrl, linkColor)
            append("\n\n")
            append("【内容来源说明】\n")
            append("应用内的影视数据来自第三方公开站点，仅供个人学习与体验。请在下载后 24 小时内删除，并支持正版内容。\n\n")
            append("【免责声明】\n")
            append("本应用仅作技术演示与学习交流之用，不对所展示内容的版权归属负责。使用本应用所产生的任何后果，均由使用者自行承担。")
        }
    }
}

/** 在光标位置插入一个可点击的 URL（指定颜色 + 下划线），随后恢复默认样式。 */
private fun AnnotatedString.Builder.withLink(url: String, color: Color) {
    val id = pushStringAnnotation("URL", url)
    withStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline)) {
        append(url)
    }
    pop(id)
}

/** 公告弹窗内的细分隔线。 */
@Composable
private fun DividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(OnDark.copy(alpha = 0.08f)),
    )
}
