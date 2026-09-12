package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.local.dao.FavoriteDao
import com.example.tuanyingshi.data.local.entity.FavoriteEntity
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.data.remote.backend.FavoriteRequestDTO
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.SourceMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepositoryImpl @Inject constructor(private val dao: FavoriteDao) : FavoriteRepository {
    override fun observeFavorites(): Flow<List<FavoriteEntity>> = dao.observe()

    override fun observeIsFavorite(detailUrl: String): Flow<Boolean> = dao.observeExists(detailUrl)

    override suspend fun isFavorite(detailUrl: String): Boolean = dao.exists(detailUrl)

    override suspend fun toggle(anime: Anime, mode: SourceMode) {
        if (dao.exists(anime.detailUrl)) {
            remove(anime.detailUrl)
        } else {
            add(anime, mode)
        }
    }

    override suspend fun add(anime: Anime, mode: SourceMode) {
        // 本地写入：保证详情页收藏星标、本地观察者立即一致。
        dao.insert(FavoriteEntity(
            title = anime.title,
            detailUrl = anime.detailUrl,
            imgUrl = anime.img,
            source = mode.name,
        ))
        // 后端模式：同步到自建后端（幂等覆盖），失败不影响本地。
        if (BackendPrefs.isBackendMode()) {
            runCatching {
                BackendClient.api.addFavorite(FavoriteRequestDTO(
                    animeId = anime.detailUrl.toLongOrNull(),
                    detailUrl = null,
                    title = anime.title,
                    imgUrl = anime.img,
                ))
            }
        }
    }

    override suspend fun remove(detailUrl: String) {
        dao.delete(detailUrl)
        if (BackendPrefs.isBackendMode()) {
            runCatching {
                BackendClient.api.deleteFavorite(
                    animeId = detailUrl.toLongOrNull(),
                    detailUrl = null,
                )
            }
        }
    }
}
