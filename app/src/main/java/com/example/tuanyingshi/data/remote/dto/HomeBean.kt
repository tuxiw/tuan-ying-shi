package com.example.tuanyingshi.data.remote.dto

import com.example.tuanyingshi.domain.model.Home
import com.example.tuanyingshi.domain.model.HomeBanner

data class HomeBean(
    val title: String,
    val moreUrl: String = "",
    val animes: List<AnimeBean>,
    val banners: List<HomeBannerBean> = emptyList(),
) {
    fun toHome(): Home {
        val homeItems = animes.map { it.toAnime() }
        val bannerItems = banners.map { it.toHomeBanner() }
        return Home(title = title, animeList = homeItems, banners = bannerItems)
    }
}

data class HomeBannerBean(
    val title: String,
    val imageUrl: String,
    val actionType: String? = null,
    val actionValue: String? = null,
) {
    fun toHomeBanner(): HomeBanner {
        return HomeBanner(
            title = title,
            imageUrl = imageUrl,
            actionType = actionType,
            actionValue = actionValue,
        )
    }
}
