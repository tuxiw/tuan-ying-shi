package com.example.tuanyingshi.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.example.tuanyingshi.BuildConfig
import com.example.tuanyingshi.R
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * 播放器截图通知：截图成功保存到「用户选择的下载目录 /img」后，在状态栏发一条
 * 带缩略图（大图样式）的通知，点击可直接用系统相册 / 图片查看器打开该图片。
 *
 * - 每条截图用独立通知 id，连续截图不会互相覆盖；
 * - Android 13+ 未授予 POST_NOTIFICATIONS（或用户关闭了通知）时，降级为 Toast 提示路径，
 *   避免直接 notify 抛 SecurityException。
 */
object ScreenshotNotifier {
    private const val CHANNEL_ID = "screenshot_channel"
    private const val CHANNEL_NAME = "截图通知"

    /** 自增通知 id：起始值避开下载通知（1001）。 */
    private val idSeed = AtomicInteger(3000)

    private fun ensureChannel(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "播放器截图保存完成提醒"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** 点击通知：用系统看图应用打开刚保存的图片。 */
    private fun viewIntent(context: Context, file: File): PendingIntent? {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        }.getOrNull() ?: return null
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 没有任何看图应用可处理时，不要让点击崩掉
        if (intent.resolveActivity(context.packageManager) == null) return null
        return PendingIntent.getActivity(
            context,
            file.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 大图样式用的缩略图：按最长边采样解码，避免把整张截图读进内存。 */
    private fun decodePreview(file: File, maxEdge: Int = 512): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) sample *= 2
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    /**
     * 发一条「截图已保存」通知。可从任意线程调用：
     * 缩略图解码在调用方线程（播放器侧为 IO 线程）完成，只有发通知切回主线程。
     */
    fun notify(context: Context, file: File) {
        val app = context.applicationContext
        val preview = decodePreview(file)
        Handler(Looper.getMainLooper()).post {
            runCatching { notifyInternal(app, file, preview) }
        }
    }

    private fun notifyInternal(context: Context, file: File, preview: Bitmap?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            // 通知被关闭（Android 13+ 未授权 / 用户手动关）：至少要让用户知道图存到哪了
            Toast.makeText(context, "截图已保存：${file.absolutePath}", Toast.LENGTH_LONG).show()
            return
        }
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("截图已保存")
            .setContentText(file.name)
            .setSubText(file.parentFile?.absolutePath ?: file.absolutePath)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        viewIntent(context, file)?.let { builder.setContentIntent(it) }
        if (preview != null) {
            builder.setLargeIcon(preview)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(preview)
                    .bigLargeIcon(null as Bitmap?),
            )
        }
        manager.notify(idSeed.getAndIncrement(), builder.build())
    }
}
