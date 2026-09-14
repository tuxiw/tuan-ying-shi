package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 新手引导的展示记录。
 * 仅首次进入 App 时展示一次多页引导；用户看完并点击「立即体验」后写入标记，之后不再自动弹出。
 * 设置页「再看一次新手引导」会调用 [reset] 清除标记以重新展示。
 *
 * [shown] 暴露为 StateFlow，便于 AppNavigation 在引导结束（[markShown]）后联动弹出首启权限说明。
 */
object OnboardingPrefs {
    private const val KEY_ONBOARDING_SHOWN = "onboarding_shown"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _shown = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_SHOWN, false))
    val shown: StateFlow<Boolean> = _shown.asStateFlow()

    /** 是否已经展示过新手引导。 */
    fun hasShown(): Boolean = _shown.value

    /** 用户看完引导并确认后调用，标记已展示。 */
    fun markShown() {
        prefs.edit().putBoolean(KEY_ONBOARDING_SHOWN, true).apply()
        _shown.value = true
    }

    /** 清除标记（供「再看一次新手引导」使用），下次进入会重新展示首次引导。 */
    fun reset() {
        prefs.edit().remove(KEY_ONBOARDING_SHOWN).apply()
        _shown.value = false
    }
}
