package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 专属 APP 后端模式偏好（实验功能，默认未配置）。
 *
 * 与 [BackendPrefs]「自建后端模式」相互独立、互不干扰：
 * - [BackendPrefs] 是用户自托管的后端，开启后同时接管「内容数据 + 登录态用户功能」（历史 / 收藏 / 评论 / 反馈 / 账号）；
 * - 本模式只接管「内容数据」（首页 / 排期 / 排行 / 搜索 / 分类 / 详情 / 剧集），
 *   用户功能（历史 / 收藏 / 评论 / 账号等）仍走 [BackendPrefs] 的后端，两者配置互不影响。
 *
 * 默认 [enabled] = false 且 [baseUrl] 为空（未配置）；配置后由 [SourceHolder] 把内容源切到
 * [com.example.tuanyingshi.data.remote.parse.DedicatedBackendAnimeSource]。
 */
object DedicatedBackendPrefs {
    private const val KEY_ENABLED = "dedicated_backend_enabled"
    private const val KEY_BASE_URL = "dedicated_backend_base_url"
    private const val KEY_TOKEN = "dedicated_backend_token"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    var baseUrl: String = prefs.getString(KEY_BASE_URL, "") ?: ""
        private set

    var token: String = prefs.getString(KEY_TOKEN, "") ?: ""
        private set

    /** 同步读取是否启用专属 APP 后端模式（非 Compose 场景使用）。 */
    fun isEnabled(): Boolean = _enabled.value

    /** 归一化后的专属后端 API 基址（含 `/api/v1/`）。 */
    val apiBaseUrl: String
        get() {
            val raw = baseUrl.trim().removeSuffix("/")
            if (raw.isBlank()) return ""
            val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                raw
            } else {
                "https://$raw"
            }
            val base = if (withScheme.endsWith("/api/v1")) withScheme else "$withScheme/api/v1"
            return "$base/"
        }

    /** 专属后端站点根地址（不含 `/api/v1`），用于拼接 `/uploads` 等静态资源。 */
    val originUrl: String
        get() = apiBaseUrl.removeSuffix("/").removeSuffix("/api/v1")

    /**
     * 把后端返回的相对地址（如 `/uploads/avatar/x.jpg`）转成可直接加载的绝对地址。
     * 已是 http(s) 开头的原样返回；后端地址未配置或路径为空时返回 null。
     */
    fun absoluteUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val origin = originUrl
        if (origin.isBlank()) return null
        return if (path.startsWith("/")) origin + path else "$origin/$path"
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    fun setBaseUrl(value: String) {
        baseUrl = value.trim()
        prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
    }

    fun setToken(value: String) {
        token = value.trim()
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }
}
