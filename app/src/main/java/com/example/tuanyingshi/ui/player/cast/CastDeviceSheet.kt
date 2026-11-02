package com.example.tuanyingshi.ui.player.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 投屏设备选择面板。
 *
 * 打开即自动搜索（局域网 SSDP），支持手动重新搜索；搜索不到时给出可操作的提示，
 * 而不是干等一个空白列表——投屏失败最常见的原因是手机和电视不在同一 Wi-Fi。
 *
 * @param activeDeviceName 当前正在投屏的设备名；非空时面板顶部出现「停止投屏」。
 * @param onCast 选中设备后的回调（真正发起投屏由调用方处理，便于复用同一段错误提示逻辑）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDeviceSheet(
    onDismiss: () -> Unit,
    onCast: (CastDevice) -> Unit,
    activeDeviceName: String? = null,
    onStop: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var devices by remember { mutableStateOf<List<CastDevice>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }
    var searchFailed by remember { mutableStateOf(false) }
    var searchToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(searchToken) {
        searching = true
        searchFailed = false
        devices = emptyList()
        val found = runCatching { DlnaCastManager.discover(context) }
        searching = false
        found.onSuccess { devices = it }
        if (found.isFailure) searchFailed = true
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Cast, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("投屏到电视", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (!searching) {
                    IconButton(onClick = { searchToken++ }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "重新搜索")
                    }
                }
            }
            Text(
                text = "请确保手机与电视连接同一 Wi-Fi。需要登录或防盗链的片源可能无法投屏。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            if (activeDeviceName != null && onStop != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable {
                            onStop()
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.StopCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "正在投屏到「$activeDeviceName」，点此停止",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            when {
                searching -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("正在搜索局域网设备…", style = MaterialTheme.typography.bodyMedium)
                }

                devices.isEmpty() -> Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = if (searchFailed) "搜索失败，请检查网络后重试" else "没有找到可投屏的设备",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    TextButton(onClick = { searchToken++ }) { Text("重新搜索") }
                }

                else -> devices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                onCast(device)
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                            }
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Tv, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            device.model?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
