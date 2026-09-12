package com.example.tuanyingshi.data.remote.parse.util

import android.annotation.SuppressLint
import android.util.Base64
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.net.http.SslError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.SslErrorHandler
import androidx.annotation.CallSuper
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.util.DebugLogCollector
import com.example.tuanyingshi.util.DefaultUserAgent
import com.example.tuanyingshi.util.DownloadManager
import com.example.tuanyingshi.util.log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

private const val LOG_TAG = "WebViewUtil"

/**
 * 全局 WebView 互斥锁。
 *
 * Android 上所有 WebView 共享同一个 Chromium 渲染进程。搜索 / 详情 / 播放若并发创建多个
 * WebView，渲染进程会被拖垮、`evaluateJavascript` 回调迟迟不触发，最终整页 30s 超时拿不到
 * 内容（日志里「14 源全 html=null」正是此因）。这里把所有 WebView 操作串行化，任一时刻
 * 只有一个 WebView 在跑，彻底消除并发干扰。
 */
internal val webViewLock = Mutex()

/**
 * WebView 拦截器——加载播放页并拦截真实视频流地址（对标 LaQoo WebViewUtil）。
 * 增强版：shouldInterceptRequest 中对同站资源用 OkHttp + 伪 Host 重新获取以绕过 WAF。
 */
class WebViewUtil {

    private var webView: WebView? = null

    suspend fun interceptRequest(
        url: String,
        regex: String = ".mp4|.m3u8",
        timeoutMs: Long = 35_000L,
        userAgent: String = DefaultUserAgent,
        referer: String = "",
    ): String = withContext(Dispatchers.Main) {
        webViewLock.withLock {
        createWebView(userAgent)

        "interceptRequest: 入口 url=${url.take(160)} regex='$regex' timeoutMs=$timeoutMs referer=${referer.take(60)} ua.len=${userAgent.length}".log(LOG_TAG)
        val regexPattern = regex.toRegex()
        // 兜底：站点 matchVideoUrl 配错/为空时，退而捕获任意 HLS/MP4 直链（含查询串）。
        // 苹果CMS 类站点（如 jibi）由播放器 JS 经 XHR/fetch 拉取 m3u8，
        // onLoadResource 不一定触发，故 shouldInterceptRequest 兜底必捕。
        val fallbackPattern = """(?i).+\.(m3u8|mp4|mkv|flv)(\?.*)?$""".toRegex()
        // 跨线程：shouldInterceptRequest 在 WebView 网络线程回调，onLoadResource 在主线程回调，
        // 用 AtomicReference 保证可见性。
        val matchedUrl = AtomicReference<String?>(null)
        // 扫描计数：仅用于超时时报告「共拦截 N 个请求」以判断是「完全没触发」还是「触发了没匹配」。
        val scanCount = AtomicInteger(0)
        var renderGone = false

        fun tryMatch(reqUrl: String): Boolean {
            if (matchedUrl.get() != null) return true
            // 永远不把切片(.ts)当视频直链：切片由播放器按清单自行拉取，
            // 且站点 matchVideoUrl 万一误匹配到切片时也能兜住。
            if ("""(?i)\.ts(\?.*)?$""".toRegex().matches(reqUrl)) return false
            val hit = when {
                reqUrl.contains(regexPattern) -> "site"
                fallbackPattern.matches(reqUrl) -> "fallback"
                else -> null
            }
            if (hit != null) {
                matchedUrl.set(reqUrl)
                reqUrl.log(LOG_TAG, "Regex match succeeded($hit)")
                return true
            }
            return false
        }

        webView?.webViewClient = object : BlockedResWebViewClient() {
            // shouldInterceptRequest 对所有请求（含 XHR/fetch 的 m3u8）必触发，比 onLoadResource 可靠
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString()
                if (reqUrl != null) {
                    scanCount.incrementAndGet()
                    val lower = reqUrl.lowercase()
                    if (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".ts") ||
                        lower.contains(".key") || lower.contains(".mkv") || lower.contains(".flv")
                    ) {
                        "[scan #${scanCount.get()}] $reqUrl".log(LOG_TAG)
                    }
                    tryMatch(reqUrl)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onLoadResource(view: WebView?, requestUrl: String) {
                // 兜底匹配（不打印，避免刷屏；视频相关请求已在 shouldInterceptRequest 记录为 [scan]）
                tryMatch(requestUrl)
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                renderGone = true
                DebugLogCollector.e("WebView", LOG_TAG, "onRenderProcessGone: didCrash=${detail?.didCrash()}")
                return true
            }
        }

        if (referer.isNotBlank()) webView?.loadUrl(url, mapOf("Referer" to referer)) else webView?.loadUrl(url)
        "interceptRequest: loadUrl 已派发，开始在 ${timeoutMs}ms 内等待视频直链匹配…".log(LOG_TAG)

        try {
            withTimeout(timeoutMs) {
                while (matchedUrl.get() == null) {
                    if (renderGone) throw RuntimeException("WebView 渲染进程已崩溃 (code -1)")
                    delay(100)
                }
            }
            val finalUrl = matchedUrl.get() ?: throw TimeoutException("No matching URL found")
            "[interceptRequest] 命中视频直链 → ${finalUrl.take(180)}".log(LOG_TAG)
            finalUrl
        } catch (_: TimeoutCancellationException) {
            "interceptRequest: TIMEOUT($timeoutMs) 共扫描 ${scanCount.get()} 个请求仍未命中（regex='$regex'）；请检查站点 matchVideoUrl / 兜底正则是否覆盖该 m3u8".log(LOG_TAG)
            throw TimeoutException("Web connection timeout exception")
        } finally {
            destroyWebView()
        }
        }
    }

