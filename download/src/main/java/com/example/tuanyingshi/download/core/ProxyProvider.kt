package com.example.tuanyingshi.download.core

import java.net.Proxy

/**
 * 由宿主 App 注入的代理获取器。
 * - 返回 null：使用 OkHttp 默认（系统）代理；
 * - 返回具体 [Proxy]：覆盖为指定代理（如用户自定义 HTTP 代理或强制直连 NO_PROXY）。
 *
 * 宿主 App（团影视）在 [com.example.tuanyingshi.util.ProxyHolder] 初始化时赋值，
 * 这样下载模块无需依赖 App 模块即可获得统一的代理配置。
 */
var httpProxyProvider: (() -> Proxy?)? = null

/**
 * 由宿主 App 注入的代理身份认证器（HTTP Basic）。
 * 返回 null 表示无需代理鉴权；返回具体 [okhttp3.Authenticator] 时在收到 407 时自动附加凭据。
 * 宿主 App（团影视）在 [com.example.tuanyingshi.util.ProxyHolder] 初始化时赋值。
 */
var httpProxyAuthenticator: (() -> okhttp3.Authenticator?)? = null
