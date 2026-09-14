package com.example.tuanyingshi.danmaku.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Source : https://github.com/open-ani/ani/blob/339fa3b41b20a2db951a130121f5611106f2c002/danmaku/api/src/commonMain/kotlin/DanmakuCollection.kt#L70
 */
interface DanmakuSession {
    val totalCount: Flow<Int?> get() = emptyFlow()

    /**
     * 创建一个随视频进度 [curTimeMillis] 匹配到的弹幕数据流.
     *
     * [curTimeMillis] 当前的视频播放进度
     * [isPlayingFlow] 表示当前视频是否处于播放/暂停状态，如果为暂停状态，将不会轮询发送弹幕
     */
    fun at(
        curTimeMillis: () -> Duration,
        isPlayingFlow: Flow<Boolean> = flowOf(true)
    ): Flow<DanmakuEvent>
}

sealed class DanmakuEvent {
    /**
     * 发送一个新弹幕
     */
    class Add(val danmaku: Danmaku) : DanmakuEvent()

    /**
     * 清空屏幕并以这些弹幕填充. 常见于快进/快退时
     *
     * @param list 按 [Danmaku.playTimeMillis] 升序（由旧到新）。
     *        消费方（[com.example.tuanyingshi.danmaku.ui.DanmakuHostState.repopulate]）会按每条弹幕自己的
     *        播放时间推算它此刻**本应处于**的位置后再装填，而不是让它们一起从屏幕右缘重新开始滚。
     * @param playTimeMillis 当前播放器的时间
     */
    data class Repopulate(val list: List<Danmaku>, val playTimeMillis: Long) : DanmakuEvent()
}

