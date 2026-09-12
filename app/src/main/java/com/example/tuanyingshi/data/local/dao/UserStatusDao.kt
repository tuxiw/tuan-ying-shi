package com.example.tuanyingshi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tuanyingshi.data.local.entity.UserAnimeStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStatusDao {
    /** 全部「抛弃」记录（按抛弃时间倒序）。 */
    @Query("SELECT * FROM user_anime_status_table ORDER BY created_at DESC")
    fun observeAll(): Flow<List<UserAnimeStatusEntity>>

    /** 某番剧是否已被抛弃。 */
    @Query("SELECT EXISTS(SELECT 1 FROM user_anime_status_table WHERE detail_url = :detailUrl)")
    fun observeExists(detailUrl: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UserAnimeStatusEntity)

    @Query("SELECT * FROM user_anime_status_table")
    suspend fun getAll(): List<UserAnimeStatusEntity>

    @Query("DELETE FROM user_anime_status_table WHERE detail_url = :detailUrl")
    suspend fun delete(detailUrl: String)

    @Query("DELETE FROM user_anime_status_table")
    suspend fun deleteAll()
}
