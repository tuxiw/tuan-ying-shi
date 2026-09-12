package com.example.tuanyingshi.data.remote.api.cycani

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 次元城（cycani.org）HTTP API 接口定义。
 */
interface CycaniApiService {

    /**
     * 登录接口。
     */
    @POST("api/auth/login")
    suspend fun login(@Body request: CycaniLoginRequest): CycaniResponse<CycaniLoginData>

    /**
     * 排行榜 / 视频列表。
     * @param zoneId 分区 ID，截图示例为 1（TV 动画）
     */
    @GET("api/ranks/{zoneId}/videos")
    suspend fun getRankVideos(
        @Path("zoneId") zoneId: Int,
    ): CycaniResponse<CycaniRankData>

    /**
     * 首页推荐：返回 TV番组、剧场版等分组及分组内的视频列表。
     */
    @GET("api/index/recommend")
    suspend fun getRecommend(): CycaniResponse<CycaniRecommendData>

    /**
     * 轮播图广告。
     * @param position 广告位，如 "banner"
     */
    @GET("api/app/adverts")
    suspend fun getBannerAdverts(
        @Query("position") position: String = "banner",
    ): CycaniResponse<CycaniAdvertData>

    /**
     * 排期表。
     * @param weekday 星期，1=周一 … 7=周日
     */
    @GET("api/index/weekday")
    suspend fun getWeekday(
        @Query("weekday") weekday: Int,
    ): CycaniResponse<CycaniWeekdayData>

    /**
     * 视频详情。
     * @param videoId 视频 ID，如 3859
     */
    @GET("api/videos/{videoId}")
    suspend fun getVideoInfo(
        @Path("videoId") videoId: Long,
    ): CycaniResponse<CycaniVideoInfoData>

    /**
     * 视频选集（播放 ID 列表）。
     * @param videoId 视频 ID
     * @param playerCode 播放源 code，如 "cychub"
     */
    @GET("api/videos/{videoId}/sections")
    suspend fun getVideoSections(
        @Path("videoId") videoId: Long,
        @Query("player_code") playerCode: String = "cychub",
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 48,
    ): CycaniResponse<CycaniSectionsData>

    /**
     * 真实播放地址。
     * @param sectionId 选集 ID，如 51454
     */
    @GET("api/v2/sections/{sectionId}/play-url")
    suspend fun getPlayUrl(
        @Path("sectionId") sectionId: Long,
    ): CycaniResponse<CycaniPlayUrlData>

    /**
     * 搜索视频（分页）。
     * @param q 搜索关键词
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数，默认 24
     */
    @GET("api/videos/search")
    suspend fun searchVideos(
        @Query("q") q: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 24,
    ): CycaniResponse<CycaniSearchData>

    /**
     * 筛选视频列表（分类浏览页用）。
     *
     * 参数规则：
     * - zoneId：1=TV番组, 2=剧场番组（必填）
     * - tag：题材标签，"全部"时不传
     * - year：年份，"全部"时不传
     * - orderBy：排序方式。更新时间(默认)不传, hits=热度, score=评分
     */
    @GET("api/videos")
    suspend fun filterVideos(
        @Query("zone_id") zoneId: Int,
        @Query("tag") tag: String? = null,
        @Query("year") year: Int? = null,
        @Query("order_by") orderBy: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 24,
    ): CycaniResponse<CycaniSearchData>
}