class TimeBasedDanmakuSession private constructor(
    /**
     * 一个[Danmaku] list. 必须根据 [DanmakuInfo.playTime] 排序且创建后不可更改，是一条动漫完整的弹幕列表.
     */
    private val list: List<Danmaku>,
    private val flowCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val tickDelayTimeMs: Long = 400, // 轮询要发送弹幕的间隔，单位[MillisSeconds]毫秒
) : DanmakuSession {
    override val totalCount: Flow<Int?> = flowOf(list.size)

    /**
     * 当前激活的轮询状态，由 [at] 在启动时注入。
     * [reset] 通过该引用在 seek 时清空“上一帧”状态，强制下一次轮询以新位置重排屏幕。
     */
    private var state: DanmakuSessionFlowState? = null

    companion object {
        fun create(
            sequence: Sequence<Danmaku>,
            coroutineContext: CoroutineContext = EmptyCoroutineContext,
        ): TimeBasedDanmakuSession {
            val list = sequence.mapTo(ArrayList()) { sanitize(it) }
            list.sortBy { it.playTimeMillis }
            return TimeBasedDanmakuSession(list, coroutineContext)
        }
    }

    /**
     * 接收一个视频的播放进度[Duration]. 和一个[List<DanmakuRegexFilter>]，根据视频进度和过滤后的弹幕列表，通过call [DanmakuSessionAlgorithm] 的 [tick] 函数发送弹幕
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun at(
        curTimeMillis: () -> Duration,
        isPlayingFlow: Flow<Boolean>
    ): Flow<DanmakuEvent> {
        if (list.isEmpty()) {
            return emptyFlow() // fast path
        }
        val newState = DanmakuSessionFlowState(
            list,
            repopulateThreshold = 3.seconds,
            repopulateDistance = { 10.seconds },
        )
        state = newState
        val algorithm = DanmakuSessionAlgorithm(newState)
        return isPlayingFlow.flatMapLatest { isPlaying ->
            channelFlow {
                // 一个单独协程收集当前进度
                launch(Dispatchers.Main) {
                    while (isActive && isPlaying) {
                        newState.curTimeShared = curTimeMillis()
                        delay(tickDelayTimeMs)
                    }
                }

                val sendItem: (DanmakuEvent) -> Boolean = {
                    trySend(it).isSuccess
                }

                while (isActive && isPlaying) { // 只有当视频播放时才进行轮询发送弹幕
                    algorithm.tick(sendItem)
                    delay(tickDelayTimeMs) // always check for cancellation
                }
            }.flowOn(flowCoroutineContext)
        }
    }

    /**
     * 通知弹幕引擎播放进度已被手动跳转（拖动进度条 / 点击进度条 / 快进快退按钮 / 长按倍速等任意 [player.seekTo]）。
     *
     * 默认实现仅靠“时间差 >= [DanmakuSessionFlowState.repopulateThreshold]”的隐式启发式来重排屏幕，
     * 对小于该阈值（默认 3 秒）的拖动完全无感知，导致：前进时重复叠加、后退时区域缺失、陈旧弹幕残留。
     *
     * 本方法显式清空内部“上一帧”状态（[DanmakuSessionFlowState.lastTime] 置为 [Duration.INFINITE]、
     * [DanmakuSessionFlowState.lastIndex] 归零），使下一次 [DanmakuSessionAlgorithm.tick] 必定走
     * Repopulate 分支：清空屏幕并以跳转后的新位置重新装填，彻底消除 seek 后的弹幕错乱。
     *
     * 应在 ExoPlayer 的 [androidx.media3.common.Player.Listener.onPositionDiscontinuity] 回调
     * （reason 为 SEEK / SEEK_ADJUSTMENT）中调用，传入跳转后的播放进度（毫秒）。
     */
    fun reset(curTimeMillis: Long) {
        val s = state ?: return
        s.curTimeShared = curTimeMillis.milliseconds
        s.lastTime = Duration.INFINITE // 下一次 tick 必走 Repopulate 分支
        s.lastIndex = -1
    }

    // 下面的算法有 bug, 而且会创建大量协程影响性能
    /*      var lastTime: Duration = Duration.ZERO
            var lastIndex = -1// last index at which we accessed [list]
            return progress.map { it - shiftMillis.milliseconds }
                .let {
                    if (samplePeriod == Duration.ZERO) it else it.sample(samplePeriod)
                }
                .transformLatest { curTime ->
                    if (curTime < lastTime) {
                        // Went back, reset position to the correct one
                        lastIndex = list.indexOfFirst { it.playTimeMillis >= curTime.inWholeMilliseconds } - 1
                        if (lastIndex == -2) {
                            lastIndex = -1
                        }
                    }

                    lastTime = curTime

                    val curTimeSecs = curTime.inWholeMilliseconds

                    for (i in (lastIndex + 1)..list.lastIndex) {
                        val item = list[i]
                        if (curTimeSecs >= item.playTimeMillis // 达到了弹幕发送的时间
                        ) {
                            if (curTimeSecs - item.playTimeMillis > 3000) {
                                // 只发送三秒以内的, 否则会导致快进之后发送大量过期弹幕
                                continue
                            }
                            lastIndex = i
                            emit(item) // Note: 可能会因为有新的 [curTime] 而 cancel
                        } else {
                            // not yet, 因为 list 是排序的, 这也说明后面的弹幕都还没到时间
                            break
                        }
                    }
                }
                .flowOn(coroutineContext) */
}

internal class DanmakuSessionFlowState(
    var list: List<Danmaku>,
    /**
     * 当前视频播放进度
     */
    @Volatile
    var curTimeShared: Duration = Duration.INFINITE,

    /**
     * 每当快进/快退超过这个阈值后, 重新装填整个屏幕弹幕
     */
    val repopulateThreshold: Duration = 3.seconds,
    /**
     * 重新装填屏幕弹幕时, 从当前时间开始往旧重新装填的距离. 例如当前时间为 15s, repopulateDistance 为 3s, 则会装填 12-15s 的弹幕
     * 需要根据屏幕宽度, 弹幕密度, 以及弹幕速度计算
     */
    val repopulateDistance: () -> Duration,
    /**
     * 重新装填屏幕弹幕时, 最多装填的弹幕数量
     */
    val repopulateMaxCount: Int = 40,
) {
    var lastTime: Duration = Duration.INFINITE

    /**
     * 最后成功发送了的弹幕的索引
     */
    var lastIndex = -1
}


