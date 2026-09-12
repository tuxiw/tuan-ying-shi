package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 弹幕显示 / 样式相关偏好（对应「播放设置 → 弹幕设置」页）。
 *
 * 与播放体验密切相关的开关与滑块都集中在这里，UI 层用 `collectAsStateWithLifecycle` 订阅，
 * PlayerScreen 读取后构造 [com.example.tuanyingshi.danmaku.ui.DanmakuConfig] 喂给弹幕引擎；
 * 任一项变化都会让引擎（[com.example.tuanyingshi.danmaku.ui.rememberDanmakuHostState]
 * 按 config 为 key）重建，参数立即生效。
 *
 * 默认值与「弹幕设置」页 slider 的初始位置一致；调用 [resetAll] 可一键还原。
 */
object DanmakuPrefs {
    // 屏蔽
    private const val KEY_KEYWORD_FILTER_ENABLED = "danmaku_keyword_filter_enabled"
    private const val KEY_KEYWORD_FILTER_LIST = "danmaku_keyword_filter_list" // 逗号分隔
    // 显示
    private const val KEY_DISPLAY_AREA_PERCENT = "danmaku_display_area_percent" // 0..100
    private const val KEY_PRESENT_DURATION_MS = "danmaku_present_duration_ms"   // 顶部/底部弹幕
    private const val KEY_LINE_HEIGHT_MULTIPLIER = "danmaku_line_height_multiplier"
    private const val KEY_FOLLOW_PLAYBACK_SPEED = "danmaku_follow_playback_speed"
    private const val KEY_ENABLE_TOP = "danmaku_enable_top"
    private const val KEY_ENABLE_BOTTOM = "danmaku_enable_bottom"
    private const val KEY_ENABLE_FLOATING = "danmaku_enable_floating"
    private const val KEY_ALLOW_OVERLAP = "danmaku_allow_overlap"
    private const val KEY_DEDUP_ENABLED = "danmaku_dedup_enabled"
    // 样式
    private const val KEY_ENABLE_STROKE = "danmaku_enable_stroke"
    private const val KEY_ENABLE_COLOR = "danmaku_enable_color"
    private const val KEY_STROKE_WIDTH = "danmaku_stroke_width"
    private const val KEY_FONT_SIZE_SP = "danmaku_font_size_sp"
    private const val KEY_FONT_WEIGHT_INDEX = "danmaku_font_weight_index" // 1..9 → W100..W900
    private const val KEY_ALPHA_PERCENT = "danmaku_alpha_percent" // 0..100

    private val prefs = TuanyingApp.getInstance().preferences

    private val _keywordFilterEnabled = MutableStateFlow(prefs.getBoolean(KEY_KEYWORD_FILTER_ENABLED, false))
    val keywordFilterEnabled: StateFlow<Boolean> = _keywordFilterEnabled.asStateFlow()

    private val _keywordFilterList = MutableStateFlow(prefs.getString(KEY_KEYWORD_FILTER_LIST, "") ?: "")
    val keywordFilterList: StateFlow<String> = _keywordFilterList.asStateFlow()

    private val _displayAreaPercent = MutableStateFlow(prefs.getInt(KEY_DISPLAY_AREA_PERCENT, 100))
    val displayAreaPercent: StateFlow<Int> = _displayAreaPercent.asStateFlow()

    private val _presentDurationMs = MutableStateFlow(prefs.getInt(KEY_PRESENT_DURATION_MS, 8000))
    val presentDurationMs: StateFlow<Int> = _presentDurationMs.asStateFlow()

    private val _lineHeightMultiplier = MutableStateFlow(
        prefs.getFloat(KEY_LINE_HEIGHT_MULTIPLIER, 1.0f)
    )
    val lineHeightMultiplier: StateFlow<Float> = _lineHeightMultiplier.asStateFlow()

    private val _followPlaybackSpeed = MutableStateFlow(prefs.getBoolean(KEY_FOLLOW_PLAYBACK_SPEED, true))
    val followPlaybackSpeed: StateFlow<Boolean> = _followPlaybackSpeed.asStateFlow()

