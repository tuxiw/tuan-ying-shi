package com.example.tuanyingshi.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import java.io.File

fun getDownloadLocationUri(context: Context): String? =
    context.preferences.getString(KEY_DOWNLOAD_LOCATION_URI, null)

fun setDownloadLocationUri(context: Context, uri: String?) {
    context.preferences.edit().putString(KEY_DOWNLOAD_LOCATION_URI, uri).apply()
}

/**
 * 打开下载目录（跳转系统文件管理器）。
 * 优先用 SAF 持久化的 treeUri 构造文档 URI，以目录形式打开（DocumentsUI / 文件管理器）；
 * 失败（未选择目录 / ROM 不支持 ACTION_VIEW 目录）时兜底 Toast 显示真实路径。
 */
fun openDownloadDir(context: Context) {
    val uriStr = getDownloadLocationUri(context)
    if (!uriStr.isNullOrBlank()) {
        val opened = runCatching {
            val treeUri = Uri.parse(uriStr)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (opened) return
    }
    val dir = resolveDownloadDir(context)
    Toast.makeText(context, "下载目录：${dir.absolutePath}", Toast.LENGTH_LONG).show()
}

/**
 * 把用户在设置里用 SAF（OpenDocumentTree）选择的目录持久化，并返回可直接用
 * java.io.File 写入的真实路径。解析失败（如不支持的存储卷）时回退到应用私有
 * Movies 目录（始终可写），保证下载功能不崩。
 */
fun resolveDownloadDir(context: Context): File {
    val uriStr = getDownloadLocationUri(context)
    if (!uriStr.isNullOrBlank()) {
        try {
            val uri = Uri.parse(uriStr)
            val path = treeUriToFilePath(uri)
            if (path != null) {
                val dir = File(path)
                if (dir.exists() || dir.mkdirs()) return dir
            }
        } catch (_: Exception) {
            // 落到下面的兜底目录
        }
    }
    val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "tuanyingshi")
    fallback.mkdirs()
    return fallback
}

/** content://.../tree/primary:Download/x -> /storage/emulated/0/Download/x */
private fun treeUriToFilePath(uri: Uri): String? {
    val path = uri.path ?: return null
    val seg = path.removePrefix("/tree/")
    val parts = seg.split(":", limit = 2)
    if (parts.size != 2) return null
    val (volume, rest) = parts[0] to parts[1]
    val root = when (volume) {
        "primary" -> "/storage/emulated/0"
        else -> "/storage/$volume"
    }
    return if (rest.isBlank()) root else "$root/$rest"
}

/** 设置页展示当前下载位置的可读名称（优先用解析出的真实路径，否则提示默认）。 */
fun downloadLocationDisplayName(context: Context): String {
    val uriStr = getDownloadLocationUri(context) ?: return "默认（应用私有目录）"
    val parsed = treeUriToFilePath(Uri.parse(uriStr))
    return parsed?.let { "存储目录: $it" } ?: "已选择自定义目录"
}
