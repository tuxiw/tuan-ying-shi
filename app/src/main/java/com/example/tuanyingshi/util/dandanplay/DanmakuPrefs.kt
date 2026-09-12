package com.example.tuanyingshi.util.dandanplay

import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.util.preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 弹幕偏好：
 * - [enabled]：是否启用弹幕拉取（默认开启）。关闭后播放弹弹play 源不再加载弹幕，
 *   仅影响弹幕，不影响视频与选集解析。
 *
 * 通过 SharedPreferences 持久化，并用 StateFlow 暴露给 Compose 与 ViewModel。
 */
object DanmakuPrefs {
    private const val KEY_ENABLED = "danmaku_enabled"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }
}
