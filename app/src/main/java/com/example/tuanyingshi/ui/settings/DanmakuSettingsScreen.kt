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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Crop75
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.util.DanmakuPrefs

/**
 * 弹幕设置页（设置 → 通用 → 播放设置 → 弹幕设置）。
 *
 * 三大段：
 * 1. 弹幕屏蔽 —— 关键词屏蔽（开关 + 内嵌编辑弹窗）；
 * 2. 弹幕显示 —— 显示区域 / 持续时间 / 行高 / 跟随倍速 / 顶部·底部·滚动 / 海量叠加 / 去重；
 * 3. 弹幕样式 —— 描边 / 描边粗细 / 颜色 / 字体大小·字重 / 不透明度。
 *
 * 所有偏好通过 [DanmakuPrefs] 持久化（SharedPreferences + StateFlow），设置变化后由 PlayerScreen
 * 重新构造 DanmakuConfig，弹幕引擎（rememberDanmakuHostState）以 config 为 key 自动重建。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanmakuSettingsScreen(navController: NavController) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("弹幕设置", color = MaterialTheme.colorScheme.onBackground) },
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
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            DanmakuSettingsContent()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 内容区（供手机独立页 / 平板左右布局复用）。 */
@Composable
fun DanmakuSettingsContent() {
    // 弹幕屏蔽
    SectionHeader("弹幕屏蔽")
    KeywordFilterRow()

    Spacer(Modifier.height(8.dp))

    // 弹幕显示
    SectionHeader("弹幕显示")
    DisplayAreaRow()
    DurationRow()
    LineHeightRow()
    FollowPlaybackSpeedRow()
    EnableTopRow()
    EnableBottomRow()
    EnableFloatingRow()
    AllowOverlapRow()
    DedupRow()

    Spacer(Modifier.height(8.dp))

    // 弹幕样式
    SectionHeader("弹幕样式")
    EnableStrokeRow()
    StrokeWidthRow()
    EnableColorRow()
    FontSizeRow()
    FontWeightRow()
    AlphaRow()

    Spacer(Modifier.height(16.dp))

    // 恢复默认设置
    ResetRow()
}

// ─────────────────────────────────────────────────────────────────────────────
// 段落小标题
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

