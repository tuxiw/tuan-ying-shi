package com.example.tuanyingshi.data.remote.api.cycani

import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.util.applyProxy
import com.example.tuanyingshi.util.preferences
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 次元城（cycani）源站账号管理器。
 * - 仅用于 Cycanime 数据源登录态维护；
 * - token / 用户名用普通 SharedPreferences 持久化（后续可升级为 EncryptedSharedPreferences）。
 */
object CycaniAuthManager {

    private const val TAG = "CycaniAuthManager"

    private const val KEY_CYCANI_TOKEN = "cycani_token"
    private const val KEY_CYCANI_USERNAME = "cycani_username"
    private const val KEY_CYCANI_EMAIL = "cycani_email"
    private const val KEY_CYCANI_COOKIES = "cycani_cookies"
    /** token 过期时间（源站登录接口返回 expires_at 原始串）。 */
    private const val KEY_CYCANI_TOKEN_EXPIRES_AT = "cycani_token_expires_at"
    /** 源站用户 ID（登录接口 user.id）。 */
    private const val KEY_CYCANI_USER_ID = "cycani_user_id"
    /** 源站昵称（user.nickname，可能为空）。 */
    private const val KEY_CYCANI_NICKNAME = "cycani_nickname"
    /** 源站头像地址（user.avatar_url，可能为空）。 */
    private const val KEY_CYCANI_AVATAR = "cycani_avatar"
    /** 源站用户组名（如「普通用户」）。 */
    private const val KEY_CYCANI_GROUP = "cycani_group"

    private const val BASE_URL = "https://www.cycani.org"
    private const val LOGIN_URL = "https://www.cycani.org/api/auth/login"
    /** 邮箱验证码（注册 / 找回密码等场景共用）。 */
    private const val SEND_CODE_URL = "https://www.cycani.org/api/auth/send-code"
    private const val REGISTER_URL = "https://www.cycani.org/api/v2/auth/register"

    private val prefs: SharedPreferences
        get() = TuanyingApp.getInstance().preferences

    private val gson = Gson()