    /**
     * 用 WebView 加载页面并取「渲染后」的 outerHTML。
     *
     * 次元城等站点有 WAF（JS 挑战），纯 OkHttp 抓到的是挑战页/空壳，
     * 必须让 WebView 跑完 JS（含 WAF 校验 + React 水合）后再读 DOM。
     * 返回 null 表示加载失败/超时，调用方应退回 OkHttp 或报错。
     *
     * 注意：WebView 必须在主线程操作，故本方法内部切到 [Dispatchers.Main]。
     */
    suspend fun getPageHtml(
        url: String,
        timeoutMs: Long = 30_000L,
        userAgent: String = DefaultUserAgent,
        waitAfterLoadMs: Long = 1200L,
        referer: String = "",
    ): String? = withContext(Dispatchers.Main) {
        webViewLock.withLock {
        createWebView(userAgent)
        val deferred = CompletableDeferred<String?>()
        var settled = false
        var pageFinished = false
        "getPageHtml: enter url=$url waitAfterLoadMs=$waitAfterLoadMs timeoutMs=$timeoutMs referer=${referer.take(40)}".log(LOG_TAG)

        /**
         * 抓取当前 DOM 的 outerHTML。
         * - 返回 null/空 -> 调用方走 OkHttp 兜底；
         * - 非空 -> 直接使用。
         *
         * 关键：不依赖 [WebViewClient.onPageFinished] 来触发抓取。许多影视站是长轮询 /
         * 无限滚动页面（如 vdm5 的 `ok_xm` 心跳每 3s 一次），`onPageFinished` 因某个
         * 子资源迟迟不结束而**永远不触发**，导致整页 30s 超时拿不到内容。
         * 这里改由「loadUrl 后固定等待 [waitAfterLoadMs]」的兜底定时器触发捕获，
         * 只要页面渲染出正文即可读到，与 onPageFinished 是否触发无关。
         */
        fun capture(from: String) {
            if (settled) return
            val wv = webView ?: return
            wv.evaluateJavascript(
                "(function(){var h=document.documentElement.outerHTML;" +
                    "try{return btoa(unescape(encodeURIComponent(h)));}catch(e){return '';}})()"
            ) { result ->
                if (settled) return@evaluateJavascript
                settled = true
                val html = decodeHtmlResult(result)
                "getPageHtml: capture($from) → ${if (html == null) "null/空" else "len=${html.length}"}".log(LOG_TAG)
                deferred.complete(html)
            }
        }

        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                pageFinished = true
                "getPageHtml: onPageFinished $loadedUrl".log(LOG_TAG)
                if (settled) return
                // 页面加载完再等一会（JS 水合 / 动态区块稳定）后也尝试捕获一次（更快路径）
                view?.postDelayed({ capture("onPageFinished") }, waitAfterLoadMs)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler,
                error: SslError?,
            ) {
                DebugLogCollector.e("WebView", LOG_TAG, "onReceivedSslError $url : ${error?.primaryError}")
                // 抓取场景不做证书强校验：自签 / WAF 中间证书 / 证书链不全等情况直接继续加载。
                handler.proceed()
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?,
            ) {
                DebugLogCollector.e("WebView", LOG_TAG, "onReceivedError code=$errorCode $failingUrl : $description")
                // 仅记录，不直接判失败：部分站点主文档报错（重定向 / 子资源被掐）后仍能渲染出正文，
                // 交给固定定时器去抓真实 DOM；真正的网络失败会由 capture() 拿到空内容后走 OkHttp 兜底。
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    DebugLogCollector.e(
                        "WebView",
                        LOG_TAG,
                        "onReceivedError(main) code=${error?.errorCode} ${request.url} : ${error?.description}",
                    )
                    "getPageHtml: onReceivedError(main) code=${error?.errorCode} ${request.url} : ${error?.description}".log(LOG_TAG)
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                val u = request?.url?.toString()
                if (request?.isForMainFrame == true || u?.contains("cycani") == true || u?.contains("ciyuancheng") == true) {
                    DebugLogCollector.e(
                        "WebView",
                        LOG_TAG,
                        "onReceivedHttpError ${errorResponse?.statusCode} $u",
                    )
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                if (!settled) {
                    settled = true
                    DebugLogCollector.e("WebView", LOG_TAG, "onRenderProcessGone: didCrash=${detail?.didCrash()}")
                    "getPageHtml: onRenderProcessGone didCrash=${detail?.didCrash()}".log(LOG_TAG)
                    deferred.complete(null)
                }
                return true
            }
        }

        // 兜底：loadUrl 后固定等待再抓一次（即使 onPageFinished 因长轮询子资源迟迟不触发）
        webView?.postDelayed({ capture("timer") }, waitAfterLoadMs)
        if (referer.isNotBlank()) webView?.loadUrl(url, mapOf("Referer" to referer)) else webView?.loadUrl(url)
        val result = try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            "getPageHtml: TIMEOUT($timeoutMs) → 返回 null；pageFinished=$pageFinished".log(LOG_TAG)
            null
        } finally {
            settled = true
            destroyWebView()
        }
        "getPageHtml: 退出 result=${if (result == null) "null" else "len=${result.length}"} pageFinished=$pageFinished".log(LOG_TAG)
        result
        }
    }

    /** evaluateJavascript 回调拿到的是 base64 字符串（被 JSON 引号包裹），解出原始 HTML。 */
    private fun decodeHtmlResult(result: String?): String? {
        if (result == null) return null
        val trimmed = result.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        val b64 = trimmed.removeSurrounding("\"")
        return runCatching {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        }.getOrElse { trimmed }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(userAgent: String) {
        destroyWebView()
        webView = WebView(TuanyingApp.getInstance()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.databaseEnabled = true
            settings.userAgentString = userAgent
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // 抓数据不需要图片/缓存：降低渲染进程内存占用，规避模拟器上 OOM 导致的
            // `Renderer process crash detected (code -1)`
            settings.loadsImagesAutomatically = false
            settings.blockNetworkImage = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            // 调试模式开启 WebView 日志时，把页面内 console.* 收集进 DebugLogCollector
            webChromeClient = DebugLogCollector.consoleClient()
        }
    }

    private fun destroyWebView() {
        val v = webView
        webView = null
        scheduleSafeDestroyWebView(v)
        "DestroyWebView(scheduled)".log(LOG_TAG)
    }

    fun clearWeb() {
        webView?.clear()
        destroyWebView()
    }

    private fun WebView.clear() {
        clearCache(true)
        clearHistory()
        clearFormData()
        clearMatches()
    }
}

