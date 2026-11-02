package com.example.tuanyingshi.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.tuanyingshi.ui.components.isTablet

/** 毫秒 → mm:ss 或 h:mm:ss（续播提示条的进度文案）。 */
fun formatResumePlaybackTime(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

/**
 * 续播提示条：进入播放页自动跳到上次进度后，在底部居中展示「已从 mm:ss 处继续播放」，
 * 并提供「从头播放」按钮（记忆播放的快捷反选项）。
 * 约 6 秒后或用户拖动进度条 / 手动关闭时由调用方收起（本组件只负责展示与回调）。
 *
 * 命名带 Resume 前缀以避开 PlayerScreen 中同包的私有 ResumeBanner / formatResumeTime。
 */
@Composable
fun ResumePlaybackBanner(
    positionMs: Long,
    onPlayFromStart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 手机端（最小宽 < 600dp）用紧凑尺寸：视频区本身就不高，默认尺寸会显得过大、压画面。
    // 平板 / 大屏维持原来的宽松尺寸，保证触控目标。
    val compact = !isTablet()
    val hPad = if (compact) 10.dp else 14.dp
    val vPad = if (compact) 4.dp else 10.dp
    val gap = if (compact) 4.dp else 10.dp
    val iconSize = if (compact) 14.dp else 18.dp
    val actionHeight = if (compact) 28.dp else 40.dp
    val actionHPad = if (compact) 6.dp else 12.dp
    val closeSize = if (compact) 24.dp else 28.dp
    val closeIcon = if (compact) 14.dp else 16.dp
    val textStyle = if (compact) {
        MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)
    } else {
        MaterialTheme.typography.bodySmall
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = hPad, vertical = vPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (!compact) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                text = "已从 ${formatResumePlaybackTime(positionMs)} 处继续播放",
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onPlayFromStart,
                modifier = Modifier.height(actionHeight),
                contentPadding = PaddingValues(horizontal = actionHPad, vertical = 0.dp),
            ) {
                Text(
                    "从头播放",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(closeSize)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭提示",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(closeIcon),
                )
            }
        }
    }
}
