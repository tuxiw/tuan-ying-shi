package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.data.remote.api.AnimeApi
import com.example.tuanyingshi.data.remote.FilterPage
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.AnimeDetail
import com.example.tuanyingshi.domain.model.AnimeStatus
import com.example.tuanyingshi.domain.model.AnimeType
import com.example.tuanyingshi.domain.model.Home
import com.example.tuanyingshi.domain.model.Region
import com.example.tuanyingshi.domain.model.WebVideo
import com.example.tuanyingshi.domain.repository.AnimeRepository
import com.example.tuanyingshi.util.Resource
import com.example.tuanyingshi.util.Result
import com.example.tuanyingshi.util.SearchStreamState
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.invokeApi
import com.example.tuanyingshi.util.map
import com.example.tuanyingshi.util.safeCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对标 LaQoo AnimeRepositoryImpl + 团影视扩展方法实现。
 */
@Singleton
class AnimeRepositoryImpl @Inject constructor(
    private val animeApi: AnimeApi
) : AnimeRepository {

    override suspend fun getHomeData(): Resource<List<Home>> {
        val response = invokeApi {
            animeApi.getHomeAllData()
        }
        return when (response) {
            is Resource.Error -> Resource.Error(error = response.error)
            is Resource.Loading -> Resource.Loading
            is Resource.Success -> Resource.Success(
                data = response.data?.map { it.toHome() }.orEmpty()
            )
        }
    }

    override suspend fun getAnimeDetail(
        detailUrl: String,
        mode: SourceMode,
        sourceId: String,
    ): Resource<AnimeDetail?> {
        val response = invokeApi {
            animeApi.getAnimeDetail(detailUrl, mode, sourceId)
        }
        return when (response) {
            is Resource.Error -> Resource.Error(error = response.error)
            is Resource.Loading -> Resource.Loading
            is Resource.Success -> Resource.Success(
                data = response.data?.toAnimeDetail()
            )
        }
    }

    override suspend fun getVideoData(episodeUrl: String, mode: SourceMode): Result<WebVideo> {
        return safeCall {
            animeApi.getVideoData(episodeUrl, mode)
        }.map { it.toWebVideo() }
    }

    override suspend fun getVideoDataByRule(ruleId: String, episodeUrl: String): Result<WebVideo> {
        return safeCall {
            animeApi.getVideoDataByRule(ruleId, episodeUrl)
        }.map { it.toWebVideo() }
    }

    override fun getSearchStream(query: String, mode: SourceMode): Flow<SearchStreamState> {
        return animeApi.getSearchStream(query, mode)
    }

    override suspend fun getSearchPage(query: String, page: Int, mode: SourceMode): List<Anime> {
        return animeApi.getSearchData(query, page, mode).map { it.toAnime() }
    }

    override suspend fun getFilterPage(
        zoneId: Int,
        tag: String?,
        year: Int?,
        orderBy: String?,
        region: String?,
        type: String?,
        status: String?,
        page: Int,
        mode: SourceMode,
    ): FilterPage<Anime> {
        return runCatching {
            val pageResult = animeApi.getFilterData(zoneId, tag, year, orderBy, region, type, status, page, mode)
            FilterPage(
                items = pageResult.items.map { it.toAnime() },
                hasMore = pageResult.hasMore,
            )
        }.getOrDefault(FilterPage(emptyList()))
    }

    override suspend fun getWeekData(): Resource<Map<Int, List<Anime>>> {
        val response = invokeApi {
            animeApi.getWeekDate()
        }
        return when (response) {
            is Resource.Error -> Resource.Error(error = response.error)
            is Resource.Loading -> Resource.Loading
            is Resource.Success -> Resource.Success(
                data = response.data?.mapValues { (_, v) -> v.map { it.toAnime() } } ?: emptyMap()
            )
        }
    }

    /* 团影视扩展方法：基于首页数据返回全部 / 排行，不做结构化筛选（Anime 轻量模型无 region/type 字段） */

    override suspend fun getAnimeList(
        region: Region?,
        type: AnimeType?,
        status: AnimeStatus?,
        year: Int?,
        page: Int,
    ): List<Anime> {
        val homeData = getHomeData()
        val allAnime = when (homeData) {
            is Resource.Success -> homeData.data?.flatMap { it.animeList }.orEmpty().distinctBy { it.detailUrl }
            else -> emptyList()
        }
        return allAnime
    }

    override suspend fun getRanking(limit: Int): Map<String, List<Anime>> {
        // 优先使用数据源的独立排行 API（如 cycani 的 /api/ranks/1/videos）
        val sourceRanking = animeApi.getRanking()
        // 仅当排行榜确实返回了条目才采用；若 map 非空但所有列表为空（如接口需登录 / 失败），
        // 回退到首页数据兜底，避免首页「TV番组 / 剧场番组」分类空白。
        val hasContent = sourceRanking.values.any { it.isNotEmpty() }
        if (sourceRanking.isNotEmpty() && hasContent) {
            return sourceRanking.mapValues { (_, list) ->
                list.take(limit).map { it.toAnime() }
            }
        }

        val homeData = getHomeData()
        val homes = (homeData as? Resource.Success)?.data.orEmpty()
        val rankHome = homes.firstOrNull { it.title.contains("排行") }
        val list = rankHome?.animeList ?: homes.flatMap { it.animeList }.distinctBy { it.detailUrl }
        return mapOf("TV番组" to list.take(limit))
    }
}
