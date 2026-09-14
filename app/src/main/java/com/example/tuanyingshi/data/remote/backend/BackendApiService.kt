package com.example.tuanyingshi.data.remote.backend

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * 后端 /api/v1 内容接口（全部 permitAll，无需登录即可读取）。
 *
 * 基址由 [com.example.tuanyingshi.util.BackendPrefs.apiBaseUrl] 提供（已含 `/api/v1/`），
 * 故这里的路径均为相对路径。字段与 Spring Boot 端 VO 对齐。
 */
interface BackendApiService {

    /** 首页聚合：轮播 + 全部区块。 */
    @GET("home")
    suspend fun home(): ApiResp<HomeVO>

    /** 放送表（单日），weekday 缺省返回全部在播。 */
    @GET("schedule")
    suspend fun schedule(@Query("weekday") weekday: Int? = null): ApiResp<List<AnimeCardVO>>

    /** 放送表（整周：1-7 → 列表）。Gson 会把 Integer key 序列化为 String，映射层再转 Int。 */
    @GET("schedule/week")
    suspend fun scheduleWeek(): ApiResp<Map<String, List<AnimeCardVO>>>

    /** 排行榜：period=week 周榜（默认）/ total 总榜 / score 评分 / follow 追番。 */
    @GET("ranking")
    suspend fun ranking(
        @Query("zoneId") zoneId: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("period") period: String = "week",
    ): ApiResp<List<AnimeCardVO>>

