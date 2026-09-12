package com.example.tuanyingshi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
    "#FFFF4D6D", // 樱粉（默认）
    "#FF9D7CE0", // 魅紫
    "#FF42A5F5", // 青蓝
    "#FF26C6A5", // 湖绿
    "#FF4CAF50", // 翠绿
    "#FFFF9800", // 橙黄
    "#FFE53935", // 赤红
    "#FF5C6BC0", // 靛蓝
    "#FFE91E63", // 品红
    "#FFFF7043", // 暖橙
)

private const val DEFAULT_ACCENT = "#FFFF4D6D"

/**
 * 主题配色设置内容（可嵌入设置页右侧 / 独立页）。
 * 包含：外观模式（跟随系统 / 暗色 / 亮色）+ 强调色（预置色板 + 自定义 HSV 取色器）。
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
        SectionTitle("外观模式")
        Spacer(Modifier.height(8.dp))
        ThemeMode.entries.forEach { mode ->
            val title = when (mode) {
                ThemeMode.SYSTEM -> "跟随系统"
                ThemeMode.DARK -> "暗色"
                ThemeMode.LIGHT -> "亮色"
            }
            ModeOptionRow(
                title = title,
                selected = config.mode == mode,
                onClick = { ThemePrefs.setMode(mode) },
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("主题配色")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "选择全应用强调色；恢复默认即可回到内置品牌色。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 5,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PRESET_ACCENTS.forEach { hex ->
                val color = parseColorHex(hex) ?: TuanyingPink
                val selected = currentHex == hex || (currentHex == null && hex == DEFAULT_ACCENT)
                ColorSwatch(
                    color = color,
                    selected = selected,
                    onClick = {
                        ThemePrefs.setAccent(hex)
                        showCustom = false
                    },
                )
            }
            CustomSwatch(
                selected = showCustom,
                current = customColor,
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun ModeOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CustomSwatch(
    selected: Boolean,
    current: Color,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) borderColor else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Palette,
            contentDescription = "自定义颜色",
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
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
