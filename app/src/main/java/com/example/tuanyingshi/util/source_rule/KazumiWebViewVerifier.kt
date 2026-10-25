package com.example.tuanyingshi.util.source_rule

import android.annotation.SuppressLint
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.data.remote.parse.util.scheduleSafeDestroyWebView
import com.example.tuanyingshi.data.remote.parse.util.webViewLock
import com.example.tuanyingshi.util.DebugLogCollector
import com.example.tuanyingshi.util.DefaultUserAgent
import com.example.tuanyingshi.util.ProxyHolder
import com.example.tuanyingshi.util.ProxyMode
import com.example.tuanyingshi.util.applyProxy
import com.example.tuanyingshi.util.log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

// 统一用 CfVerification tag，方便用同一过滤条件同时观察协调器与过盾器内部状态（含 loadUrl / 进度 / 轮询状态）。
private const val TAG = "CfVerification"

/** 各阶段超时（毫秒）。 */
private const val IMAGE_WAIT_MS = 20_000L
private const val SUBMIT_WAIT_MS = 15_000L
private const val AUTOCLICK_WAIT_MS = 20_000L
private const val SCRIPT_WAIT_MS = 20_000L

/** Cloudflare 轮询间隔（对齐 HaRu 的 500ms）。 */
private const val CF_POLL_INTERVAL_MS = 500L
/** Cloudflare 验证总超时：JS 挑战通常 5~10s 自动放行，Turnstile 需人工点击故给足时间。 */
private const val CF_TOTAL_TIMEOUT_MS = 45_000L
/** 多久没等到 clearance 才提示用户「可能卡住了」（只改文案，不自动刷新）。 */
private const val CF_WAIT_HINT_MS = 8_000L

/**
 * 验证结果：收割到的页面 HTML（可直接当搜索结果解析，省一次请求）+ 验证得到的 Cookie + UA。
 */
data class VerifyResult(
    val html: String?,
    val cookies: String,
    val userAgent: String,
)

/**
 * 反爬验证器——完全复刻 Kazumi 的 WebView 验证机制（三种 [CaptchaType]）。
 *
 * 复刻要点：
 * - 用系统 WebView 真把验证页加载出来（不是绕过，而是帮用户过一次验证）；
 * - 类型1（图片验证码）：注入 JS 按 XPath 轮询验证码 `<img>`，Canvas 截图 `toDataURL` 回传 base64，
 *   经 [captchaInputProvider] 让用户填码，再在页内按 XPath 填入+点击提交，MutationObserver 看
 *   验证码消失即通过；
 * - 类型2（自动点击）：注入 JS 找到按钮 XPath 自动点击，按钮消失/页面跳转即通过；
 * - 类型3（自定义JS）：注入规则 `captchaScript`，脚本调用 `window.KazumiCaptcha.done/clicked` 即通过；
 * - 通过后收割页面 HTML + 读 WebView Cookie + UA，存入 [SourceCookieManager]（按 host 隔离）。
 *
 * 复用全局 [webViewLock] 串行化，避免与其他 WebView 并发导致渲染进程崩溃（对齐项目既有约定）。
 */
object KazumiWebViewVerifier {

    /**
     * 类型1 验证时回调：传入验证码图片 base64（形如 `data:image/png;base64,...`），返回用户输入的
     * 验证码文本；返回 null 表示用户取消。由 UI 层提供（弹验证码输入框）。
     * 若留空且规则为类型1，验证会被跳过（无法交互）。
     */
    var captchaInputProvider: (suspend (String) -> String?)? = null

