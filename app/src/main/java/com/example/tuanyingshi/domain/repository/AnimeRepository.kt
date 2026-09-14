package com.example.tuanyingshi.domain.repository

import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.AnimeDetail
import com.example.tuanyingshi.domain.model.AnimeStatus
import com.example.tuanyingshi.domain.model.AnimeType
import com.example.tuanyingshi.domain.model.Home
import com.example.tuanyingshi.domain.model.Region
import com.example.tuanyingshi.domain.model.WebVideo
import com.example.tuanyingshi.data.remote.FilterPage
import com.example.tuanyingshi.util.Resource
import com.example.tuanyingshi.util.Result
import com.example.tuanyingshi.util.SearchStreamState
import com.example.tuanyingshi.util.SourceMode
import kotlinx.coroutines.flow.Flow

/**
 * 对标 LaQoo AnimeRepository 接口 + 团影视扩展方法（getAnimeList / getRanking）。
 */
interface AnimeRepository {
    suspend fun getHomeData(): Resource<List<Home>>

    /**
     * @param detailUrl 详情页地址
     * @param mode 当前数据源模式
     * @param sourceId 可选，来源 CSS 规则 id（聚合搜索结果携带）。非空时优先用该规则直达详情，
     *   避免回退到聚合源逐条试全部 CSS 源。
     */
    suspend fun getAnimeDetail(detailUrl: String, mode: SourceMode, sourceId: String = ""): Resource<AnimeDetail?>

    suspend fun getVideoData(episodeUrl: String, mode: SourceMode): Result<WebVideo>

    /** 按指定 CSS 规则源（ruleId）解析单集视频直链，供播放器「换源」使用。 */
    suspend fun getVideoDataByRule(ruleId: String, episodeUrl: String): Result<WebVideo>

    /**
     * 流式搜索：逐源返回结果快照（[SearchStreamState]），快源先到先显、慢源后补。
     * 由 ViewModel 转为列表状态并合并内容过滤。
     */
    fun getSearchStream(query: String, mode: SourceMode): Flow<SearchStreamState>

    /** 单页搜索（「加载更多」翻页用），带 TTL 缓存，返回已转模型的列表。仅 API 类源有效。 */
    suspend fun getSearchPage(query: String, page: Int, mode: SourceMode): List<Anime>

    /**
     * 分类筛选列表（分类浏览页用，单页）。zoneId/tag/year/orderBy 含义见 AnimeSource.getFilterData。
     * 返回已转模型的本页列表；空列表表示无数据或到达末页（由 ViewModel 负责翻页聚合）。
     */
    suspend fun getFilterPage(
        zoneId: Int,
        tag: String?,
        year: Int?,
        orderBy: String?,
        region: String?,
        type: String?,
        status: String?,
        page: Int,
        mode: SourceMode,
    ): FilterPage<Anime>

    suspend fun getWeekData(): Resource<Map<Int, List<Anime>>>

    /* 团影视扩展方法（分类筛选 + 排行榜） */
    suspend fun getAnimeList(
        region: Region? = null,
        type: AnimeType? = null,
        status: AnimeStatus? = null,
        year: Int? = null,
        page: Int = 1,
    ): List<Anime>

    suspend fun getRanking(limit: Int = 12): Map<String, List<Anime>>
}
