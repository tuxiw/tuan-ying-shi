package com.example.tuanyingshi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.tuanyingshi.data.local.entity.EpisodeEntity
import com.example.tuanyingshi.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_table ORDER BY updated_at DESC")
    fun observe(): Flow<List<HistoryEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM history_table WHERE detail_url = :detailUrl)")
    fun observeExists(detailUrl: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity): Long

    @Query("SELECT history_id FROM history_table WHERE detail_url = :detailUrl LIMIT 1")
    suspend fun findHistoryId(detailUrl: String): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(entity: HistoryEntity): Long

    @Update
    suspend fun updateHistory(entity: HistoryEntity)

    /**
     * 写入一条观看记录（历史 + 本集进度），**在同一个事务内**完成。
     *
     * 必须原子：播放页的进度保存有多个并发入口（15 秒周期保存 / 打开设置 / 退出 /
     * 切集 / 播放失败），两条独立事务一旦交错——A 取到 history_id=5，B 紧接着把这条历史
     * 换成了 id=6，A 再用 5 去插 episode_table——就会撞上
     * `SQLiteConstraintException: FOREIGN KEY constraint failed`（code 787）直接崩溃。
     */
    @Transaction
    suspend fun recordEpisode(history: HistoryEntity, episode: EpisodeEntity) {
        val id = upsertKeepId(history)
        insertEpisode(episode.copy(historyId = id))
    }

    /**
     * 存在则**原地 UPDATE**（保留 history_id），不存在才 INSERT。
     *
     * 不用 REPLACE：REPLACE 是「删除 + 新插」，会拿到一个全新的 history_id，
     * 既让调用方持有的 id 立刻失效，又会因外键 ON DELETE CASCADE 连带删掉该番所有集的
     * 进度行（而这里只会补回当前一集）。
     */
    @Transaction
    suspend fun upsertKeepId(entity: HistoryEntity): Long {
        val existing = findHistoryId(entity.detailUrl)
        return if (existing != null) {
            updateHistory(entity.copy(historyId = existing))
            existing
        } else {
            insertHistory(entity)
        }
    }

    @Query("SELECT * FROM history_table")
    suspend fun getAll(): List<HistoryEntity>

    @Query("SELECT * FROM episode_table WHERE history_id = :historyId")
    suspend fun getEpisodes(historyId: Long): List<EpisodeEntity>

    @Query("UPDATE history_table SET updated_at = :time WHERE detail_url = :detailUrl")
    suspend fun updateDate(detailUrl: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE history_table SET last_episode_name = :name, last_episode_url = :url WHERE detail_url = :detailUrl")
    suspend fun updateEpisode(detailUrl: String, name: String, url: String)

    @Query("DELETE FROM history_table WHERE detail_url = :detailUrl")
    suspend fun delete(detailUrl: String)

    @Query("DELETE FROM history_table")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(entity: EpisodeEntity)

    @Query("SELECT * FROM episode_table WHERE history_id = (SELECT history_id FROM history_table WHERE detail_url = :detailUrl)")
    fun observeEpisodes(detailUrl: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episode_table WHERE episode_url = :episodeUrl LIMIT 1")
    fun observeEpisode(episodeUrl: String): Flow<EpisodeEntity?>

    /** 取某集已保存的播放进度（毫秒）；无记录返回 null。供「记忆播放」续播定位。 */
    @Query("SELECT last_position FROM episode_table WHERE episode_url = :episodeUrl LIMIT 1")
    suspend fun getEpisodePosition(episodeUrl: String): Long?
}
