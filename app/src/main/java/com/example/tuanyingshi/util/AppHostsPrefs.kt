package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局 Hosts 偏好（内置 Hosts 开关 + 自定义 Hosts 文本）。
 *
 * 与 [ProxyHolder] 同址持久化于 `tuanyingshi` 的 `tuanying_prefs`，并用 StateFlow 暴露给 Compose，
 * 设置页改动后实时刷新；DNS 类 [AppHosts] 在每次建连时实时读取本对象，无需重启。
 *
 * 默认：**内置 Hosts 关闭**（用户需在“网络设置”主动开启）。
 */
object AppHostsPrefs {

    private const val KEY_USE_BUILT_IN_HOSTS = "use_builtin_hosts"
    private const val KEY_CUSTOM_HOSTS = "custom_hosts"

    private val prefs = TuanyingApp.getInstance().preferences

    /** 是否启用内置 Hosts（默认 false）。 */
    val useBuiltInHosts: Boolean
        get() = prefs.getBoolean(KEY_USE_BUILT_IN_HOSTS, false)

    /** 自定义 Hosts 文本（通用 hosts 文件格式，默认空）。 */
    var customHostsData: String
        get() = prefs.getString(KEY_CUSTOM_HOSTS, "") ?: ""
        set(value) {
            val trimmed = value.trim('\n', '\r', ' ')
            prefs.edit().putString(KEY_CUSTOM_HOSTS, trimmed).apply()
            _customHostsFlow.value = trimmed
        }

    // ── 暴露给 Compose 的可观察流 ──

    private val _useBuiltInFlow = MutableStateFlow(useBuiltInHosts)
    val useBuiltInFlow = _useBuiltInFlow.asStateFlow()

    private val _customHostsFlow = MutableStateFlow(customHostsData)
    val customHostsFlow = _customHostsFlow.asStateFlow()

    fun setUseBuiltInHosts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_BUILT_IN_HOSTS, enabled).apply()
        _useBuiltInFlow.value = enabled
    }
}
