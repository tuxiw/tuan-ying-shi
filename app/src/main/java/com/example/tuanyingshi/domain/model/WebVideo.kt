package com.example.tuanyingshi.domain.model

/**
 * 解析后的视频直链（对标 LaQoo WebVideo）。
 */
data class WebVideo(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** 提供本视频流的来源标识（CSS 规则源 id / 内置源 id）。 */
    val sourceId: String = "",
    /** 提供本视频流的来源名（如某个 CSS 规则源名称 / "次元城"）。 */
    val sourceName: String = "",
    /** 来源图标 URL（CSS 规则源才有）。 */
    val iconUrl: String = "",
)
