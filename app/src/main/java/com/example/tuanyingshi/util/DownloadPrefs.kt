package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 下载偏好：
 * - [concurrentDownloads]：同时下载的任务数（默认 3，范围 1..10）。
 *   通过 SharedPreferences 持久化，并用 StateFlow 暴露给下载设置页与下载队列。
 * - [threadPerTask]：单个任务的下载线程数（分片并发数，默认 4，范围 1..10）。
 */
object DownloadPrefs {
    private const val KEY_CONCURRENT = "download_concurrent_count"
    private const val KEY_THREADS = "download_thread_per_task"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _concurrentDownloads =
        MutableStateFlow(coerce(prefs.getInt(KEY_CONCURRENT, 3)))
    val concurrentDownloads: StateFlow<Int> = _concurrentDownloads.asStateFlow()

    private val _threadPerTask =
        MutableStateFlow(coerceThreads(prefs.getInt(KEY_THREADS, 4)))
    val threadPerTask: StateFlow<Int> = _threadPerTask.asStateFlow()

    fun getConcurrentDownloads(): Int = _concurrentDownloads.value

    fun setConcurrentDownloads(count: Int) {
        val c = coerce(count)
        prefs.edit().putInt(KEY_CONCURRENT, c).apply()
        _concurrentDownloads.value = c
    }

    /** 单个任务的下载线程数（分片并发数），默认 4，范围 1..10。 */
    fun getThreadPerTask(): Int = _threadPerTask.value

    fun setThreadPerTask(count: Int) {
        val c = coerceThreads(count)
        prefs.edit().putInt(KEY_THREADS, c).apply()
        _threadPerTask.value = c
    }

    /** 同时下载任务数限制在 1..10（滑动条档位）。 */
    private fun coerce(count: Int): Int = count.coerceIn(1, 10)

    /** 单任务线程数限制在 1..10（滑动条档位）。 */
    private fun coerceThreads(count: Int): Int = count.coerceIn(1, 10)
}
