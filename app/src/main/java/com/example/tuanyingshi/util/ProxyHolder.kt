package com.example.tuanyingshi.util

import android.os.Build
import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.Executor

/**
 * 网络代理模式。
 * - NONE：不使用代理（直连）
 * - SYSTEM：使用系统代理（OkHttp 走默认 ProxySelector；WebView 清除覆盖，恢复系统默认）
 * - CUSTOM：使用用户配置的代理（HTTP 或 SOCKS5，可按需鉴权）
 */
enum class ProxyMode {
    NONE,
    SYSTEM,
    CUSTOM,
}

/**
 * 自定义代理协议类型。
 * - HTTP：标准 HTTP 代理（CONNECT 隧道）
 * - SOCKS：SOCKS5 代理
 */
enum class ProxyType {
    HTTP,
    SOCKS,
}

/**
 * 全局代理配置中心。
 * 持有当前代理模式、协议类型、自定义地址、端口与鉴权信息，并在变更时：
 *  1. 同步给下载模块（m3u8 下载走 OkHttp）；
 *  2. 同步给系统 WebView（解析页走 WebView，API 28+ 用 ProxyController）；
 *  3. 通过 [applyProxy] 把代理与鉴权注入各 OkHttp 客户端。
 *
 * 注：WebView 的 ProxyConfig 仅支持 HTTP 代理规则，因此 SOCKS 类型在 WebView 解析页
 * 会退化为系统默认（OkHttp 侧仍按 SOCKS5 生效）。
 */
object ProxyHolder {

    private const val KEY_PROXY_MODE = "proxy_mode"
    private const val KEY_PROXY_TYPE = "proxy_type"
    private const val KEY_PROXY_HOST = "proxy_host"
    private const val KEY_PROXY_PORT = "proxy_port"
    private const val KEY_PROXY_USER = "proxy_user"
    private const val KEY_PROXY_PASS = "proxy_pass"

    var mode: ProxyMode = ProxyMode.NONE
        private set
    var type: ProxyType = ProxyType.HTTP
        private set
    var host: String = ""
        private set
    var port: Int = 0
        private set
    var username: String = ""
        private set
    var password: String = ""
        private set

    init {
        load()
        syncProxyToModules()
        applyWebViewProxy()
    }

    fun load() {
        val prefs = TuanyingApp.getInstance().preferences
        mode = runCatching {
            ProxyMode.valueOf(
                prefs.getString(KEY_PROXY_MODE, ProxyMode.NONE.name) ?: ProxyMode.NONE.name,
            )
        }.getOrDefault(ProxyMode.NONE)
        type = runCatching {
            ProxyType.valueOf(
                prefs.getString(KEY_PROXY_TYPE, ProxyType.HTTP.name) ?: ProxyType.HTTP.name,
            )
        }.getOrDefault(ProxyType.HTTP)
        host = prefs.getString(KEY_PROXY_HOST, "") ?: ""
        port = prefs.getInt(KEY_PROXY_PORT, 0)
        username = prefs.getString(KEY_PROXY_USER, "") ?: ""
        password = prefs.getString(KEY_PROXY_PASS, "") ?: ""
    }

    /**
     * 在 [com.example.tuanyingshi.TuanyingApp.onCreate] 中调用，
     * 提前加载并应用已保存的代理配置（触发本 object 的 init 块）。
     */
    fun ensureInitialized() {
        // 调用任意成员即可触发 object 初始化；init 已包含 load / syncToDownloadModule / applyWebViewProxy。
    }