    /** 搜索：keyword 优先，分页 records。 */
    @GET("search")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): ApiResp<ApiPage<AnimeCardVO>>

    /** 番剧详情（登录后附带用户态）。 */
    @GET("anime/{id}")
    suspend fun animeDetail(@Path("id") id: String): ApiResp<AnimeDetailVO>

    /** 相关推荐。 */
    @GET("anime/{id}/related")
    suspend fun related(
        @Path("id") id: String,
        @Query("limit") limit: Int = 8,
    ): ApiResp<List<AnimeCardVO>>

    /** 上报播放量：开始播放时调用，后端 hits / week_hits +1（无需登录）。 */
    @POST("anime/{id}/play")
    suspend fun recordPlay(@Path("id") id: Long): ApiResp<Unit>

    /** 分类筛选列表。 */
    @GET("filter/list")
    suspend fun filterList(@QueryMap query: Map<String, String>): ApiResp<ApiPage<AnimeCardVO>>

    /** 筛选面板选项（题材 / 年份 / 地区 / 类型 / 状态 / 排序），一次返回，客户端无需硬编码。 */
    @GET("filter/options")
    suspend fun filterOptions(): ApiResp<FilterOptionsVO>

    /** 弹幕：兼容弹弹play 返回结构；按 animeId 拉取。 */
    @GET("danmaku")
    suspend fun danmaku(
        @Query("animeId") animeId: Long? = null,
        @Query("detailUrl") detailUrl: String? = null,
        @Query("episodeId") episodeId: Long? = null,
        @Query("withRelated") withRelated: Boolean = true,
    ): ApiResp<DanmakuListVO>
    /** 发送弹幕（需登录，时间单位为秒）。返回结构与拉取一致，可直接本地乐观插入。 */
    @POST("danmaku")
    suspend fun sendDanmaku(@Body req: DanmakuRequestDTO): ApiResp<DanmakuItemVO>

    // ───────────────────────── 设备上报 ─────────────────────────

    /**
     * 注册 / 更新当前设备（需登录）。
     *
     * 后台「用户分析 - 登录设备」的数据来源：登录时上报一次，之后每次启动按需心跳刷新
     * 设备型号与 App 版本（由 [com.example.tuanyingshi.util.DeviceInfo] 组装）。
     */
    @POST("user/devices")
    suspend fun saveDevice(@Body req: DeviceRequestDTO): ApiResp<DeviceVO>

    // ───────────────────────── 账号（需登录态）─────────────────────────

    /** 发送邮箱验证码（注册用）。 */
    @POST("auth/send-code")
    suspend fun sendCode(@Body req: SendCodeRequestDTO): ApiResp<Unit>

    /** 邮箱验证码注册。 */
    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequestDTO): ApiResp<LoginResultVO>

    /** 登录（用户名或邮箱 + 密码）。 */
    @POST("auth/login")
    suspend fun login(@Body req: LoginRequestDTO): ApiResp<LoginResultVO>

    /** 退出登录。 */
    @POST("auth/logout")
    suspend fun logout(): ApiResp<Unit>

    /** 当前用户资料。 */
    @GET("user/profile")
    suspend fun profile(): ApiResp<UserVO>

    /** 修改个人资料（nickname / avatar / signature，null 表示不修改），返回最新用户信息。 */
    @PUT("user/profile")
    suspend fun updateProfile(@Body req: UpdateProfileRequestDTO): ApiResp<UserVO>

    /** 修改密码（需原密码），成功后建议客户端重新登录。 */
    @POST("user/password")
    suspend fun changePassword(@Body req: ChangePasswordRequestDTO): ApiResp<Unit>

    /** 上传头像：multipart 字段名 file，返回 { url, absoluteUrl }。 */
    @Multipart
    @POST("user/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): ApiResp<Map<String, Any?>>

    // ───────────────────────── 扫码登录 ─────────────────────────

    /** 创建二维码票据（未登录，对应后端 /auth/qrcode/create）。 */
    @POST("auth/qrcode/create")
    suspend fun qrCreate(): ApiResp<QrCreateVO>

    /** 已登录设备扫码，标记 SCANNED（对应后端 /auth/qrcode/scan）。 */
    @POST("auth/qrcode/scan")
    suspend fun qrScan(@Body req: QrTicketDTO): ApiResp<Unit>

    /** 已登录设备确认登录，为当前用户签发令牌（对应后端 /auth/qrcode/confirm）。 */
    @POST("auth/qrcode/confirm")
    suspend fun qrConfirm(@Body req: QrTicketDTO): ApiResp<Unit>

    /** 已登录设备取消本次扫码（对应后端 /auth/qrcode/cancel）。 */
    @POST("auth/qrcode/cancel")
    suspend fun qrCancel(@Body req: QrTicketDTO): ApiResp<Unit>

    /** 轮询状态（未登录，对应后端 /auth/qrcode/status）；CONFIRMED 时一次性返回令牌。 */
    @GET("auth/qrcode/status")
    suspend fun qrStatus(@Query("ticket") ticket: String): ApiResp<QrStatusVO>

    // ───────────────────────── 收藏 ─────────────────────────

    @GET("user/favorites")
    suspend fun favorites(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): ApiResp<ApiPage<FavoriteVO>>

    @POST("user/favorites")
    suspend fun addFavorite(@Body req: FavoriteRequestDTO): ApiResp<Unit>

    @DELETE("user/favorites")
    suspend fun deleteFavorite(
        @Query("animeId") animeId: Long?,
        @Query("detailUrl") detailUrl: String?,
    ): ApiResp<Unit>

    @GET("user/favorites/check")
    suspend fun checkFavorite(
        @Query("animeId") animeId: Long?,
        @Query("detailUrl") detailUrl: String?,
    ): ApiResp<Map<String, Any?>>

    // ───────────────────────── 浏览历史 ─────────────────────────

    @GET("user/history")
    suspend fun history(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): ApiResp<ApiPage<HistoryItemVO>>

    @POST("user/history")
    suspend fun addHistory(@Body req: HistoryRequestDTO): ApiResp<Unit>

    @DELETE("user/history")
    suspend fun deleteHistory(
        @Query("animeId") animeId: Long?,
        @Query("detailUrl") detailUrl: String?,
    ): ApiResp<Unit>

    @DELETE("user/history/clear")
    suspend fun clearHistory(): ApiResp<Unit>

    // ───────────────────────── 番剧标记（在看 / 看过 / 抛弃）─────────────────────────

    /** 查询某标记下的全部 detailUrl 列表。 */
    @GET("user/marks")
    suspend fun marks(@Query("mark") mark: String? = null): ApiResp<List<String>>

    /** 设置 / 取消标记（mark 传空串取消）。 */
    @POST("user/marks/mark")
    suspend fun setMark(@Body req: MarkRequestDTO): ApiResp<Map<String, Any?>>

    // ───────────────────────── 番剧评分（1.0 - 10.0）─────────────────────────

    /** 评分 / 修改评分（需登录）。detailUrl 与 animeId 至少传一个；重复评分覆盖为最新。 */
    @POST("user/ratings")
    suspend fun setRating(@Body req: RatingRequestDTO): ApiResp<ScoreVO>

    /** 查询当前用户对某内容的评分（需登录）；未评分时 data.score 为 null。 */
    @GET("user/ratings/one")
    suspend fun myRating(
        @Query("animeId") animeId: Long? = null,
        @Query("detailUrl") detailUrl: String? = null,
    ): ApiResp<ScoreVO>

    /** 查询当前用户的全部评分（按更新时间倒序，后端默认最多 500 条）。 */
    @GET("user/ratings/all")
    suspend fun myRatings(
        @Query("limit") limit: Int = 50,
    ): ApiResp<List<RatingItemVO>>

    // ───────────────────────── 反馈 ─────────────────────────

    @POST("ops/feedback")
    suspend fun submitFeedback(@Body req: FeedbackRequestDTO): ApiResp<Unit>

    /** 公告列表：启用且在有效期内，按 sort / id 倒序。 */
    @GET("ops/announcement/list")
    suspend fun announcements(): ApiResp<List<AnnouncementVO>>

    /** 最新一条弹窗公告；当前没有可弹公告时 data 为 null。 */
    @GET("ops/announcement/popup")
    suspend fun announcementPopup(): ApiResp<AnnouncementVO>

    /** 检查更新：按平台与本地版本号判断是否有新版本（无需登录）。 */
    @GET("ops/version/check")
    suspend fun checkVersion(
        @Query("platform") platform: String? = null,
        @Query("channel") channel: String? = null,
        @Query("versionCode") versionCode: Int? = null,
    ): ApiResp<VersionCheckVO>

    // ───────────────────────── 评论 ─────────────────────────

    /** 某剧集的顶层评论列表（animeId 或 detailUrl 二选一；按时间升序）。 */
    @GET("comments")
    suspend fun comments(
        @Query("animeId") animeId: Long?,
        @Query("detailUrl") detailUrl: String?,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50,
    ): ApiResp<ApiPage<CommentVO>>

    /** 发表评论 / 回复（需登录）。 */
    @POST("comments")
    suspend fun createComment(@Body req: CommentRequestDTO): ApiResp<CommentVO>
}
