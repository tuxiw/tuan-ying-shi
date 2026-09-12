package com.example.tuanyingshi.di

import com.example.tuanyingshi.data.remote.api.AnimeApi
import com.example.tuanyingshi.data.remote.api.AnimeApiImpl
import com.example.tuanyingshi.data.repository.AnimeRepositoryImpl
import com.example.tuanyingshi.data.repository.DownloadRepository
import com.example.tuanyingshi.data.repository.DownloadRepositoryImpl
import com.example.tuanyingshi.data.repository.FavoriteRepository
import com.example.tuanyingshi.data.repository.FavoriteRepositoryImpl
import com.example.tuanyingshi.data.repository.HistoryRepository
import com.example.tuanyingshi.data.repository.HistoryRepositoryImpl
import com.example.tuanyingshi.domain.repository.AnimeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 对标 LaQoo ApiModule：接口 → 实现的绑定全部走 @Binds（实现类由 @Inject constructor 提供）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ApiModule {

    @Singleton
    @Binds
    abstract fun providesAnimeApi(animeApiImpl: AnimeApiImpl): AnimeApi

    @Singleton
    @Binds
    abstract fun providesAnimeRepository(animeRepositoryImpl: AnimeRepositoryImpl): AnimeRepository

    @Singleton
    @Binds
    abstract fun providesFavoriteRepository(favoriteRepositoryImpl: FavoriteRepositoryImpl): FavoriteRepository

    @Singleton
    @Binds
    abstract fun providesHistoryRepository(historyRepositoryImpl: HistoryRepositoryImpl): HistoryRepository

    @Singleton
    @Binds
    abstract fun providesDownloadRepository(downloadRepositoryImpl: DownloadRepositoryImpl): DownloadRepository
}