    /**
     * 对单个规则执行一次搜索页验证。
     * @return 验证结果；取消/失败/未启用返回 null。
     */
    suspend fun verifySearch(
        rule: SourceRule,
        keyword: String,
        fallbackInputProvider: (suspend (String) -> String?)? = null,
    ): VerifyResult? {
        val cfg = rule.antiCrawler
        if (!cfg.enabled) return null
        val searchUrl = ResourceVerifyChecker.buildSearchRequestUrl(rule, keyword) ?: return null

        val inputProvider = fallbackInputProvider ?: captchaInputProvider
        if (cfg.captchaType == CaptchaType.IMAGE_CAPTCHA && inputProvider == null) {
            "$TAG verifySearch: 类型1需要用户输入但无 captchaInputProvider，跳过".log(TAG)
            return null
        }

        return withContext(Dispatchers.Main) {
            webViewLock.withLock {
                runVerifyOnMain(rule, searchUrl, cfg, inputProvider)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private suspend fun runVerifyOnMain(
        rule: SourceRule,
        searchUrl: String,
        cfg: AntiCrawlerConfig,
        inputProvider: (suspend (String) -> String?)?,
    ): VerifyResult? {
        val tag = "Kazumi[${rule.name}]"
        var webView: WebView? = null
        // shouldInterceptRequest 跑在后台线程，CookieManager 实例必须在主线程取好再传参
        val cookieManager = CookieManager.getInstance()
        val imageDeferred = CompletableDeferred<String>()   // 第一次捕到的验证码图片 base64
        val goneDeferred = CompletableDeferred<Unit>()      // 验证通过（消失 / done）
        var clickedFlag = false
        var submittedFlag = false
        val bridge = KazumiBridge()

        bridge.onImage = { b64 -> if (!imageDeferred.isCompleted) imageDeferred.complete(b64) }
        bridge.onGone = { if (!goneDeferred.isCompleted) goneDeferred.complete(Unit) }
        bridge.onDone = { if (!goneDeferred.isCompleted) goneDeferred.complete(Unit) }
        bridge.onButtonClicked = { clickedFlag = true }
        bridge.onLog = { DebugLogCollector.d("KazumiVerifier", "verifier", it) }

        val inject = buildInjectScript(cfg)

        try {
            webView = createWebView()
            webView!!.addJavascriptInterface(bridge, "KazumiBridge")
            webView!!.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    // 随页面加载尽早注入（等价于 Kazumi 的 AT_DOCUMENT_START）。
                    view?.evaluateJavascript(inject, null)
                    // 类型2：已点击过验证按钮，新页面加载即视为通过（页面跳转型验证）
                    if (clickedFlag) bridge.onGone?.invoke()
                    // 类型1：提交验证码后页面可能直接跳转到结果页，此时验证码节点随导航消失，
                    // MutationObserver 在旧文档上失效；新页面加载即视为通过（对齐 Kazumi 的 onLoadStop 逻辑）。
                    if (submittedFlag) bridge.onGone?.invoke()
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    DebugLogCollector.e("KazumiVerifier", "verifier", "$tag onReceivedError code=$errorCode $failingUrl : $description")
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    webRequest: WebResourceRequest?,
                ): WebResourceResponse? {
                    // ⚠️ 后台线程回调：不得调用 WebView 方法（view.settings 会触发 checkThread 崩溃）。
                    // UA 即 createWebView() 所用的 DefaultUserAgent；CookieManager 主线程预取传入。
                    if (webRequest == null || !shouldBridgeWebViewRequests()) return null
                    return bridgeWebResource(
                        webRequest,
                        DefaultUserAgent,
                        cookieManager,
                    )
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    if (!goneDeferred.isCompleted) {
                        goneDeferred.completeExceptionally(RuntimeException("WebView 渲染进程崩溃 (code -1)"))
                    }
                    DebugLogCollector.e("KazumiVerifier", "verifier", "$tag onRenderProcessGone didCrash=${detail?.didCrash()}")
                    return true
                }
            }

            // 注：Kazumi 在加载前会 deleteAllCookies 保证干净挑战；但本 App 多源共享 WebView Cookie 罐，
            // 全局清除会误伤其它源（如次元城 WAF cookie），故这里不清除，依赖「无有效 clearance cookie
            // 时站点自会弹出验证页」这一事实——而本方法本就在检测到验证页后才被调用。
            webView!!.loadUrl(searchUrl)
            "$tag loadUrl: $searchUrl".log(TAG)

            when (cfg.captchaType) {
                CaptchaType.IMAGE_CAPTCHA -> {
                    val image = try {
                        withTimeout(IMAGE_WAIT_MS) { imageDeferred.await() }
                    } catch (_: TimeoutCancellationException) {
                        "$tag 超时未捕获验证码图片".log(TAG)
                        null
                    } ?: return finalizeCancelled(searchUrl, webView)

                    val code = inputProvider?.invoke(image)
                    if (code.isNullOrBlank()) {
                        "$tag 用户取消/未输入验证码".log(TAG)
                        return finalizeCancelled(searchUrl, webView)
                    }
                    submitCaptcha(webView!!, code, cfg)
                    submittedFlag = true
                    waitGone(goneDeferred, SUBMIT_WAIT_MS, tag)
                }

                CaptchaType.AUTO_CLICK_BUTTON -> {
                    waitGone(goneDeferred, AUTOCLICK_WAIT_MS, tag)
                }

                CaptchaType.CUSTOM_JAVASCRIPT -> {
                    waitGone(goneDeferred, SCRIPT_WAIT_MS, tag)
                }

                else -> {
                    "$tag 未知 captchaType=${cfg.captchaType}".log(TAG)
                    return finalizeCancelled(searchUrl, webView)
                }
            }

            // 验证通过：收割页面 HTML + 读 WebView Cookie + UA，并持久化
            val html = harvestHtml(webView!!)
            val cookies = SourceCookieManager.webViewCookieNow(searchUrl)
            val ua = webView!!.settings.userAgentString
            SourceCookieManager.saveFromWebView(searchUrl, cookies, ua)
            "$tag 验证通过：htmlLen=${html?.length ?: 0} cookieLen=${cookies.length}".log(TAG)
            return VerifyResult(html, cookies, ua)
        } catch (e: Throwable) {
            "$tag 验证异常：${e.message}".log(TAG)
            return finalizeCancelled(searchUrl, webView)
        } finally {
            scheduleSafeDestroyWebView(webView)
        }
    }

    // ─────────────────── Cloudflare 5 秒盾 / Turnstile 验证 ───────────────────

    /**
     * Cloudflare 专用验证：**不看 XPath、不做用户输入**，纯轮询等待盾自动放行。
     *
     * 移植自 HaRu（Flutter）`HanimeCloudflarePage` 的核心循环，四条关键约定：
     * 1. **UA 绑定**：用调用方给的 UA（必须与后续 OkHttp 请求完全一致），否则 cf_clearance 立刻失效；
     * 2. **cf_clearance 是 httpOnly**：只能从 `CookieManager.getCookie(origin)` 读，
     *    `document.cookie` 读不到，所以每次轮询都额外拉一次原生 cookie；
     * 3. **见过挑战页后必须等 clearance 出现**才算通过（`observedChallenge → hasClearance`），
     *    否则会在盾尚未下发 cookie 时就提前收割，后续请求照旧被拦；
     * 4. **禁止自动 reload**：等待超时只改提示文案，等用户手动点「重试」（自动重刷会触发
     *    「等不到 cookie → 重刷 → 再挑战」死循环，这是 HaRu 踩过的坑）。
     *
     * @param request 由 [CfVerificationCoordinator] 统一排队下发（已去重 / 串行）
     * @param onStatus 状态文案回调，转发给 UI 卡片
     * @return 验证结果（HTML + Cookie + UA）；超时且仍是被拦页时返回 null
     */
    suspend fun verifyCloudflare(
        request: CfVerificationRequest,
        onStatus: (String) -> Unit = {},
    ): VerifyResult? = withContext(Dispatchers.Main) {
        webViewLock.withLock { runCloudflareVerify(request, onStatus) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runCloudflareVerify(
        request: CfVerificationRequest,
        onStatus: (String) -> Unit,
    ): VerifyResult? {
        val tag = "Cf[${request.host}]"
        val startedAt = System.currentTimeMillis()
        val cookieUrl = CloudflareDetector.originOf(request.url)
        // shouldInterceptRequest 跑在后台线程，CookieManager 实例必须在主线程取好再传参
        val cookieManager = CookieManager.getInstance()
        var webView: WebView? = null
        var progress = 0
        var observedChallenge = false
        var waitingSince: Long? = null
        var waitHinted = false
        var interactionPublished = false

        try {
            webView = createCloudflareWebView(request.userAgent)
            val wv = webView
            var lastStateLogAt = 0L
            wv.webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    val old = progress
                    progress = newProgress
                    // 进度是判断「WebView 到底连没连上」的最直接信号，关键节点打点
                    if (newProgress != old && (newProgress == 100 || newProgress % 25 == 0)) {
                        "$tag onProgressChanged: $newProgress url=${view?.url}".log(TAG)
                    }
                }
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    val msg = "onReceivedError code=$errorCode $failingUrl : $description"
                    DebugLogCollector.e("CfVerifier", "verifier", "$tag $msg")
                    "$tag $msg".log(TAG)
                    // 只有「主文档 + 连接类错误」才打断用户（子资源 favicon 404 之类很常见，不打扰）
                    val mainFrameLike = failingUrl != null && runCatching {
                        URI(failingUrl).host.equals(request.host, ignoreCase = true)
                    }.getOrDefault(false)
                    val connective = errorCode in intArrayOf(
                        WebViewClient.ERROR_HOST_LOOKUP,
                        WebViewClient.ERROR_CONNECT,
                        WebViewClient.ERROR_TIMEOUT,
                        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
                        WebViewClient.ERROR_PROXY_AUTHENTICATION,
                    )
                    if (mainFrameLike && connective) {
                        onStatus("页面连接失败（${description ?: "code=$errorCode"}），请到设置里检查代理，或点「重试」。")
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?,
                ) {
                    // 只记录主文档的 HTTP 错误（子资源 404/403 很常见，不关心）
                    if (request?.isForMainFrame == true) {
                        val msg = "onReceivedHttpError ${errorResponse?.statusCode} ${request?.url}"
                        DebugLogCollector.e("CfVerifier", "verifier", "$tag $msg")
                        "$tag $msg".log(TAG)
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    webRequest: WebResourceRequest?,
                ): WebResourceResponse? {
                    // ⚠️ 本回调在后台线程执行：绝不能调用 WebView 的任何方法（含 view.settings），
                    // 否则触发 checkThread 异常直接崩溃（曾实测「A WebView method was called on
                    // thread 'ThreadPoolForeg'」）。UA 与 CookieManager 均在主线程预取后传参。
                    if (webRequest == null || !shouldBridgeWebViewRequests()) return null
                    return bridgeWebResource(
                        webRequest,
                        request.userAgent,
                        cookieManager,
                    )
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    "$tag doUpdateVisitedHistory: $url reload=$isReload".log(TAG)
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    DebugLogCollector.e("CfVerifier", "verifier", "$tag onRenderProcessGone didCrash=${detail?.didCrash()}")
                    return true
                }
            }
            // 立刻暴露给 UI：卡片会在「检测到交互控件」或「等待超时」时把它挂到界面上显示
            CfVerificationCoordinator.attachWebView(wv)
            "$tag loadUrl: ${request.url}".log(TAG)
            wv.loadUrl(request.url)

            while (System.currentTimeMillis() - startedAt < CF_TOTAL_TIMEOUT_MS) {
                delay(CF_POLL_INTERVAL_MS)
                val currentUrl = wv.url.orEmpty()
                val cookie = runCatching { CookieManager.getInstance().getCookie(cookieUrl) }.getOrNull().orEmpty()
                val snap = snapshotCfPage(wv)
                if (snap == null) {
                    // 快照拿不到（evaluateJavascript 失败/渲染进程异常）也要留痕，避免「白屏卡住」无从排查
                    if (System.currentTimeMillis() - lastStateLogAt >= 5_000) {
                        lastStateLogAt = System.currentTimeMillis()
                        "$tag 轮询状态: snapshot=null progress=$progress url=$currentUrl cookieLen=${cookie.length}".log(TAG)
                        if (progress >= 90) onStatus("页面已加载但无法读取内容，可点「重试」。")
                    }
                    continue
                }
                val hostMatches = runCatching { URI(currentUrl).host }.getOrNull()
                    ?.equals(request.host, ignoreCase = true) ?: false
                val isChallenge = CloudflareDetector.isChallenge(
                    "<html><head><title>${snap.title}</title></head><body>${snap.bodyText}</body></html>",
                )
                if (isChallenge) observedChallenge = true
                val hasClearance = CloudflareDetector.hasClearance(cookie)

                // 每 5 秒输出一次完整轮询状态：排查「一直白屏/一直等待」的关键证据链
                if (System.currentTimeMillis() - lastStateLogAt >= 5_000) {
                    lastStateLogAt = System.currentTimeMillis()
                    "$tag 轮询状态: progress=$progress url=$currentUrl title=${snap.title.take(40)} " +
                        "readyState=${snap.readyState} htmlLen=${snap.pageHtml.length} " +
                        "markers=${CloudflareDetector.containsChallengeMarkers(snap.pageHtml)} " +
                        "clearance=$hasClearance interactive=${snap.interactive}".log(TAG)
                    // 无交互控件、无进展时给用户一个非空白的状态（不覆盖「请完成验证」提示）
                    if (!observedChallenge && !hasClearance && progress >= 90) {
                        onStatus("已连接 ${request.host}，正在等待验证结果…（${(System.currentTimeMillis() - startedAt) / 1000}s）")
                    }
                }

                if (isChallenge && snap.interactive && !interactionPublished) {
                    interactionPublished = true
                    CfVerificationCoordinator.markInteractionRequired(true)
                    onStatus("请在网页中完成验证，成功后会自动继续。")
                }

                val loaded = progress >= 90 || (hasClearance && snap.readyState == "complete")
                // 通关判定对齐 Han1meViewer：拿到 cf_clearance 即视为真正过盾。
                // 仅当页面 HTML 明确仍含 #challenge-form/#challenge-success-text/#challenge-error-text
                // 标记时才视为未完成；其余情况（含快照失败/截断导致 pageHtml 为空）一律放行，
                // 避免「已拿到 clearance 却因 HTML 截断被误杀」的死循环。
                val challengeStillPresent = snap.pageHtml.isNotBlank() &&
                    CloudflareDetector.containsChallengeMarkers(snap.pageHtml)
                val canComplete = hostMatches &&
                    snap.hasDocument &&
                    loaded &&
                    hasClearance &&
                    !challengeStillPresent
                if (canComplete) {
                    val html = harvestHtml(wv)
                    if (!html.isNullOrBlank() && !CloudflareDetector.containsChallengeMarkers(html)) {
                        SourceCookieManager.saveFromWebView(
                            currentUrl.ifBlank { request.url },
                            cookie,
                            request.userAgent,
                        )
                        "$tag 验证通过：progress=$progress clearance=$hasClearance htmlLen=${html.length} 耗时=${System.currentTimeMillis() - startedAt}ms"
                            .log(TAG)
                        onStatus("验证通过，正在继续加载…")
                        return VerifyResult(html, cookie, request.userAgent)
                    }
                }

                val now = System.currentTimeMillis()
                if (observedChallenge && !isChallenge && !hasClearance && progress >= 90 && waitingSince == null) {
                    waitingSince = now
                    onStatus("正在等待验证结果…")
                }
                if (!hasClearance && waitingSince != null && now - waitingSince >= CF_WAIT_HINT_MS && !waitHinted) {
                    waitHinted = true
                    CfVerificationCoordinator.markInteractionRequired(true)
                    onStatus("验证似乎卡住了，可以在卡片里手动重试。")
                }
            }
            val lastCookie = runCatching { CookieManager.getInstance().getCookie(cookieUrl) }.getOrNull().orEmpty()
            val html = harvestHtml(wv)
            val gotClearance = CloudflareDetector.hasClearance(lastCookie)
            "$tag 等待超时（${CF_TOTAL_TIMEOUT_MS}ms）htmlLen=${html?.length ?: 0} clearance=$gotClearance".log(TAG)
            // 超时仍未拿到 cf_clearance → 视为失败，避免把无 clearance 的页面当成功回填。
            return if (gotClearance && !html.isNullOrBlank() && !CloudflareDetector.containsChallengeMarkers(html)) {
                SourceCookieManager.saveFromWebView(request.url, lastCookie, request.userAgent)
                VerifyResult(html, lastCookie, request.userAgent)
            } else {
                null
            }
        } catch (e: Throwable) {
            "$tag 验证异常：${e.message}".log(TAG)
            return null
        } finally {
            val wv = webView
            if (wv != null) {
                // 若已被挂载到 Compose 卡片，先摘下来再销毁，避免 View 树持有已销毁的 WebView
                runCatching { (wv.parent as? ViewGroup)?.removeView(wv) }
                CfVerificationCoordinator.attachWebView(null)
                CfVerificationCoordinator.markInteractionRequired(false)
                scheduleSafeDestroyWebView(wv)
            }
        }
    }

    /**
     * 过盾 WebView 请求桥接客户端：与 API 请求共用同一代理栈（[applyProxy] 动态读取 ProxyHolder）。
     * 背景：WebView 的 ProxyOverride 属于隐藏 API（android.webkit.ProxyController 反射在多数
     * 系统上被受限 API 拦截、静默失败），且部分 WebView 版本对 HTTPS 请求不套用代理——表现为
     * 「OkHttp 开代理能连上站点（拿到 403 挑战页），WebView 却 ERR_CONNECTION_TIMED_OUT」。
     * 桥接后 WebView 的全部 http(s) 请求都经 OkHttp 转发，代理必然生效。
     */
    private val bridgeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .applyProxy()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 是否需要桥接（最后手段）：仅当「CUSTOM 代理模式 + WebView 原生代理未生效」时才桥接。
     *
     * ⚠️ WebView 代理已生效时**绝不桥接**：WebResourceRequest 不暴露 POST body，桥接只能发空 body，
     * Cloudflare 挑战向 /cdn-cgi/challenge-platform/... 的 POST 会全部失败 → 挑战页永远转圈、
     * cf_clearance 永远拿不到（2026-10-25 实测：主文档能加载但 spinner 不结束）。
     * 原生代理的 WebView 则与普通浏览器完全一致，POST/JS/Cookie 全部正常。
     */
    private fun shouldBridgeWebViewRequests(): Boolean =
        ProxyHolder.mode == ProxyMode.CUSTOM && !ProxyHolder.webViewProxyApplied

    /**
     * 把 WebView 的 http(s) 请求经 OkHttp 桥接转发，返回可交给 WebView 的响应。
     * - 复制 WebView 请求头（去掉 Cookie/Accept-Encoding：前者以 CookieManager 为准，
     *   后者让 OkHttp 透明解压，避免把 gzip 流当原文交给 WebView）；
     * - Cookie 从 CookieManager 读取注入（Set-Cookie 由响应侧写回）；
     * - 响应 Set-Cookie 逐条写回 CookieManager——cf_clearance 只有落进 CookieManager，
     *   轮询循环才收割得到（WebView 不会应用 WebResourceResponse 头里的 Set-Cookie）。
     * - 失败返回 null：WebView 回退原生直连（会触发 onReceivedError，状态对用户可见）。
     */
    private fun bridgeWebResource(
        request: WebResourceRequest,
        userAgent: String,
        cookieManager: CookieManager,
    ): WebResourceResponse? {
        val urlStr = request.url?.toString() ?: return null
        val scheme = request.url?.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        return runCatching {
            val builder = Request.Builder().url(urlStr)
            var hasUa = false
            request.requestHeaders?.forEach { (k, v) ->
                when {
                    k.equals("Cookie", ignoreCase = true) ||
                        k.equals("Accept-Encoding", ignoreCase = true) -> Unit
                    else -> {
                        if (k.equals("User-Agent", ignoreCase = true)) hasUa = true
                        builder.header(k, v)
                    }
                }
            }
            if (!hasUa) builder.header("User-Agent", userAgent)
            runCatching { cookieManager.getCookie(urlStr) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { builder.header("Cookie", it) }
            // 注：WebResourceRequest 拿不到 POST body，按空 body 发送（CF 挑战主文档为 GET，
            // Turnstile 回调以 GET/JS 为主，此局限只影响极少数带 body 的子请求）。
            when (request.method.uppercase()) {
                "POST" -> builder.post(RequestBody.create(null, ByteArray(0)))
                "HEAD" -> builder.head()
                "DELETE" -> builder.delete()
                else -> Unit
            }
            val resp = bridgeClient.newCall(builder.build()).execute()
            resp.headers("Set-Cookie").forEach { c ->
                runCatching { cookieManager.setCookie(urlStr, c) }
            }
            val contentType = resp.header("Content-Type") ?: "text/html"
            val mime = contentType.substringBefore(";").trim().ifBlank { "text/html" }
            val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
                .find(contentType)?.groupValues?.get(1)?.trim()
            val headers = linkedMapOf<String, String>()
            resp.headers.forEach { (k, v) ->
                if (!k.equals("Content-Encoding", ignoreCase = true) &&
                    !k.equals("Content-Length", ignoreCase = true) &&
                    !k.equals("Transfer-Encoding", ignoreCase = true) &&
                    !k.equals("Set-Cookie", ignoreCase = true)
                ) headers[k] = v
            }
            val bodyStream = resp.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))
            WebResourceResponse(
                mime, charset, resp.code, resp.message.ifBlank { "OK" }, headers, bodyStream,
            )
        }.onFailure {
            "桥接 WebView 请求失败 $urlStr : ${it.message}".log(TAG)
        }.getOrNull()
    }

    /**
     * CF 验证专用 WebView：与 OkHttp 同 UA、开启 JS 与 DOM 存储，
     * 且**允许加载图片**（Turnstile / 某些盾的行内验证资源依赖图片，禁图会卡在挑战页）。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createCloudflareWebView(userAgent: String): WebView {
        return WebView(TuanyingApp.getInstance()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.databaseEnabled = true
            settings.userAgentString = userAgent
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.loadsImagesAutomatically = true
            settings.blockNetworkImage = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // Turnstile 等验证控件依赖 window.open，必须允许 JS 开窗口。
            settings.javaScriptCanOpenWindowsAutomatically = true
            // 关键：Cloudflare Turnstile 在 challenges.cloudflare.com 等第三方域下发 cookie，
            // 不接受第三方 Cookie 会让 cf_clearance 永远拿不到（参考 Han1meViewer CloudflareActivity）。
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this@apply, true)
            webChromeClient = DebugLogCollector.consoleClient()
        }
    }

    /** 页面快照：轮询判定通过与否的三类信号来源。 */
    private data class CfSnapshot(
        val title: String,
        val bodyText: String,
        val readyState: String,
        val interactive: Boolean,
        val pageHtml: String,
    ) {
        val hasDocument: Boolean
            get() = title.isNotBlank() || bodyText.isNotBlank() || readyState == "interactive" || readyState == "complete"
    }

    /** 读一次页面快照（title / 正文前 4000 字 / readyState / 是否有可见的验证控件）。 */
    private suspend fun snapshotCfPage(wv: WebView): CfSnapshot? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<CfSnapshot?>()
        runCatching {
            wv.evaluateJavascript(CF_SNAPSHOT_JS) { result -> deferred.complete(parseCfSnapshot(decodeJsText(result))) }
        }.onFailure { deferred.complete(null) }
        withTimeoutOrNull(CF_SNAPSHOT_TIMEOUT_MS) { deferred.await() }
    }

