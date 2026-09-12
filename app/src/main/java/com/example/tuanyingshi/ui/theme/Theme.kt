package com.example.tuanyingshi.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import kotlin.math.pow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tuanyingshi.util.ThemeMode
import com.example.tuanyingshi.util.ThemePrefs

private val DarkColorScheme = darkColorScheme(
    primary = Pink80,
    onPrimary = Color(0xFF3A0A22),
    primaryContainer = Color(0xFF6E1B45),
    onPrimaryContainer = Color(0xFFFFD9E8),
    secondary = Purple80,
    onSecondary = Color(0xFF2A1C44),
    tertiary = Cyan80,
    background = DarkBackground,
    onBackground = OnDark,
    surface = DarkSurface,
    onSurface = OnDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC9C9D4),
    // 容器色（底部弹窗 / 对话框 / 菜单等浮层背景）。
    // 自定义暗色方案原先未声明这些槽位，导致 ModalBottomSheet、AlertDialog 等
    // 浮层回退到浅色背景（白底）。在此显式声明，确保全应用浮层统一为暗色。
    surfaceContainerLowest = Color(0xFF0B0B0F),
    surfaceContainerLow = Color(0xFF1A1A20),
    surfaceContainer = Color(0xFF1F1F26),
    surfaceContainerHigh = Color(0xFF24242C),
    surfaceContainerHighest = Color(0xFF2E2E37),
    // inverse 槽位：默认 Snackbar 的底色/字色读取这两个值。
    // 原先未声明，导致提示条回退到浅色背景；在此显式指定为深色底 + 浅色字。
    inverseSurface = DarkSurface,
    inverseOnSurface = OnDark,
    inversePrimary = Pink80,
    outline = Color(0xFF3A3A44),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFB93A77),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF6750A4),
    background = Color(0xFFFCFCFD),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1B1F),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFEDEDF2),
)

/**
 * 根据暗/亮模式与强调色生成配色。
 * - [accent] 为 null：沿用内置暗/亮方案（保持默认观感）。
 * - [accent] 非 null：以内置方案为基底，仅替换强调色相关槽位（primary / onPrimary /
 *   primaryContainer / onPrimaryContainer / inversePrimary），保证全应用高亮元素统一变色。
 */
private fun buildScheme(dark: Boolean, accent: Color?): ColorScheme {
    val base = if (dark) DarkColorScheme else LightColorScheme
    if (accent == null) return base
    val onPrimary = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val primaryContainer = blend(accent, base.surface, 0.18f)
    return base.copy(
        primary = accent,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = accent,
        inversePrimary = accent,
    )
}

@Composable
fun TuanyingshiTheme(
    content: @Composable () -> Unit,
) {
    val config by ThemePrefs.config.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (config.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val accent = parseColorHex(config.accentHex) ?: TuanyingPink
    val colorScheme = remember(darkTheme, accent) { buildScheme(darkTheme, accent) }

    // 跟随主题切换状态栏 / 导航栏图标的明暗，避免亮色背景下图标不可见
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        window?.let {
            val controller = WindowCompat.getInsetsController(it, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/** 相对亮度（0~1），用于判断强调色上应配黑字还是白字。 */
fun Color.luminance(): Float {
    fun channel(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/** 两色按 [ratio] 线性混合（ratio=1 取 a，0 取 b）。 */
fun blend(a: Color, b: Color, ratio: Float): Color = Color(
    red = a.red * ratio + b.red * (1f - ratio),
    green = a.green * ratio + b.green * (1f - ratio),
    blue = a.blue * ratio + b.blue * (1f - ratio),
    alpha = 1f,
)

/** 解析 `#RRGGBB` / `#AARRGGBB` 为 Compose 颜色，失败返回 null。 */
fun parseColorHex(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching { Color(AndroidColor.parseColor(hex)) }.getOrNull()
}

/** 颜色转 `#RRGGBB`（忽略透明度）。 */
fun Color.toHex(): String =
    "#" + String.format(
        "%02X%02X%02X",
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
    )

/** HSV 表示（h:0~360, s/v:0~1），用于自定义取色器的滑块。 */
data class Hsv(val h: Float, val s: Float, val v: Float)

fun colorToHsv(c: Color): Hsv {
    val r = c.red
    val g = c.green
    val b = c.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else d / max
    return Hsv(h, s, max)
}

fun hsvToColor(hsv: Hsv): Color = Color.hsv(hsv.h, hsv.s, hsv.v)
