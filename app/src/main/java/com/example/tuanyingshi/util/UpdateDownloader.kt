package com.example.tuanyingshi.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 应用内更新下载 / 安装器。
 *
 * - [download]：把 APK 流式下载到应用私有 Download 目录（[Context.getExternalFilesDir]），
 *   通过 [onProgress] 回调下载进度（0~100；无法获知总大小时回调 -1 表示「不确定」）。
 * - [install]：通过 FileProvider 暴露 APK 并触发系统安装。若系统因「未知来源」限制而拒绝，
 *   会自动引导用户前往「安装未知应用」设置页，并返回 false。
 */
object UpdateDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** FileProvider authority（与 AndroidManifest 中声明一致）。 */
    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    /**
     * 下载 APK 到应用私有 Download 目录。
     * @return 下载完成的文件；失败（网络错误 / IO 异常）返回 null。
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            dir.mkdirs()
            val out = File(dir, fileName)
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                val body = resp.body ?: throw RuntimeException("空响应体")
                val total = body.contentLength()
                resp.body!!.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) {
                                onProgress((downloaded * 100 / total).toInt())
                            } else {
                                onProgress(-1) // 不确定进度
                            }
                        }
                    }
                }
            }
            onProgress(100)
            out
        }.getOrNull()
    }

    /**
     * 构造安装 APK 的 Intent（供直接启动或在通知 PendingIntent 中复用）。
     */
    fun buildInstallIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 触发系统安装 APK。
     * @return true 表示已成功发起安装 Intent；false 表示被系统拦截（未知来源限制），
     *         此时会自动引导用户前往设置页开启权限。
     */
    fun install(context: Context, file: File): Boolean {
        return runCatching {
            context.startActivity(buildInstallIntent(context, file))
            true
        }.getOrElse {
            // 多半是「未知来源」限制：引导去设置页开启本应用的安装权限
            openInstallSettings(context)
            false
        }
    }

    /**
     * 启动前台下载服务（[UpdateDownloadService]），让 APK 在后台下载并通过通知栏展示进度。
     * 调用后会立即返回，下载不受调用方（如弹窗）生命周期影响。
     *
     * @param url      安装包直链
     * @param fileName 保存到本地下载目录的文件名
     * @param channel  下载渠道名（gitee / github），仅用于通知文案
     * @param version  版本号（如 "v0.3.6"），仅用于通知文案
     */
    fun startBackgroundDownload(
        context: Context,
        url: String,
        fileName: String,
        channel: String,
        version: String,
    ) {
        val intent = Intent(context, UpdateDownloadService::class.java).apply {
            putExtra(UpdateDownloadService.EXTRA_URL, url)
            putExtra(UpdateDownloadService.EXTRA_FILE_NAME, fileName)
            putExtra(UpdateDownloadService.EXTRA_CHANNEL, channel)
            putExtra(UpdateDownloadService.EXTRA_VERSION, version)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /** 引导用户前往「设置 → 安装未知应用」页面（针对本应用授权）。 */
    private fun openInstallSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
}
