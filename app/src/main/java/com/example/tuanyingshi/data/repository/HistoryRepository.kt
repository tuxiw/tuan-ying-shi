package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.local.entity.HistoryEntity
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.Episode
import com.example.tuanyingshi.util.SourceMode
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeHistory(): Flow<List<HistoryEntity>>
    fun observeExists(detailUrl: String): Flow<Boolean>
    suspend fun record(
        anime: Anime,
        episode: Episode,
        position: Long,
        mode: SourceMode,
        sourceName: String = "",
        /** 为 false 时只写本地库、不上报后端（用于播放中周期保存，避免后端进度接口被高频调用）。 */
        syncToBackend: Boolean = true,
    )
    /** 仅当该详情尚无历史记录时写入一条浏览记录（不覆盖播放进度）。 */
    suspend fun recordBrowse(anime: Anime, mode: SourceMode, sourceName: String = "")
    /** 取某集已保存的播放进度（毫秒）；无记录返回 0。供「记忆播放」续播定位。 */
    suspend fun getEpisodePosition(episodeUrl: String): Long
    suspend fun remove(detailUrl: String)
    suspend fun deleteAll()
}
