package com.example.tuanyingshi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tuanyingshi.util.USER_ANIME_STATUS_TABLE

/**
 * 用户对番剧的自定义状态（目前用于「抛弃」）。
 * 以 detail_url 唯一键，便于「不显示看过和抛弃的条目」从列表剔除。
 */
@Entity(
    tableName = USER_ANIME_STATUS_TABLE,
    indices = [Index("detail_url", unique = true)],
)
data class UserAnimeStatusEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "status_id") val statusId: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "detail_url") val detailUrl: String,
    @ColumnInfo(name = "img_url") val imgUrl: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
