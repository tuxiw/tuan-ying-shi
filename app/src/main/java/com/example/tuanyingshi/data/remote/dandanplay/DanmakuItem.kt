package com.example.tuanyingshi.data.remote.dandanplay

/**
 * 单条弹幕。时间单位秒，与播放器进度对齐；
 * [mode] 沿用弹弹play 约定（1=滚动，4=底部，5=顶部），渲染引擎后续可据此分流。
 */
data class DanmakuItem(
    val time: Double,
    val text: String,
    val color: String = "#FFFFFF",
    val mode: Int = 1,
)
