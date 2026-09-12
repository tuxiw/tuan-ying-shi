package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.local.dao.DownloadGroupRow
import com.example.tuanyingshi.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeAll(): Flow<List<DownloadEntity>>
    suspend fun getAll(): List<DownloadEntity>
    suspend fun getByEpisode(episodeUrl: String): DownloadEntity?
    suspend fun getByDetailUrl(detailUrl: String): List<DownloadEntity>
    suspend fun getCompletedByDetailUrl(detailUrl: String): List<DownloadEntity>
    suspend fun getGroups(): List<DownloadGroupRow>
    suspend fun upsert(entity: DownloadEntity): Long
    suspend fun updateStatus(episodeUrl: String, status: Int, progress: Int)
    suspend fun updateStatusByDetailUrl(detailUrl: String, status: Int, progress: Int)

    /** 补填某番剧组的封面图（用于修复旧记录 anime_img 为空的情况）。 */
    suspend fun updateAnimeImgByDetailUrl(detailUrl: String, animeImg: String)
    suspend fun delete(episodeUrl: String)
    suspend fun deleteByDetailUrl(detailUrl: String)
    suspend fun deleteAll()
}