    private const val CF_SNAPSHOT_TIMEOUT_MS = 3000L

    private const val CF_SNAPSHOT_JS = """
        (function(){
          function visible(el){
            var r = el.getBoundingClientRect();
            var s = window.getComputedStyle(el);
            return r.width > 4 && r.height > 4 && s.display !== 'none' && s.visibility !== 'hidden';
          }
          var selectors = ['input[type="checkbox"]','[role="checkbox"]','iframe[src*="turnstile"]','iframe[src*="challenges.cloudflare.com"]'];
          var interactive = selectors.some(function(sel){
            var list = document.querySelectorAll(sel);
            for (var i = 0; i < list.length; i++) { if (visible(list[i])) return true; }
            return false;
          });
          return JSON.stringify({
            title: document.title || '',
            bodyText: (document.body ? document.body.innerText : '').slice(0, 4000),
            readyState: document.readyState || '',
            interactive: interactive,
            pageHtml: (document.documentElement ? document.documentElement.innerHTML : '').slice(0, 12000)
          });
        })()
    """

    /** evaluateJavascript 返回的是 JSON 字符串字面量，先解一层 JSON 再交给 [parseCfSnapshot]。 */
    private fun decodeJsText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        if (trimmed == "null") return ""
        return runCatching { org.json.JSONTokener(trimmed).nextValue()?.toString() ?: "" }.getOrDefault(trimmed)
    }

    private fun parseCfSnapshot(json: String): CfSnapshot? {
        if (json.isBlank()) return null
        return runCatching {
            val obj = org.json.JSONObject(json)
            CfSnapshot(
                title = obj.optString("title", ""),
                bodyText = obj.optString("bodyText", ""),
                readyState = obj.optString("readyState", ""),
                interactive = obj.optBoolean("interactive", false),
                pageHtml = obj.optString("pageHtml", ""),
            )
        }.getOrNull()
    }

    private suspend fun waitGone(gone: CompletableDeferred<Unit>, ms: Long, tag: String) {
        try {
            withTimeout(ms) { gone.await() }
            "$tag 验证消失事件已触发".log(TAG)
        } catch (_: TimeoutCancellationException) {
            "$tag 等待验证消失超时($ms)".log(TAG)
            throw RuntimeException("验证超时")
        }
    }

    /** 取消/失败路径：尽量保存已拿到的 cookie，返回 null。 */
    private suspend fun finalizeCancelled(searchUrl: String, webView: WebView?): VerifyResult? {
        if (webView != null) {
            val cookies = SourceCookieManager.webViewCookieNow(searchUrl)
            if (cookies.isNotBlank()) {
                SourceCookieManager.saveFromWebView(searchUrl, cookies, webView.settings.userAgentString)
            }
        }
        return null
    }

    /** 在页内按 XPath 填入验证码并点击提交按钮。 */
    private fun submitCaptcha(wv: WebView, code: String, cfg: AntiCrawlerConfig) {
        val safeCode = code.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "")
        val js = """
        (function(){
          function xpath(expr){ try { return document.evaluate(expr, document, null, XPathResult.FIRST_ORDER_NODE_TYPE, null).singleNodeValue; } catch(e){ return null; } }
          var input = xpath(${jsStr(cfg.captchaInput)});
          if (input){ input.value = '$safeCode'; input.dispatchEvent(new Event('input',{bubbles:true})); input.dispatchEvent(new Event('change',{bubbles:true})); }
          var btn = xpath(${jsStr(cfg.captchaButton)});
          if (btn){ btn.click(); }
        })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    /** 收割当前 DOM 的 outerHTML（base64 编码后回传，对应 WebViewUtil 的做法）。 */
    private suspend fun harvestHtml(wv: WebView): String? = withContext(Dispatchers.Main) {
        val d = CompletableDeferred<String?>()
        wv.evaluateJavascript(
            "(function(){var h=document.documentElement.outerHTML;try{return btoa(unescape(encodeURIComponent(h)));}catch(e){return '';}})()"
        ) { result -> d.complete(decodeHtmlResult(result)) }
        d.await()
    }

    private fun decodeHtmlResult(result: String?): String? {
        if (result == null) return null
        val trimmed = result.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        val b64 = trimmed.removeSurrounding("\"")
        return runCatching {
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrElse { trimmed }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return WebView(TuanyingApp.getInstance()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.databaseEnabled = true
            settings.userAgentString = DefaultUserAgent
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.loadsImagesAutomatically = false
            settings.blockNetworkImage = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            webChromeClient = DebugLogCollector.consoleClient()
        }
    }

    /** 把字符串包成单引号 JS 字面量，转义单引号与反斜杠。 */
    private fun jsStr(s: String): String = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

    /** 按 captchaType 生成注入脚本（移植自 Kazumi 的 user script）。 */
    private fun buildInjectScript(cfg: AntiCrawlerConfig): String {
        val xpathFn = """
        function _x(expr){ try { return document.evaluate(expr, document, null, XPathResult.FIRST_ORDER_NODE_TYPE, null).singleNodeValue; } catch(e){ return null; } }
        """
        return when (cfg.captchaType) {
            CaptchaType.IMAGE_CAPTCHA -> """
            (function(){
              var _imgX = ${jsStr(cfg.captchaImage)};
              var _inputX = ${jsStr(cfg.captchaInput)};
              var _found = false;
              var _observer = null;
              var _poller = null;
              $xpathFn
              function _capture(node){
                try { var c=document.createElement('canvas'); c.width=node.naturalWidth||node.width||100; c.height=node.naturalHeight||node.height||40;
                  var ctx=c.getContext('2d'); ctx.drawImage(node,0,0); window.KazumiBridge.onCaptchaImage(c.toDataURL('image/png')); } catch(e){}
              }
              function _startDisappear(){
                if(_observer || !_found) return;
                _observer = new MutationObserver(function(){ var nd=_x(_imgX); if(!nd){ window.KazumiBridge.onCaptchaGone(); } });
                _observer.observe(document.documentElement, {childList:true, subtree:true, attributes:true});
              }
              function _check(){ var n=_x(_imgX); if(n){ _found=true; _capture(n); _startDisappear(); return true; } return false; }
              function _focus(){ if(!_inputX) return; var el=_x(_inputX); if(el){ if(typeof jQuery!=='undefined'&&jQuery){ jQuery(el).trigger('focus'); } else if(typeof $!=='undefined'&&$){ $(el).trigger('focus'); } else { el.focus(); } } }
              window.addEventListener('DOMContentLoaded', function(){ _focus(); });
              if(!_check()){ _poller=setInterval(function(){ if(_check()){ clearInterval(_poller); _poller=null; } }, 500); }
            })();
            """.trimIndent()

            CaptchaType.AUTO_CLICK_BUTTON -> """
            (function(){
              var _btnX = ${jsStr(cfg.captchaButton)};
              $xpathFn
              var _obs = new MutationObserver(function(){ var b=_x(_btnX); if(!b){ window.KazumiBridge.onCaptchaGone(); } });
              _obs.observe(document.documentElement, {childList:true, subtree:true, attributes:true});
              function _click(){ var b=_x(_btnX); if(b){ b.click(); window.KazumiBridge.onButtonClicked(); return true; } return false; }
              if(!_click()){ setInterval(function(){ _click(); }, 500); }
            })();
            """.trimIndent()

            CaptchaType.CUSTOM_JAVASCRIPT -> """
            (function(){
              window.KazumiCaptcha = {
                log: function(m){ try{ window.KazumiBridge.onLog(String(m)); }catch(e){} },
                clicked: function(){ try{ window.KazumiBridge.onButtonClicked(); }catch(e){} },
                done: function(){ try{ window.KazumiBridge.onDone(); }catch(e){} },
                fail: function(m){ try{ window.KazumiBridge.onLog('fail:'+m); }catch(e){} }
              };
              try {
                ${cfg.captchaScript}
              } catch(e){ window.KazumiBridge.onLog('script error: '+ (e && e.message)); }
            })();
            """.trimIndent()

            else -> "/* unknown captchaType, no inject */"
        }
    }

    /** JS → Kotlin 桥（addJavascriptInterface 目标）。 */
    private class KazumiBridge {
        var onImage: ((String) -> Unit)? = null
        var onGone: (() -> Unit)? = null
        var onDone: (() -> Unit)? = null
        var onButtonClicked: (() -> Unit)? = null
        var onLog: ((String) -> Unit)? = null

        @JavascriptInterface
        fun onCaptchaImage(base64: String) {
            if (base64.isNotBlank()) onImage?.invoke(base64)
        }

        @JavascriptInterface
        fun onCaptchaGone() = onGone?.invoke() ?: Unit

        @JavascriptInterface
        fun onDone() = onDone?.invoke() ?: Unit

        @JavascriptInterface
        fun onButtonClicked() = onButtonClicked?.invoke() ?: Unit

        @JavascriptInterface
        fun onLog(msg: String) = onLog?.invoke(msg) ?: Unit
    }
}
