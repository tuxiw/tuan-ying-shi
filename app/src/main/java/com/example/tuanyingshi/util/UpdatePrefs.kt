package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 更新检查相关偏好。
 *
 * 字段：
 * - [autoCheckUpdate]：是否在每次启动应用时自动检查新版本。默认 true（开启）。
 * - [inAppUpdate]：是否启用「应用内更新」。开启时点击立即下载会在应用内直接下载并安装
 *   对应架构的安装包；关闭时则跳转到发布页 / GitHub 网页下载。默认 true（开启）。
 */
object UpdatePrefs {
    private const val KEY_AUTO_CHECK = "update_auto_check"
    private const val KEY_IN_APP_UPDATE = "update_in_app_update"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _autoCheckUpdate = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK, true))
    val autoCheckUpdate: StateFlow<Boolean> = _autoCheckUpdate.asStateFlow()

    private val _inAppUpdate = MutableStateFlow(prefs.getBoolean(KEY_IN_APP_UPDATE, true))
    val inAppUpdate: StateFlow<Boolean> = _inAppUpdate.asStateFlow()

    /** 同步读取（供非 Compose 场景使用）。 */
    fun isAutoCheckEnabled(): Boolean = _autoCheckUpdate.value

    fun setAutoCheckUpdate(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply()
        _autoCheckUpdate.value = value
    }

    fun setInAppUpdate(value: Boolean) {
        prefs.edit().putBoolean(KEY_IN_APP_UPDATE, value).apply()
        _inAppUpdate.value = value
    }
}
