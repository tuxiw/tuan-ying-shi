package com.lanlinju.videoplayer

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * 由宿主 App 注入的代理获取器。
 * - 返回 null：走系统默认（跟随系统代理）；
 * - 返回 [Proxy]（含 [Proxy.NO_PROXY] 强制直连）：视频请求走该代理。
 *
 * 宿主 App（团影视）在 Application 启动时赋值（指向 ProxyHolder.okHttpProxy），
 * 让视频直链（如 Girigiri / 嘶哩嘶哩 的 m3u8、mp4）也遵循 App 内代理设置。
 * 经 [PlayerProxySelector] 每次建连时实时读取，改代理后重新播放即生效、无需重建播放器。
 */
var playerProxyProvider: (() -> Proxy?)? = null

/**
 * 由宿主 App 注入的代理身份认证器（HTTP Basic）。
 * 返回 null 表示无需代理鉴权；返回具体 [okhttp3.Authenticator] 时在收到 407 时自动附加凭据。
 * 宿主 App（团影视）在 Application 启动时赋值（指向 ProxyHolder.proxyAuthenticator）。
 */
var playerProxyAuthenticator: (() -> okhttp3.Authenticator?)? = null

/**
 * 动态代理选择器：每次建立网络连接时实时读取 [playerProxyProvider]。
 * 语义与 download 模块的 DownloadProxySelector 保持一致：
 * - provider 返回具体 [Proxy]（含 NO_PROXY 强制直连）：覆盖为该代理；
 * - provider 为 null 或未注入：委托系统默认选择器（即不设置代理时的原生行为）。
 */
object PlayerProxySelector : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        val proxy = playerProxyProvider?.invoke()
        return if (proxy != null) {
            listOf(proxy)
        } else {
            runCatching { ProxySelector.getDefault()?.select(uri).orEmpty() }
                .getOrDefault(emptyList())
                .ifEmpty { listOf(Proxy.NO_PROXY) }
        }
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        runCatching { ProxySelector.getDefault()?.connectFailed(uri, sa, ioe) }
    }
}
