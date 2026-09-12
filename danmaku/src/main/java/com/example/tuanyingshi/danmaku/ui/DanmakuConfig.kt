package com.example.tuanyingshi.danmaku.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 用于限制弹幕的最低和最高速度
 * 同时根据弹幕长度基于[baseSpeed]计算相应速度
 */
internal val MaxSpeedMultiplier = 2f
internal val BaseTextLength = 80.dp
internal val MaxTextLength = 620.dp

/**
 * Configuration for the presentation of each [Danmaku].
 */
@Immutable
data class DanmakuConfig(
    /**
     * Controls the text styles of the [Danmaku].
     * For example, font size, stroke width.
     */
    val style: DanmakuStyle = DanmakuStyle.Default,
    /**
     * Time for the [Danmaku] to move from the right edge to the left edge of the screen.
     * In other words, it controls the movement speed of a [Danmaku].
     *
     * Unit: dp/s
     */
    val baseSpeed: Float = 88f,
    /**
     * The minimum distance between two [Danmaku]s so that they don't overlap.
     */
    val safeSeparation: Dp = 2.dp,
    /**
     * 弹幕在播放区域中的显示区域占比. 0.1 表示占播放区域的 10%, 1.0 表示铺满整个播放区域.
     *
     * 范围: `[0, 1]`。播放器内默认铺满（1.0f），即「全屏方式显示」；
     * 窗口缩放时由 [com.example.tuanyingshi.danmaku.ui.DanmakuHostState] 按真实尺寸重建轨道，
     * 弹幕始终贴合当前播放区域，不会因窗口缩小而溢出或错位。
     */
    val displayArea: Float = 1.0f,
    /**
     * 允许彩色弹幕. 禁用时将会把所有彩色弹幕都显示为白色.
     */
    val enableColor: Boolean = true,
    /**
     * 是否启用顶部弹幕
     */
    val enableTop: Boolean = true,
    /**
     * 是否启用浮动弹幕
     */
    val enableFloating: Boolean = true,
    /**
     * 是否启用底部弹幕
     */
    val enableBottom: Boolean = true,
    /**
     * 弹幕是否跟随视频播放倍速（开启后 1.5x 速度播放时弹幕同步加速，0.5x 时减速）。
     * 关闭时弹幕保持原速，常用于需要仔细阅读弹幕内容的场景。
     */
    val followPlaybackSpeed: Boolean = true,
    /**
     * 海量弹幕模式：开启后即使轨道已满也会强制放置（同一轨道多条弹幕叠加绘制），
     * 避免高密度弹幕流时被丢。关闭时按正常规则（无空闲轨道则丢弃）。
     */
    val allowOverlap: Boolean = false,
    /**
     * 弹幕去重：开启后相同文本的弹幕在去重窗口内只会保留一条，避免刷屏。
     */
    val dedupEnabled: Boolean = false,
    /**
     * 关键词屏蔽列表：含任一关键词的弹幕会被丢弃（大小写不敏感、按包含匹配）。
     * 空列表表示不过滤。
     */
    val keywordFilter: List<String> = emptyList(),
    /**
     * 调试模式, 启用发送弹幕的信息和弹幕处理信息.
     */
    val isDebug: Boolean = false,
    /**
     * 轨道属性
     */
    val danmakuTrackProperties: DanmakuTrackProperties = DanmakuTrackProperties.Default,
) {
    companion object {
        @Stable
        val Default = DanmakuConfig()
    }
}

@Immutable
data class DanmakuStyle(
    /**
     * 字体大小。
     * 注：18.sp 在高 DPI 平板（如 2560 宽屏）上渲染约 57px，已与轨道高度（trackHeight）持平，
     * 加上 W600 + 4f 描边会让弹幕显得过厚、压住画面；平板默认降到 16.sp（仍清晰可读）。
     */
    val fontSize: TextUnit = 16.sp,
    val fontWeight: FontWeight = FontWeight.W600,
    /**
     * 弹幕文字 + 描边的整体透明度。
     * 原 0.8f 在大屏 + 高密度场景下会形成明显遮挡（白色填充 + 黑色描边都半透叠加，画面细节被压）。
     * 降到 0.6f 后画面可透过来，弹幕仍清晰但不再"覆盖"画面（尤其是字幕区）。
     */
    val alpha: Float = 0.6f,
    val strokeColor: Color = Color.Black,
    /**
     * 描边宽度。原 4f 让每条弹幕视觉边界很厚，进一步加剧遮挡感；降到 2.5f 既保留可读性，又不再压画面。
     */
    val strokeWidth: Float = 2.5f,
    /**
     * 是否绘制黑色描边。关闭后仅渲染白色填充（无外框），适合在暗色画面或用户偏好极简时使用。
     * 关闭时 [strokeWidth] 仍参与轨道高度计算（保持布局稳定），仅不绘制。
     */
    val enableStroke: Boolean = true,
    val shadow: Shadow? = null,
) {
    @Stable
    fun styleForBorder(): TextStyle = if (!enableStroke) {
        // 关闭描边：返回与文字同色的透明样式，避免白白绘制一次不可见描边。
        TextStyle(
            fontSize = fontSize,
            color = Color.Transparent,
            fontWeight = fontWeight,
            textMotion = TextMotion.Animated,
            shadow = shadow,
        )
    } else {
        TextStyle(
            fontSize = fontSize,
            color = strokeColor.copy(alpha),
            fontWeight = fontWeight,
            drawStyle = Stroke(
                width = strokeWidth,
                join = StrokeJoin.Round,
            ),
            textMotion = TextMotion.Animated,
            shadow = shadow,
        )
    }

    // 'inside' the border
    @Stable
    fun styleForText(color: Color = Color.White): TextStyle = TextStyle(
        fontSize = fontSize,
        color = color.copy(alpha),
        fontWeight = fontWeight,
        textMotion = TextMotion.Animated,
    )

    override fun toString(): String {
        return "DanmakuStyle(fontSize=$fontSize, fontWeight=$fontWeight, alpha=$alpha, strokeColor=$strokeColor, strokeMiter=$strokeWidth, strokeEnabled=$enableStroke, shadow=$shadow)"
    }

    companion object {
        @Stable
        val Default = DanmakuStyle()
    }
}

@Immutable
data class DanmakuTrackProperties(
    /**
     * Shift of the danmaku to be considered as fully out of the screen.
     */
    val visibilitySafeArea: Int = 0,
    /**
     * vertical padding of track, both top and bottom.
     */
    val verticalPadding: Int = 1,
    /**
     * speed multiplier for speed of floating danmaku.
     * represents a multiplier to speed that 2x length of danmaku text
     */
    val speedMultiplier: Float = 1.14f,
    /**
     * fixed danmaku present duration.
     * unit: ms
     */
    val fixedDanmakuPresentDuration: Long = 8000,
    /**
     * 弹幕行高倍数。
     * 1.0 = 字号完全贴满轨道；1.6 = 上下各加约 30% 间距（视觉上更舒展）；
     * 2.0 = 行间距加倍（轨道数减半、稀疏显示）。
     * 与 DanmakuStyle.fontSize 共同决定轨道高度（trackHeight）。
     */
    val lineHeightMultiplier: Float = 1.0f,
) {
    companion object {
        @Stable
        val Default = DanmakuTrackProperties()
    }
}