// ─────────────────────────────────────────────────────────────────────────────
// 开关行（复用项目 SwitchRow 风格）
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DanmakuSwitchRow(
    icon: ImageVector,
    title: String,
    desc: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
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
            if (desc.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 滑动条行：图标 + 标题 + 右侧数值徽章 + 下方带刻度点样式的 Slider
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 通用滑块行：
 * - value: 当前生效值（外部受控）
 * - displayValue: 徽章里要展示的字符串
 * - valueRange: 取值范围
 * - steps: 离散档位（-1 表示连续）
 * - onChange: 拖动期间回调；松手时通过 [onChangeFinished] 持久化
 */
@Composable
private fun DanmakuSliderRow(
    icon: ImageVector,
    title: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    // 拖动期间本地保存，松手时回写到 prefs（避免高频写入）。
    var sliderPosition by remember(value) { mutableFloatStateOf(value) }
    val min = valueRange.start
    val max = valueRange.endInclusive
    val stepCount = if (steps > 0) steps else 8 // 视觉刻度点数（仅外观）

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
            // 数值徽章
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
            valueRange = min..max,
            steps = if (steps > 0) steps else 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            ),
        )
        // 视觉刻度点（纯装饰，不参与交互）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(stepCount) {
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

// ─────────────────────────────────────────────────────────────────────────────
// 关键词屏蔽（开关 + 点击打开编辑弹窗）
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KeywordFilterRow() {
    val enabled by DanmakuPrefs.keywordFilterEnabled.collectAsStateWithLifecycle()
    val listText by DanmakuPrefs.keywordFilterList.collectAsStateWithLifecycle()
    val parsed = remember(listText) { DanmakuPrefs.keywordFilterListParsed() }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "关键词屏蔽",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        !enabled -> "关闭 · 点击管理关键词列表"
                        parsed.isEmpty() -> "已开启 · 尚未添加关键词"
                        else -> "已开启 · 当前 ${parsed.size} 个关键词"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { DanmakuPrefs.setKeywordFilterEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }

    if (showDialog) {
        KeywordFilterDialog(
            initial = listText,
            onDismiss = { showDialog = false },
            onConfirm = { newText ->
                DanmakuPrefs.setKeywordFilterList(newText)
                // 有内容时自动开启；为空时维持当前开关状态（用户可手动关）。
                if (DanmakuPrefs.keywordFilterListParsed().isNotEmpty()) {
                    DanmakuPrefs.setKeywordFilterEnabled(true)
                }
                showDialog = false
            },
        )
    }
}

@Composable
private fun KeywordFilterDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑屏蔽关键词") },
        text = {
            Column {
                Text(
                    text = "包含任一关键词的弹幕将被丢弃（大小写不敏感）。可用换行、逗号或「、」分隔多个。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    placeholder = { Text("剧透\n剧透警告\n完结剧透") },
                    singleLine = false,
                    textStyle = TextStyle.Default,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 弹幕显示区
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DisplayAreaRow() {
    val percent by DanmakuPrefs.displayAreaPercent.collectAsStateWithLifecycle()
    DanmakuSliderRow(
        icon = Icons.Filled.Crop75,
        title = "弹幕区域",
        value = percent.toFloat(),
        displayValue = "$percent%",
        valueRange = 10f..100f,
        steps = 8,
        onValueChange = { DanmakuPrefs.setDisplayAreaPercent(it.toInt()) },
    )
}

@Composable
private fun DurationRow() {
    val ms by DanmakuPrefs.presentDurationMs.collectAsStateWithLifecycle()
    DanmakuSliderRow(
        icon = Icons.Filled.Timer,
        title = "弹幕持续时间",
        value = ms.toFloat(),
        displayValue = "${ms / 1000} 秒",
        valueRange = 1000f..15000f,
        steps = 13,
        onValueChange = { DanmakuPrefs.setPresentDurationMs(it.toInt()) },
    )
}

@Composable
private fun LineHeightRow() {
    val v by DanmakuPrefs.lineHeightMultiplier.collectAsStateWithLifecycle()
    DanmakuSliderRow(
        icon = Icons.Filled.FormatLineSpacing,
        title = "弹幕行高",
        value = v,
        displayValue = String.format("%.1f", v),
        valueRange = 1.0f..2.5f,
        steps = 14,
        onValueChange = { DanmakuPrefs.setLineHeightMultiplier(it) },
    )
}

@Composable
private fun FollowPlaybackSpeedRow() {
    val v by DanmakuPrefs.followPlaybackSpeed.collectAsStateWithLifecycle()
    DanmakuSwitchRow(
        icon = Icons.Filled.Speed,
        title = "弹幕跟随视频倍速",
        desc = "开启后弹幕速度会随视频倍速而改变",
        checked = v,
        onCheckedChange = { DanmakuPrefs.setFollowPlaybackSpeed(it) },
    )
}

@Composable
private fun EnableTopRow() {
    val v by DanmakuPrefs.enableTop.collectAsStateWithLifecycle()
    DanmakuSwitchRow(
        icon = Icons.Filled.VerticalAlignTop,
        title = "顶部弹幕",
        checked = v,
        onCheckedChange = { DanmakuPrefs.setEnableTop(it) },
    )
}

@Composable
private fun EnableBottomRow() {
    val v by DanmakuPrefs.enableBottom.collectAsStateWithLifecycle()
    DanmakuSwitchRow(
        icon = Icons.Filled.VerticalAlignBottom,
        title = "底部弹幕",
        checked = v,
        onCheckedChange = { DanmakuPrefs.setEnableBottom(it) },
    )
}

@Composable
private fun EnableFloatingRow() {
    val v by DanmakuPrefs.enableFloating.collectAsStateWithLifecycle()
    DanmakuSwitchRow(
        icon = Icons.Filled.CompareArrows,
        title = "滚动弹幕",
        checked = v,
        onCheckedChange = { DanmakuPrefs.setEnableFloating(it) },
    )
}

@Composable
private fun AllowOverlapRow() {
    val v by DanmakuPrefs.allowOverlap.collectAsStateWithLifecycle()
    DanmakuSwitchRow(
        icon = Icons.Filled.Layers,
        title = "海量弹幕",
        desc = "弹幕过多时进行叠加绘制",
        checked = v,
        onCheckedChange = { DanmakuPrefs.setAllowOverlap(it) },
    )
}

@Composable
private fun DedupRow() {
    val v by DanmakuPrefs.dedupEnabled.collectAsStateWithLifecycle()
    DanmakuSwitchRow(
        icon = Icons.Filled.FilterAlt,
        title = "弹幕去重",
        desc = "相同内容弹幕过多时合并为一条弹幕",
        checked = v,
        onCheckedChange = { DanmakuPrefs.setDedupEnabled(it) },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 弹幕样式区
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EnableStrokeRow() {
    val v by DanmakuPrefs.enableStroke.collectAsStateWithLifecycle()
    DanmakuSwitchRow(
        icon = Icons.Filled.BorderOuter,
        title = "弹幕描边",
        checked = v,
        onCheckedChange = { DanmakuPrefs.setEnableStroke(it) },
    )
}

@Composable
private fun StrokeWidthRow() {
    val w by DanmakuPrefs.strokeWidth.collectAsStateWithLifecycle()
    DanmakuSliderRow(
        icon = Icons.Filled.LineWeight,
        title = "弹幕描边粗细",
        value = w,
        displayValue = String.format("%.1f", w),
        valueRange = 0f..5f,
        steps = 9,
        onValueChange = { DanmakuPrefs.setStrokeWidth(it) },
    )
}

@Composable
private fun EnableColorRow() {
    val v by DanmakuPrefs.enableColor.collectAsStateWithLifecycle()
    DanmakuSwitchRow(
        icon = Icons.Filled.Palette,
        title = "弹幕颜色",
        checked = v,
        onCheckedChange = { DanmakuPrefs.setEnableColor(it) },
    )
}

@Composable
private fun FontSizeRow() {
    val v by DanmakuPrefs.fontSizeSp.collectAsStateWithLifecycle()
    DanmakuSliderRow(
        icon = Icons.Filled.FormatSize,
        title = "字体大小",
        value = v,
        displayValue = v.toInt().toString(),
        valueRange = 10f..40f,
        steps = 29,
        onValueChange = { DanmakuPrefs.setFontSizeSp(it) },
    )
}

@Composable
private fun FontWeightRow() {
    val v by DanmakuPrefs.fontWeightIndex.collectAsStateWithLifecycle()
    DanmakuSliderRow(
        icon = Icons.Filled.FormatBold,
        title = "字体字重",
        value = v.toFloat(),
        displayValue = v.toString(),
        valueRange = 1f..9f,
        steps = 7,
        onValueChange = { DanmakuPrefs.setFontWeightIndex(it.toInt()) },
    )
}

@Composable
private fun AlphaRow() {
    val percent by DanmakuPrefs.alphaPercent.collectAsStateWithLifecycle()
    DanmakuSliderRow(
        icon = Icons.Filled.Opacity,
        title = "弹幕不透明度",
        value = percent.toFloat(),
        displayValue = "$percent%",
        valueRange = 20f..100f,
        steps = 7,
        onValueChange = { DanmakuPrefs.setAlphaPercent(it.toInt()) },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 恢复默认设置
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResetRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { DanmakuPrefs.resetAll() }
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
                text = "将弹幕相关设置恢复为默认值",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}