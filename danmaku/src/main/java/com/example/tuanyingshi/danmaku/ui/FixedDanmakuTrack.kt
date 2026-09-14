package com.example.tuanyingshi.danmaku.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/**
 * FixedDanmakuTrack 中的弹幕在以下情况会移除:
 * - tick 中的逻辑帧检测
 * - [FixedDanmakuTrack.place] 覆盖了正在显示的弹幕
 * - 调用 [DanmakuTrack.clearAll]
 * 移除时必须调用 [onRemoveDanmaku] 避免内存泄露.
 */
@Stable
internal class FixedDanmakuTrack<T : SizeSpecifiedDanmaku>(
    val trackIndex: Int,
    val fromBottom: Boolean,
    private val elapsedFrameTimeNanos: () -> Long,
    private val trackHeight: Int,
    private val trackWidth: Int,
    private val hostHeight: Int,
    private val durationMillis: Long, //弹幕的显示时间，目前还不能自定义
    private val onRemoveDanmaku: (FixedDanmaku<T>) -> Unit //某个弹幕需要消失时，必须调用此函数以避免内存泄漏.
) : DanmakuTrack<T, FixedDanmaku<T>> {

    internal var currentDanmaku: FixedDanmaku<T>? = null  // 当前显示的弹幕

    /**
     * 将新的弹幕放置在轨道上，如果已经有弹幕显示，则移除旧的弹幕.
     *
     * @param danmaku 要放置的弹幕对象
     * @return 新的 FixedDanmaku 对象
     */
    override fun place(danmaku: T): FixedDanmaku<T> = place(danmaku, 0L)

    /**
     * 放置一条「已经显示过 [elapsedMillis]」的顶部/底部弹幕，专供 seek 后的重新装填使用。
     * 普通发送传 [elapsedMillis] = 0，等价于原来的行为。
     */
    internal fun place(danmaku: T, elapsedMillis: Long): FixedDanmaku<T> {
        val upcomingDanmaku = FixedDanmaku(
            danmaku,
            elapsedFrameTimeNanos() - elapsedMillis * 1_000_000L,
            trackIndex,
            trackHeight,
            trackWidth,
            hostHeight,
            fromBottom
        )
        currentDanmaku?.let(onRemoveDanmaku)  // 如果有当前弹幕，移除它
        currentDanmaku = upcomingDanmaku  // 设置新的弹幕为当前弹幕
        return upcomingDanmaku
    }

    /**
     * 重新装填专用：按剩余显示时长放回顶部/底部弹幕。
     * 已经显示满 [durationMillis] 的（剩余时长为 0 或负数）直接丢弃，不占轨道。
     */
    internal fun tryPlaceProgressed(danmaku: T, elapsedMillis: Long): FixedDanmaku<T>? {
        if (elapsedMillis >= durationMillis) return null
        if (!canPlace(danmaku)) return null
        return place(danmaku, elapsedMillis)
    }

    /**
     * 检查是否可以在轨道上放置新的弹幕.
     *
     * @param danmaku 要放置的弹幕对象
     * @return `true` 如果可以放置，否则 `false`
     */
    override fun canPlace(danmaku: T): Boolean {
        return currentDanmaku == null
    }

    /**
     * 清除轨道上的所有弹幕.
     */
    override fun clearAll() {
        currentDanmaku?.let(onRemoveDanmaku)  // 如果有当前弹幕，移除它
        currentDanmaku = null  // 清空当前弹幕
    }

    /**
     * 在每一帧更新时调用此方法，检查并移除已显示超过 [durationMillis] 的弹幕.
     */
    override fun tick() {
        val current = currentDanmaku ?: return
        val danmakuTime = current.placeFrameTimeNanos
        if (elapsedFrameTimeNanos() - danmakuTime >= durationMillis * 1_000_000) {
            onRemoveDanmaku(current)
            currentDanmaku = null
        }
    }

    override fun toString(): String {
        return "FixedTrack(index=${trackIndex}, " +
                "placeTime=${currentDanmaku?.placeFrameTimeNanos?.div(1_000_000)})"
    }
}

@Immutable
internal class FixedDanmaku<T : SizeSpecifiedDanmaku>(
    val danmaku: T,
    val placeFrameTimeNanos: Long,
    private val trackIndex: Int,
    private val trackHeight: Int,
    private val trackWidth: Int,
    private val hostHeight: Int,
    private val fromBottom: Boolean,
) {
    /**
     * 弹幕在屏幕上的 Y 坐标位置，由轨道高度和轨道索引计算得出.
     * 这是一个懒加载属性，仅在第一次访问时计算.
     */
    val screenPosY: Float by lazy { calculatePosY() }

    /**
     * 弹幕在屏幕上的 X 坐标位置. 计算方式为将弹幕水平居中.
     */
    val screenPosX: Float = (trackWidth - danmaku.danmakuWidth) / 2f

    /**
     * 计算弹幕在屏幕上的 Y 坐标位置.
     *
     * @return 计算后的 Y 坐标位置
     */
    private fun calculatePosY(): Float {
        return if (fromBottom) {
            hostHeight - (trackIndex + 1) * trackHeight.toFloat()
        } else {
            trackIndex * trackHeight.toFloat()
        }
    }

    override fun toString(): String {
        return "FixedDanmaku(width=${danmaku.danmakuWidth}, y=$screenPosY)"
    }
}