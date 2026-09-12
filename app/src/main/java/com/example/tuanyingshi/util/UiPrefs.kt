package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 界面偏好（UI 个性化）。
 *
 * 字段：
 * - [navLabelsVisible]：导航栏是否显示文字标签。默认 true（显示）。
 *   关闭后手机底部导航 / 平板侧边导航仅显示图标。
 */
object UiPrefs {
    private const val KEY_NAV_LABELS_VISIBLE = "nav_labels_visible"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _navLabelsVisible = MutableStateFlow(prefs.getBoolean(KEY_NAV_LABELS_VISIBLE, true))
    val navLabelsVisible: StateFlow<Boolean> = _navLabelsVisible.asStateFlow()

    /** 同步读取（供非 Compose 场景使用）。 */
    fun isNavLabelsVisible(): Boolean = _navLabelsVisible.value

    fun setNavLabelsVisible(value: Boolean) {
        prefs.edit().putBoolean(KEY_NAV_LABELS_VISIBLE, value).apply()
        _navLabelsVisible.value = value
    }
}
