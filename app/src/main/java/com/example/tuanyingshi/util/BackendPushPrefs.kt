package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 后端模式专属推送偏好（仅在「后端模式」开启时有意义）。
 *
 * - [updatePushEnabled]：是否接受后端模式的「软件更新推送」。开启时，应用启动会自动检查
 *   后端下发的版本更新（[com.example.tuanyingshi.util.UpdateChecker]）；关闭则不再自动检查。
 *   默认开启。手动点击「检查更新」不受此开关限制（用户主动发起）。
 * - [announcementPushEnabled]：是否接受后端模式的「公告推送」。开启时，应用启动会弹出
 *   后端发布的公告弹窗（[com.example.tuanyingshi.ui.navigation.AppNavigation]）；关闭则不再弹出。
 *   默认开启。
 */
object BackendPushPrefs {
    private const val KEY_UPDATE_PUSH = "backend_push_update_enabled"
    private const val KEY_ANNOUNCEMENT_PUSH = "backend_push_announcement_enabled"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _updatePushEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_UPDATE_PUSH, true))
    val updatePushEnabled: StateFlow<Boolean> = _updatePushEnabled.asStateFlow()

    private val _announcementPushEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_ANNOUNCEMENT_PUSH, true))
    val announcementPushEnabled: StateFlow<Boolean> = _announcementPushEnabled.asStateFlow()

    /** 同步读取：是否接受后端软件更新推送。 */
    fun isUpdatePushEnabled(): Boolean = _updatePushEnabled.value

    /** 同步读取：是否接受后端公告推送。 */
    fun isAnnouncementPushEnabled(): Boolean = _announcementPushEnabled.value

    fun setUpdatePushEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_PUSH, enabled).apply()
        _updatePushEnabled.value = enabled
    }

    fun setAnnouncementPushEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANNOUNCEMENT_PUSH, enabled).apply()
        _announcementPushEnabled.value = enabled
    }
}
