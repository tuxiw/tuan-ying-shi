package com.example.tuanyingshi.download.concurrent

import android.util.Log
import com.example.tuanyingshi.download.State
import com.example.tuanyingshi.download.core.DownloadQueue
import com.example.tuanyingshi.download.core.DownloadTask
import com.example.tuanyingshi.util.DownloadPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 可实时调节并发数的下载队列（替代 download 库默认的固定 MAX_TASK_NUMBER 单例队列）。
 *
 * 设计要点：
 * - 单一消费者泵（pump）从无限缓冲 channel 取任务，按 [DownloadPrefs] 的当前值节流启动；
 * - 用户修改「同时下载数」时无需重建队列，下一轮起即生效；
 * - [DownloadTask.stop] 内部会取消自身的 downloadJob，因此 dequeue 无需额外动作。
 */
object ConfigurableDownloadQueue : DownloadQueue {
    private const val TAG = "DLQueue"
    private val channel = Channel<DownloadTask>(Channel.UNLIMITED)
    private val active = AtomicInteger(0)
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureStarted() {
        if (started.compareAndSet(false, true)) {
            scope.launch { pump() }
        }
    }

    private fun maxActive(): Int = DownloadPrefs.getConcurrentDownloads().coerceAtLeast(1)

    private suspend fun pump() {
        for (task in channel) {
            // 等待出现空闲槽位（用户调小并发数时，已运行的任务自然结束后才会继续）
            while (active.get() >= maxActive()) {
                if (!scope.isActive) break
                delay(120)
            }

            // 关键：不能用 task.canStart() 判断是否启动。
            // DownloadTask.start() 是「先把状态置为 Waiting，再入队」，而 canStart() 只认
            // None / Failed / Stopped —— 对排队中的 Waiting 任务永远返回 false，
            // 导致任务被 pump 直接跳过、永远不真正开始下载
            // （现象：一直 0%、下载目录无任何文件、任务状态长期停留在 DOWNLOADING）。
            // 这里只跳过已成功或已被用户停止的任务，其余一律启动。
            val state = task.getState()
            if (state is State.Succeed || state is State.Stopped) {
                Log.d(TAG, "skip task: ${task.param.saveName}, state=${state::class.simpleName}")
                continue
            }

            active.incrementAndGet()
            Log.d(
                TAG,
                "start task: ${task.param.saveName}, active=${active.get()}/${maxActive()}",
            )
            scope.launch {
                try {
                    task.suspendStart()
                } finally {
                    active.decrementAndGet()
                }
            }
        }
    }

    override suspend fun enqueue(task: DownloadTask) {
        ensureStarted()
        // Channel.UNLIMITED 的 trySend 始终成功；此处兜底以避免极端情况下的竞态
        if (!channel.trySend(task).isSuccess) {
            scope.launch { channel.send(task) }
        }
    }

    override suspend fun dequeue(task: DownloadTask) {
        // 真正的取消由 DownloadTask.stop() 触发（cancel downloadJob），此处无需处理
    }
}
