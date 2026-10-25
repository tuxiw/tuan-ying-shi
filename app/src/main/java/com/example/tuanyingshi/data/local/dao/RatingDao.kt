package com.example.tuanyingshi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tuanyingshi.data.local.entity.RatingEntity
import kotlinx.coroutines.flow.Flow

/** 本地评分（我的评分）。 */
@Dao
interface RatingDao {
    @Query("SELECT * FROM rating_table ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<RatingEntity>>

    @Query("SELECT * FROM rating_table WHERE detail_url = :detailUrl LIMIT 1")
    suspend fun get(detailUrl: String): RatingEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM rating_table WHERE detail_url = :detailUrl)")
    suspend fun exists(detailUrl: String): Boolean

    /** 重复评分覆盖同 url 的旧记录。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RatingEntity)

    @Query("DELETE FROM rating_table WHERE detail_url = :detailUrl")
    suspend fun delete(detailUrl: String)

    @Query("DELETE FROM rating_table")
    suspend fun deleteAll()
}
