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

    private val prefs = TuanyingApp.getInstance().preferences

    private val _debugModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_MODE, false))
    val debugModeEnabled: StateFlow<Boolean> = _debugModeEnabled.asStateFlow()

    private val _webViewLogEnabled = MutableStateFlow(prefs.getBoolean(KEY_WEBVIEW_LOG, false))
    val webViewLogEnabled: StateFlow<Boolean> = _webViewLogEnabled.asStateFlow()

    init {
        // 同步启动状态的 WebView 日志开关到收集器
        DebugLogCollector.setEnabled(_webViewLogEnabled.value)
    }

    fun isDebugModeEnabled(): Boolean = _debugModeEnabled.value
    fun isWebViewLogEnabled(): Boolean = _webViewLogEnabled.value

    fun setDebugModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
        _debugModeEnabled.value = enabled
    }

    fun setWebViewLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEBVIEW_LOG, enabled).apply()
        _webViewLogEnabled.value = enabled
        DebugLogCollector.setEnabled(enabled)
    }
}