    private val _enableTop = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_TOP, true))
    val enableTop: StateFlow<Boolean> = _enableTop.asStateFlow()

    private val _enableBottom = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_BOTTOM, false))
    val enableBottom: StateFlow<Boolean> = _enableBottom.asStateFlow()

    private val _enableFloating = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_FLOATING, true))
    val enableFloating: StateFlow<Boolean> = _enableFloating.asStateFlow()

    private val _allowOverlap = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_OVERLAP, false))
    val allowOverlap: StateFlow<Boolean> = _allowOverlap.asStateFlow()

    private val _dedupEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEDUP_ENABLED, false))
    val dedupEnabled: StateFlow<Boolean> = _dedupEnabled.asStateFlow()

    private val _enableStroke = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_STROKE, true))
    val enableStroke: StateFlow<Boolean> = _enableStroke.asStateFlow()

    private val _enableColor = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_COLOR, true))
    val enableColor: StateFlow<Boolean> = _enableColor.asStateFlow()

    private val _strokeWidth = MutableStateFlow(prefs.getFloat(KEY_STROKE_WIDTH, 2.5f))
    val strokeWidth: StateFlow<Float> = _strokeWidth.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(prefs.getFloat(KEY_FONT_SIZE_SP, 16f))
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

    private val _fontWeightIndex = MutableStateFlow(prefs.getInt(KEY_FONT_WEIGHT_INDEX, 6)) // 默认 W600
    val fontWeightIndex: StateFlow<Int> = _fontWeightIndex.asStateFlow()

    private val _alphaPercent = MutableStateFlow(prefs.getInt(KEY_ALPHA_PERCENT, 60))
    val alphaPercent: StateFlow<Int> = _alphaPercent.asStateFlow()

    // —— setters ——

    fun setKeywordFilterEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_KEYWORD_FILTER_ENABLED, value).apply()
        _keywordFilterEnabled.value = value
    }

    /** 关键词列表（按行/逗号分隔均可）。传入时自动按行 + 逗号切分并去除空白。 */
    fun setKeywordFilterList(value: String) {
        prefs.edit().putString(KEY_KEYWORD_FILTER_LIST, value).apply()
        _keywordFilterList.value = value
    }

    /** 当前生效的关键词列表（已切分 + 去空）。用于直接喂给 DanmakuConfig.keywordFilter。 */
    fun keywordFilterListParsed(): List<String> = _keywordFilterList.value
        .split('\n', ',', '，', '、')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun setDisplayAreaPercent(value: Int) {
        val v = value.coerceIn(10, 100) // 至少留 10%，避免完全隐藏
        prefs.edit().putInt(KEY_DISPLAY_AREA_PERCENT, v).apply()
        _displayAreaPercent.value = v
    }

    fun setPresentDurationMs(value: Int) {
        val v = value.coerceIn(1000, 15000)
        prefs.edit().putInt(KEY_PRESENT_DURATION_MS, v).apply()
        _presentDurationMs.value = v
    }

    fun setLineHeightMultiplier(value: Float) {
        val v = value.coerceIn(1.0f, 2.5f)
        prefs.edit().putFloat(KEY_LINE_HEIGHT_MULTIPLIER, v).apply()
        _lineHeightMultiplier.value = v
    }

    fun setFollowPlaybackSpeed(value: Boolean) {
        prefs.edit().putBoolean(KEY_FOLLOW_PLAYBACK_SPEED, value).apply()
        _followPlaybackSpeed.value = value
    }

    fun setEnableTop(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_TOP, value).apply()
        _enableTop.value = value
    }

    fun setEnableBottom(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_BOTTOM, value).apply()
        _enableBottom.value = value
    }

    fun setEnableFloating(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_FLOATING, value).apply()
        _enableFloating.value = value
    }

    fun setAllowOverlap(value: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_OVERLAP, value).apply()
        _allowOverlap.value = value
    }

    fun setDedupEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_DEDUP_ENABLED, value).apply()
        _dedupEnabled.value = value
    }

    fun setEnableStroke(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_STROKE, value).apply()
        _enableStroke.value = value
    }

    fun setEnableColor(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_COLOR, value).apply()
        _enableColor.value = value
    }

    fun setStrokeWidth(value: Float) {
        val v = value.coerceIn(0f, 5f)
        prefs.edit().putFloat(KEY_STROKE_WIDTH, v).apply()
        _strokeWidth.value = v
    }

    fun setFontSizeSp(value: Float) {
        val v = value.coerceIn(10f, 40f)
        prefs.edit().putFloat(KEY_FONT_SIZE_SP, v).apply()
        _fontSizeSp.value = v
    }

    fun setFontWeightIndex(value: Int) {
        val v = value.coerceIn(1, 9)
        prefs.edit().putInt(KEY_FONT_WEIGHT_INDEX, v).apply()
        _fontWeightIndex.value = v
    }

    fun setAlphaPercent(value: Int) {
        val v = value.coerceIn(20, 100) // 至少 20%，防止完全消失
        prefs.edit().putInt(KEY_ALPHA_PERCENT, v).apply()
        _alphaPercent.value = v
    }

    /** 一键恢复默认值（清除所有偏好 + 把 StateFlow 重置到默认）。 */
    fun resetAll() {
        val editor = prefs.edit()
        listOf(
            KEY_KEYWORD_FILTER_ENABLED, KEY_KEYWORD_FILTER_LIST,
            KEY_DISPLAY_AREA_PERCENT, KEY_PRESENT_DURATION_MS, KEY_LINE_HEIGHT_MULTIPLIER,
            KEY_FOLLOW_PLAYBACK_SPEED, KEY_ENABLE_TOP, KEY_ENABLE_BOTTOM, KEY_ENABLE_FLOATING,
            KEY_ALLOW_OVERLAP, KEY_DEDUP_ENABLED,
            KEY_ENABLE_STROKE, KEY_ENABLE_COLOR, KEY_STROKE_WIDTH,
            KEY_FONT_SIZE_SP, KEY_FONT_WEIGHT_INDEX, KEY_ALPHA_PERCENT,
        ).forEach { editor.remove(it) }
        editor.apply()

        _keywordFilterEnabled.value = false
        _keywordFilterList.value = ""
        _displayAreaPercent.value = 100
        _presentDurationMs.value = 8000
        _lineHeightMultiplier.value = 1.0f
        _followPlaybackSpeed.value = true
        _enableTop.value = true
        _enableBottom.value = false
        _enableFloating.value = true
        _allowOverlap.value = false
        _dedupEnabled.value = false
        _enableStroke.value = true
        _enableColor.value = true
        _strokeWidth.value = 2.5f
        _fontSizeSp.value = 16f
        _fontWeightIndex.value = 6
        _alphaPercent.value = 60
    }
}