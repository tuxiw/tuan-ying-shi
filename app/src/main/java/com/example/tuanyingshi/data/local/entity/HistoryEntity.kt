package com.example.tuanyingshi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tuanyingshi.util.HISTORY_TABLE

@Entity(
    tableName = HISTORY_TABLE,
    indices = [Index("detail_url", unique = true)]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "history_id") val historyId: Long = 0L,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "img_url") val imgUrl: String,
    @ColumnInfo(name = "detail_url") val detailUrl: String,
    @ColumnInfo(name = "source") val source: String,
    /** 使用的具体数据源名称（如「叽哔动漫」「次元城」），供浏览历史页展示来源。 */
    @ColumnInfo(name = "source_name") val sourceName: String = "",
    @ColumnInfo(name = "last_episode_name") val lastEpisodeName: String = "",
    @ColumnInfo(name = "last_episode_url") val lastEpisodeUrl: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
