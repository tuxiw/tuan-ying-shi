package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 后端模式偏好。
 *
 * 开启 [enabled] 后，App 的全部内容数据（首页 / 排期 / 排行 / 搜索 / 分类 / 详情 / 剧集 / 弹幕）
 * 统一走自建后端 API，同时禁用本地数据源与弹幕源切换（见 [SourceHolder]、[SettingsScreen]）。
 *
 * - [enabled]：是否启用后端模式（[isBackendMode] 为同步读取版，供非 Compose 场景使用）
 * - [baseUrl]：后端地址（用户输入，如 `http://192.168.1.10:8080`），自动补 `/api/v1`
 * - [token]：可选 JWT，留空则匿名访问（后端公开内容接口 `permitAll`）
 */
object BackendPrefs {
    private const val KEY_ENABLED = "backend_enabled"
    private const val KEY_BASE_URL = "backend_base_url"
    private const val KEY_TOKEN = "backend_token"
    private const val KEY_USER_ID = "backend_user_id"
    private const val KEY_USERNAME = "backend_username"
    private const val KEY_EMAIL = "backend_email"
    private const val KEY_NICKNAME = "backend_nickname"
    private const val KEY_AVATAR = "backend_avatar"
    private const val KEY_SIGNATURE = "backend_signature"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    var baseUrl: String = prefs.getString(KEY_BASE_URL, "") ?: ""
        private set

    var token: String = prefs.getString(KEY_TOKEN, "") ?: ""
        private set

    var userId: Long = prefs.getLong(KEY_USER_ID, 0L)
        private set

    var username: String = prefs.getString(KEY_USERNAME, "") ?: ""
        private set

    var email: String = prefs.getString(KEY_EMAIL, "") ?: ""
        private set

    var nickname: String = prefs.getString(KEY_NICKNAME, "") ?: ""
        private set

    var avatar: String = prefs.getString(KEY_AVATAR, "") ?: ""
        private set

    var signature: String = prefs.getString(KEY_SIGNATURE, "") ?: ""
        private set

    /** 是否已登录（持有非空令牌即视为已登录）。 */
    val isLoggedIn: Boolean get() = token.isNotBlank()

    /** 同步读取是否处于后端模式（供 [SourceHolder] / [AnimeApiImpl] / [PlayerViewModel] 等非 Compose 调用）。 */
    fun isBackendMode(): Boolean = _enabled.value

    /** 写入一次登录会话（令牌 + 用户信息）。 */
    fun setSession(
        accessToken: String,
        userId: Long,
        username: String,
        email: String,
        nickname: String?,
        avatar: String?,
        signature: String? = null,
    ) {
        token = accessToken
        this.userId = userId
        this.username = username
        this.email = email
        this.nickname = nickname ?: ""
        this.avatar = avatar ?: ""
        this.signature = signature ?: ""
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_EMAIL, email)
            .putString(KEY_NICKNAME, this.nickname)
            .putString(KEY_AVATAR, this.avatar)
            .putString(KEY_SIGNATURE, this.signature)
            .apply()
    }

    /** 清除登录会话（令牌与用户信息），保留后端地址与开关。 */
    fun clearSession() {
        token = ""
        userId = 0L
        username = ""
        email = ""
        nickname = ""
        avatar = ""
        signature = ""
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_EMAIL)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR)
            .remove(KEY_SIGNATURE)
            .apply()
    }

    /**
     * 归一化后的后端 API 基址（含 `/api/v1/`）。
     * 用户可输入根地址、含 `/api/v1` 的地址或带斜杠的地址，这里统一成 Retrofit 需要的形态。
     */
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

    /** 后端站点根地址（不含 `/api/v1`），用于拼接 `/uploads` 等静态资源。 */
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