    private val loginClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(30L, TimeUnit.SECONDS)
            .applyProxy()
            .build()
    }

    private val _loginState = MutableStateFlow(loadLoginState())
    val loginState: StateFlow<CycaniLoginState> = _loginState.asStateFlow()

    /**
     * 已登录用户的资料快照（ID / 头像 / 昵称），供「我的」页展示。
     * 登录失效或主动退出后置为 null，UI 据此回到「点击登录」态。
     */
    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<CycaniProfile?> = _profile.asStateFlow()

    /**
     * 登录态已失效标记（供 UI 弹「重新登录」）。
     *
     * 触发源：[com.example.tuanyingshi.data.remote.parse.CycanimeSource] 在 API 返回
     * 401/403 或登录类错误码时调用 [markSessionExpired]。
     * 为 true 时 UI 应引导用户重新登录；登录成功或用户主动忽略后由 [clearSessionExpiredFlag] 复位。
     */
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    val token: String?
        get() = prefs.getString(KEY_CYCANI_TOKEN, null)

    /** 上次登录的用户名（失效后保留，便于重新登录时回填）。 */
    val username: String?
        get() = prefs.getString(KEY_CYCANI_USERNAME, null)

    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank()

    /**
     * token 是否已到期（本地判定，无需先发一次必然失败的请求）。
     *
     * 登录接口返回 expires_at（epoch 秒 / 毫秒或时间串），这里提前 60s 判定为过期，
     * 避免「刚好卡在过期瞬间」的请求。解析不出过期时间时不算过期，仍以服务端返回为准。
     */
    val isTokenExpired: Boolean
        get() {
            val expiresAt = tokenExpiresAtMillis() ?: return false
            return System.currentTimeMillis() >= expiresAt - 60_000L
        }

    private val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * 调用源站登录接口，成功后持久化 token 与用户名。
     *
     * 优先走 OkHttp（Java 网络栈）：在部分模拟器 / 定制 ROM 上，WebView 的 Chromium
     * 网络栈连不上外网（表现为首页永远加载不完、60s 超时），而 OkHttp 正常。
     * 仅当 OkHttp 被 WAF 拦截（返回挑战页而非 JSON）时，才退回 WebView 登录，
     * 由浏览器执行 JS 挑战拿到 clearance cookie 后再 fetch 登录接口。
     *
     * @return 登录成功返回 null；失败返回可读的错误信息。
     */
    suspend fun login(username: String, password: String): String? {
        // 1. 优先 OkHttp（可拿到真实 JSON / 可读错误，不依赖 WebView 网络栈）
        val okError = directLogin(username, password)
        if (okError == null) return null
        Log.w(TAG, "OkHttp login failed: $okError")

        // 2. 兜底 WebView（过 JS 挑战）
        val webResult = CycaniWebViewLogin.login(username, password)
        if (webResult.isSuccess) {
            return saveLoginData(webResult.getOrNull()!!, fallbackUsername = username)
        }
        val webError = webResult.exceptionOrNull()?.message ?: "WebView 登录失败"
        Log.w(TAG, "WebView login also failed: $webError")
        // 返回更可读的 OkHttp 错误作为最终提示
        return okError
    }

    /**
     * 发送注册用的邮箱验证码（POST /api/auth/send-code，body: {email, type:"register"}）。
     *
     * @return 成功返回 null；失败返回可读错误信息（可能是 WAF 拦截或站方文案）。
     */
    suspend fun sendRegisterCode(email: String): String? {
        val normalized = email.trim()
        if (normalized.isBlank()) return "请输入邮箱"
        return postJson(SEND_CODE_URL, CycaniSendCodeRequest(normalized, "register"))
    }

    /**
     * 注册次元城账号（POST /api/v2/auth/register）。
     *
     * 注册成功后**自动登录**（复用 [login]），让「我的」页立刻展示新账号资料。
     *
     * @return 注册并登录成功返回 null；否则返回错误信息（注册失败或自动登录失败）。
     */
    suspend fun register(
        username: String,
        password: String,
        email: String,
        emailCode: String,
    ): String? {
        val error = postJson(
            REGISTER_URL,
            CycaniRegisterRequest(
                username = username.trim(),
                password = password,
                email = email.trim(),
                emailCode = emailCode.trim(),
            ),
        )
        if (error != null) return error
        return login(username.trim(), password)
    }

    /**
     * 通用 JSON POST（验证码 / 注册等无需 token 的接口）。
     *
     * - 先预热首页拿 WAF cookie，再带 cookie 发请求（与 [directLogin] 同一套路）；
     * - 只认包体 code==0，其余一律按站方 msg 回传（便于直接把原因展示给用户）。
     *
     * @return 成功返回 null，失败返回错误文案。
     */
    private suspend fun postJson(url: String, body: Any): String? = withContext(Dispatchers.IO) {
        val cookieHeader = warmUpCookies().joinToString("; ") { it.substringBefore(";") }
        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(body).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Content-Type", "application/json")
            .header("Origin", BASE_URL)
            .header("x-app-name", "cyc_web")
            .header("x-app-version", "cycweb")
            .header("x-time-zone", "Asia/Hong_Kong")
            .header("referer", "$BASE_URL/")
            .apply { if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader) }
            .build()

        runCatching {
            loginClient.newCall(request).execute().use { response ->
                val text = response.body?.string()
                    ?: return@withContext "请求失败：服务器未返回数据"
                Log.d(TAG, "post $url -> ${response.code}, ${text.take(200)}")

                val obj = runCatching { gson.fromJson(text, JsonElement::class.java) }
                    .getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                if (obj == null) {
                    return@withContext "请求失败：返回非 JSON（疑似被 WAF 拦截）"
                }
                val code = obj.get("code")?.asInt ?: 0
                val msg = obj.get("msg")?.asString.orEmpty()
                if (code != 0) return@withContext msg.ifBlank { "请求失败：code=$code" }
                null
            }
        }.getOrElse { "请求失败：${it.message}" }
    }

    /**
     * 首页预热：触发 WAF 并收集首访 Set-Cookie（后续 API 请求带上，降低被拦截概率）。
     * 失败不影响主流程，返回空列表。
     */
    private suspend fun warmUpCookies(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val warmReq = Request.Builder()
                .url(BASE_URL)
                .get()
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("x-app-name", "cyc_web")
                .header("x-app-version", "cycweb")
                .header("x-time-zone", "Asia/Hong_Kong")
                .build()
            loginClient.newCall(warmReq).execute().use { it.headers("Set-Cookie") }
        }.getOrDefault(emptyList())
    }

    /**
     * 直接用 OkHttp 调登录接口（Java 网络栈，不依赖 WebView）。
     *
     * - 先 GET 首页触发 WAF 并收集 Set-Cookie；
     * - POST 登录时带上这些 cookie；
     * - 不手动设置 Accept-Encoding，让 OkHttp 自动解 gzip（否则 body 会是乱码）。
     */
    private suspend fun directLogin(username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            // 1) 首页预热，收集 WAF 首访 cookie
            val warmCookies = warmUpCookies()

            val cookieHeader = warmCookies.joinToString("; ") { it.substringBefore(";") }

            // 2) POST 登录
            val body = gson.toJson(CycaniLoginRequest(username, password))
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(LOGIN_URL)
                .post(body)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.cycani.org")
                .header("x-app-name", "cyc_web")
                .header("x-app-version", "cycweb")
                .header("x-time-zone", "Asia/Hong_Kong")
                .header("referer", "https://www.cycani.org/")
                .apply { if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader) }
                .build()

            runCatching {
                loginClient.newCall(request).execute().use { response ->
                    // 关键：不手动加 Accept-Encoding，OkHttp 会自动解 gzip
                    val responseBody = response.body?.string()
                        ?: return@withContext "登录失败：服务器未返回数据"

                    Log.d(TAG, "direct login response code=${response.code}, body=${responseBody.take(300)}")

                    if (!response.isSuccessful) {
                        return@withContext "登录失败：HTTP ${response.code}，${responseBody.take(120)}"
                    }

                    val jsonElement = runCatching { gson.fromJson(responseBody, JsonElement::class.java) }
                        .getOrNull()
                    if (jsonElement == null || jsonElement.isJsonNull) {
                        return@withContext "登录失败：返回非 JSON（疑似被 WAF 拦截），body=${responseBody.take(120)}"
                    }
                    if (jsonElement.isJsonPrimitive) {
                        return@withContext "登录失败：${jsonElement.asString}"
                    }
                    if (!jsonElement.isJsonObject) {
                        return@withContext "登录失败：返回格式异常"
                    }

                    val result = gson.fromJson(jsonElement, CycaniResponse::class.java)
                        ?: return@withContext "登录失败：解析响应失败"

                    if (result.code != 0) {
                        return@withContext result.msg.ifBlank { "登录失败：code=${result.code}" }
                    }

                    val loginData = gson.fromJson(
                        gson.toJson(result.data),
                        CycaniLoginData::class.java
                    ) ?: return@withContext "登录失败：未获取到登录信息"

                    // 3) 合并首访 cookie + 登录响应 cookie，供后续 API 复用
                    val allCookies = warmCookies + response.headers("Set-Cookie")
                    saveLoginData(loginData, fallbackUsername = username, extraCookies = allCookies)
                }
            }.getOrElse { "登录失败：${it.message}" }
        }

    private fun saveLoginData(
        loginData: CycaniLoginData,
        fallbackUsername: String,
        extraCookies: List<String> = emptyList(),
    ): String? {
        val user = loginData.user
        saveSession(
            token = loginData.token,
            username = user?.username ?: fallbackUsername,
            email = user?.email ?: "",
            expiresAt = loginData.expiresAt,
            userId = user?.id,
            nickname = user?.nickname,
            avatarUrl = user?.avatarUrl,
        )
        // WebView 登录后同步 WAF Cookie，供后续 OkHttp API 请求绕过 WAF
        syncCookiesFromWebView()
        // 叠加 OkHttp 直连拿到的 cookie
        if (extraCookies.isNotEmpty()) {
            val existing = cookies?.let { listOf(it) }.orEmpty()
            val merged = (existing + extraCookies.map { it.substringBefore(";") })
                .distinct()
                .joinToString("; ")
            prefs.edit().putString(KEY_CYCANI_COOKIES, merged).apply()
            Log.d(TAG, "merged ${merged.length} chars of cookies from OkHttp")
        }
        return null
    }

    fun saveSession(
        token: String,
        username: String,
        email: String = "",
        expiresAt: String? = null,
        userId: Long? = null,
        nickname: String? = null,
        avatarUrl: String? = null,
    ) {
        prefs.edit().apply {
            putString(KEY_CYCANI_TOKEN, token)
            putString(KEY_CYCANI_USERNAME, username)
            putString(KEY_CYCANI_EMAIL, email)
            putString(KEY_CYCANI_NICKNAME, nickname.orEmpty())
            putString(KEY_CYCANI_AVATAR, avatarUrl.orEmpty())
            if (userId != null) putLong(KEY_CYCANI_USER_ID, userId) else remove(KEY_CYCANI_USER_ID)
            if (!expiresAt.isNullOrBlank()) putString(KEY_CYCANI_TOKEN_EXPIRES_AT, expiresAt)
            apply()
        }
        _loginState.value = CycaniLoginState.LoggedIn(username, email)
        _profile.value = CycaniProfile(
            userId = userId,
            username = username,
            nickname = nickname?.takeIf { it.isNotBlank() },
            email = email.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
        )
        // 登录成功即复位「失效」信号（重新登录后无需再弹窗）
        if (_sessionExpired.value) _sessionExpired.value = false
    }

    /**
     * 标记登录态失效（由数据源层在收到 401/403 或登录类错误时调用）。
     *
     * - 清除 token：后续请求不再携带无效凭据，避免每次都重复 401；
     * - 保留用户名：重新登录时回填；
     * - 仅当本地**确实持有过 token** 时才置 [sessionExpired]：从未登录的用户不会因为
     *   接口要求登录而被误判为「登录失效」。
     *
     * @param reason 触发原因，仅用于日志排查。
     */
    fun markSessionExpired(reason: String? = null) {
        val hadToken = isLoggedIn
        if (hadToken) {
            prefs.edit()
                .remove(KEY_CYCANI_TOKEN)
                .remove(KEY_CYCANI_TOKEN_EXPIRES_AT)
                .apply()
            // 失效后不再当作已登录用户展示（UI 引导重新登录），但保留用户名供回填
            _profile.value = null
            _loginState.value = CycaniLoginState.LoggedOut
            Log.w(TAG, "cycani session expired, token cleared. reason=$reason")
        } else {
            Log.d(TAG, "cycani auth error but no token locally, reason=$reason")
        }
        if (hadToken && !_sessionExpired.value) _sessionExpired.value = true
    }

    /** 用户主动关闭「重新登录」弹窗：复位信号，避免每次进入页面都反复弹出。 */
    fun clearSessionExpiredFlag() {
        if (_sessionExpired.value) _sessionExpired.value = false
    }

    fun clearSession() {
        prefs.edit().apply {
            remove(KEY_CYCANI_TOKEN)
            remove(KEY_CYCANI_USERNAME)
            remove(KEY_CYCANI_EMAIL)
            remove(KEY_CYCANI_COOKIES)
            remove(KEY_CYCANI_TOKEN_EXPIRES_AT)
            remove(KEY_CYCANI_USER_ID)
            remove(KEY_CYCANI_NICKNAME)
            remove(KEY_CYCANI_AVATAR)
            apply()
        }
        _profile.value = null
        _loginState.value = CycaniLoginState.LoggedOut
        if (_sessionExpired.value) _sessionExpired.value = false
    }

    /**
     * 解析登录接口返回的过期时间；无法识别时返回 null（交回服务端判定）。
     * 支持 epoch 秒 / 毫秒，以及常见 ISO 时间串（`2026-10-11T12:00:00Z` / 带毫秒或 `+08:00`）。
     */
    private fun tokenExpiresAtMillis(): Long? {
        val raw = prefs.getString(KEY_CYCANI_TOKEN_EXPIRES_AT, null)?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        raw.toLongOrNull()?.let {
            // 10 位=秒，13 位=毫秒；小于 10^12 一律按秒处理
            return if (it < 1_000_000_000_000L) it * 1000L else it
        }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ss",
        )
        for (pattern in patterns) {
            val millis = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw)?.time
            }.getOrNull()
            if (millis != null) return millis
        }
        return null
    }

    /**
     * 把 WebView 中的 WAF / 登录 Cookie 同步到本地，供 OkHttp 后续 API 请求使用。
     */
    fun syncCookiesFromWebView() {
        val cookies = CookieManager.getInstance().getCookie("https://www.cycani.org")
        if (!cookies.isNullOrBlank()) {
            prefs.edit().putString(KEY_CYCANI_COOKIES, cookies).apply()
            Log.d(TAG, "synced ${cookies.length} chars of cookies")
        }
    }

    /**
     * 当前用于 cycani API 请求的 Cookie 字符串（来自 WebView 同步）。
     */
    val cookies: String?
        get() = prefs.getString(KEY_CYCANI_COOKIES, null)

    private fun loadLoginState(): CycaniLoginState {
        val username = prefs.getString(KEY_CYCANI_USERNAME, null)
        val email = prefs.getString(KEY_CYCANI_EMAIL, null)
        return if (isLoggedIn && username != null) {
            CycaniLoginState.LoggedIn(username, email.orEmpty())
        } else {
            CycaniLoginState.LoggedOut
        }
    }

    /** 从本地持久化恢复资料快照；未登录（无 token）时为 null。 */
    private fun loadProfile(): CycaniProfile? {
        if (!isLoggedIn) return null
        val username = prefs.getString(KEY_CYCANI_USERNAME, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return CycaniProfile(
            userId = prefs.getLong(KEY_CYCANI_USER_ID, -1L).takeIf { it > 0 },
            username = username,
            nickname = prefs.getString(KEY_CYCANI_NICKNAME, null)?.takeIf { it.isNotBlank() },
            email = prefs.getString(KEY_CYCANI_EMAIL, null)?.takeIf { it.isNotBlank() },
            avatarUrl = prefs.getString(KEY_CYCANI_AVATAR, null)?.takeIf { it.isNotBlank() },
            groupName = prefs.getString(KEY_CYCANI_GROUP, null)?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * 拉取源站最新账号资料（GET /api/user/me），成功后刷新 [profile] 并落库。
     *
     * 用于「我的」页展示真实 ID / 头像 / 用户组：登录接口返回的 user 节点可能是精简的
     * （老版本登录、或站方未回传 avatar），进入页面时拉一次即可补全。
     *
     * @return 成功返回最新 [CycaniProfile]；失败返回 null（已登录态失效时内部会标记失效）。
     */
    suspend fun fetchProfile(): CycaniProfile? = withContext(Dispatchers.IO) {
        val currentToken = token
        if (currentToken.isNullOrBlank()) return@withContext null

        val request = Request.Builder()
            .url("$BASE_URL/api/user/me")
            .get()
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("x-app-name", "cyc_web")
            .header("x-app-version", "cycweb")
            .header("x-time-zone", "Asia/Hong_Kong")
            .header("referer", "https://www.cycani.org/")
            .header("Authorization", currentToken)
            .apply { cookies?.let { header("Cookie", it) } }
            .build()

        val body = runCatching {
            loginClient.newCall(request).execute().use { resp ->
                when (resp.code) {
                    401, 403 -> {
                        markSessionExpired("fetchProfile HTTP ${resp.code}")
                        return@withContext null
                    }
                    else -> resp.body?.string()
                }
            }
        }.getOrElse {
            Log.w(TAG, "fetchProfile request failed: ${it.message}")
            null
        }
        if (body.isNullOrBlank()) return@withContext null

        val parsed = runCatching {
            val element = gson.fromJson(body, JsonElement::class.java)
            if (element == null || !element.isJsonObject) return@runCatching null
            val obj = element.asJsonObject
            val code = obj.get("code")?.asInt ?: return@runCatching null
            if (code != 0) {
                // 站方把登录失效也塞在 200 包体里
                val msg = obj.get("msg")?.asString.orEmpty()
                if (CycaniResponse<Unit>(code, msg, null).isAuthError()) {
                    markSessionExpired("fetchProfile code=$code msg=$msg")
                }
                return@runCatching null
            }
            val data = obj.get("data") ?: return@runCatching null
            gson.fromJson(data, CycaniUserProfile::class.java)
        }.getOrNull()

        if (parsed == null) {
            Log.w(TAG, "fetchProfile parse failed: ${body.take(120)}")
            return@withContext null
        }

        val merged = CycaniProfile(
            userId = parsed.id?.takeIf { it > 0 },
            username = parsed.username?.takeIf { it.isNotBlank() }
                ?: username?.takeIf { it.isNotBlank() } ?: return@withContext null,
            nickname = parsed.nickname?.takeIf { it.isNotBlank() },
            email = parsed.email?.takeIf { it.isNotBlank() },
            avatarUrl = parsed.avatarUrl?.takeIf { it.isNotBlank() },
            groupName = parsed.groupName?.takeIf { it.isNotBlank() },
        )
        prefs.edit().apply {
            merged.userId?.let { putLong(KEY_CYCANI_USER_ID, it) } ?: remove(KEY_CYCANI_USER_ID)
            putString(KEY_CYCANI_USERNAME, merged.username)
            putString(KEY_CYCANI_NICKNAME, merged.nickname.orEmpty())
            putString(KEY_CYCANI_EMAIL, merged.email.orEmpty())
            putString(KEY_CYCANI_AVATAR, merged.avatarUrl.orEmpty())
            putString(KEY_CYCANI_GROUP, merged.groupName.orEmpty())
            apply()
        }
        _profile.value = merged
        Log.d(TAG, "fetchProfile ok: id=${merged.userId} username=${merged.username} group=${merged.groupName}")
        merged
    }
}

sealed interface CycaniLoginState {
    data object LoggedOut : CycaniLoginState
    data class LoggedIn(val username: String, val email: String = "") : CycaniLoginState
}

/**
 * 次元城账号资料快照（「我的」页展示用）。
 *
 * 字段全部可空：登录接口与 /api/user/me 都可能不回传昵称 / 头像，
 * UI 需按「昵称 → 用户名」「头像 → 首字母占位」逐级回退。
 */
data class CycaniProfile(
    val userId: Long?,
    val username: String,
    val nickname: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val groupName: String? = null,
) {
    /** 展示名：优先昵称，回退用户名。 */
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: username
}
