package com.example.tuanyingshi.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.tuanyingshi.BuildConfig
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.data.remote.parse.AnimeSource
import com.example.tuanyingshi.download.utils.decrypt
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

val Context.preferences: SharedPreferences
    get() = getSharedPreferences("tuanying_prefs", Context.MODE_PRIVATE)

/**
 * 获取默认的动漫域名（用户可在设置中覆盖）
 */
fun AnimeSource.getDefaultDomain(): String {
    return TuanyingApp.getInstance().preferences.getString(KEY_SOURCE_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN
}

/**
 * AnimeSource 的 preferences 扩展属性
 */
val AnimeSource.preferences: SharedPreferences
    get() = TuanyingApp.getInstance().preferences

suspend fun AnimeSource.getDocument(url: String,webURl: String): Document {
    val source = DownloadManager.getHtml(url,webURl)
    return Jsoup.parse(source)
}

/**
 * 先 Base64 解码，再 AES 解密（嘶哩嘶哩视频流解析用）
 */
fun AnimeSource.decryptData(data: String, key: String, iv: String): String {
    val bytes = java.util.Base64.getDecoder().decode(data.toByteArray())
    val debytes = bytes.decrypt(key.toByteArray(), iv.toByteArray())
    return debytes.decodeToString()
}

fun <T> T.log(tag: String = "Debug", prefix: String = ""): T {
    val prefixStr = if (prefix.isEmpty()) "" else "[$prefix] "
    val msg = if (this is Throwable) (this.message ?: "") else toString()
    val fullMsg = prefixStr + msg
    if (BuildConfig.DEBUG) {
        if (this is Throwable) {
            Log.w(tag, prefixStr + this.message, this)
        } else {
            Log.d(tag, fullMsg)
        }
    }
    // 调试日志收集：不受 BuildConfig.DEBUG 限制，仅由 DebugPrefs 的 WebView 日志开关控制，
    // 让「调试模式 → 开启 WebView 日志」能收集到应用内（含源层/网络）的日志与异常。
    if (this is Throwable) DebugLogCollector.e("App", tag, this)
    else DebugLogCollector.d("App", tag, fullMsg)
    return this
}
