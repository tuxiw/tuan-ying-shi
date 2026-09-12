package com.example.tuanyingshi.data.local.dao

import androidx.room.ColumnInfo

/**
 * [DownloadDao.getGroups] 的返回行：仅含分组所需的字段
 * （detail_url 作为分组键，anime_title / anime_img 用于卡片展示）。
 */
data class DownloadGroupRow(
    @ColumnInfo(name = "detail_url") val detailUrl: String,
    @ColumnInfo(name = "anime_title") val animeTitle: String,
    @ColumnInfo(name = "anime_img") val animeImg: String,
)
