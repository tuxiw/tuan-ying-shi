package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 调试模式偏好：
 * - [debugModeEnabled]：是否解锁「调试模式」入口（默认隐藏，需关于页连点版本号开启）；
 * - [webViewLogEnabled]：是否在 DebugScreen 中收集/展示 WebView 与错误日志。
 *
 * 两者都通过 SharedPreferences 持久化，并用 StateFlow 暴露给 Compose。
 */
object DebugPrefs {
    private const val KEY_DEBUG_MODE = "debug_mode_enabled"
    private const val KEY_WEBVIEW_LOG = "debug_webview_log_enabled"

    // 版本号连点解锁开发者选项：计数落盘，切页 / 旋转 / 重启都不丢进度
    private const val KEY_VERSION_TAPS = "debug_mode_version_taps"
    private const val KEY_VERSION_TAP_AT = "debug_mode_version_tap_at"
    const val VERSION_TAP_THRESHOLD = 7
    /** 超过该间隔才算新的一次连点（重新计数）。放宽到 8s，避免用户边看提示边点导致计数被重置。 */
    private const val TAP_WINDOW_MS = 8_000L

    private val prefs = TuanyingApp.getInstance().preferences

    private val _debugModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_MODE, false))
    val debugModeEnabled: StateFlow<Boolean> = _debugModeEnabled.asStateFlow()

    private val _webViewLogEnabled = MutableStateFlow(prefs.getBoolean(KEY_WEBVIEW_LOG, false))
    val webViewLogEnabled: StateFlow<Boolean> = _webViewLogEnabled.asStateFlow()

    init {
        // 自愈：旧版本（计数落盘但从未真正解锁的缺陷版本）可能残留 taps >= 阈值，
        // 启动时直接补解锁，避免「提示已开启但入口不出现」的僵死状态。
        if (!_debugModeEnabled.value && prefs.getInt(KEY_VERSION_TAPS, 0) >= VERSION_TAP_THRESHOLD) {
            setDebugModeEnabled(true)
        }
        // 同步启动状态的 WebView 日志开关到收集器
        DebugLogCollector.setEnabled(_webViewLogEnabled.value)
    }

    fun isDebugModeEnabled(): Boolean = _debugModeEnabled.value
    fun isWebViewLogEnabled(): Boolean = _webViewLogEnabled.value

    fun setDebugModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
        _debugModeEnabled.value = enabled
    }

    /**
     * 记录一次版本号点击。
     *
     * 返回 true 表示已达阈值、开发者选项已开启。计数写入 SharedPreferences，
     * 因此离开关于页、旋转屏幕或重启应用后进度都还在（旧实现只用 `remember`，
     * 一离开关于页计数就归零，用户会遇到「连点没反应 / 入口消失」）。
     */
    fun registerVersionTap(): Boolean {
        if (_debugModeEnabled.value) return false
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_VERSION_TAP_AT, 0L)
        val prev = if (now - last > TAP_WINDOW_MS) 0 else prefs.getInt(KEY_VERSION_TAPS, 0)
        val next = prev + 1
        prefs.edit()
            .putInt(KEY_VERSION_TAPS, next)
            .putLong(KEY_VERSION_TAP_AT, now)
            .apply()
        if (next < VERSION_TAP_THRESHOLD) return false
        // 解锁：清空计数，避免重复触发
        prefs.edit()
            .remove(KEY_VERSION_TAPS)
            .remove(KEY_VERSION_TAP_AT)
            .apply()
        setDebugModeEnabled(true)
        return true
    }

    /** 距离解锁还需点击几次（已解锁返回 0）。用于在关于页提示「再点 N 次」。 */
    fun versionTapsRemaining(): Int =
        if (_debugModeEnabled.value) 0 else VERSION_TAP_THRESHOLD - prefs.getInt(KEY_VERSION_TAPS, 0)

    fun setWebViewLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEBVIEW_LOG, enabled).apply()
        _webViewLogEnabled.value = enabled
        DebugLogCollector.setEnabled(enabled)
    }
}