abstract class BlockedResWebViewClient(
    private val blockRes: Array<String> = arrayOf(
        ".css", ".ts",
        ".mp3", ".m4a",
        ".gif", ".jpg", ".png", ".webp", ".jpeg", ".svg",
        ".woff", ".woff2", ".ttf", ".eot"
    )
) : WebViewClient() {

    private val blockWebResourceRequest =
        WebResourceResponse("text/html", "utf-8", ByteArrayInputStream("".toByteArray()))

    @CallSuper
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest?
    ) = run {
        val url = request?.url?.toString() ?: return null
        if (blockRes.any { url.contains(it) }) {
            url.log(LOG_TAG, "BlockedRes")
            view.post { view.webViewClient.onLoadResource(view, url) }
            blockWebResourceRequest
        } else if (isBaseHost(url)) {
            val html = DownloadManager.getHtmlSync(url)
            if (html != null) {
                WebResourceResponse("text/html", "utf-8", html.byteInputStream())
            } else null
        } else {
            null
        }
    }

    private fun isBaseHost(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("://www.ciyuancheng.net") ||
            lower.contains("://www.cycani.org") ||
            lower.contains("://www.cyc-anime.net")
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        val url = request?.url?.toString()
        DebugLogCollector.e(
            "WebView",
            LOG_TAG,
            "onReceivedError ${error?.errorCode} $url : ${error?.description}",
        )
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        val url = request?.url?.toString()
        DebugLogCollector.e(
            "WebView",
            LOG_TAG,
            "onReceivedHttpError ${errorResponse?.statusCode} $url",
        )
    }
}

/**
 * 安全销毁 WebView。
 *
 * 渲染进程在 JS 执行刚结束、尚未收尾时，若被同步 [WebView.destroy] 强杀，
 * Chromium 会打印 `Renderer process crash detected (code -1)`。这里：
 * 1. 用 try/catch 兜底，避免 destroy 抛异常影响调用方；
 * 2. 把销毁推迟 500ms（主线程），给渲染进程留出收尾窗口。
 */
fun safeDestroyWebView(webView: WebView?) {
    if (webView == null) return
    try {
        webView.stopLoading()
        webView.destroy()
    } catch (t: Throwable) {
        Log.w(LOG_TAG, "safeDestroyWebView: ${t.message}")
    }
}

/** 主线程上延迟销毁 WebView，规避同步 destroy 引发的渲染进程崩溃。 */
fun scheduleSafeDestroyWebView(webView: WebView?) {
    if (webView == null) return
    try {
        webView.postDelayed({ safeDestroyWebView(webView) }, 500)
    } catch (t: Throwable) {
        safeDestroyWebView(webView)
    }
}
