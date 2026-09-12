package com.example.tuanyingshi.di

import android.content.Context
import android.content.SharedPreferences
import com.example.tuanyingshi.data.local.AppDatabase
import com.example.tuanyingshi.data.local.dao.DownloadDao
import com.example.tuanyingshi.data.local.dao.FavoriteDao
import com.example.tuanyingshi.data.local.dao.HistoryDao
import com.example.tuanyingshi.data.local.dao.UserStatusDao
import com.example.tuanyingshi.util.preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 对标 LaQoo AppModule：数据库 / DAO / SharedPreferences 等基础设施的 @Provides 供给。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun providesContext(
        @ApplicationContext app: Context
    ): Context = app

    @Singleton
    @Provides
    fun providesDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun providesFavoriteDao(database: AppDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun providesHistoryDao(database: AppDatabase): HistoryDao = database.historyDao()

    @Provides
    fun providesDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun providesUserStatusDao(database: AppDatabase): UserStatusDao = database.userStatusDao()

    @Singleton
    @Provides
    fun providesPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.preferences
    }
}
