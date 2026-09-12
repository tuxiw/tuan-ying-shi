package com.example.tuanyingshi.domain.model

import kotlinx.serialization.Serializable

/**
 * 选集——url 可能是直链也可能是剧集页（需二次解析 getVideoData）。
 * 内嵌播放进度，用于历史记录与记忆播放（对标 LaQoo Episode）。
 */
@Serializable
data class Episode(
    val name: String,
    val url: String,
    val lastPlayPosition: Long = 0L,
    val isPlayed: Boolean = false,
    val isDownloaded: Boolean = false,
    val historyId: Long = 0L,
)
