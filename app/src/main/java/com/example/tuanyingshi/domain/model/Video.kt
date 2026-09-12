package com.example.tuanyingshi.domain.model

/**
 * 播放器完整上下文（对标 LaQoo Video）。
 */
data class Video(
    val title: String,
    val url: String,
    val episodeName: String,
    val episodeUrl: String,
    val lastPlayPosition: Long = 0L,
    val currentEpisodeIndex: Int,
    val episodes: List<Episode>,
    val headers: Map<String, String> = emptyMap(),
    /** 提供本集播放流的来源标识（CSS 规则源 id / 内置源 id），空表示未知。 */
    val sourceId: String = "",
    /** 提供本集播放流的来源名（如某个 CSS 规则源名称 / "次元城"），用于播放器"来源"展示。 */
    val sourceName: String = "",
    /** 来源图标 URL（CSS 规则源才有），空表示使用默认占位图标。 */
    val iconUrl: String = "",
)
