package com.example.tuanyingshi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.PlaybackPrefs

/**
 * 播放设置页（设置 → 通用 → 播放设置）：
 *
 * 三个段：
 * 1. 通用开关：自动换集 / 自动换源 / 弹幕默认开启 / 手机端自动全屏 / 平板端自动全屏 / 弹幕设置入口
 * 2. 播放行为：后台播放 / 自动跳转 / 广告过滤 / 禁用动画 / 隐身模式
 * 3. 播放参数：默认倍速 / 长按倍速 / 方向键跳转 / 跳过时长 / 控制栏消失时间 / 默认视频比例
 * 末尾：一键恢复默认设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(navController: NavController) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("播放设置", color = MaterialTheme.colorScheme.onBackground) },
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
                .verticalScroll(rememberScrollState()),
        ) {
            PlaybackSettingsContent(navController)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 播放设置内容区（供手机页与平板左右布局右侧共用）。 */
@Composable
fun PlaybackSettingsContent(navController: NavController? = null) {
    val autoNextEpisode by PlaybackPrefs.autoNextEpisode.collectAsStateWithLifecycle()
    val autoSwitchOnFail by PlaybackPrefs.autoSwitchOnFail.collectAsStateWithLifecycle()
    val danmakuDefaultOn by PlaybackPrefs.danmakuDefaultOn.collectAsStateWithLifecycle()
    val autoFullscreen by PlaybackPrefs.autoFullscreen.collectAsStateWithLifecycle()
    val autoFullscreenTablet by PlaybackPrefs.autoFullscreenTablet.collectAsStateWithLifecycle()

    // ── 通用开关 ─────────────────────────────────
    SwitchRow(
        icon = Icons.Filled.SkipNext,
        title = "播放完成后自动换集",
        desc = "当前一集播放结束后，自动连播下一集",
        checked = autoNextEpisode,
        onCheckedChange = { PlaybackPrefs.setAutoNextEpisode(it) },
    )
    SwitchRow(
        icon = Icons.Filled.SwapHoriz,
        title = "无法播放时自动换线路或源",
        desc = "播放失败时自动尝试切换线路或更换数据源，全部不可用时才提示手动选择",
        checked = autoSwitchOnFail,
        onCheckedChange = { PlaybackPrefs.setAutoSwitchOnFail(it) },
    )
    SwitchRow(
        icon = Icons.Filled.Subtitles,
        title = "弹幕默认开启",
        desc = "进入播放页时自动显示弹幕，无需每次手动点开（仍需开启弹幕数据拉取）",
        checked = danmakuDefaultOn,
        onCheckedChange = { PlaybackPrefs.setDanmakuDefaultOn(it) },
    )
    SwitchRow(
        icon = Icons.Filled.Fullscreen,
        title = "进入播放页自动全屏",
        desc = "打开视频时自动进入全屏播放，无需手动点开全屏按钮（关闭后即使横屏也保持普通播放，仅手机端生效）",
        checked = autoFullscreen,
        onCheckedChange = { PlaybackPrefs.setAutoFullscreen(it) },
    )
    SwitchRow(
        icon = Icons.Filled.Tablet,
        title = "平板端进入播放页自动全屏",
        desc = "平板设备打开视频时自动进入全屏（关闭后即使横屏也保持普通播放，手机端由上方「进入播放页自动全屏」控制）",
        checked = autoFullscreenTablet,
        onCheckedChange = { PlaybackPrefs.setAutoFullscreenTablet(it) },
    )
    if (navController != null) {
        NavRow(
            icon = Icons.Filled.Subtitles,
            title = "弹幕设置",
            desc = "弹幕屏蔽、显示区域、样式等",
            onClick = { navController.navigate(Screen.DanmakuSettings.route) },
        )
    }

    // ── 播放行为 ─────────────────────────────────
    Spacer(Modifier.height(8.dp))
    SectionHeader("播放行为")
    val backgroundPlay by PlaybackPrefs.backgroundPlayEnabled.collectAsStateWithLifecycle()
    SwitchRow(
        icon = Icons.Filled.Headphones,
        title = "后台播放",
        desc = "应用退到后台或熄屏时继续播放音频",
        checked = backgroundPlay,
        onCheckedChange = { PlaybackPrefs.setBackgroundPlayEnabled(it) },
    )
    val autoResume by PlaybackPrefs.autoResumeEnabled.collectAsStateWithLifecycle()
    SwitchRow(
        icon = Icons.Filled.History,
        title = "自动跳转",
        desc = "跳转到上次播放位置",
        checked = autoResume,
        onCheckedChange = { PlaybackPrefs.setAutoResumeEnabled(it) },
    )
    val forceHlsAdFilter by PlaybackPrefs.forceHlsAdFilter.collectAsStateWithLifecycle()
    SwitchRow(
        icon = Icons.Filled.Block,
        title = "广告过滤",
        desc = "强制启用 HLS 广告过滤，忽略规则设置",
        checked = forceHlsAdFilter,
        onCheckedChange = { PlaybackPrefs.setForceHlsAdFilter(it) },
    )
    val disableAnimations by PlaybackPrefs.disableAnimations.collectAsStateWithLifecycle()
    SwitchRow(
        icon = Icons.Filled.VisibilityOff,
        title = "禁用动画",
        desc = "禁用播放器内的过渡动画",
        checked = disableAnimations,
        onCheckedChange = { PlaybackPrefs.setDisableAnimations(it) },
    )
    val incognitoMode by PlaybackPrefs.incognitoMode.collectAsStateWithLifecycle()
    SwitchRow(
        icon = Icons.Filled.VisibilityOff,
        title = "隐身模式",
        desc = "不保留观看记录",
        checked = incognitoMode,
        onCheckedChange = { PlaybackPrefs.setIncognitoMode(it) },
    )

    // ── 播放参数 ─────────────────────────────────
    Spacer(Modifier.height(8.dp))
    SectionHeader("播放参数")
    val defaultSpeed by PlaybackPrefs.defaultSpeed.collectAsStateWithLifecycle()
    PlaybackSliderRow(
        icon = Icons.Filled.Speed,
        title = "默认倍速",
        value = defaultSpeed,
        displayValue = String.format("%.1fx", defaultSpeed),
        valueRange = 0.5f..3.0f,
        steps = 24, // 0.5/3.0 -> 0.1 步长
        onValueChange = { PlaybackPrefs.setDefaultSpeed(it) },
    )
    val longPressSpeed by PlaybackPrefs.longPressSpeed.collectAsStateWithLifecycle()
    PlaybackSliderRow(
        icon = Icons.Filled.FastForward,
        title = "长按倍速",
        value = longPressSpeed,
        displayValue = String.format("%.1fx", longPressSpeed),
        valueRange = 1.0f..5.0f,
        steps = 39, // 0.1 步长
        onValueChange = { PlaybackPrefs.setLongPressSpeed(it) },
    )
    val arrowKeySkipSeconds by PlaybackPrefs.arrowKeySkipSeconds.collectAsStateWithLifecycle()
    PlaybackSliderRow(
        icon = Icons.Filled.SwapHoriz,
        title = "方向键跳转",
        value = arrowKeySkipSeconds.toFloat(),
        displayValue = "$arrowKeySkipSeconds 秒",
        valueRange = 1f..120f,
        steps = 118,
        onValueChange = { PlaybackPrefs.setArrowKeySkipSeconds(it.toInt()) },
    )
    val skipDurationSeconds by PlaybackPrefs.skipDurationSeconds.collectAsStateWithLifecycle()
    PlaybackSliderRow(
        icon = Icons.Filled.SkipNext,
        title = "跳过时长",
        value = skipDurationSeconds.toFloat(),
        displayValue = "$skipDurationSeconds 秒",
        valueRange = 10f..600f,
        steps = 58, // 10 秒步长
        onValueChange = { PlaybackPrefs.setSkipDurationSeconds(it.toInt()) },
    )
    val controlHideSeconds by PlaybackPrefs.controlHideSeconds.collectAsStateWithLifecycle()
    PlaybackSliderRow(
        icon = Icons.Filled.Timer,
        title = "控制栏消失时间",
        value = controlHideSeconds.toFloat(),
        displayValue = "$controlHideSeconds 秒",
        valueRange = 1f..30f,
        steps = 28,
        onValueChange = { PlaybackPrefs.setControlHideSeconds(it.toInt()) },
    )
    val defaultVideoAspectRatio by PlaybackPrefs.defaultVideoAspectRatio.collectAsStateWithLifecycle()
    VideoAspectRatioRow(value = defaultVideoAspectRatio)

    Spacer(Modifier.height(16.dp))

    // ── 恢复默认设置 ─────────────────────────────────
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { PlaybackPrefs.resetAll() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.RestartAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "恢复默认设置",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "将播放相关设置恢复为默认值",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 辅助组件
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp, end = 20.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** 跳转型 Row（图标 + 标题 + 描述 + 右侧 Chevron）。 */
@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Slider 行：图标 + 标题 + 右侧数值徽章 + 下方 Slider。视觉与弹幕设置页一致。 */
@Composable
private fun PlaybackSliderRow(
    icon: ImageVector,
    title: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    var sliderPosition by remember(value) { mutableFloatStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = displayValue,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onValueChange(sliderPosition) },
            valueRange = valueRange,
            steps = if (steps > 0) steps else 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            ),
        )
        // 视觉刻度点（纯装饰）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(8) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                )
            }
        }
    }
}

/** 默认视频比例：可点击 Row 弹出 Radio 选择器（自动 / 16:9 / 4:3 / 全屏）。 */
@Composable
private fun VideoAspectRatioRow(value: String) {
    var showDialog by remember { mutableStateOf(false) }
    val display = when (value) {
        "16:9" -> "16:9"
        "4:3" -> "4:3"
        "full" -> "全屏"
        else -> "自动"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AspectRatio,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "默认视频比例",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = display,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    if (showDialog) {
        VideoAspectRatioDialog(
            current = value,
            onDismiss = { showDialog = false },
            onConfirm = { selected ->
                PlaybackPrefs.setDefaultVideoAspectRatio(selected)
                showDialog = false
            },
        )
    }
}

@Composable
private fun VideoAspectRatioDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val options = listOf(
        "auto" to "自动（默认 Fit）",
        "16:9" to "16:9",
        "4:3" to "4:3",
        "full" to "全屏（不变形填充）",
    )
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("默认视频比例") },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = key }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == key, onClick = { selected = key })
                        Spacer(Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}