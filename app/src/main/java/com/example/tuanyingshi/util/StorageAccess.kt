package com.example.tuanyingshi.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import com.example.tuanyingshi.BuildConfig
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 存储权限工具：下载位置用 SAF 选了 treeUri，但 App 实际是通过「原始文件路径 + java.io.File」
 * 读写该目录（见 [resolveDownloadDir]）。在 Android 10+ 上读取共享存储里的文件需要对应权限：
 * - Android 11+（API 30+）：以「所有文件访问」(MANAGE_EXTERNAL_STORAGE) 为准，覆盖任意路径；
 * - Android 6~10：运行时申请 READ_EXTERNAL_STORAGE；
 * - Android 5.1 及以下：安装即授权。
 */

/** 当前是否已具备读写外部存储（用户指定下载位置）的权限。 */
fun hasStorageAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

/** 是否需要（且尚未获得）文件访问权限。 */
fun shouldRequestStorageAccess(context: Context): Boolean = !hasStorageAccess(context)

/** 文件是否位于应用私有目录（无需任何存储权限即可读写）。 */
fun isInAppPrivateDir(context: Context, file: java.io.File): Boolean {
    val path = file.absolutePath
    return listOfNotNull(
        context.getExternalFilesDir(null)?.absolutePath,
        context.filesDir?.absolutePath,
        context.cacheDir?.absolutePath,
        context.getExternalCacheDir()?.absolutePath,
    ).any { path.startsWith(it) }
}

/**
 * 把部分 content:// URI 解析为真实文件路径，供 ExoPlayer 以 file:// 方式读取。
 *
 * 适用场景：文件管理器（如小米文件管理器）通过「用本 App 打开」共享出来的视频，
 * 其 content:// provider 未导出、也未给本 App 授权，直接交给 ExoPlayer 会抛
 * SecurityException。但这类 URI 的路径段直接编码了真实文件位置，可映射回
 * /storage/emulated/0/... 后用「所有文件访问」权限读取。
 *
 * 仅支持可映射为真实路径的来源；其余 content://（云盘等）返回 null，由调用方兜底拷贝。
 */
fun contentUriToFilePath(context: Context, uri: Uri): String? {
    val authority = uri.authority ?: return null
    val path = uri.path ?: return null
    return when {
        // 小米文件管理器：content://com.android.fileexplorer.myprovider/external_files/...
        authority == "com.android.fileexplorer.myprovider" && path.startsWith("/external_files") -> {
            val rest = path.removePrefix("/external_files").trimStart('/')
            File(Environment.getExternalStorageDirectory(), rest).absolutePath
        }
        // 标准 SAF 文档：content://com.android.externalstorage.documents/document/primary:Download/x
        authority == "com.android.externalstorage.documents" -> runCatching {
            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":", limit = 2)
            val volume = parts[0]
            val rest = parts.getOrNull(1) ?: ""
            val root = if (volume == "primary") {
                Environment.getExternalStorageDirectory().absolutePath
            } else {
                "/storage/$volume"
            }
            if (rest.isBlank()) root else "$root/$rest"
        }.getOrNull()
        else -> null
    }
}

/**
 * 把 content:// 视频拷贝到应用缓存目录，返回缓存文件。
 * 仅当共享方授予了 URI 读取权限（FLAG_GRANT_READ_URI_PERMISSION）时才能成功；
 * 否则抛 SecurityException / IOException，由调用方提示用户。
 *
 * 用于「无法映射为真实文件路径」的 content://（如云盘 App 共享），避免直接把
 * 未授权 URI 交给 ExoPlayer。
 */
fun copyContentUriToCache(context: Context, uri: Uri): File {
    val name = queryContentDisplayName(context, uri) ?: "video_${System.currentTimeMillis()}"
    val safeName = name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "video.ts" }
    val out = File(context.cacheDir, "ext_video/$safeName")
    out.parentFile?.mkdirs()
    val input = context.contentResolver.openInputStream(uri)
        ?: throw java.io.IOException("无法打开视频流（权限不足或文件不存在）")
    input.use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
    return out
}

/** 从 content:// 读取显示名（用于缓存文件命名）。 */
private fun queryContentDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    }.getOrNull()
}

/** 跳转到系统「所有文件访问」设置页（Android 11+）。 */
private fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
            }
            context.startActivity(intent)
        }
    }
}

/**
 * 组合侧存储权限请求器。
 * - Android 11+：跳转「所有文件访问」设置页，返回后回调结果；
 * - Android 6~10：弹窗申请 READ_EXTERNAL_STORAGE，返回后回调结果；
 * - 已授权时 [granted] 为 true，调用 [StorageAccessRequester.request] 直接回调 true。
 *
 * 用法：
 * ```
 * val requester = rememberStorageAccessRequester { granted -> /* 重新渲染 */ }
 * if (needsAccess && !requester.granted) Button(onClick = requester::request) { Text("授予文件访问权限") }
 * ```
 */
@Composable
fun rememberStorageAccessRequester(onResult: (Boolean) -> Unit): StorageAccessRequester {
    val context = LocalContext.current
    var grantedState by remember { mutableStateOf(hasStorageAccess(context)) }

    val manageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // 从「所有文件访问」设置页返回后，重新评估授权状态
        grantedState = hasStorageAccess(context)
        onResult(grantedState)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        grantedState = hasStorageAccess(context)
        onResult(grantedState)
    }

    return remember(context) {
        object : StorageAccessRequester {
            override val granted: Boolean
                get() = grantedState

            override fun request() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        onResult(true)
                        return
                    }
                    // 用编译期常量包名，避免某些 ROM 上 LocalContext 取到的 packageName 为空
                    // 导致 Uri 变成 "package:" 而使 Intent 无法被任何 Activity 解析。
                    val pkg = BuildConfig.APPLICATION_ID
                    val manageIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", pkg, null)
                    }
                    if (manageIntent.resolveActivity(context.packageManager) != null) {
                        runCatching { manageLauncher.launch(manageIntent) }
                        return
                    }
                    // 部分 ROM（如 MIUI）没有「所有文件访问」专用设置页，
                    // 回退到应用详情页，用户可在其中手动授予存储权限。
                    val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", pkg, null)
                    }
                    runCatching {
                        if (detailsIntent.resolveActivity(context.packageManager) != null) {
                            manageLauncher.launch(detailsIntent)
                        } else {
                            throw android.content.ActivityNotFoundException("无可用设置页")
                        }
                    }.onFailure {
                        Toast.makeText(
                            context,
                            "无法打开权限设置页，请到系统设置中手动授予「所有文件访问」权限",
                            Toast.LENGTH_LONG,
                        ).show()
                        onResult(false)
                    }
                } else {
                    permLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }
}

interface StorageAccessRequester {
    val granted: Boolean
    fun request()
}
