package com.example.tuanyingshi.domain.model

/**
 * 首页区块（对标 LaQoo Home）。
 */
data class Home(
    val title: String,
    val animeList: List<Anime>,
    val banners: List<HomeBanner> = emptyList(),
)

/**
 * 首页轮播图条目。
 */
data class HomeBanner(
    val title: String,
    val imageUrl: String,
    val actionType: String?,
    val actionValue: String?,
)
