package com.example.tuanyingshi.util

import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 桥接「系统/文件管理器用本 App 打开视频」的意图与导航。
 *
 * - [com.example.tuanyingshi.MainActivity] 在 onCreate / onNewIntent 收到
 *   [android.content.Intent.ACTION_VIEW] 且携带视频 uri 时，构造 [ExternalPlayRequest]
 *   并 [post] 到这里；
 * - [com.example.tuanyingshi.ui.navigation.AppNavigation] 用 `collect` 消费，
 *   导航到外部播放页（[com.example.tuanyingshi.ui.player.ExternalPlayerScreen]）。
 *
 * 为什么用 SharedFlow(replay = 1) 而非 Channel：
 * - 冷启动时 [MainActivity] 在 setContent 之前就 post 了意图，此时 Compose 里的收集器
 *   还没启动。replay = 1 会把最近一次的值**回放**给后到的收集者，避免事件丢失、App 只进首页。
 * - 同时保留对「再次用本 App 打开另一个视频」的实时投递能力（同一收集器会持续收到新值）。
 * - 用 `collect`（而非 consume），收集协程被取消（如旋屏重建）时不会关闭单例流，
 *   后续仍能继续收发。
 */
object ExternalIntentBus {
    private val _flow = MutableSharedFlow<ExternalPlayRequest>(replay = 1)
    val flow: SharedFlow<ExternalPlayRequest> get() = _flow.asSharedFlow()

    fun post(request: ExternalPlayRequest) {
        _flow.tryEmit(request)
    }
}

/** 一次「用本 App 打开视频」的请求。 */
data class ExternalPlayRequest(
    val uri: Uri,
    val title: String?,
)
