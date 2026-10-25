package com.example.tuanyingshi.util.source_rule

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 数据源订阅自动更新 Worker：每 1 小时由 WorkManager 调度一次（全局唯一周期任务）。
 *
 * 与旧实现「进程内 `delay` 轮询」的区别：
 * - 本任务由系统调度，App 进程被杀 / 被缓存冻结（Android 12+）/ Doze 息屏后依然会执行
 *   （时间点可能被系统推后，但不会像进程内计时器那样直接丢失）；
 * - 带 [NetworkType.CONNECTED] 约束：断网时自动等待，恢复网络后立即补跑，不会白白失败；
 * - 单条订阅刷新失败时返回 [Result.retry]，按退避策略重试，不必干等下一个整点。
 *
 * 入队见 [enqueuePeriodic]（[SourceSubscriptionRepository.startAutoUpdate] 调用）。
 */
class SubscriptionUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val subs = SourceSubscriptionRepository.subscriptions.value
        if (subs.isEmpty()) return Result.success()
        var allOk = true
        subs.forEach { sub ->
            val ok = runCatching { SourceSubscriptionRepository.refresh(sub) }.getOrDefault(false)
            if (!ok) allOk = false
        }
        return if (allOk) Result.success() else Result.retry()
    }

    companion object {
        /** 周期任务名（唯一，供 KEEP 策略去重）。 */
        const val WORK_NAME: String = "source-subscription-auto-update"

        /**
         * 入队唯一周期任务。已存在则保留原任务（KEEP），不会重置周期——
         * 因此每次 App 启动都可以安全调用。
         */
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
