package com.example.tuanyingshi.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.tuanyingshi.MainActivity
import com.example.tuanyingshi.ui.navigation.Screen

/**
 * 下载通知工具：负责创建通知渠道，并发送下载进度 / 完成 / 失败提醒。
 *
 * - 进度通知为「进行中」（ongoing），显示百分比；
 * - 完成 / 失败通知可点击或点「前往查看」跳转到「我的下载」页；
 * - 是否展示由 [NotifPrefs.downloadNotificationEnabled] 控制（在「消息通知」里可关闭）。
 */
object DownloadNotifier {
    private const val CHANNEL_ID = "download_channel"
    private const val CHANNEL_NAME = "下载通知"
    private const val NOTIF_ID = 1001

    /** 确保通知渠道已创建（Android 8+ 必需，重复调用幂等）。 */
    private fun ensureChannel(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "番剧下载进度与完成提醒"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** 点击 / 「前往查看」意图：打开 App 并跳转到下载列表页。 */
    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.tuanyingshi.OPEN_DOWNLOADS"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("nav_route", Screen.Downloads.route)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notifyQueued(context: Context, animeTitle: String, count: Int) {
        if (!NotifPrefs.isDownloadNotificationEnabled()) return
        ensureChannel(context)
        val pi = contentIntent(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.tuanyingshi.R.drawable.ic_notification)
            .setContentTitle("已加入下载队列")
            .setContentText("$animeTitle · 共 $count 集")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(
                android.R.drawable.ic_menu_view,
                "前往查看",
                pi,
            )
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    fun notifyProgress(context: Context, animeTitle: String, episodeName: String, percent: Int) {
        if (!NotifPrefs.isDownloadNotificationEnabled()) return
        ensureChannel(context)
        val pi = contentIntent(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.tuanyingshi.R.drawable.ic_notification)
            .setContentTitle("正在下载：$animeTitle")
            .setContentText(if (percent <= 0) episodeName else "$episodeName · $percent%")
            .setProgress(0, 0, percent <= 0)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    fun notifyDone(context: Context, animeTitle: String, episodeName: String) {
        if (!NotifPrefs.isDownloadNotificationEnabled()) return
        ensureChannel(context)
        val pi = contentIntent(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.tuanyingshi.R.drawable.ic_notification)
            .setContentTitle("下载完成")
            .setContentText("$animeTitle - $episodeName")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(
                android.R.drawable.ic_menu_view,
                "前往查看",
                pi,
            )
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    fun notifyFailed(context: Context, animeTitle: String, episodeName: String) {
        if (!NotifPrefs.isDownloadNotificationEnabled()) return
        ensureChannel(context)
        val pi = contentIntent(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.tuanyingshi.R.drawable.ic_notification)
            .setContentTitle("下载失败")
            .setContentText("$animeTitle - $episodeName")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(
                android.R.drawable.ic_menu_view,
                "前往查看",
                pi,
            )
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }
}
