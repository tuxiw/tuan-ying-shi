package com.example.tuanyingshi.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tuanyingshi.util.source_rule.CfVerificationCoordinator
import kotlinx.coroutines.delay

/**
 * 自动放行类挑战的安静观察期：这段时间验证在后台跑，不打扰用户。
 * 设为 0：验证一开始就把卡片（含 WebView）挂出来。原因——Cloudflare 的 JS 盾依赖页面可见
 * （不可见时 requestAnimationFrame 会被暂停，导致盾跑不完、永远拿不到 cf_clearance），
 * 而 detached 的 WebView 在部分 ROM 上被视为不可见。立即显示可保证 WebView 一加载就可见、可交互。
 */
private const val QUIET_WINDOW_MS = 0L

/**
 * 全局 Cloudflare 验证闸门。
 *
 * 移植自 HaRu 的 `HanimeCloudflareGate`：
 * - 有验证请求时在整屏上方叠半透明遮罩 + 居中卡片；
 * - **不是立刻弹**：先安静等待 [QUIET_WINDOW_MS]，大部分 CF 的 JS 挑战会在此期间自动放行，
 *   用户毫无感知；只有「超过安静期」或「检测到需要人工点击的控件（Turnstile）」才把
 *   WebView 真正显示出来；
 * - 提供「重试」（手动 reload，禁止自动 reload）与「取消」（中断当前验证）。
 *
 * 用法：在 Compose 根节点包一层
 * ```
 * CfVerificationGate { AppNavigation() }
 * ```
 */
@Composable
fun CfVerificationGate(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        CfVerificationOverlay()
    }
}

@Composable
private fun CfVerificationOverlay() {
    val coordinator = CfVerificationCoordinator
    val active = coordinator.active.collectAsStateWithLifecycle().value
    if (active == null) return

    val webView by coordinator.webView.collectAsStateWithLifecycle()
    val status by coordinator.status.collectAsStateWithLifecycle()
    val interactionRequired by coordinator.interactionRequired.collectAsStateWithLifecycle()

    var revealed by remember(active.id) { mutableStateOf(false) }
    LaunchedEffect(active.id, interactionRequired) {
        if (interactionRequired) {
            revealed = true
        } else {
            delay(QUIET_WINDOW_MS)
            revealed = true
        }
    }
    if (!revealed) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { /* 遮罩吞掉点击，避免误触背后的界面 */ },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints {
            val cardWidth = minOf(maxWidth * 0.92f, 420.dp)
            val cardHeight = minOf(maxHeight * 0.66f, 500.dp)
            Card(
                modifier = Modifier.width(cardWidth).height(cardHeight),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 8.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "站点安全验证",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { CfVerificationCoordinator.cancelActive() }) {
                            Icon(Icons.Filled.Close, contentDescription = "取消")
                        }
                    }
                    val host = remember(active.id) { active.host }
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        val current = webView
                        if (current != null) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { current },
                                onRelease = { view -> (view.parent as? ViewGroup)?.removeView(view) },
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = status.ifBlank { "正在通过浏览器连接站点…" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = status.ifBlank { "正在验证…" },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { CfVerificationCoordinator.retryNow() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}
