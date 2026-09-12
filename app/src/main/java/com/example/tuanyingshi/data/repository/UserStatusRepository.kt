package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.local.dao.UserStatusDao
import com.example.tuanyingshi.data.local.entity.UserAnimeStatusEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户对番剧的自定义状态仓储（目前承载「抛弃」）。
 * 「看过」状态由 HistoryRepository 的历史记录推导，不在此表。
 */
@Singleton
class UserStatusRepository @Inject constructor(
    private val dao: UserStatusDao,
) {
    /** 全部抛弃记录。 */
    fun observeAllAbandoned(): Flow<List<UserAnimeStatusEntity>> = dao.observeAll()

    /** 某番剧是否已被抛弃。 */
    fun observeAbandoned(detailUrl: String): Flow<Boolean> = dao.observeExists(detailUrl)

    /** 抛弃某番剧（已存在则更新时间）。 */
    suspend fun setAbandoned(detailUrl: String, title: String, imgUrl: String) {
        dao.insert(UserAnimeStatusEntity(title = title, detailUrl = detailUrl, imgUrl = imgUrl))
    }

    /** 取消抛弃。 */
    suspend fun removeAbandoned(detailUrl: String) = dao.delete(detailUrl)

    suspend fun clearAll() = dao.deleteAll()
}
