package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「根据当前网络智能切换数据源」偏好。
 *
 * - [enabled]：总开关，**默认开启**（[KEY_ENABLED] 默认 true）。开启后 App 会按当前网络类型
 *   自动把资源源切到用户为该网络配置（或自动挑选）的源。
 * - 每种网络类型可绑定一个数据源 id（[wifiSourceId] / [cellularSourceId] / [ethernetSourceId] /
 *   [offlineSourceId]），为空表示「自动选择」，由 [SmartSourceSwitcher] 按网络类型挑一个可用源。
 *
 * 后端模式下本功能完全失效（数据源由后端接管，见 [SourceHolder] / [BackendPrefs]）。
 * 通过 SharedPreferences（tuanying_prefs）持久化，并用 StateFlow 暴露给 Compose 与切源逻辑。
 */
object SmartSourcePrefs {
    private const val KEY_ENABLED = "smart_source_enabled"
    private const val KEY_WIFI = "smart_source_wifi"
    private const val KEY_CELLULAR = "smart_source_cellular"
    private const val KEY_ETHERNET = "smart_source_ethernet"
    private const val KEY_OFFLINE = "smart_source_offline"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _wifiSourceId = MutableStateFlow(prefs.getString(KEY_WIFI, null))
    val wifiSourceId: StateFlow<String?> = _wifiSourceId.asStateFlow()

    private val _cellularSourceId = MutableStateFlow(prefs.getString(KEY_CELLULAR, null))
    val cellularSourceId: StateFlow<String?> = _cellularSourceId.asStateFlow()

    private val _ethernetSourceId = MutableStateFlow(prefs.getString(KEY_ETHERNET, null))
    val ethernetSourceId: StateFlow<String?> = _ethernetSourceId.asStateFlow()

    private val _offlineSourceId = MutableStateFlow(prefs.getString(KEY_OFFLINE, null))
    val offlineSourceId: StateFlow<String?> = _offlineSourceId.asStateFlow()

    /** 同步读取总开关状态（供非 Compose 调用）。 */
    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    /** 设置某网络类型对应的数据源 id（null = 自动选择）。 */
    fun setNetworkSourceId(type: NetworkType, id: String?) {
        when (type) {
            NetworkType.WIFI -> setWifi(id)
            NetworkType.CELLULAR -> setCellular(id)
            NetworkType.ETHERNET -> setEthernet(id)
            NetworkType.OFFLINE -> setOffline(id)
        }
    }

    private fun setWifi(id: String?) {
        prefs.edit().putString(KEY_WIFI, id).apply()
        _wifiSourceId.value = id
    }

    private fun setCellular(id: String?) {
        prefs.edit().putString(KEY_CELLULAR, id).apply()
        _cellularSourceId.value = id
    }

    private fun setEthernet(id: String?) {
        prefs.edit().putString(KEY_ETHERNET, id).apply()
        _ethernetSourceId.value = id
    }

    private fun setOffline(id: String?) {
        prefs.edit().putString(KEY_OFFLINE, id).apply()
        _offlineSourceId.value = id
    }
}
