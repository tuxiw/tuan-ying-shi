package com.example.tuanyingshi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tuanyingshi.data.local.dao.DownloadDao
import com.example.tuanyingshi.data.local.dao.FavoriteDao
import com.example.tuanyingshi.data.local.dao.HistoryDao
import com.example.tuanyingshi.data.local.dao.RatingDao
import com.example.tuanyingshi.data.local.dao.UserStatusDao
import com.example.tuanyingshi.data.local.entity.DownloadEntity
import com.example.tuanyingshi.data.local.entity.EpisodeEntity
import com.example.tuanyingshi.data.local.entity.FavoriteEntity
import com.example.tuanyingshi.data.local.entity.HistoryEntity
import com.example.tuanyingshi.data.local.entity.RatingEntity
import com.example.tuanyingshi.data.local.entity.UserAnimeStatusEntity
import com.example.tuanyingshi.util.ANIME_DATABASE

/**
 * 3 → 4：下载表新增 anime_img（番剧封面）列，便于离线分组卡片展示封面。
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_table ADD COLUMN anime_img TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * 4 → 5：新增 user_anime_status_table（用户对番剧的自定义状态，承载「抛弃」）。
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_anime_status_table (
                status_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                detail_url TEXT NOT NULL,
                img_url TEXT NOT NULL,
                created_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_user_anime_status_table_detail_url " +
                "ON user_anime_status_table(detail_url)",
        )
    }
}

/**
 * 5 → 6：新增 rating_table（本地评分，非后端模式下的「我的评分」）。
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rating_table (
                rating_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                detail_url TEXT NOT NULL,
                img_url TEXT NOT NULL DEFAULT '',
                rating REAL NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_rating_table_detail_url " +
                "ON rating_table(detail_url)",
        )
    }
}

/**
 * 6 → 7：历史表新增 source_name（当时使用的数据源名称），浏览历史页可展示「用的哪个源」。
 */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history_table ADD COLUMN source_name TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [
        FavoriteEntity::class,
        HistoryEntity::class,
        EpisodeEntity::class,
        DownloadEntity::class,
        UserAnimeStatusEntity::class,
        RatingEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun userStatusDao(): UserStatusDao
    abstract fun ratingDao(): RatingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 进程内唯一数据库实例。Hilt 的 providesDatabase 也走这里，确保全局单例。 */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    ANIME_DATABASE,
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
