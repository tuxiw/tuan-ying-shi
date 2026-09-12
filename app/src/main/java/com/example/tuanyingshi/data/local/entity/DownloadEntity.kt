package com.example.tuanyingshi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tuanyingshi.util.DOWNLOAD_TABLE

/**
 * 下载记录（对标 LaQoo 的 download_table）。
 * status: 0=排队中 1=下载中 2=已完成 3=失败。
 */
@Entity(
    tableName = DOWNLOAD_TABLE,
    indices = [Index("episode_url", unique = true)],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "anime_title") val animeTitle: String,
    @ColumnInfo(name = "anime_img") val animeImg: String = "",
    @ColumnInfo(name = "episode_name") val episodeName: String,
    @ColumnInfo(name = "detail_url") val detailUrl: String,
    @ColumnInfo(name = "episode_url") val episodeUrl: String,
    @ColumnInfo(name = "save_path") val savePath: String,
    @ColumnInfo(name = "save_name") val saveName: String,
    @ColumnInfo(name = "status") val status: Int,
    @ColumnInfo(name = "progress") val progress: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
