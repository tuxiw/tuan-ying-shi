package com.example.tuanyingshi.util

import kotlinx.coroutines.channels.Channel

/**
 * 应用内导航请求总线：用于从系统通知等「非 Compose 上下文」触发页面跳转。
 *
 * 例如下载完成通知的「前往查看」点击后，MainActivity 收到带有 nav_route 的 intent，
 * 通过 [requestNavigation] 发送路由字符串，AppNavigation 收集后执行 navigate。
 *
 * 使用 CONFLATED 通道：冷启动期投递的请求会被缓冲，待导航层就绪后只投递一次，
 * 避免 SharedFlow(replay=0) 收不到冷启动期值的竞态问题。
 */
object NavIntentBus {
    val channel = Channel<String>(Channel.CONFLATED)

    fun requestNavigation(route: String) {
        channel.trySend(route)
    }
}
