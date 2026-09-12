package com.example.tuanyingshi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tuanyingshi.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favourite_table ORDER BY created_at DESC")
    fun observe(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourite_table WHERE detail_url = :detailUrl)")
    suspend fun exists(detailUrl: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favourite_table WHERE detail_url = :detailUrl)")
    fun observeExists(detailUrl: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("SELECT * FROM favourite_table")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("DELETE FROM favourite_table WHERE detail_url = :detailUrl")
    suspend fun delete(detailUrl: String)
}
