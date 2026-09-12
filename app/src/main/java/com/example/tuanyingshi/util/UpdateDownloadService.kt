package com.example.tuanyingshi.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.tuanyingshi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * 应用内更新的后台下载服务（前台服务）。
 *
 * 点击更新弹窗的「立即更新」后，由 [UpdateDownloader.startBackgroundDownload] 拉起本服务，
 * 在后台以流式方式下载 APK，并通过通知栏实时展示进度；下载完成 / 失败时更新通知。
 * 即便用户关闭了更新弹窗、甚至切到其它 App，下载仍会继续进行。
 *
 * 通知交互：
 * - 下载中：通知为「进行中」，点击打开 App；附带「取消」操作可中止下载。
 * - 下载完成：通知提示「点击安装」，点击触发系统安装器。
 * - 下载失败：通知提示失败原因，点击重新下载。
 */
class UpdateDownloadService : android.app.Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var notificationManager: NotificationManager
    private val cancelReceiver = CancelReceiver()

    // 限流：避免高频刷新通知导致系统丢弃
    private var lastPct = -2
    private var lastUpdate = 0L

    // 保存启动时的原始 extras，供「重试」时重建 Intent
    private var launchExtras: Intent? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
        // 动态注册「取消下载」广播（无需在 Manifest 声明 receiver）
        // Android 13+ 必须显式指定导出标志，否则抛 SecurityException。
        registerReceiver(
            cancelReceiver,
            IntentFilter(ACTION_CANCEL),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.RECEIVER_NOT_EXPORTED
            } else {
                0
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_CANCEL -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "tuanyingshi_update.apk"
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: "gitee"
        val version = intent.getStringExtra(EXTRA_VERSION) ?: ""

        // 记录原始 extras，供失败时「重试」重建 Intent
        launchExtras = Intent(this, UpdateDownloadService::class.java).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_FILE_NAME, fileName)
            putExtra(EXTRA_CHANNEL, channel)
            putExtra(EXTRA_VERSION, version)
        }

        // 必须在启动后 5s 内进入前台，否则系统会 ANR / 杀掉服务
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildDownloading(version, channel, 0, false),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        scope.launch {
            var file: File? = null
            try {
                file = UpdateDownloader.download(
                    this@UpdateDownloadService,
                    url,
                    fileName,
                ) { pct -> publishProgress(version, channel, pct) }
            } catch (_: Throwable) {
                file = null
            }

            if (file == null) {
                showFailed(version, channel)
                stopSelf()
            } else {
                showCompleted(file, version, channel)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(cancelReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "应用更新",
                NotificationManager.IMPORTANCE_LOW,
            )
            ch.setSound(null, null)
            ch.enableLights(false)
            ch.enableVibration(false)
            ch.setShowBadge(false)
            notificationManager.createNotificationChannel(ch)
        }
    }

    /** 下载进度回调：带限流地刷新「下载中」通知。 */
    private fun publishProgress(version: String, channel: String, pct: Int) {
        val now = System.currentTimeMillis()
        val known = pct >= 0
        val changed = pct != lastPct
        val throttled = (now - lastUpdate) > 600
        if (!changed) return
        if (known && pct < 100 && !throttled) return
        lastPct = pct
        lastUpdate = now
        // 100% 与 0% 立即刷新，确保首尾可见
        if (pct == 100 || pct == 0 || !known) {
            lastUpdate = now
        }
        notificationManager.notify(
            NOTIF_ID,
            buildDownloading(version, channel, pct, !known),
        )
    }

    private fun buildDownloading(
        version: String,
        channel: String,
        pct: Int,
        indeterminate: Boolean,
    ): Notification {
        val title = "正在下载更新"
        val text = if (indeterminate) {
            "团影视 $version · 来自 $channel · 准备中…"
        } else {
            "团影视 $version · 来自 $channel · $pct%"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "取消", cancelIntent())

        if (indeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, pct.coerceIn(0, 100), false)
        }
        return builder.build()
    }

    private fun showCompleted(file: File, version: String, channel: String) {
        val title = "更新下载完成"
        val text = "团影视 $version · 点击安装"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(installIntent(file))
        notificationManager.notify(NOTIF_ID, builder.build())
        // 退出前台但保留该通知（DETACH：通知不会被一并移除）
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
    }

    private fun showFailed(version: String, channel: String) {
        val title = "更新下载失败"
        val text = "团影视 $version · 点击重试（来自 $channel）"
        // 复用启动 Intent 作为「重试」入口
        val retry = PendingIntent.getService(
            this,
            REQ_RETRY,
            launchExtras ?: Intent(this, UpdateDownloadService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(retry)
        notificationManager.notify(NOTIF_ID, builder.build())
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
    }

    private fun openAppIntent(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pi = PendingIntent.getActivity(
            this,
            0,
            launch ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return pi
    }

    private fun installIntent(file: File): PendingIntent {
        val i = UpdateDownloader.buildInstallIntent(this, file)
        return PendingIntent.getActivity(
            this,
            REQ_INSTALL,
            i,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(): PendingIntent {
        val i = Intent(this, CancelReceiver::class.java).apply {
            action = ACTION_CANCEL
        }
        return PendingIntent.getBroadcast(
            this,
            REQ_CANCEL,
            i,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 取消下载的广播接收器。 */
    class CancelReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CANCEL && context != null) {
                // 移除进行中的通知，并停止服务（onDestroy 会取消协程）
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIF_ID)
                context.stopService(Intent(context, UpdateDownloadService::class.java))
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "app_update_channel"
        const val NOTIF_ID = 1001

        const val EXTRA_URL = "extra_url"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_VERSION = "extra_version"

        const val ACTION_CANCEL = "com.example.tuanyingshi.action.UPDATE_CANCEL"

        private const val REQ_INSTALL = 2
        private const val REQ_CANCEL = 3
        private const val REQ_RETRY = 4
    }
}
