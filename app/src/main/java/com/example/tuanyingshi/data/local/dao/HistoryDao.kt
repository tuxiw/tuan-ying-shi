package com.example.tuanyingshi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tuanyingshi.data.local.entity.EpisodeEntity
import com.example.tuanyingshi.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_table ORDER BY updated_at DESC")
    fun observe(): Flow<List<HistoryEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM history_table WHERE detail_url = :detailUrl)")
    fun observeExists(detailUrl: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity): Long

    @Query("SELECT * FROM history_table")
    suspend fun getAll(): List<HistoryEntity>

    @Query("SELECT * FROM episode_table WHERE history_id = :historyId")
    suspend fun getEpisodes(historyId: Long): List<EpisodeEntity>

    @Query("UPDATE history_table SET updated_at = :time WHERE detail_url = :detailUrl")
    suspend fun updateDate(detailUrl: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE history_table SET last_episode_name = :name, last_episode_url = :url WHERE detail_url = :detailUrl")
    suspend fun updateEpisode(detailUrl: String, name: String, url: String)

    @Query("DELETE FROM history_table WHERE detail_url = :detailUrl")
    suspend fun delete(detailUrl: String)

    @Query("DELETE FROM history_table")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(entity: EpisodeEntity)

    @Query("SELECT * FROM episode_table WHERE history_id = (SELECT history_id FROM history_table WHERE detail_url = :detailUrl)")
    fun observeEpisodes(detailUrl: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episode_table WHERE episode_url = :episodeUrl LIMIT 1")
    fun observeEpisode(episodeUrl: String): Flow<EpisodeEntity?>
}
