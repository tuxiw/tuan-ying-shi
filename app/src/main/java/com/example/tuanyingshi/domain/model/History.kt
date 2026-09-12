package com.example.tuanyingshi.domain.model

import com.example.tuanyingshi.util.SourceMode

/**
 * 播放历史——按 detailUrl 唯一，内嵌选集（对标 LaQoo History）。
 */
data class History(
    val title: String,
    val imgUrl: String,
    val detailUrl: String,
    val lastEpisodeName: String = "",
    val lastEpisodeUrl: String = "",
    val sourceMode: SourceMode,
    val time: String = "",
    val episodes: List<Episode> = emptyList()
)
