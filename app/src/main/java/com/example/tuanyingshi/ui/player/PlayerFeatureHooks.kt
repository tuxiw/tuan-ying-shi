package com.example.tuanyingshi.ui.player

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tuanyingshi.util.PipController
import com.example.tuanyingshi.util.PlayerEnhancementPrefs
import com.example.tuanyingshi.util.PlayerMorePrefs
import com.lanlinju.videoplayer.MoreItemKey
import com.lanlinju.videoplayer.VideoPlayerState

/**
 * 各播放页共用的小窗（画中画）状态。
 *
 * @param inPip 当前是否在小窗里——为 true 时播放页应当隐藏整套控制层。
 * @param enter 进入小窗，返回是否成功（设备不支持 / 系统拒绝时为 false）。
 */
@Immutable
class PlayerPipState(
    val inPip: Boolean,
    val enter: () -> Boolean,
)

/**
 * 组装小窗状态：订阅系统回调、按视频比例同步小窗窗口尺寸、进出小窗时开关控制层。
 */
@Composable
fun rememberPlayerPipState(playerState: VideoPlayerState): PlayerPipState {
    val context = LocalContext.current
    val inPip by PipController.inPip.collectAsStateWithLifecycle()
    val videoSize by playerState.videoSize
    val aspectRatio = if (videoSize.height > 0) {
        videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
    } else {
        16f / 9f
    }

    LaunchedEffect(inPip) {
        // 退出小窗时复位；进入小窗的置位放在 enter() 里（必须早于系统的 ON_STOP）。
        if (!inPip) playerState.inPipMode = false
        if (inPip) playerState.hideControlUi() else playerState.showControlUi()
    }
    LaunchedEffect(inPip, aspectRatio) {
        val activity = context as? Activity ?: return@LaunchedEffect
        // 小窗里的双击 → 切换「贴合画面 / 大窗」，并记住这次的选择（下次进小窗沿用）。
        // Android 12+ 上双击放大是由系统手势处理的（应用收不到事件），这里是给仍会把
        // 触摸事件交给应用的机型兜底，两条路径都指向「改变小窗尺寸」。
        playerState.pipToggleSize = if (inPip) {
            {
                val large = PipController.toggleSize(activity, aspectRatio)
                Toast.makeText(context, if (large) "小窗已放大" else "小窗已还原", Toast.LENGTH_SHORT).show()
            }
        } else {
            null
        }
        PipController.syncParams(activity, PipController.effectiveRatio(aspectRatio))
    }

    return remember(inPip, aspectRatio) {
        PlayerPipState(
            inPip = inPip,
            enter = {
                val activity = context as? Activity
                if (activity == null) {
                    false
                } else {
                    // 先置位再进入：部分机型进入小窗时 Activity 会收到 ON_STOP，
                    // 播放器默认会在 ON_STOP 暂停 —— 小窗里画面仍可见，那样就成了「切小窗就停」。
                    playerState.inPipMode = true
                    val ok = PipController.enter(activity, aspectRatio)
                    if (!ok) playerState.inPipMode = false
                    ok
                }
            },
        )
    }
}

/**
 * 「编辑播放器」：决定「更多」面板里显示哪些条目。
 *
 * 刻意只做显隐、不做排序：面板条目少（≤9），排序带来的收益远小于它的交互复杂度
 * （拖拽排序在小屏 + 弹窗里极易误触）。隐藏只是不显示，偏好仍在，重新勾选即可恢复。
 */
@Composable
fun EditPlayerPanelDialog(onDismiss: () -> Unit) {
    val hidden by PlayerMorePrefs.hiddenItems.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF1C1C1E),
            contentColor = Color.White,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = "编辑播放器",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { PlayerMorePrefs.showAll() }) {
                        Text("全部显示", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = "勾选「更多」面板中需要显示的条目。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    MoreItemKey.entries.forEach { item ->
                        val visible = item !in hidden
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.label,
                                color = Color.White.copy(alpha = 0.95f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = visible,
                                onCheckedChange = { PlayerMorePrefs.toggle(item, !it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
                                    uncheckedBorderColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("完成", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * 色彩辅助：偏好 → 播放器，并把「更多」面板里临时改的模式回写偏好，
 * 保证换集 / 换播放页 / 下次进入仍然生效。
 */
@Composable
fun ColorAssistSync(playerState: VideoPlayerState) {
    val pref by PlayerEnhancementPrefs.colorAssistMode.collectAsStateWithLifecycle()
    LaunchedEffect(pref) {
        playerState.setColorAssistMode(pref)
    }
    val runtime by playerState.colorAssistMode
    LaunchedEffect(runtime) {
        if (PlayerEnhancementPrefs.colorAssistMode.value != runtime) {
            PlayerEnhancementPrefs.setColorAssistMode(runtime)
        }
    }
}