/**
 * 弹幕装填算法的具体实现
 */
internal class DanmakuSessionAlgorithm(val state: DanmakuSessionFlowState) {
    /**
     * 对于每一个时间已经到达的弹幕, 并更新 [DanmakuSessionFlowState.lastIndex]
     */
    private inline fun useEachDanmaku(block: (Danmaku) -> Unit) {
        var i = state.lastIndex + 1
        val list = state.list
        try {
            while (i <= list.lastIndex) {
                block(list[i])
                i++
            }
            // 都发送成功了, 说明我们到了最后
        } finally {
            state.lastIndex = i - 1
        }
    }

    fun tick(sendEvent: (DanmakuEvent) -> Boolean) {
        val curTime = state.curTimeShared
        if (curTime == Duration.INFINITE) {
            return
        }
        val list = state.list

        try {
            if (state.lastTime == Duration.INFINITE // 第一帧
                || (curTime - state.lastTime).absoluteValue >= state.repopulateThreshold
            ) {
                // 移动太远, 重新装填屏幕弹幕
                // 初次播放如果进度不是在 0 也会触发这个
                val curTimeMillis = curTime.inWholeMilliseconds
                val targetTime = (curTime - state.repopulateDistance()).inWholeMilliseconds

                // 窗口起点：最后一个 playTime < targetTime 的索引（-1 表示窗口覆盖到列表开头）
                val windowStart = list
                    .binarySearchBy(targetTime, selector = { it.playTimeMillis })
                    .let {
                        if (it >= 0) {
                            if (list[it].playTimeMillis < targetTime) {
                                it + 1
                            } else it
                        } else -(it + 1) - 1
                    }
                    .coerceAtLeast(-1)

                // 收集窗口内、时间不晚于当前进度的弹幕（由旧到新）。
                // 注意：这里必须把 lastIndex 一路推进到「当前进度」，否则窗口计数超上限时提前
                // 结束会让上一帧的 Add 分支在下一帧把窗口里剩余的过去弹幕一次性全部补发出去，
                // 表现为「拖动进度条后弹幕瞬间糊满屏幕」。
                val window = ArrayList<Danmaku>()
                var i = windowStart + 1
                while (i <= list.lastIndex && list[i].playTimeMillis <= curTimeMillis) {
                    window.add(list[i])
                    i++
                }
                state.lastIndex = i - 1

                // 窗口命中过多时只保留距离当前进度最近的 repopulateMaxCount 条，
                // 其余更旧的弹幕即使重新装填也早已滚出屏幕，没必要发送。
                val overflow = window.size - state.repopulateMaxCount
                val candidates = if (overflow > 0) {
                    window.subList(overflow, window.size).toList()
                } else {
                    window
                }

                sendEvent(DanmakuEvent.Repopulate(candidates, curTimeMillis))
                return
            }
        } finally { // 总是更新
            state.lastTime = curTime
        }

        val curTimeMillis = curTime.inWholeMilliseconds

        useEachDanmaku { item ->
            if (curTimeMillis < item.playTimeMillis) {
                // 还没有达到弹幕发送时间, 因为 list 是排序的, 这也说明后面的弹幕都还没到时间
                return
            }
            if (!sendEvent(DanmakuEvent.Add(item))) { // Send Add Event
                return // 发送失败, 意味着 channel 满了, 即 flow collector 满了, 下一逻辑帧再尝试
            }
        }
    }

}

/**
 * Danmaku Sanitizer
 */
private fun sanitize(danmaku: Danmaku): Danmaku = danmaku.run {
    if (text.indexOf("\n") == -1 && text.isNotEmpty()) return@run this

    copy(
        text = text
            .replace("\n\r", " ")
            .replace("\r\n", " ")
            .replace("\n", " ")
            .trim()
            .ifEmpty { " " },
    )
}
