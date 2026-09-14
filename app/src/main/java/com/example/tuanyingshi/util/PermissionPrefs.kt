package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp

/**
 * 首次启动的「权限说明」展示标记。
 *
 * 仅在用户首次进入（已看过新手引导，且本标记尚未置位）时，由 [com.example.tuanyingshi.ui.navigation.AppNavigation]
 * 弹出一次「权限说明」弹窗，介绍存储 / 通知权限的用途并一次性申请，避免「用到才申请」导致
 * 下载 / 本地播放 / 下载进度通知等功能异常。相机权限（扫码登录）保持按需申请，不在此处请求。
 *
 * 标记置位后不再自动弹出；用户任何时候都可在对应功能页按需再次授权。
 */
object PermissionPrefs {
    private const val KEY_INTRO_SHOWN = "permission_intro_shown"

    private val prefs = TuanyingApp.getInstance().preferences

    /** 是否已经展示过首启权限说明。 */
    fun introShown(): Boolean = prefs.getBoolean(KEY_INTRO_SHOWN, false)

    /** 标记首启权限说明已展示（无论用户选择「授权」还是「暂不」）。 */
    fun markIntroShown() {
        prefs.edit().putBoolean(KEY_INTRO_SHOWN, true).apply()
    }
}
