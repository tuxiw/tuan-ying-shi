package com.example.tuanyingshi.util.source_rule

import android.webkit.WebView
import com.example.tuanyingshi.util.log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** 一次 Cloudflare 验证请求。 */
data class CfVerificationRequest(
    val id: Int,
    val url: String,
    val host: String,
    /** 验证时使用的 UA（必须与后续 OkHttp 请求的 UA 完全一致，否则 clearance 立即失效）。 */
    val userAgent: String,
)

/**
 * Cloudflare 验证协调器。
 *
 * 移植自 HaRu（Flutter）的 `HanimeCloudflareCoordinator`，解决两个并发问题：
 * 1. **同 URL 去重**：聚合搜索时 N 个请求同时撞 CF，如果各自弹一次 WebView，
 *    共享的 WebView 渲染进程会被拖垮（本项目已深受其害）。这里按 URL 合并为一次验证，
 *    后续请求复用同一结果；
 * 2. **串行执行**：任意时刻只有一个 WebView 在过盾。
 *
 * 同时向 UI 暴露 [active] / [status] / [webView] / [interactionRequired]，供 Compose 卡片
 * 显示进度、按需把 WebView 挂载到界面上（Turnstile 类交互挑战必须可见才能通过）。
 */
object CfVerificationCoordinator {

    private const val TAG = "CfVerification"

    /** 单一宿主失败后的冷却时间，避免「验证失败 → 立刻重试 → 再失败」死循环。 */
    private const val COOLDOWN_MS = 60_000L

    /** 总开关（后续接到设置项）。 */
    @Volatile
    var enabled: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = ArrayDeque<CfVerificationRequest>()
    private val pendingByUrl = ConcurrentHashMap<String, CompletableDeferred<VerifyResult?>>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private var idSeq = 0

    private val queueMutex = Mutex()
    private val pumpMutex = Mutex()

    private val _active = MutableStateFlow<CfVerificationRequest?>(null)
    val active: StateFlow<CfVerificationRequest?> = _active.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    /** 当前验证用的 WebView（UI 需要交互时把它挂到 Compose 里显示）。 */
    private val _webView = MutableStateFlow<WebView?>(null)
    val webView: StateFlow<WebView?> = _webView.asStateFlow()

    /** 是否需要用户可见地参与（检测到 Turnstile 复选框等可见控件）。 */
    private val _interactionRequired = MutableStateFlow(false)
    val interactionRequired: StateFlow<Boolean> = _interactionRequired.asStateFlow()

    /**
     * 请求一次 Cloudflare 验证。
     *
     * @return 验证结果（HTML + Cookie + UA）；已在冷却中 / 关闭开关 / 超时 / 取消返回 null。
     */
    suspend fun verify(url: String, userAgent: String): VerifyResult? {
        if (!enabled) return null
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return null
        val last = failedAt[host]
        if (last != null && System.currentTimeMillis() - last < COOLDOWN_MS) {
            "$TAG verify: $host 处于冷却期，跳过".log(TAG)
            return null
        }
        // 检查 + 入队必须在同一把锁里：聚合搜索常常并发打同一个 URL，
        // 不加锁会出现两个调用各自创建一次验证（重复弹 WebView）。
        val deferred = CompletableDeferred<VerifyResult?>()
        queueMutex.withLock {
            val existing = pendingByUrl[url]
            if (existing != null) {
                "$TAG verify: 合并同 URL 请求 url=$url".log(TAG)
                return runCatching { existing.await() }.getOrNull()
            }
            pendingByUrl[url] = deferred
            queue.addLast(CfVerificationRequest(++idSeq, url, host, userAgent))
        }
        scope.launch { pump() }
        return runCatching { deferred.await() }.getOrNull().also { result ->
            if (result == null) failedAt[host] = System.currentTimeMillis()
        }
    }

    /** 由 [KazumiWebViewVerifier] 调用：把正在跑的 WebView 暴露给 UI。 */
    fun attachWebView(webView: WebView?) {
        _webView.value = webView
    }

    fun publishStatus(text: String) {
        _status.value = text
    }

    fun markInteractionRequired(required: Boolean) {
        _interactionRequired.value = required
    }

    /** 用户在卡片上点「重试」：只对当前 WebView 重新加载一次（禁止自动 reload，参照 HaRu）。 */
    fun retryNow() {
        val wv = _webView.value ?: return
        wv.post { runCatching { wv.reload() } }
        _status.value = "已重新加载页面，请在网页中完成验证…"
    }

    /** 用户在卡片上点「取消」：中断当前验证。 */
    fun cancelActive() {
        _webView.value?.post { runCatching { _webView.value?.stopLoading() } }
        _status.value = "已取消"
        scope.launch {
            queueMutex.withLock { queue.clear() }
            val snapshot = pendingByUrl.toList()
            pendingByUrl.clear()
            snapshot.forEach { (_, d) -> if (!d.isCompleted) d.complete(null) }
            _webView.value = null
            _interactionRequired.value = false
            _active.value = null
        }
    }

    private suspend fun pump() {
        pumpMutex.withLock {
            while (true) {
                val request = queueMutex.withLock {
                    if (queue.isEmpty()) null else queue.removeFirst()
                } ?: break
                if (System.currentTimeMillis() - (failedAt[request.host] ?: 0) < COOLDOWN_MS) {
                    pendingByUrl.remove(request.url)?.let { if (!it.isCompleted) it.complete(null) }
                    continue
                }
                _active.value = request
                _interactionRequired.value = false
                _status.value = "正在通过浏览器连接 ${request.host}…"
                val result = runCatching {
                    KazumiWebViewVerifier.verifyCloudflare(request) { text -> _status.value = text }
                }.onFailure {
                    "$TAG pump: ${it.javaClass.simpleName}: ${it.message}".log(TAG)
                }.getOrNull()
                _webView.value = null
                _interactionRequired.value = false
                _active.value = null
                pendingByUrl.remove(request.url)?.let { if (!it.isCompleted) it.complete(result) }
                if (result == null) failedAt[request.host] = System.currentTimeMillis()
            }
        }
    }
}
