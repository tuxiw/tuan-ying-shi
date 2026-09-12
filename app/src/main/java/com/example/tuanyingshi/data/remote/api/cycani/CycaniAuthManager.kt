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

    private const val BASE_URL = "https://www.cycani.org"
    private const val LOGIN_URL = "https://www.cycani.org/api/auth/login"

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

    val token: String?
        get() = prefs.getString(KEY_CYCANI_TOKEN, null)

    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank()

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
     * 直接用 OkHttp 调登录接口（Java 网络栈，不依赖 WebView）。
     *
     * - 先 GET 首页触发 WAF 并收集 Set-Cookie；
     * - POST 登录时带上这些 cookie；
     * - 不手动设置 Accept-Encoding，让 OkHttp 自动解 gzip（否则 body 会是乱码）。
     */
    private suspend fun directLogin(username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            // 1) 首页预热，收集 WAF 首访 cookie
            val warmCookies = runCatching {
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

    fun saveSession(token: String, username: String, email: String = "") {
        prefs.edit().apply {
            putString(KEY_CYCANI_TOKEN, token)
            putString(KEY_CYCANI_USERNAME, username)
            putString(KEY_CYCANI_EMAIL, email)
            apply()
        }
        _loginState.value = CycaniLoginState.LoggedIn(username, email)
    }

    fun clearSession() {
        prefs.edit().apply {
            remove(KEY_CYCANI_TOKEN)
            remove(KEY_CYCANI_USERNAME)
            remove(KEY_CYCANI_EMAIL)
            remove(KEY_CYCANI_COOKIES)
            apply()
        }
        _loginState.value = CycaniLoginState.LoggedOut
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
}

sealed interface CycaniLoginState {
    data object LoggedOut : CycaniLoginState
    data class LoggedIn(val username: String, val email: String = "") : CycaniLoginState
}
