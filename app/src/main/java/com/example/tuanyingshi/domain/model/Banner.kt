package com.example.tuanyingshi.domain.model

/**
 * 首页轮播图（团影视特有，LaQoo 无 Banner）。
 */
data class Banner(
    val id: String,
    val image: String,
    val title: String,
    val animeId: String,
)
