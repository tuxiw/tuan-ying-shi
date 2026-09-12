package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.local.dao.DownloadDao
import com.example.tuanyingshi.data.local.dao.DownloadGroupRow
import com.example.tuanyingshi.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(private val dao: DownloadDao) : DownloadRepository {
    override fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()
    override suspend fun getAll(): List<DownloadEntity> = dao.getAll()
    override suspend fun getByEpisode(episodeUrl: String): DownloadEntity? = dao.getByEpisode(episodeUrl)
    override suspend fun getByDetailUrl(detailUrl: String): List<DownloadEntity> = dao.getByDetailUrl(detailUrl)
    override suspend fun getCompletedByDetailUrl(detailUrl: String): List<DownloadEntity> =
        dao.getCompletedByDetailUrl(detailUrl)
    override suspend fun getGroups(): List<DownloadGroupRow> = dao.getGroups()
    override suspend fun upsert(entity: DownloadEntity) = dao.upsert(entity)
    override suspend fun updateStatus(episodeUrl: String, status: Int, progress: Int) =
        dao.updateStatus(episodeUrl, status, progress)
    override suspend fun updateStatusByDetailUrl(detailUrl: String, status: Int, progress: Int) =
        dao.updateStatusByDetailUrl(detailUrl, status, progress)

    override suspend fun updateAnimeImgByDetailUrl(detailUrl: String, animeImg: String) =
        dao.updateAnimeImgByDetailUrl(detailUrl, animeImg)
    override suspend fun delete(episodeUrl: String) = dao.delete(episodeUrl)
    override suspend fun deleteByDetailUrl(detailUrl: String) = dao.deleteByDetailUrl(detailUrl)
    override suspend fun deleteAll() = dao.deleteAll()
}
