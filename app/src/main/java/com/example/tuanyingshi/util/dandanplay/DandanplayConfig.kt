package com.example.tuanyingshi.util.dandanplay

import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.util.preferences

/**
 * 弹弹play 开放弹幕网络（API v2）接入配置。
 *
 * AppId / AppSecret 为应用级密钥，已内置默认值且不在 UI 中展示或允许用户修改，
 * 避免泄露。baseUrl 仍可在「设置 → 弹幕源管理 → 弹弹play」中查看/编辑，便于切换
 * 代理或调试地址。
 */
object DandanplayConfig {
    private const val KEY_APP_ID = "dandanplay_app_id"
    private const val KEY_APP_SECRET = "dandanplay_app_secret"
    private const val KEY_BASE_URL = "dandanplay_base_url"
    private const val DEFAULT_BASE_URL = "https://api.dandanplay.net"

    /** 内置应用 ID，不在 UI 暴露。 */
    private const val DEFAULT_APP_ID = "7v7sygwqv7"

    /** 内置应用密钥，不在 UI 暴露。 */
    private const val DEFAULT_APP_SECRET = "nMes4bU3wUgxHxhm9AYzhLgCKve69SYc"

    private val prefs = TuanyingApp.getInstance().preferences

    /** 应用 ID。 */
    var appId: String
        get() = (prefs.getString(KEY_APP_ID, "") ?: "").ifBlank { DEFAULT_APP_ID }
        set(value) = prefs.edit().putString(KEY_APP_ID, value.trim()).apply()

    /** 应用密钥（签名模式仅用于本地计算签名，不直传；生产建议走后端代理）。 */
    var appSecret: String
        get() = (prefs.getString(KEY_APP_SECRET, "") ?: "").ifBlank { DEFAULT_APP_SECRET }
        set(value) = prefs.edit().putString(KEY_APP_SECRET, value.trim()).apply()

    /** API 根地址。 */
    var baseUrl: String
        get() = (prefs.getString(KEY_BASE_URL, "") ?: "").ifBlank { DEFAULT_BASE_URL }
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim()).apply()

    /** 是否已配置可用（AppId / AppSecret 均非空）。 */
    val isConfigured: Boolean
        get() = appId.isNotBlank() && appSecret.isNotBlank()
}