    fun save(
        mode: ProxyMode,
        type: ProxyType,
        host: String,
        port: Int,
        username: String,
        password: String,
    ) {
        this.mode = mode
        this.type = type
        this.host = host.trim()
        this.port = port
        this.username = username.trim()
        this.password = password
        TuanyingApp.getInstance().preferences.edit().apply {
            putString(KEY_PROXY_MODE, mode.name)
            putString(KEY_PROXY_TYPE, type.name)
            putString(KEY_PROXY_HOST, this@ProxyHolder.host)
            putInt(KEY_PROXY_PORT, port)
            putString(KEY_PROXY_USER, this@ProxyHolder.username)
            putString(KEY_PROXY_PASS, this@ProxyHolder.password)
            apply()
        }
        syncProxyToModules()
        applyWebViewProxy()
    }

    /**
     * 给 OkHttp 用的代理对象：
     * - NONE：返回 [Proxy.NO_PROXY]（强制直连）；
     * - SYSTEM：返回 null（使用 OkHttp 默认 ProxySelector，即系统代理）；
     * - CUSTOM：返回用户指定的 HTTP 或 SOCKS5 代理（地址/端口非法时退化为 null，即系统默认）。
     */
    fun okHttpProxy(): Proxy? {
        return when (mode) {
            ProxyMode.NONE -> Proxy.NO_PROXY
            ProxyMode.SYSTEM -> null
            ProxyMode.CUSTOM -> if (host.isNotBlank() && port in 1..65535) {
                val proxyType = when (type) {
                    ProxyType.HTTP -> Proxy.Type.HTTP
                    ProxyType.SOCKS -> Proxy.Type.SOCKS
                }
                Proxy(proxyType, InetSocketAddress(host, port))
            } else {
                null
            }
        }
    }

    /**
     * 代理身份认证器（HTTP Basic）。
     * 当自定义代理且用户名/密码均非空时返回鉴权器，在收到 407 时自动附加
     * `Proxy-Authorization` 头；否则返回 null（无需鉴权）。
     * 读取的是 ProxyHolder 的实时字段，因此更换凭据后立即生效。
     */
    fun proxyAuthenticator(): Authenticator? {
        if (mode != ProxyMode.CUSTOM) return null
        if (username.isBlank() || password.isBlank()) return null
        val credential = Credentials.basic(username, password)
        return Authenticator { _: Route?, response: Response ->
            // 仅对代理返回的 407 做响应，避免把凭据误发给目标服务器
            if (response.code != 407) return@Authenticator null
            val req: Request = response.request
            req.newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }
    }

    /**
     * 把代理与鉴权注入各网络模块（下载 OkHttp / 播放器 Media3），
     * 使 m3u8 下载、视频直链请求都实时遵循 App 内代理配置。
     */
    private fun syncProxyToModules() {
        com.example.tuanyingshi.download.core.httpProxyProvider = { okHttpProxy() }
        com.example.tuanyingshi.download.core.httpProxyAuthenticator = { proxyAuthenticator() }
        com.lanlinju.videoplayer.playerProxyProvider = { okHttpProxy() }
        com.lanlinju.videoplayer.playerProxyAuthenticator = { proxyAuthenticator() }
    }

