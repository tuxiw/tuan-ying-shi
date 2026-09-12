package com.example.tuanyingshi.util.source_rule

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.data.remote.parse.util.scheduleSafeDestroyWebView
import com.example.tuanyingshi.data.remote.parse.util.webViewLock
import com.example.tuanyingshi.util.DebugLogCollector
import com.example.tuanyingshi.util.DefaultUserAgent
import com.example.tuanyingshi.util.log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "KazumiVerifier"

/** 各阶段超时（毫秒）。 */
private const val IMAGE_WAIT_MS = 20_000L
private const val SUBMIT_WAIT_MS = 15_000L
private const val AUTOCLICK_WAIT_MS = 20_000L
private const val SCRIPT_WAIT_MS = 20_000L

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
