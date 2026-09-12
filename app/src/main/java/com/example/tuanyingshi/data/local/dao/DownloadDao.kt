package com.example.tuanyingshi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tuanyingshi.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_table ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_table")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM download_table WHERE episode_url = :episodeUrl LIMIT 1")
    suspend fun getByEpisode(episodeUrl: String): DownloadEntity?

    @Query("SELECT * FROM download_table WHERE detail_url = :detailUrl ORDER BY created_at ASC")
    suspend fun getByDetailUrl(detailUrl: String): List<DownloadEntity>

    @Query("SELECT * FROM download_table WHERE detail_url = :detailUrl AND status = 2 ORDER BY created_at ASC")
    suspend fun getCompletedByDetailUrl(detailUrl: String): List<DownloadEntity>

    @Query("SELECT * FROM download_table WHERE status = 2 ORDER BY created_at DESC")
    suspend fun getCompleted(): List<DownloadEntity>

    /** 所有去重后的番剧（按 detail_url 分组，取最新一条的标题/封面）。 */
    @Query("SELECT detail_url, anime_title, anime_img FROM download_table GROUP BY detail_url ORDER BY MAX(created_at) DESC")
    suspend fun getGroups(): List<DownloadGroupRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity): Long

    @Query("UPDATE download_table SET status = :status, progress = :progress WHERE episode_url = :episodeUrl")
    suspend fun updateStatus(episodeUrl: String, status: Int, progress: Int)

    @Query("UPDATE download_table SET status = :status, progress = :progress WHERE detail_url = :detailUrl")
    suspend fun updateStatusByDetailUrl(detailUrl: String, status: Int, progress: Int)

    @Query("UPDATE download_table SET anime_img = :animeImg WHERE detail_url = :detailUrl")
    suspend fun updateAnimeImgByDetailUrl(detailUrl: String, animeImg: String)

    @Query("DELETE FROM download_table WHERE episode_url = :episodeUrl")
    suspend fun delete(episodeUrl: String)

    @Query("DELETE FROM download_table WHERE detail_url = :detailUrl")
    suspend fun deleteByDetailUrl(detailUrl: String)

    @Query("DELETE FROM download_table")
    suspend fun deleteAll()
}
