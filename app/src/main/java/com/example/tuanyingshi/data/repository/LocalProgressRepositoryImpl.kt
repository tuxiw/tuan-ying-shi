package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.local.dao.LocalProgressDao
import com.example.tuanyingshi.data.local.entity.LocalPlayProgressEntity
import javax.inject.Inject

class LocalProgressRepositoryImpl @Inject constructor(
    private val dao: LocalProgressDao,
) : LocalProgressRepository {

    override suspend fun getPosition(key: String): Long =
        runCatching { dao.getPosition(key) }.getOrNull() ?: 0L

    override suspend fun save(key: String, name: String, position: Long, duration: Long) {
        if (key.isBlank()) return
        // 与在线一致：观看不足 5s 不记，避免把续播位置洗成 0。
        if (position < 5_000L) return
        runCatching {
            // 已接近片尾（末尾 10s 内）视为看完：删除记录，下次从头播放，不再提示「继续播放」。
            if (duration > 0L && position >= duration - 10_000L) {
                dao.delete(key)
            } else {
                dao.upsert(
                    LocalPlayProgressEntity(
                        filePath = key,
                        name = name,
                        lastPosition = position,
                        duration = duration,
                    ),
                )
            }
        }
    }

    override suspend fun remove(key: String) {
        if (key.isBlank()) return
        runCatching { dao.delete(key) }
    }
}
