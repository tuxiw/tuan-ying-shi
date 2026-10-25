package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.local.dao.RatingDao
import com.example.tuanyingshi.data.local.entity.RatingEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地评分仓储（Room）：承载非后端模式（纯本地数据源）下的「我的评分」。
 * 后端模式的评分走后端 API，不走这里。
 */
@Singleton
class UserRatingRepository @Inject constructor(
    private val dao: RatingDao,
) {
    /** 全部本地评分（按评分时间倒序）。 */
    fun observeAll(): Flow<List<RatingEntity>> = dao.observeAll()

    /** 某番剧的本地评分（星级 1-5），不存在返回 null。 */
    suspend fun get(detailUrl: String): Float? = dao.get(detailUrl)?.rating

    /** 写入 / 覆盖评分（detail_url 唯一，重复评分会覆盖）。 */
    suspend fun save(detailUrl: String, title: String, imgUrl: String, rating: Float) {
        val now = System.currentTimeMillis()
        val exist = dao.get(detailUrl)
        dao.insert(
            RatingEntity(
                title = title,
                detailUrl = detailUrl,
                imgUrl = imgUrl,
                rating = rating,
                createdAt = exist?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /** 删除某番剧评分。 */
    suspend fun remove(detailUrl: String) = dao.delete(detailUrl)

    suspend fun clearAll() = dao.deleteAll()
}