    /**
     * 同步到系统 WebView（API 28+）。
     * 注意：android.webkit.ProxyController / ProxyConfig 属于系统 API，
     * 不在公开 SDK 的 android.jar 中（已实测 android-35 jar 中不存在），
     * 只能通过反射调用；反射失败（受限名单 / 厂商 ROM 差异）时
     * 静默降级为 WebView 默认行为（跟随系统代理）。
     * 另：ProxyConfig 仅支持 HTTP 代理规则，SOCKS 类型在此处退化为清除覆盖。
     */
    private fun applyWebViewProxy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            val controllerClass = Class.forName("android.webkit.ProxyController")
            val configClass = Class.forName("android.webkit.ProxyConfig")
            val builderClass = Class.forName("android.webkit.ProxyConfig\$Builder")
            val controller = controllerClass.getMethod("getInstance").invoke(null)
            val executor: Executor = Dispatchers.IO.asExecutor()
            when (mode) {
                ProxyMode.CUSTOM -> {
                    if (type == ProxyType.HTTP && host.isNotBlank() && port in 1..65535) {
                        val builder = builderClass.getDeclaredConstructor().newInstance()
                        builderClass.getMethod("addProxyRule", String::class.java)
                            .invoke(builder, "PROXY $host:$port")
                        val config = builderClass.getMethod("build").invoke(builder)
                        controllerClass
                            .getMethod(
                                "setProxyOverride",
                                configClass,
                                Executor::class.java,
                                Runnable::class.java,
                            )
                            .invoke(controller, config, executor, Runnable { })
                    } else {
                        // SOCKS 或非法的 HTTP：清除覆盖，交由系统默认
                        controllerClass
                            .getMethod(
                                "clearProxyOverride",
                                Executor::class.java,
                                Runnable::class.java,
                            )
                            .invoke(controller, executor, Runnable { })
                    }
                }
                else -> {
                    controllerClass
                        .getMethod(
                            "clearProxyOverride",
                            Executor::class.java,
                            Runnable::class.java,
                        )
                        .invoke(controller, executor, Runnable { })
                }
            }
        }
    }

    /** 设置页展示用的简介文本。 */
    fun summary(): String = when (mode) {
        ProxyMode.NONE -> "不使用代理"
        ProxyMode.SYSTEM -> "系统代理"
        ProxyMode.CUSTOM -> {
            val proto = if (type == ProxyType.SOCKS) "SOCKS5" else "HTTP"
            val auth = if (username.isNotBlank()) "（已鉴权）" else ""
            if (host.isNotBlank() && port in 1..65535) {
                "$proto 代理 $host:$port$auth"
            } else {
                "$proto 代理（未配置地址）"
            }
        }
    }
}

/**
 * 动态代理选择器：每次建立网络连接时实时读取 [ProxyHolder] 当前配置。
 * 与 [okHttpProxy] 的区别——后者在 OkHttpClient 构建时快照一次，
 * 客户端被缓存（如 DownloadManager.client）后改配置不生效；
 * 而 ProxySelector 是每次建连时查询，改完代理立即生效、无需重启。
 */
fun dynamicProxySelector(): ProxySelector = DynamicAppProxySelector

private object DynamicAppProxySelector : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        return when (ProxyHolder.mode) {
            ProxyMode.NONE -> listOf(Proxy.NO_PROXY)
            // 委托系统默认选择器（即 OkHttp 不设置代理时的原生行为）
            ProxyMode.SYSTEM -> systemProxies(uri)
            ProxyMode.CUSTOM -> {
                val p = ProxyHolder.okHttpProxy()
                    ?: return systemProxies(uri) // 配置非法时退化为系统默认
                listOf(p)
            }
        }
    }

    private fun systemProxies(uri: URI?): List<Proxy> =
        runCatching { ProxySelector.getDefault()?.select(uri).orEmpty() }
            .getOrDefault(emptyList())
            .ifEmpty { listOf(Proxy.NO_PROXY) }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        // 委托系统默认选择器，保持其内部状态一致
        runCatching { ProxySelector.getDefault()?.connectFailed(uri, sa, ioe) }
    }
}

/**
 * 便捷扩展：把当前代理配置（含鉴权）应用到 [okhttp3.OkHttpClient.Builder]。
 * 挂载动态 [ProxySelector]（每次建连实时读取配置）与 [Authenticator]（代理 407 鉴权），
 * 因此客户端即使被缓存为单例，改代理或凭据后也能立即生效。
 */
fun OkHttpClient.Builder.applyProxy(): OkHttpClient.Builder {
    proxySelector(dynamicProxySelector())
    proxyAuthenticator(DynamicProxyAuthenticator)
    return this
}

/**
 * 通用代理鉴权器：委托给 [ProxyHolder.proxyAuthenticator]，
 * 缺省（无需鉴权）时返回 null 表示不重试。
 */
private object DynamicProxyAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        return ProxyHolder.proxyAuthenticator()?.authenticate(route, response)
    }
}
