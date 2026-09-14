package com.example.tuanyingshi.data.remote.api

import com.example.tuanyingshi.data.remote.FilterPage
import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.util.SearchStreamState
import com.example.tuanyingshi.util.SourceMode
import kotlinx.coroutines.flow.Flow

interface AnimeApi {
    suspend fun getHomeAllData(): List<HomeBean>

    suspend fun getAnimeDetail(detailUrl: String, mode: SourceMode, sourceId: String = ""): AnimeDetailBean

    suspend fun getVideoData(episodeUrl: String, mode: SourceMode): VideoBean

    /** 按指定 CSS 规则源（ruleId）解析单集视频直链，供播放器「换源」使用。 */
    suspend fun getVideoDataByRule(ruleId: String, episodeUrl: String): VideoBean

    suspend fun getSearchData(query: String, page: Int, mode: SourceMode): List<AnimeBean>

    /**
     * 流式搜索：返回结果快照流 [SearchStreamState]，各数据源（聚合源=各 CSS 规则）逐源返回，
     * 快源先到先显，慢源（WebView 冷启动）完成后再补，不再等最慢源拖住整页。
     */
    fun getSearchStream(query: String, mode: SourceMode): Flow<SearchStreamState>

    /**
     * 分类筛选列表（分类浏览页用）。zoneId/tag/year/orderBy/region/type/status 含义见 AnimeSource.getFilterData。
     */
    suspend fun getFilterData(
        zoneId: Int,
        tag: String?,
        year: Int?,
        orderBy: String?,
        region: String?,
        type: String?,
        status: String?,
        page: Int,
        mode: SourceMode,
    ): FilterPage<AnimeBean>

    suspend fun getWeekDate(): Map<Int, List<AnimeBean>>

    /** 排行榜：数据源提供独立 API 时优先使用，按分类名返回。 */
    suspend fun getRanking(): Map<String, List<AnimeBean>>
}
