package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.local.dao.HistoryDao
import com.example.tuanyingshi.data.local.entity.EpisodeEntity
import com.example.tuanyingshi.data.local.entity.HistoryEntity
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.data.remote.backend.HistoryRequestDTO
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.Episode
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.SourceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(private val dao: HistoryDao) : HistoryRepository {
    override fun observeHistory(): Flow<List<HistoryEntity>> = dao.observe()

    override fun observeExists(detailUrl: String): Flow<Boolean> = dao.observeExists(detailUrl)

    override suspend fun record(
        anime: Anime,
        episode: Episode,
        position: Long,
        mode: SourceMode,
        sourceName: String,
        syncToBackend: Boolean,
    ) {
        // 本地写入（position 为毫秒，本地库原样保存）。
        // 历史与集进度必须一次写完（见 HistoryDao.recordEpisode 注释）：分两次事务时，
        // 并发的进度保存会互相把对方的 history_id 顶掉，导致外键约束失败并崩溃。
        // 写进度属于「锦上添花」，任何残留异常都不该把 App 打挂，故兜底捕获并记日志。
        runCatching {
            dao.recordEpisode(
                history = HistoryEntity(
                    title = anime.title,
                    imgUrl = anime.img,
                    detailUrl = anime.detailUrl,
                    source = mode.name,
                    sourceName = sourceName,
                    lastEpisodeName = episode.name,
                    lastEpisodeUrl = episode.url,
                ),
                episode = EpisodeEntity(
                    historyId = 0L, // 由事务内按 detail_url 解析出的真实 id 覆盖
                    name = episode.name,
                    episodeUrl = episode.url,
                    lastPosition = position,
                ),
            )
        }.onFailure { e ->
            android.util.Log.w(
                "HistoryRepo",
                "写入观看记录失败：${anime.detailUrl} / ${episode.url}",
                e,
            )
        }
        // 后端模式：上报播放进度（后端要求秒，故毫秒 → 秒）。周期保存时跳过，避免高频调用。
        if (syncToBackend) {
            syncHistory(anime, episode.name, episode.url, (position / 1000).toInt(), mode)
        }
    }

    override suspend fun recordBrowse(anime: Anime, mode: SourceMode, sourceName: String) {
        if (dao.observeExists(anime.detailUrl).first()) return
        dao.upsertKeepId(
            HistoryEntity(
                title = anime.title,
                imgUrl = anime.img,
                detailUrl = anime.detailUrl,
                source = mode.name,
                sourceName = sourceName,
            )
        )
        // 后端模式：同步一条浏览记录（无进度，仅标记看过）。
        syncHistory(anime, null, null, null, mode)
    }

    override suspend fun getEpisodePosition(episodeUrl: String): Long =
        dao.getEpisodePosition(episodeUrl) ?: 0L

    override suspend fun remove(detailUrl: String) {
        dao.delete(detailUrl)
        if (BackendPrefs.isBackendMode()) {
            runCatching {
                BackendClient.api.deleteHistory(
                    animeId = detailUrl.toLongOrNull(),
                    detailUrl = null,
                )
            }
        }
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
        if (BackendPrefs.isBackendMode()) {
            runCatching { BackendClient.api.clearHistory() }
        }
    }

    /** 后端模式下上报观看记录（幂等覆盖）；本地始终已写入，故失败不影响本地展示。 */
    private suspend fun syncHistory(anime: Anime, episodeName: String?, episodeUrl: String?, positionSec: Int?, mode: SourceMode) {
        if (!BackendPrefs.isBackendMode()) return
        val animeId = anime.detailUrl.toLongOrNull()
        // 仅当确有可上报内容（有标题，或有播放进度）时才打后端，避免无意义的空浏览记录。
        if (anime.title.isBlank() && episodeName == null && positionSec == null) return
        // addHistory 是 suspend 调用，需在 suspend 上下文中直接调用（不可包在 runCatching 的非挂起 lambda 内）。
        try {
            BackendClient.api.addHistory(HistoryRequestDTO(
                animeId = animeId,
                detailUrl = null,
                title = anime.title,
                imgUrl = anime.img,
                position = positionSec,
                lastEpisodeName = episodeName,
                lastEpisodeUrl = episodeUrl,
                sourceId = mode.name,
                sourceMode = mode.name,
            ))
        } catch (e: Exception) {
            // 云端上报失败不影响本地已写入的历史。
        }
    }
}
