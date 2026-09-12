package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 内容过滤偏好（NSFW 屏蔽 / 隐藏看过与抛弃）。
 *
 * 字段：
 * - [nsfwBlock]：是否屏蔽 NSFW 内容（番剧标签含 成人/肉番/NSFW/R18）。默认 true（屏蔽）。
 * - [hideWatched]：是否在列表中隐藏「看过」与「抛弃」的条目。默认 false（显示）。
 */
object ContentPrefs {
    private const val KEY_NSFW_BLOCK = "nsfw_block"
    private const val KEY_HIDE_WATCHED = "hide_watched"
    private const val KEY_HIDE_HOME_BANNER = "hide_home_banner"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _nsfwBlock = MutableStateFlow(prefs.getBoolean(KEY_NSFW_BLOCK, true))
    val nsfwBlock: StateFlow<Boolean> = _nsfwBlock.asStateFlow()

    private val _hideWatched = MutableStateFlow(prefs.getBoolean(KEY_HIDE_WATCHED, false))
    val hideWatched: StateFlow<Boolean> = _hideWatched.asStateFlow()

    /**
     * 移除首页轮播图：开启后隐藏「推荐」页顶部的轮播 Banner。
     * 默认 false（显示轮播图）。
     */
    private val _hideHomeBanner = MutableStateFlow(prefs.getBoolean(KEY_HIDE_HOME_BANNER, false))
    val hideHomeBanner: StateFlow<Boolean> = _hideHomeBanner.asStateFlow()

    /** 同步读取（供非 Compose 场景使用）。 */
    fun isNsfwBlocked(): Boolean = _nsfwBlock.value
    fun isHideWatched(): Boolean = _hideWatched.value
    fun isHideHomeBanner(): Boolean = _hideHomeBanner.value

    fun setNsfwBlock(value: Boolean) {
        prefs.edit().putBoolean(KEY_NSFW_BLOCK, value).apply()
        _nsfwBlock.value = value
    }

    fun setHideWatched(value: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_WATCHED, value).apply()
        _hideWatched.value = value
    }

    fun setHideHomeBanner(value: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_HOME_BANNER, value).apply()
        _hideHomeBanner.value = value
    }

    // ── 通用行为偏好 ───────────────────────────────────────

    private const val KEY_EXIT_CONFIRM = "exit_confirm"
    private const val KEY_FILTER_RESET_ON_EXIT = "filter_reset_on_exit"

    /** 二级退出确认：连按两次返回键才退出 App。默认 true（开启）。 */
    private val _exitConfirm = MutableStateFlow(prefs.getBoolean(KEY_EXIT_CONFIRM, true))
    val exitConfirm: StateFlow<Boolean> = _exitConfirm.asStateFlow()

    /**
     * 分类浏览筛选重置策略：true = 仅「退出 App / 切换数据源」时重置（开启后筛选跨导航持久）；
     * false = 每次进入分类浏览页都重置为默认。
     * 默认 true（开启）。
     */
    private val _filterResetOnExit = MutableStateFlow(prefs.getBoolean(KEY_FILTER_RESET_ON_EXIT, true))
    val filterResetOnExit: StateFlow<Boolean> = _filterResetOnExit.asStateFlow()

    fun isExitConfirm(): Boolean = _exitConfirm.value
    fun isFilterResetOnExit(): Boolean = _filterResetOnExit.value

    fun setExitConfirm(value: Boolean) {
        prefs.edit().putBoolean(KEY_EXIT_CONFIRM, value).apply()
        _exitConfirm.value = value
    }

    fun setFilterResetOnExit(value: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_RESET_ON_EXIT, value).apply()
        _filterResetOnExit.value = value
    }
}
