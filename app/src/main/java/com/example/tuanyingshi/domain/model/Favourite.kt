package com.example.tuanyingshi.domain.model

import com.example.tuanyingshi.util.SourceMode

/**
 * 收藏——按 detailUrl 唯一（对标 LaQoo Favourite）。
 */
data class Favourite(
    val title: String,
    val detailUrl: String,
    val imgUrl: String,
    val sourceMode: SourceMode
)
