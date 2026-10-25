package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置页导航偏好：控制「从二级页面返回后，设置页停在哪」。
 *
 * - [resetToDefaultOnReturn]：true = 每次从二级页面（数据源管理 / 规则编辑器 / 登录页 等）
 *   返回设置页时重置到默认「通用」分区；false（默认）= 保持返回前选中的设置分区。
 * - [lastSection]：平板双栏布局左菜单上次选中的分区 id（关闭重置时用于恢复位置）。
 *   仅在分区当前可见时才会被恢复（后端模式隐藏数据源/弹幕源、未解锁调试、未登录账号管理
 *   等情况回退「通用」），恢复校验由 UI 层（SettingsScreen）按当前可见性做。
 */
object SettingsNavPrefs {
    private const val KEY_RESET_TO_DEFAULT_ON_RETURN = "settings_reset_to_default_on_return"
    private const val KEY_LAST_SECTION = "settings_last_section"

    private const val DEFAULT_SECTION = "general"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _resetToDefaultOnReturn =
        MutableStateFlow(prefs.getBoolean(KEY_RESET_TO_DEFAULT_ON_RETURN, false))
    val resetToDefaultOnReturn: StateFlow<Boolean> = _resetToDefaultOnReturn.asStateFlow()

    private val _lastSection = MutableStateFlow(prefs.getString(KEY_LAST_SECTION, DEFAULT_SECTION) ?: DEFAULT_SECTION)
    val lastSection: StateFlow<String> = _lastSection.asStateFlow()

    /** 同步读取（供非 Compose 场景 / 初始化时使用）。 */
    fun isResetToDefaultOnReturn(): Boolean = _resetToDefaultOnReturn.value

    /** 同步读取上次选中的分区 id（从未选择过时为 "general"）。 */
    fun currentSection(): String = _lastSection.value

    fun setResetToDefaultOnReturn(value: Boolean) {
        prefs.edit().putBoolean(KEY_RESET_TO_DEFAULT_ON_RETURN, value).apply()
        _resetToDefaultOnReturn.value = value
    }

    /** 记录左菜单选中的分区（仅在值变化时写盘，避免每次点击都触发 IO）。 */
    fun setLastSection(id: String) {
        if (id == _lastSection.value) return
        prefs.edit().putString(KEY_LAST_SECTION, id).apply()
        _lastSection.value = id
    }
}
