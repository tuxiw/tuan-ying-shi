package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放相关偏好。
 *
 * 字段：
 * - [autoNextEpisode]：当前集播放完成后是否自动切换到下一集（连播）。默认 true（开启）。
 * - [autoSwitchOnFail]：无法播放（链接不可播放 / 解析失败 / 播放报错）时，是否自动尝试切换
 *   线路（同一源内的其它线路）或换源（其它 CSS 源）。默认 false（关闭，交由用户手动选择）。
 * - [danmakuDefaultOn]：进入播放页时弹幕是否默认开启。默认 false（关闭，需手动点开）。
 * - [autoFullscreen]：进入播放页时是否自动进入全屏播放（手机端，平板端由 [autoFullscreenTablet] 控制）。默认 false（关闭，仅横屏时全屏）。
 * - [autoFullscreenTablet]：平板端进入播放页时是否自动进入全屏播放。默认 true（开启，平板大屏更适合直接全屏）。
 * - [backgroundPlayEnabled]：应用退到后台或熄屏后是否继续播放音频。默认 false（关闭，避免后台耗电 / 通知栏被占用）。
 * - [autoResumeEnabled]：进入播放页时是否跳转到上次播放位置。默认 true（开启）。
 * - [forceHlsAdFilter]：强制启用 HLS 广告过滤，忽略数据源自身的开关。默认 false（关闭）。
 * - [disableAnimations]：禁用播放器内的过渡动画（进出全屏、控件淡入淡出等）。默认 false。
 * - [incognitoMode]：隐身模式 —— 不保留观看记录、不加入历史、不更新进度。默认 false。
 * - [defaultSpeed]：默认播放倍速，进入播放页时立即应用到 ExoPlayer。默认 1.0x。
 * - [longPressSpeed]：长按屏幕或按住方向键时的倍速。默认 2.0x。
 * - [arrowKeySkipSeconds]：键盘左右方向键的单次快进 / 快退秒数。默认 10 秒。
 * - [skipDurationSeconds]：播放器顶栏「跳过片头/片尾」按钮的单次跳转秒数。默认 80 秒。
 * - [controlHideSeconds]：播放控制栏自动隐藏前的停留时长。默认 4 秒。
 * - [defaultVideoAspectRatio]：默认视频比例（`auto` / `16:9` / `4:3` / `full`）。默认 `auto`。
 */
object PlaybackPrefs {
    private const val KEY_AUTO_NEXT = "playback_auto_next"
    private const val KEY_AUTO_SWITCH_FAIL = "playback_auto_switch_fail"
    private const val KEY_DANMAKU_DEFAULT_ON = "playback_danmaku_default_on"
    private const val KEY_AUTO_FULLSCREEN = "playback_auto_fullscreen"
    private const val KEY_AUTO_FULLSCREEN_TABLET = "playback_auto_fullscreen_tablet"

    private const val KEY_BACKGROUND_PLAY = "playback_background_play"
    private const val KEY_AUTO_RESUME = "playback_auto_resume"
    private const val KEY_FORCE_HLS_AD_FILTER = "playback_force_hls_ad_filter"
    private const val KEY_DISABLE_ANIMATIONS = "playback_disable_animations"
    private const val KEY_INCOGNITO_MODE = "playback_incognito_mode"
    private const val KEY_DEFAULT_SPEED = "playback_default_speed"
    private const val KEY_LONG_PRESS_SPEED = "playback_long_press_speed"
    private const val KEY_ARROW_KEY_SKIP_SECONDS = "playback_arrow_key_skip_seconds"
    private const val KEY_SKIP_DURATION_SECONDS = "playback_skip_duration_seconds"
    private const val KEY_CONTROL_HIDE_SECONDS = "playback_control_hide_seconds"
    private const val KEY_DEFAULT_VIDEO_ASPECT_RATIO = "playback_default_video_aspect_ratio"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _autoNextEpisode = MutableStateFlow(prefs.getBoolean(KEY_AUTO_NEXT, true))
    val autoNextEpisode: StateFlow<Boolean> = _autoNextEpisode.asStateFlow()

