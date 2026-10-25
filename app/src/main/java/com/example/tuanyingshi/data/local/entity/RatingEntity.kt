package com.example.tuanyingshi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tuanyingshi.util.RATING_TABLE

/**
 * 本地评分记录（非后端模式下的「我的评分」）。
 * 以 detail_url 唯一键，重复评分覆盖；星级口径与详情页一致（1-5 星）。
 */
@Entity(
    tableName = RATING_TABLE,
    indices = [Index("detail_url", unique = true)],
)
data class RatingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rating_id") val ratingId: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "detail_url") val detailUrl: String,
    @ColumnInfo(name = "img_url") val imgUrl: String = "",
    @ColumnInfo(name = "rating") val rating: Float,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
