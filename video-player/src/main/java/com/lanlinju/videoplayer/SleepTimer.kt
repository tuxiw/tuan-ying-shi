package com.lanlinju.videoplayer

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 定时关闭（睡眠定时器）。
 *
 * 全局单例而不是绑在某个播放页上：用户设置「30 分钟后关闭」后可能在播放页里换集、
 * 甚至从一个播放页跳到另一个播放页（本地播放 / 外链播放），计时都不该重置。
 * 真正的「到点暂停」由 [VideoPlayer] 里的协程统一执行——任何时刻只有一个播放器在跑。
 *
 * 用 [SystemClock.elapsedRealtime]（开机计时、不受用户改系统时间影响）而不是墙上时钟，
 * 避免用户/系统调整时间导致定时器提前或延后触发。
 */
object SleepTimer {
    private val _deadlineElapsedMs = MutableStateFlow<Long?>(null)

    /** 到期时刻（elapsedRealtime 毫秒）；null 表示未开启。 */
    val deadlineElapsedMs: StateFlow<Long?> = _deadlineElapsedMs.asStateFlow()

    /** 最近一次定时关闭是否已经触发（供 UI 提示 / 重置）。 */
    private val _fired = MutableStateFlow(false)
    val fired: StateFlow<Boolean> = _fired.asStateFlow()

    /** 开启定时关闭，[minutes] 分钟后暂停播放。 */
    fun start(minutes: Int) {
        if (minutes <= 0) return
        _fired.value = false
        _deadlineElapsedMs.value = SystemClock.elapsedRealtime() + minutes * 60_000L
    }

    /** 取消定时关闭。 */
    fun cancel() {
        _deadlineElapsedMs.value = null
        _fired.value = false
    }

    /** 距到期还剩多少毫秒；未开启返回 0。 */
    fun remainingMs(): Long {
        val deadline = _deadlineElapsedMs.value ?: return 0L
        return (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    /** 供播放器在到点后调用：清空截止时刻并标记已触发。 */
    fun markFired() {
        _deadlineElapsedMs.value = null
        _fired.value = true
    }
}

/** 把剩余毫秒转成「12:34」形式；不足 1 分钟时显示「<1 分钟」。 */
fun formatSleepRemaining(remainingMs: Long): String {
    if (remainingMs <= 0L) return "即将关闭"
    val totalSeconds = remainingMs / 1000L
    if (totalSeconds < 60L) return "<1 分钟"
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes >= 60L) {
        val hours = minutes / 60L
        val restMinutes = minutes % 60L
        "%d 小时 %02d 分".format(hours, restMinutes)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
