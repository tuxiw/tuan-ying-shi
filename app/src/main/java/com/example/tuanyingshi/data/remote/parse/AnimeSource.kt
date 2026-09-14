package com.example.tuanyingshi.data.remote.parse

import com.example.tuanyingshi.data.remote.FilterPage
import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.util.DownloadManager
import com.example.tuanyingshi.util.preferences

interface AnimeSource {

    val KEY_SOURCE_DOMAIN: String
        get() = "${this.javaClass.simpleName}Domain"

    val DEFAULT_DOMAIN: String

    var baseUrl: String

    var WEB_URL: String

    /** 抓取根域名 HTML（诊断用）。默认走通用 OkHttp；有特殊请求头的源自行覆盖。 */
    suspend fun getRootHtml(): String = DownloadManager.getHtml(baseUrl,WEB_URL)

    suspend fun getHomeData(): List<HomeBean>

    suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean

    suspend fun getVideoData(episodeUrl: String): VideoBean

    suspend fun getSearchData(query: String, page: Int): List<AnimeBean>

    /**
     * 分类筛选列表（分类浏览页用）。
     * @param zoneId 分区：1=TV番组, 2=剧场番组
     * @param tag 题材标签，null 表示全部
     * @param year 年份，null 表示全部
     * @param orderBy 排序：null=更新时间(默认), "hits"=热度, "score"=评分, "follow"=追番
     * @param region 地区代码（JP/CN/US/KR），null 表示全部
     * @param type 类型代码（SERIES/OVA），null 表示全部
     * @param status 状态代码（ONGOING/FINISHED），null 表示全部
     * @param page 页码，从 1 开始
     *
     * 默认空实现：仅支持结构化筛选的源（如 cycani / 后端）覆盖；其它源无需实现。
     */
    suspend fun getFilterData(
        zoneId: Int,
        tag: String?,
        year: Int?,
        orderBy: String?,
        region: String? = null,
        type: String? = null,
        status: String? = null,
        page: Int,
    ): FilterPage<AnimeBean> = FilterPage(emptyList())

    suspend fun getWeekData(): Map<Int, List<AnimeBean>>

    /**
     * 排行榜（可选实现）。
     * 数据源若提供独立排行 API 可覆盖；默认返回空，由 Repository 从首页数据兜底。
     */
    suspend fun getRanking(): Map<String, List<AnimeBean>> = emptyMap()

    fun onEnter() {}

    fun onExit() {}
}
