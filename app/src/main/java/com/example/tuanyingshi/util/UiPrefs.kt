package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 手动设定时列表列数可选范围。 */
const val COLUMNS_MIN = 2
const val COLUMNS_MAX = 6
/** 自动模式下允许的最大列数（平板 / 横屏 / 折叠屏展开态）。 */
const val COLUMNS_AUTO_MAX = 8
const val COLUMNS_DEFAULT = 3

/**
 * 把任意值收敛到有效列数区间。
 * @param max 上限，手动设定用 [COLUMNS_MAX]，按屏幕自适应计算用 [COLUMNS_AUTO_MAX]。
 */
fun sanitizeColumns(value: Int, max: Int = COLUMNS_MAX): Int = value.coerceIn(COLUMNS_MIN, max)

/**
 * 界面偏好（UI 个性化）。
 *
 * 字段：
 * - [navLabelsVisible]：导航栏是否显示文字标签。默认 true（显示）。
 *   关闭后手机底部导航 / 平板侧边导航仅显示图标。
 * - [homeColumns]：首页列表（推荐 / 热播 / 分类浏览）每行列数，手动档 2-6，默认 3。
 * - [detailColumns]：详情页列表（选集网格）每行列数，手动档 2-6，默认 3。
 * - [homeColumnsAuto] / [detailColumnsAuto]：是否跟随屏幕自适应。
 *   **默认开启**：从未手动改过列数的用户走自动档（列数按屏幕宽度与横竖屏在 UI 侧实时算）；
 *   一旦在「布局设置」里点过具体数字，就切为该手动值，直到用户重新点回「自动」。
 */
object UiPrefs {
    private const val KEY_NAV_LABELS_VISIBLE = "nav_labels_visible"
    private const val KEY_HOME_COLUMNS = "home_columns"
    private const val KEY_DETAIL_COLUMNS = "detail_columns"
    private const val KEY_HOME_COLUMNS_AUTO = "home_columns_auto"
    private const val KEY_DETAIL_COLUMNS_AUTO = "detail_columns_auto"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _navLabelsVisible = MutableStateFlow(prefs.getBoolean(KEY_NAV_LABELS_VISIBLE, true))
    val navLabelsVisible: StateFlow<Boolean> = _navLabelsVisible.asStateFlow()

    // ───────── 布局：列表列数 ─────────

    private val _homeColumns =
        MutableStateFlow(sanitizeColumns(prefs.getInt(KEY_HOME_COLUMNS, COLUMNS_DEFAULT)))
    val homeColumns: StateFlow<Int> = _homeColumns.asStateFlow()

    private val _detailColumns =
        MutableStateFlow(sanitizeColumns(prefs.getInt(KEY_DETAIL_COLUMNS, COLUMNS_DEFAULT)))
    val detailColumns: StateFlow<Int> = _detailColumns.asStateFlow()

    // ───────── 布局：是否跟随屏幕自适应 ─────────

    private val _homeColumnsAuto =
        MutableStateFlow(resolveAutoMode(KEY_HOME_COLUMNS_AUTO, KEY_HOME_COLUMNS))
    val homeColumnsAuto: StateFlow<Boolean> = _homeColumnsAuto.asStateFlow()

    private val _detailColumnsAuto =
        MutableStateFlow(resolveAutoMode(KEY_DETAIL_COLUMNS_AUTO, KEY_DETAIL_COLUMNS))
    val detailColumnsAuto: StateFlow<Boolean> = _detailColumnsAuto.asStateFlow()

    /**
     * 自动模式初值判定：显式写过 auto 键就以其为准；否则「从未手动设过列数」= 自动。
     * 这样老用户已保存的固定列数不会被静默改成自适应。
     */
    private fun resolveAutoMode(autoKey: String, manualKey: String): Boolean =
        if (prefs.contains(autoKey)) prefs.getBoolean(autoKey, true) else !prefs.contains(manualKey)

    /** 同步读取（供非 Compose 场景使用）。 */
    fun isNavLabelsVisible(): Boolean = _navLabelsVisible.value

    fun setNavLabelsVisible(value: Boolean) {
        prefs.edit().putBoolean(KEY_NAV_LABELS_VISIBLE, value).apply()
        _navLabelsVisible.value = value
    }

    /** 首页列表列数（同步读取）。 */
    fun homeColumnsSync(): Int = _homeColumns.value

    /**
     * 设置首页列表列数（手动档）。调用后自动关闭该页的自适应。
     */
    fun setHomeColumns(value: Int) {
        val v = sanitizeColumns(value)
        prefs.edit()
            .putInt(KEY_HOME_COLUMNS, v)
            .putBoolean(KEY_HOME_COLUMNS_AUTO, false)
            .apply()
        if (_homeColumns.value != v) _homeColumns.value = v
        if (_homeColumnsAuto.value) _homeColumnsAuto.value = false
    }

    /** 首页列表列数改为跟随屏幕自适应。 */
    fun setHomeColumnsAuto() {
        if (!prefs.getBoolean(KEY_HOME_COLUMNS_AUTO, true) || !_homeColumnsAuto.value) {
            prefs.edit().putBoolean(KEY_HOME_COLUMNS_AUTO, true).apply()
        }
        if (!_homeColumnsAuto.value) _homeColumnsAuto.value = true
    }

    /**
     * 设置详情页列表列数（手动档）。调用后自动关闭该页的自适应。
     */
    fun setDetailColumns(value: Int) {
        val v = sanitizeColumns(value)
        prefs.edit()
            .putInt(KEY_DETAIL_COLUMNS, v)
            .putBoolean(KEY_DETAIL_COLUMNS_AUTO, false)
            .apply()
        if (_detailColumns.value != v) _detailColumns.value = v
        if (_detailColumnsAuto.value) _detailColumnsAuto.value = false
    }

    /** 详情页列表列数改为跟随屏幕自适应。 */
    fun setDetailColumnsAuto() {
        prefs.edit().putBoolean(KEY_DETAIL_COLUMNS_AUTO, true).apply()
        if (!_detailColumnsAuto.value) _detailColumnsAuto.value = true
    }
}