    private val _autoSwitchOnFail = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SWITCH_FAIL, false))
    val autoSwitchOnFail: StateFlow<Boolean> = _autoSwitchOnFail.asStateFlow()

    private val _danmakuDefaultOn = MutableStateFlow(prefs.getBoolean(KEY_DANMAKU_DEFAULT_ON, false))
    val danmakuDefaultOn: StateFlow<Boolean> = _danmakuDefaultOn.asStateFlow()

    private val _autoFullscreen = MutableStateFlow(prefs.getBoolean(KEY_AUTO_FULLSCREEN, false))
    val autoFullscreen: StateFlow<Boolean> = _autoFullscreen.asStateFlow()

    private val _autoFullscreenTablet = MutableStateFlow(prefs.getBoolean(KEY_AUTO_FULLSCREEN_TABLET, true))
    val autoFullscreenTablet: StateFlow<Boolean> = _autoFullscreenTablet.asStateFlow()

    private val _backgroundPlayEnabled = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND_PLAY, false))
    val backgroundPlayEnabled: StateFlow<Boolean> = _backgroundPlayEnabled.asStateFlow()

    private val _autoResumeEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_RESUME, true))
    val autoResumeEnabled: StateFlow<Boolean> = _autoResumeEnabled.asStateFlow()

    private val _forceHlsAdFilter = MutableStateFlow(prefs.getBoolean(KEY_FORCE_HLS_AD_FILTER, false))
    val forceHlsAdFilter: StateFlow<Boolean> = _forceHlsAdFilter.asStateFlow()

    private val _disableAnimations = MutableStateFlow(prefs.getBoolean(KEY_DISABLE_ANIMATIONS, false))
    val disableAnimations: StateFlow<Boolean> = _disableAnimations.asStateFlow()

    private val _incognitoMode = MutableStateFlow(prefs.getBoolean(KEY_INCOGNITO_MODE, false))
    val incognitoMode: StateFlow<Boolean> = _incognitoMode.asStateFlow()

    private val _defaultSpeed = MutableStateFlow(prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f))
    val defaultSpeed: StateFlow<Float> = _defaultSpeed.asStateFlow()

    private val _longPressSpeed = MutableStateFlow(prefs.getFloat(KEY_LONG_PRESS_SPEED, 2.0f))
    val longPressSpeed: StateFlow<Float> = _longPressSpeed.asStateFlow()

    private val _arrowKeySkipSeconds = MutableStateFlow(prefs.getInt(KEY_ARROW_KEY_SKIP_SECONDS, 10))
    val arrowKeySkipSeconds: StateFlow<Int> = _arrowKeySkipSeconds.asStateFlow()

    private val _skipDurationSeconds = MutableStateFlow(prefs.getInt(KEY_SKIP_DURATION_SECONDS, 80))
    val skipDurationSeconds: StateFlow<Int> = _skipDurationSeconds.asStateFlow()

    private val _controlHideSeconds = MutableStateFlow(prefs.getInt(KEY_CONTROL_HIDE_SECONDS, 4))
    val controlHideSeconds: StateFlow<Int> = _controlHideSeconds.asStateFlow()

    private val _defaultVideoAspectRatio = MutableStateFlow(prefs.getString(KEY_DEFAULT_VIDEO_ASPECT_RATIO, "auto") ?: "auto")
    val defaultVideoAspectRatio: StateFlow<String> = _defaultVideoAspectRatio.asStateFlow()

    /** 同步读取（供非 Compose 场景使用）。 */
    fun isAutoNextEnabled(): Boolean = _autoNextEpisode.value

    fun setAutoNextEpisode(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_NEXT, value).apply()
        _autoNextEpisode.value = value
    }

    /** 同步读取（供 PlayerViewModel 判断失败时是否自动换线路 / 源）。 */
    fun isAutoSwitchOnFail(): Boolean = _autoSwitchOnFail.value

    fun setAutoSwitchOnFail(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SWITCH_FAIL, value).apply()
        _autoSwitchOnFail.value = value
    }

    /** 同步读取（供播放页初始化弹幕默认开关）。 */
    fun isDanmakuDefaultOn(): Boolean = _danmakuDefaultOn.value

    fun setDanmakuDefaultOn(value: Boolean) {
        prefs.edit().putBoolean(KEY_DANMAKU_DEFAULT_ON, value).apply()
        _danmakuDefaultOn.value = value
    }

    /** 同步读取（供播放页初始化是否自动全屏，手机端）。 */
    fun isAutoFullscreen(): Boolean = _autoFullscreen.value

    fun setAutoFullscreen(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FULLSCREEN, value).apply()
        _autoFullscreen.value = value
    }

    /** 同步读取（供播放页初始化是否自动全屏，平板端）。默认开启。 */
    fun isAutoFullscreenTablet(): Boolean = _autoFullscreenTablet.value

    fun setAutoFullscreenTablet(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FULLSCREEN_TABLET, value).apply()
        _autoFullscreenTablet.value = value
    }

    // —— 新增偏好 setters（与 DanmakuPrefs 风格一致：支持同步读取 + StateFlow） ——

    fun setBackgroundPlayEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_PLAY, value).apply()
        _backgroundPlayEnabled.value = value
    }

    fun setAutoResumeEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RESUME, value).apply()
        _autoResumeEnabled.value = value
    }

    fun setForceHlsAdFilter(value: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_HLS_AD_FILTER, value).apply()
        _forceHlsAdFilter.value = value
    }

    fun setDisableAnimations(value: Boolean) {
        prefs.edit().putBoolean(KEY_DISABLE_ANIMATIONS, value).apply()
        _disableAnimations.value = value
    }

    fun setIncognitoMode(value: Boolean) {
        prefs.edit().putBoolean(KEY_INCOGNITO_MODE, value).apply()
        _incognitoMode.value = value
    }

    /** 倍速取值：0.5 .. 3.0（步长 0.1 即可，与媒体播放器面板一致）。 */
    fun setDefaultSpeed(value: Float) {
        val v = value.coerceIn(0.25f, 3.0f)
        prefs.edit().putFloat(KEY_DEFAULT_SPEED, v).apply()
        _defaultSpeed.value = v
    }

    fun setLongPressSpeed(value: Float) {
        val v = value.coerceIn(1.0f, 5.0f)
        prefs.edit().putFloat(KEY_LONG_PRESS_SPEED, v).apply()
        _longPressSpeed.value = v
    }

    fun setArrowKeySkipSeconds(value: Int) {
        val v = value.coerceIn(1, 120)
        prefs.edit().putInt(KEY_ARROW_KEY_SKIP_SECONDS, v).apply()
        _arrowKeySkipSeconds.value = v
    }

    fun setSkipDurationSeconds(value: Int) {
        val v = value.coerceIn(10, 600)
        prefs.edit().putInt(KEY_SKIP_DURATION_SECONDS, v).apply()
        _skipDurationSeconds.value = v
    }

    fun setControlHideSeconds(value: Int) {
        val v = value.coerceIn(1, 30)
        prefs.edit().putInt(KEY_CONTROL_HIDE_SECONDS, v).apply()
        _controlHideSeconds.value = v
    }

    /** 视频比例：auto / 16:9 / 4:3 / full。非法值自动回落 auto。 */
    fun setDefaultVideoAspectRatio(value: String) {
        val v = if (value in ALLOWED_VIDEO_ASPECT_RATIOS) value else "auto"
        prefs.edit().putString(KEY_DEFAULT_VIDEO_ASPECT_RATIO, v).apply()
        _defaultVideoAspectRatio.value = v
    }

    /** 把 `auto / 16:9 / 4:3 / full` 映射成 video-player 的 ResizeMode 索引（若需要）。 */
    fun defaultVideoResizeModeIndex(): Int = when (_defaultVideoAspectRatio.value) {
        "16:9" -> 6   // FixedRatio_16_9
        "4:3"  -> 7   // FixedRatio_4_3
        "full" -> 4   // Full
        else   -> 0   // Fit / auto
    }

    /** 一键恢复默认值。 */
    fun resetAll() {
        val editor = prefs.edit()
        listOf(
            KEY_AUTO_NEXT, KEY_AUTO_SWITCH_FAIL, KEY_DANMAKU_DEFAULT_ON,
            KEY_AUTO_FULLSCREEN, KEY_AUTO_FULLSCREEN_TABLET,
            KEY_BACKGROUND_PLAY, KEY_AUTO_RESUME, KEY_FORCE_HLS_AD_FILTER,
            KEY_DISABLE_ANIMATIONS, KEY_INCOGNITO_MODE,
            KEY_DEFAULT_SPEED, KEY_LONG_PRESS_SPEED,
            KEY_ARROW_KEY_SKIP_SECONDS, KEY_SKIP_DURATION_SECONDS,
            KEY_CONTROL_HIDE_SECONDS, KEY_DEFAULT_VIDEO_ASPECT_RATIO,
        ).forEach { editor.remove(it) }
        editor.apply()

        _autoNextEpisode.value = true
        _autoSwitchOnFail.value = false
        _danmakuDefaultOn.value = false
        _autoFullscreen.value = false
        _autoFullscreenTablet.value = true
        _backgroundPlayEnabled.value = false
        _autoResumeEnabled.value = true
        _forceHlsAdFilter.value = false
        _disableAnimations.value = false
        _incognitoMode.value = false
        _defaultSpeed.value = 1.0f
        _longPressSpeed.value = 2.0f
        _arrowKeySkipSeconds.value = 10
        _skipDurationSeconds.value = 80
        _controlHideSeconds.value = 4
        _defaultVideoAspectRatio.value = "auto"
    }

    val ALLOWED_VIDEO_ASPECT_RATIOS = setOf("auto", "16:9", "4:3", "full")
}
