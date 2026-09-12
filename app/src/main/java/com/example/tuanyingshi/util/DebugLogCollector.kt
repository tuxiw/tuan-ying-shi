package com.example.tuanyingshi.util

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** 日志级别（与 Android Log 对齐，但更轻量）。 */
enum class DebugLogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

/** 单条调试日志。 */
data class DebugLogLine(
    val time: Long,
    val level: DebugLogLevel,
    val source: String,
    val tag: String,
    val message: String,
) {
    val timeText: String get() = TIME_FMT.format(Date(time))

    companion object {
        private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
}

/**
 * 调试日志收集器：在「调试模式 → 开启 WebView 日志」时，
 * 把应用内各处（WebView 控制台 / 网络拦截 / 源层异常）的日志收集进内存环形缓冲，
 * 供 DebugScreen 实时展示与导出。
 *
 * - 仅当 [enabled] 为真时才落盘（写入内存），关闭后不产生任何开销；
 * - 内部用 CopyOnWriteArrayList 保证多线程（主线程 / IO 协程）安全；
 * - 容量上限 [MAX_LINES]，超出 FIFO 淘汰。
 */
object DebugLogCollector {
    private const val MAX_LINES = 2000

    private val _lines = CopyOnWriteArrayList<DebugLogLine>()
    private val _logFlow = MutableStateFlow<List<DebugLogLine>>(emptyList())
    val logs: StateFlow<List<DebugLogLine>> = _logFlow.asStateFlow()

    /** 视频匹配专用日志（加载 / 预匹配 / 切集 / 换源等），独立于通用日志，供调试页单独展示与导出。 */
    private val _matchLines = CopyOnWriteArrayList<DebugLogLine>()
    private val _matchFlow = MutableStateFlow<List<DebugLogLine>>(emptyList())
    val matchLogs: StateFlow<List<DebugLogLine>> = _matchFlow.asStateFlow()

    @Volatile
    private var enabled = false

    /** 由 DebugPrefs 在开关变化时调用。 */
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) clear()
    }

    fun isEnabled(): Boolean = enabled

    fun clear() {
        _lines.clear()
        _logFlow.value = emptyList()
        _matchLines.clear()
        _matchFlow.value = emptyList()
    }

    /** 仅清空视频匹配日志（不影响通用日志）。 */
    fun clearMatch() {
        _matchLines.clear()
        _matchFlow.value = emptyList()
    }

    fun add(level: DebugLogLevel, source: String, tag: String, message: String) {
        if (!enabled) return
        // 过滤不可打印字符（WAF 乱码 / 二进制片段），保留中文与 ASCII 可读部分
        val clean = message.filter {
            it.isWhitespace() || it.code in 32..126 || it.code in 0x4E00..0x9FFF
        }
        if (clean.isBlank()) return
        _lines.add(DebugLogLine(System.currentTimeMillis(), level, source, tag, clean.take(4000)))
        while (_lines.size > MAX_LINES) {
            _lines.removeAt(0)
        }
        _logFlow.value = _lines.toList()
    }

    fun v(source: String, tag: String, msg: String) = add(DebugLogLevel.VERBOSE, source, tag, msg)
    fun d(source: String, tag: String, msg: String) = add(DebugLogLevel.DEBUG, source, tag, msg)
    fun i(source: String, tag: String, msg: String) = add(DebugLogLevel.INFO, source, tag, msg)
    fun w(source: String, tag: String, msg: String) = add(DebugLogLevel.WARN, source, tag, msg)
    fun e(source: String, tag: String, msg: String) = add(DebugLogLevel.ERROR, source, tag, msg)

    /**
     * 写入「视频匹配日志」（source 固定为 "Match"，tag 由调用方指定）。
     * 清洗 / 截断 / FIFO 逻辑与 [add] 一致；同样受 [enabled] 开关控制。
     */
    fun addMatch(level: DebugLogLevel, tag: String, message: String) {
        if (!enabled) return
        val clean = message.filter {
            it.isWhitespace() || it.code in 32..126 || it.code in 0x4E00..0x9FFF
        }
        if (clean.isBlank()) return
        _matchLines.add(DebugLogLine(System.currentTimeMillis(), level, "Match", tag, clean.take(4000)))
        while (_matchLines.size > MAX_LINES) {
            _matchLines.removeAt(0)
        }
        _matchFlow.value = _matchLines.toList()
    }

    fun matchD(tag: String, msg: String) = addMatch(DebugLogLevel.DEBUG, tag, msg)
    fun matchI(tag: String, msg: String) = addMatch(DebugLogLevel.INFO, tag, msg)
    fun matchW(tag: String, msg: String) = addMatch(DebugLogLevel.WARN, tag, msg)
    fun matchE(tag: String, msg: String) = addMatch(DebugLogLevel.ERROR, tag, msg)

    /** 把异常栈转成 ERROR 行。 */
    fun e(source: String, tag: String, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val stack = sw.toString().lineSequence().take(30).joinToString("\n")
        e(source, tag, "${t.message ?: t.javaClass.simpleName}\n$stack")
    }

    /** 返回一个可复用的 WebChromeClient，把页面内 console.* 输出收集进日志（source=WebView）。 */
    fun consoleClient(): WebChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
            message ?: return super.onConsoleMessage(message)
            val level = when (message.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> DebugLogLevel.ERROR
                ConsoleMessage.MessageLevel.WARNING -> DebugLogLevel.WARN
                ConsoleMessage.MessageLevel.LOG -> DebugLogLevel.DEBUG
                ConsoleMessage.MessageLevel.DEBUG -> com.example.tuanyingshi.util.DebugLogLevel.VERBOSE
                else -> DebugLogLevel.INFO
            }
            val src = message.sourceId()?.let { shortUrl(it) } ?: "page"
            add(level, "WebView", src, message.message())
            return super.onConsoleMessage(message)
        }
    }

    private fun shortUrl(url: String): String {
        val q = url.indexOf('?')
        return if (q > 0) url.substring(0, q) else url
    }
}
