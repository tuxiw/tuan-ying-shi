package com.example.tuanyingshi.data.remote.api.cycani

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.data.remote.parse.util.scheduleSafeDestroyWebView
import com.example.tuanyingshi.util.DebugLogCollector
import com.example.tuanyingshi.util.DefaultUserAgent
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeoutException

/**
 * 用系统 WebView 完成次元城登录，绕过 WAF JS 挑战。
 *
 * 流程：
 * 1. 加载 cycani 首页，让 WebView 自动完成 WAF 校验并拿到 Cookie；
 * 2. 通过 evaluateJavascript 在页内执行 fetch('/api/auth/login')；
 * 3. 把 JSON 响应字符串回传，解析出 token 与用户名；
 * 4. 若某次 fetch 仍在 WAF 挑战中，自动重试最多 4 次。
 *
 * 注意：部分模拟器 / 定制 ROM 上 WebView 的 Chromium 网络栈连不上外网，
 * 此时本类会超时，调用方 [CycaniAuthManager] 应优先用 OkHttp 登录。
 */
internal object CycaniWebViewLogin {

    private const val TAG = "CycaniWebViewLogin"
    private const val BASE_URL = "https://www.cycani.org"
    private const val TIMEOUT_MS = 60_000L
    private const val WAIT_AFTER_LOAD_MS = 4_000L
    private const val FALLBACK_START_MS = 12_000L
    private const val RETRY_DELAY_MS = 3_000L
    private const val MAX_RETRIES = 4
    private const val FETCH_TIMEOUT_MS = 8_000L
    private val gson = Gson()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun login(username: String, password: String): Result<CycaniLoginData> =
        withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<Result<CycaniLoginData>>()
            var settled = false
            var loginStarted = false
            var webView: WebView? = null
            var attempts = 0
            val errors = mutableListOf<String>()

            fun tryLogin(view: WebView) {
                if (settled) return
                attempts++
                injectLoginFetch(view, username, password) { result ->
                    if (settled) return@injectLoginFetch
                    result
                        .onSuccess {
                            settled = true
                            DebugLogCollector.i("Login", TAG, "WebView 登录成功")
                            deferred.complete(Result.success(it))
                        }
                        .onFailure { e ->
                            val clean = sanitizeError(e.message ?: "登录失败")
                            errors.add("第${attempts}次：$clean")
                            DebugLogCollector.w("Login", TAG, "第${attempts}次失败: $clean")
                            Log.w(TAG, "attempt $attempts failed: $clean")
                            if (attempts >= MAX_RETRIES) {
                                settled = true
                                deferred.complete(Result.failure(Exception("WAF 校验失败\n${errors.joinToString("\n")}")))
                            } else {
                                view.postDelayed({ tryLogin(view) }, RETRY_DELAY_MS)
                            }
                        }
                }
            }

            // 保证登录 fetch 只被触发一次（onPageFinished 与兜底定时器二选一）
            fun startLoginOnce(view: WebView) {
                if (loginStarted || settled) return
                loginStarted = true
                tryLogin(view)
            }

            try {
                webView = WebView(TuanyingApp.getInstance()).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    @Suppress("DEPRECATION")
                    settings.databaseEnabled = true
                    settings.userAgentString = DefaultUserAgent
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // 登录不需要图片/缓存：降低渲染进程内存占用，规避模拟器上 OOM 导致的
                    // `Renderer process crash detected (code -1)`
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    // 调试模式开启 WebView 日志时收集页面内 console.*
                    webChromeClient = DebugLogCollector.consoleClient()
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        if (settled) return
                        view?.postDelayed({
                            if (settled) return@postDelayed
                            startLoginOnce(view)
                        }, WAIT_AFTER_LOAD_MS)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?,
                    ) {
                        DebugLogCollector.e("WebView", TAG, "onReceivedError code=$errorCode $failingUrl : $description")
                        // 仅主框架加载失败才终止；子资源（图片/CSS）失败不影响登录
                        if (!settled && failingUrl?.startsWith(BASE_URL) == true) {
                            settled = true
                            deferred.complete(Result.failure(Exception("页面加载失败：$description")))
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        errorResponse: android.webkit.WebResourceResponse?,
                    ) {
                        val url = request?.url?.toString()
                        if (request?.isForMainFrame == true) {
                            DebugLogCollector.e(
                                "WebView",
                                TAG,
                                "onReceivedHttpError ${errorResponse?.statusCode} $url",
                            )
                        }
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                        if (!settled) {
                            settled = true
                            DebugLogCollector.e("WebView", TAG, "onRenderProcessGone: didCrash=${detail?.didCrash()}")
                            deferred.complete(Result.failure(Exception("WebView 渲染进程已崩溃 (code -1)，请重试或使用账号密码登录")))
                        }
                        return true
                    }
                }

                webView.loadUrl(BASE_URL)

