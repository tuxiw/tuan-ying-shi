package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知与提醒偏好，全部在「设置 → 应用 → 消息通知」中可开关：
 *
 * - [downloadNotificationEnabled]：下载番剧时是否在状态栏展示下载进度 / 完成提醒。默认开启。
 * - [offlineToastEnabled]：**离线模式提示**。无网络时（启动即离线、或使用途中断网 / 恢复联网）
 *   是否弹 Toast 告知用户已进入 / 退出离线模式。默认开启。
 * - [offlineBannerEnabled]：**离线模式顶部提醒**。无网络时是否在主界面顶部常驻一条红色横幅。
 *   默认开启。
 *
 * 说明：关闭以上两项只影响「主动提醒」，详情页 / 播放页在离线加载失败时的说明文案属于
 * 功能性错误提示，不受开关影响，避免用户完全不知道为何加载失败。
 *
 * 通过 SharedPreferences（tuanying_prefs）持久化，并用 StateFlow 暴露给 Compose 与下载管理器。
 * 键名统一使用 `notif_` 前缀，便于 [ConfigBackup] 归入「通知与提醒」备份分类。
 */
object NotifPrefs {
    private const val KEY_DOWNLOAD_NOTIF = "download_notification_enabled"
    private const val KEY_OFFLINE_TOAST = "notif_offline_toast_enabled"
    private const val KEY_OFFLINE_BANNER = "notif_offline_banner_enabled"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _downloadNotificationEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_DOWNLOAD_NOTIF, true))
    val downloadNotificationEnabled: StateFlow<Boolean> = _downloadNotificationEnabled.asStateFlow()

    private val _offlineToastEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_TOAST, true))
    val offlineToastEnabled: StateFlow<Boolean> = _offlineToastEnabled.asStateFlow()

    private val _offlineBannerEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_BANNER, true))
    val offlineBannerEnabled: StateFlow<Boolean> = _offlineBannerEnabled.asStateFlow()

    fun isDownloadNotificationEnabled(): Boolean = _downloadNotificationEnabled.value

    fun setDownloadNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOWNLOAD_NOTIF, enabled).apply()
        _downloadNotificationEnabled.value = enabled
    }

    /** 离线模式提示（Toast）是否开启。 */
    fun isOfflineToastEnabled(): Boolean = _offlineToastEnabled.value

    fun setOfflineToastEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_TOAST, enabled).apply()
        _offlineToastEnabled.value = enabled
    }

    /** 离线模式顶部提醒（横幅）是否开启。 */
    fun isOfflineBannerEnabled(): Boolean = _offlineBannerEnabled.value

    fun setOfflineBannerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_BANNER, enabled).apply()
        _offlineBannerEnabled.value = enabled
    }
}
