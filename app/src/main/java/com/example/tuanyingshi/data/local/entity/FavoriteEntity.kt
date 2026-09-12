package com.example.tuanyingshi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tuanyingshi.util.FAVOURITE_TABLE
import com.example.tuanyingshi.util.SourceMode

@Entity(
    tableName = FAVOURITE_TABLE,
    indices = [Index("detail_url", unique = true)]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "favourite_id") val favouriteId: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "detail_url") val detailUrl: String,
    @ColumnInfo(name = "img_url") val imgUrl: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
