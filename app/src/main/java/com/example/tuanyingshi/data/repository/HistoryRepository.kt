package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.local.entity.HistoryEntity
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.Episode
import com.example.tuanyingshi.util.SourceMode
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeHistory(): Flow<List<HistoryEntity>>
    fun observeExists(detailUrl: String): Flow<Boolean>
    suspend fun record(anime: Anime, episode: Episode, position: Long, mode: SourceMode)
    /** 仅当该详情尚无历史记录时写入一条浏览记录（不覆盖播放进度）。 */
    suspend fun recordBrowse(anime: Anime, mode: SourceMode)
    suspend fun remove(detailUrl: String)
    suspend fun deleteAll()
}
