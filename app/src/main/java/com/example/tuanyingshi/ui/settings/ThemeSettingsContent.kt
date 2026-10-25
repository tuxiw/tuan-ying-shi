package com.example.tuanyingshi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tuanyingshi.ui.theme.Hsv
import com.example.tuanyingshi.ui.theme.TuanyingPink
import com.example.tuanyingshi.ui.theme.colorToHsv
import com.example.tuanyingshi.ui.theme.luminance
import com.example.tuanyingshi.ui.theme.hsvToColor
import com.example.tuanyingshi.ui.theme.parseColorHex
import com.example.tuanyingshi.ui.theme.toHex
import com.example.tuanyingshi.util.ThemeMode
import com.example.tuanyingshi.util.ThemePrefs

/** 预置强调色（兼顾暗/亮背景可读性）。第一个为默认品牌色。 */
private val PRESET_ACCENTS = listOf(
    "#FF42A5F5", // 天蓝
    "#FF26C6A5", // 湖绿
    "#FF4CAF50", // 翠绿
    "#FF9D7CE0", // 魅紫（默认）
    "#FFB39DDB", // 浅紫
    "#FFBA68C8", // 兰紫
    "#FFF06292", // 樱粉
    "#FFFF7043", // 珊瑚
    "#FFFFA726", // 橙黄
    "#FFD4C04A", // 芥黄
)

private const val DEFAULT_ACCENT = "#FF9D7CE0"

/** 「自动」模式预览卡的对角线裁剪形状：保留左上三角（亮色部分）。 */
private val DiagonalShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(0f, size.height)
    close()
}

/**
 * 「主题与色彩」设置内容（可嵌入设置页右侧 / 独立页）。
 * 包含：主题模式三卡片预览选择（浅色 / 深色 / 自动）+ 色彩开关组 +
 * 调色板（预置强调色 + 自定义 HSV 取色器）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSettingsContent() {
    val config by ThemePrefs.config.collectAsStateWithLifecycle()
    val currentHex = config.accentHex

    var showCustom by remember(currentHex) {
        mutableStateOf(currentHex != null && currentHex !in PRESET_ACCENTS)
    }
    var customColor by remember(currentHex) {
        mutableStateOf(parseColorHex(currentHex) ?: TuanyingPink)
    }
    var hsv by remember(customColor) { mutableStateOf(colorToHsv(customColor)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // ───────────────── 主题模式 ─────────────────
        GroupLabel("主题")
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(8.dp))
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.LIGHT -> "浅色"
                    ThemeMode.DARK -> "深色"
                    ThemeMode.SYSTEM -> "自动"
                }
                ThemeModeCard(
                    label = label,
                    selected = config.mode == mode,
                    mode = mode,
                    onClick = { ThemePrefs.setMode(mode) },
                )
                Spacer(Modifier.width(20.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // ───────────────── 色彩开关 ─────────────────
        ThemeSwitchRow(
            title = "动态色彩",
            desc = "使用系统强调色（Android 12+ 生效）",
            checked = config.dynamicColor,
            onCheckedChange = { ThemePrefs.setDynamicColor(it) },
        )
        ThemeSwitchRow(
            title = "高对比度深色主题",
            desc = "深色模式使用纯黑背景，在 AMOLED 屏幕使用纯黑背景可以省电",
            checked = config.amoled,
            onCheckedChange = { ThemePrefs.setAmoled(it) },
        )
        ThemeSwitchRow(
            title = "播放页始终使用深色主题",
            desc = "提供更加沉浸的播放体验",
            checked = config.playerForceDark,
            onCheckedChange = { ThemePrefs.setPlayerForceDark(it) },
        )

        Spacer(Modifier.height(24.dp))

        // ───────────────── 调色板 ─────────────────
        GroupLabel("调色板")
        Spacer(Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 5,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PRESET_ACCENTS.forEach { hex ->
                val color = parseColorHex(hex) ?: TuanyingPink
                val selected = currentHex == hex || (currentHex == null && hex == DEFAULT_ACCENT)
                PaletteSwatch(
                    color = color,
                    selected = selected,
                    onClick = {
                        ThemePrefs.setAccent(hex)
                        showCustom = false
                    },
                )
            }
            PaletteSwatch(
                color = customColor,
                isCustom = true,
                selected = showCustom,
                onClick = { showCustom = !showCustom },
            )
        }

        if (showCustom) {
            Spacer(Modifier.height(16.dp))
            Text("自定义颜色", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(customColor),
            )
            Spacer(Modifier.height(12.dp))

            HsvSliderRow("色相", hsv.h / 360f) {
                hsv = hsv.copy(h = it * 360f)
                customColor = hsvToColor(hsv)
                ThemePrefs.setAccent(customColor.toHex())
            }
            HsvSliderRow("饱和度", hsv.s) {
                hsv = hsv.copy(s = it)
                customColor = hsvToColor(hsv)
                ThemePrefs.setAccent(customColor.toHex())
            }
            HsvSliderRow("明度", hsv.v) {
                hsv = hsv.copy(v = it)
                customColor = hsvToColor(hsv)
                ThemePrefs.setAccent(customColor.toHex())
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "当前：${customColor.toHex()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 分组小标题（强调色小字样式，与参考设计一致）。 */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

// ───────────────── 主题模式卡片 ─────────────────

@Composable
private fun ThemeModeCard(
    label: String,
    selected: Boolean,
    mode: ThemeMode,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable { onClick() },
        ) {
            if (mode == ThemeMode.SYSTEM) {
                ModePreview(dark = true, accent = accent)
                Box(modifier = Modifier.matchParentSize().clip(DiagonalShape)) {
                    ModePreview(dark = false, accent = accent)
                }
            } else {
                ModePreview(dark = mode == ThemeMode.DARK, accent = accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = accent),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** 卡片内的迷你界面预览：顶部色块 + 文本行 + 底部胶囊按钮行。 */
@Composable
private fun ModePreview(dark: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val bg = if (dark) Color(0xFF15151B) else Color(0xFFF4F4F6)
    val cover = accent.copy(alpha = if (dark) 0.55f else 0.8f)
    val line = if (dark) Color(0xFF3A3A44) else Color(0xFFD9D9E0)
    val pill = accent.copy(alpha = if (dark) 0.35f else 0.25f)
    Box(
        modifier = modifier
            .background(bg)
            .padding(9.dp),
    ) {
        Column {
            // 迷你封面块
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cover),
            )
            Spacer(Modifier.height(8.dp))
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(line),
                )
                Spacer(Modifier.height(5.dp))
            }
            Spacer(Modifier.weight(1f))
            // 底部：圆形 + 胶囊按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(pill),
                )
            }
        }
    }
}

// ───────────────── 开关行 ─────────────────

/** 无图标的设置开关行（标题 + 描述 + Switch），与参考设计一致。 */
@Composable
private fun ThemeSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ───────────────── 调色板 ─────────────────

/** 调色板色块：圆角色卡片内嵌圆形色标，选中时圆环 + 对勾。 */
@Composable
private fun PaletteSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    isCustom: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (isCustom && selected) MaterialTheme.colorScheme.primary else color.copy(alpha = 0.6f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isCustom) {
                Icon(
                    Icons.Filled.Palette,
                    contentDescription = "自定义颜色",
                    tint = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            } else if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun HsvSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
