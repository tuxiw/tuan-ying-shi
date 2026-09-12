package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.local.entity.FavoriteEntity
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.util.SourceMode
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun observeFavorites(): Flow<List<FavoriteEntity>>
    fun observeIsFavorite(detailUrl: String): Flow<Boolean>
    suspend fun isFavorite(detailUrl: String): Boolean
    suspend fun toggle(anime: Anime, mode: SourceMode)
    suspend fun add(anime: Anime, mode: SourceMode)
    suspend fun remove(detailUrl: String)
}
