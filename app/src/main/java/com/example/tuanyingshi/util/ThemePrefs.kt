package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题偏好：外观模式 + 强调色。
 *
 * - 外观模式 [ThemeMode]：跟随系统 / 暗色 / 亮色，默认跟随系统。
 * - 强调色 [accentHex]：`#RRGGBB` 字符串；为 null 时使用内置默认品牌色（保留原暗/亮方案）。
 */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accentHex: String? = null,
)

object ThemePrefs {
    private const val KEY_MODE = "theme_mode"
    private const val KEY_ACCENT = "theme_accent"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _config = MutableStateFlow(load())
    val config: StateFlow<ThemeConfig> = _config.asStateFlow()

    /** 同步读取（非 Compose 场景）。 */
    fun getConfig(): ThemeConfig = _config.value

    private fun load(): ThemeConfig {
        val mode = when (prefs.getString(KEY_MODE, "system")) {
            "dark" -> ThemeMode.DARK
            "light" -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
        return ThemeConfig(mode, prefs.getString(KEY_ACCENT, null))
    }

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(
            KEY_MODE,
            when (mode) {
                ThemeMode.SYSTEM -> "system"
                ThemeMode.DARK -> "dark"
                ThemeMode.LIGHT -> "light"
            },
        ).apply()
        _config.value = _config.value.copy(mode = mode)
    }

    /** 设置强调色；传 null 恢复默认品牌色。 */
    fun setAccent(hex: String?) {
        if (hex == null) {
            prefs.edit().remove(KEY_ACCENT).apply()
        } else {
            prefs.edit().putString(KEY_ACCENT, hex).apply()
        }
        _config.value = _config.value.copy(accentHex = hex)
    }
}
