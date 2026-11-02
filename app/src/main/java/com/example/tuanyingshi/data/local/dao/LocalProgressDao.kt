package com.example.tuanyingshi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tuanyingshi.data.local.entity.LocalPlayProgressEntity

@Dao
interface LocalProgressDao {
    /** 取某本地/已下载视频的续播进度（毫秒）；无记录返回 null。 */
    @Query("SELECT last_position FROM local_play_progress WHERE file_path = :key LIMIT 1")
    suspend fun getPosition(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalPlayProgressEntity)

    @Query("DELETE FROM local_play_progress WHERE file_path = :key")
    suspend fun delete(key: String)
}