                // 兜底：即便 onPageFinished 始终不触发（某些 WAF 页面保持长连接），
                // 也在固定时间后直接尝试 fetch，由 JS 侧超时保证每次尝试快速返回。
                webView.postDelayed({
                    if (!settled) startLoginOnce(webView)
                }, FALLBACK_START_MS)

                withTimeout(TIMEOUT_MS) { deferred.await() }
            } catch (_: TimeoutCancellationException) {
                if (!settled) {
                    settled = true
                    deferred.complete(Result.failure(TimeoutException("登录超时（WebView 无法加载首页，可能是网络/代理问题）")))
                }
                deferred.await()
            } finally {
                settled = true
                // 延迟安全销毁：避免数据刚返回就同步 destroy 触发渲染进程崩溃 (code -1)
                scheduleSafeDestroyWebView(webView)
            }
        }

    private fun injectLoginFetch(
        view: WebView,
        username: String,
        password: String,
        onResult: (Result<CycaniLoginData>) -> Unit,
    ) {
        val js = buildLoginJs(username, password)
        view.evaluateJavascript(js) { rawResult ->
            onResult(parseJsResult(rawResult))
        }
    }

    private fun buildLoginJs(username: String, password: String): String {
        val safeUsername = username.replace("\\", "\\\\").replace("\"", "\\\"")
        val safePassword = password.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            (function(){
                var payload = JSON.stringify({"username":"$safeUsername","password":"$safePassword"});
                function withTimeout(ms, p){
                    return new Promise(function(resolve, reject){
                        var t = setTimeout(function(){ reject(new Error('fetch timeout')); }, ms);
                        p.then(function(v){ clearTimeout(t); resolve(v); }, function(e){ clearTimeout(t); reject(e); });
                    });
                }
                return withTimeout($FETCH_TIMEOUT_MS, fetch('/api/auth/login', {
                    method: 'POST',
                    mode: 'same-origin',
                    credentials: 'include',
                    cache: 'no-store',
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json, text/plain, */*',
                        'Accept-Encoding': 'identity',
                        'x-app-name': 'cyc_web',
                        'x-app-version': 'cycweb',
                        'x-time-zone': 'Asia/Hong_Kong'
                    },
                    body: payload
                }))
                .then(function(r) {
                    // 只取前 8KB，避免 WAF 大段 HTML/JS 撑爆返回值
                    return r.text().then(function(t) { return {status:r.status, text:t.substring(0,8192)}; });
                })
                .then(function(res) {
                    try {
                        var obj = JSON.parse(res.text);
                        return JSON.stringify({ok:true, status:res.status, body:obj});
                    } catch(e) {
                        return JSON.stringify({ok:false, status:res.status, error:res.text});
                    }
                })
                .catch(function(e) {
                    return JSON.stringify({ok:false, error:String(e)});
                });
            })()
        """.trimIndent()
    }

    private fun parseJsResult(rawResult: String?): Result<CycaniLoginData> {
        if (rawResult.isNullOrBlank() || rawResult == "null") {
            return Result.failure(Exception("WebView 未返回数据"))
        }

        val resultJson = rawResult.removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

        Log.d(TAG, "webview login raw: $rawResult")
        DebugLogCollector.d("Login", TAG, "login response: ${rawResult?.take(400)}")

        val element = runCatching { gson.fromJson(resultJson, JsonElement::class.java) }
            .getOrNull()
        if (element == null || !element.isJsonObject) {
            return Result.failure(Exception("WAF 校验未完成"))
        }

        val wrapper = element.asJsonObject
        if (wrapper.get("ok")?.asBoolean != true) {
            val status = wrapper.get("status")?.asInt
            val error = sanitizeError(wrapper.get("error")?.asString ?: "登录失败")
            return Result.failure(Exception("${error}${if (status != null) " (HTTP $status)" else ""}"))
        }

        val body = wrapper.get("body") ?: return Result.failure(Exception("登录失败：无响应体"))
        if (!body.isJsonObject) {
            return Result.failure(Exception("登录失败：返回格式异常"))
        }

        val response = gson.fromJson(body, CycaniResponse::class.java)
        if (response?.code != 0) {
            return Result.failure(Exception(response?.msg ?: "登录失败：code=${response?.code}"))
        }

        val loginData = gson.fromJson(gson.toJson(response.data), CycaniLoginData::class.java)
            ?: return Result.failure(Exception("登录失败：未获取到登录信息"))

        return Result.success(loginData)
    }

    /**
     * 清理服务端可能返回的 WAF 乱码/HTML/二进制片段，
     * 避免把不可读内容直接抛到 UI 上。
     */
    private fun sanitizeError(message: String): String {
        val printable = message.filter { it.isWhitespace() || it.code in 32..126 || it.code in 0x4E00..0x9FFF }
        val trimmed = printable.trim().take(200)
        return if (trimmed.isBlank() || trimmed.length < 10) "WAF 校验未完成" else trimmed
    }
}
