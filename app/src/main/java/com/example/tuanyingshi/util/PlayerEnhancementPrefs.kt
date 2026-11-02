package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import com.lanlinju.videoplayer.enhancement.ColorAssistMode
import com.lanlinju.videoplayer.enhancement.VideoEnhancementMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放器「视频增强」相关偏好（animeko 风格画质增强 / 截图 / 进度帧预览 / 播放信息层 / 手势）。
 *
 * 字段：
 * - [enhancementMode]：画质增强模式（关闭 / 性能 / 质量）。默认关闭。
 * - [screenshotEnabled]：是否显示「截图」按钮。默认开启。
 * - [framePreviewEnabled]：拖动进度条时是否显示进度帧预览。默认开启。
 * - [statsEnabled]：是否显示「播放信息层」开关（分辨率 / 码率 / 编码 / 帧率）。默认开启。
 * - [gestureEnabled]：是否启用亮度 / 音量 / 快进手势。默认开启。
 * - [autoEnhancementOptimize]：画质增强是否按设备性能自动降级（帧率过低时自动降一档并提示）。默认开启。
 * - [autoEnhancementDowngradeFloor]：自动降级的「下限」档位。默认 [VideoEnhancementMode.PERFORMANCE]，
 *   即帧率过低时最多自动降到性能模式，**不会自动彻底关闭画质增强**，把最终开关留给用户手动决定。
 */
object PlayerEnhancementPrefs {
    private const val KEY_ENHANCEMENT_MODE = "player_enhancement_mode"
    private const val KEY_SCREENSHOT = "player_screenshot_enabled"
    private const val KEY_FRAME_PREVIEW = "player_frame_preview_enabled"
    private const val KEY_STATS = "player_stats_enabled"
    private const val KEY_GESTURE = "player_gesture_enabled"
    private const val KEY_AUTO_OPTIMIZE = "player_enhancement_auto_optimize"
    private const val KEY_AUTO_DOWNGRADE_FLOOR = "player_enhancement_auto_downgrade_floor"
    private const val KEY_COLOR_ASSIST = "player_color_assist_mode"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _enhancementMode =
        MutableStateFlow(parseMode(prefs.getString(KEY_ENHANCEMENT_MODE, "OFF")))
    val enhancementMode: StateFlow<VideoEnhancementMode> = _enhancementMode.asStateFlow()

    fun setEnhancementMode(mode: VideoEnhancementMode) {
        prefs.edit().putString(KEY_ENHANCEMENT_MODE, mode.name).apply()
        _enhancementMode.value = mode
    }

    private val _screenshotEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCREENSHOT, true))
    val screenshotEnabled: StateFlow<Boolean> = _screenshotEnabled.asStateFlow()

    fun setScreenshotEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_SCREENSHOT, value).apply()
        _screenshotEnabled.value = value
    }

    private val _framePreviewEnabled = MutableStateFlow(prefs.getBoolean(KEY_FRAME_PREVIEW, true))
    val framePreviewEnabled: StateFlow<Boolean> = _framePreviewEnabled.asStateFlow()

    fun setFramePreviewEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_FRAME_PREVIEW, value).apply()
        _framePreviewEnabled.value = value
    }

    private val _statsEnabled = MutableStateFlow(prefs.getBoolean(KEY_STATS, true))
    val statsEnabled: StateFlow<Boolean> = _statsEnabled.asStateFlow()

    fun setStatsEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_STATS, value).apply()
        _statsEnabled.value = value
    }

    private val _gestureEnabled = MutableStateFlow(prefs.getBoolean(KEY_GESTURE, true))
    val gestureEnabled: StateFlow<Boolean> = _gestureEnabled.asStateFlow()

    fun setGestureEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_GESTURE, value).apply()
        _gestureEnabled.value = value
    }

    private val _autoEnhancementOptimize = MutableStateFlow(prefs.getBoolean(KEY_AUTO_OPTIMIZE, true))
    val autoEnhancementOptimize: StateFlow<Boolean> = _autoEnhancementOptimize.asStateFlow()

    fun setAutoEnhancementOptimize(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_OPTIMIZE, value).apply()
        _autoEnhancementOptimize.value = value
    }

    /**
     * 自动降级的「下限」档位：帧率过低时自动降级不会低于此档。
     * - [VideoEnhancementMode.PERFORMANCE]（默认）：质量→性能后停住，绝不自动彻底关闭，把最终开关留给用户。
     * - [VideoEnhancementMode.OFF]：必要时一路降到关闭画质增强（原行为）。
     */
    private val _autoEnhancementDowngradeFloor = MutableStateFlow(
        parseMode(prefs.getString(KEY_AUTO_DOWNGRADE_FLOOR, VideoEnhancementMode.PERFORMANCE.name))
    )
    val autoEnhancementDowngradeFloor: StateFlow<VideoEnhancementMode> =
        _autoEnhancementDowngradeFloor.asStateFlow()

    fun setAutoEnhancementDowngradeFloor(mode: VideoEnhancementMode) {
        prefs.edit().putString(KEY_AUTO_DOWNGRADE_FLOOR, mode.name).apply()
        _autoEnhancementDowngradeFloor.value = mode
    }

    /**
     * 色彩辅助模式（不开启 / 红绿色弱 / 蓝黄色弱 / 灰度 / 高对比）。
     * 属于「用户画质偏好」，跨剧集、跨播放页保持；默认不开启。
     */
    private val _colorAssistMode = MutableStateFlow(
        parseColorMode(prefs.getString(KEY_COLOR_ASSIST, ColorAssistMode.OFF.name))
    )
    val colorAssistMode: StateFlow<ColorAssistMode> = _colorAssistMode.asStateFlow()

    fun setColorAssistMode(mode: ColorAssistMode) {
        prefs.edit().putString(KEY_COLOR_ASSIST, mode.name).apply()
        _colorAssistMode.value = mode
    }

    private fun parseMode(raw: String?): VideoEnhancementMode =
        runCatching { VideoEnhancementMode.valueOf(raw ?: "OFF") }.getOrDefault(VideoEnhancementMode.OFF)

    private fun parseColorMode(raw: String?): ColorAssistMode =
        runCatching { ColorAssistMode.valueOf(raw ?: "OFF") }.getOrDefault(ColorAssistMode.OFF)
}
