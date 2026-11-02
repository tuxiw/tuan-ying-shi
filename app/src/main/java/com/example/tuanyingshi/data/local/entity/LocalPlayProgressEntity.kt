package com.example.tuanyingshi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.tuanyingshi.util.LOCAL_PLAY_PROGRESS_TABLE

/**
 * 本地 / 已下载视频的播放进度（续播位置）。
 *
 * 与在线观看的 [EpisodeEntity] 完全独立：本地文件没有对应的在线 history 行，
 * 用 [EpisodeEntity] 会因外键约束失败且会污染在线观看历史，故单独成表，按文件绝对路径（或播放用的稳定 url）作主键。
 * 这样既覆盖「已下载番剧组」也覆盖「独立本地文件」，且不会触发任何后端同步。
 */
@Entity(tableName = LOCAL_PLAY_PROGRESS_TABLE)
data class LocalPlayProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "last_position")
    val lastPosition: Long = 0L,
    @ColumnInfo(name = "duration")
    val duration: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
