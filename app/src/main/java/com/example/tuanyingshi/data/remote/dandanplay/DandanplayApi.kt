package com.example.tuanyingshi.data.remote.dandanplay

import com.example.tuanyingshi.util.DefaultUserAgent
import com.example.tuanyingshi.util.applyProxy
import com.example.tuanyingshi.util.dandanplay.DandanplayConfig
import com.example.tuanyingshi.util.log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Base64
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 弹弹play 开放弹幕网络 API v2 客户端。
 *
 * 覆盖团影视「外部资源」要用到的全部能力：
 * - 搜索：[searchAnime]（按关键词搜索作品，弹幕反查也用它拿 bangumiId）
 * - 番剧详情：[getBangumi]（含选集 episodeId，弹幕反查用它定位具体集）
 * - 新番：[getShin]（首页 / 周表）
 * - 排行榜：[getTrendingHot] / [getTrendingRising] / [getNewAnimeHot]
 * - 弹幕：[getComment]（按 episodeId 拉弹幕库，服务端 302 跳 cas2.dandanplay.net，OkHttp 自动跟随）
 *
 * 鉴权：所有请求带 X-AppId / X-Timestamp / X-Signature（签名验证模式）。
 * 签名算法（官方文档）：base64(sha256(AppId + Timestamp + Path + AppSecret))，
 * 其中 Path 为 API 路径（以 / 开头、不含域名与查询参数）。
 *
 * ⚠️ 真实响应结构要点（实测）：
 * - 每个端点返回的是**顶层数据字段**，没有统一的 `data` 信封：
 *   search/anime → `animes`，bangumi/shin、trending → `bangumiList`，
 *   bangumi/{id} → `bangumi`，comment → `comments`，match → `matches`。
 * - `bangumiId` 在多数端点是**字符串**（如 "14236"），故模型用 String。
 * - comment 弹幕结构为 `{cid, p, m}`，`p` 编码 "时间,模式,颜色,哈希"，需拆解。
 * - 失败时（非 2xx）服务端会在 **X-Error-Message** 响应头给出原因，已捕获并打印。
 */
object DandanplayApi {
    private const val TAG = "DandanplayApi"
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .readTimeout(30, TimeUnit.SECONDS)
        .applyProxy()
        .build()

    // ───────────────────────── 请求 ─────────────────────────

    private fun buildHeaders(path: String): Map<String, String> {
        val appId = DandanplayConfig.appId
        val appSecret = DandanplayConfig.appSecret
        val ts = (System.currentTimeMillis() / 1000).toString()
        return mapOf(
            "X-AppId" to appId,
            "X-Timestamp" to ts,
            "X-Signature" to sign(appId, appSecret, ts, path),
            "User-Agent" to DefaultUserAgent,
            "Accept" to "application/json",
        )
    }

    /** 弹弹play 签名：base64( sha256( AppId + Timestamp + Path + AppSecret ) )。Path 不含查询参数。 */
    private fun sign(appId: String, appSecret: String, timestamp: String, path: String): String {
        val data = "$appId$timestamp$path$appSecret"
        val hash = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun String.encodeUrl(): String = URLEncoder.encode(this, "UTF-8")

    private suspend fun callGet(path: String, params: Map<String, String> = emptyMap()): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildString {
                    append(DandanplayConfig.baseUrl.trimEnd('/'))
                    append(path)
                    if (params.isNotEmpty()) {
                        append("?")
                        append(params.entries.joinToString("&") { "${it.key}=${it.value.encodeUrl()}" })
                    }
                }
                val req = Request.Builder().url(url).apply {
                    buildHeaders(path).forEach { (k, v) -> header(k, v) }
                }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        // 服务端在 X-Error-Message 头给出失败原因（如 Missing Authentication Headers /
                        // Invalid Timestamp / Invalid AppId / Invalid Signature / Invalid AppSecret）。
                        val errMsg = resp.header("X-Error-Message") ?: ""
                        "callGet $path -> HTTP ${resp.code}${if (errMsg.isNotBlank()) " ($errMsg)" else ""}".log(TAG)
                        return@use null
                    }
                    val bodyStr = resp.body?.string()
                    "callGet $path -> 200 (${bodyStr?.length ?: 0} bytes)".log(TAG)
                    bodyStr
                }
            }.onFailure { it.log(TAG, "callGet $path failed") }.getOrNull()
        }

    private suspend fun callPost(path: String, jsonBody: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = DandanplayConfig.baseUrl.trimEnd('/') + path
            val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(url).post(body).apply {
                buildHeaders(path).forEach { (k, v) -> header(k, v) }
            }.build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errMsg = resp.header("X-Error-Message") ?: ""
                    "callPost $path -> HTTP ${resp.code}${if (errMsg.isNotBlank()) " ($errMsg)" else ""}".log(TAG)
                    return@use null
                }
                val bodyStr = resp.body?.string()
                "callPost $path -> 200 (${bodyStr?.length ?: 0} bytes)".log(TAG)
                bodyStr
            }
        }.onFailure { it.log(TAG, "callPost $path failed") }.getOrNull()
    }

    /** 直接解析为指定类型（不依赖任何信封包裹）。json 为空时返回 null。 */
    private inline fun <reified T> fromJson(json: String?): T? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, T::class.java) }.onFailure { it.log(TAG, "fromJson") }.getOrNull()
    }

    // ───────────────────────── 公开接口 ─────────────────────────

    /** 按关键词搜索作品（番剧）。返回 animes 列表。 */
    suspend fun searchAnime(keyword: String, page: Int = 1): List<AnimeSummary> {
        val json = callGet("/api/v2/search/anime", mapOf("keyword" to keyword, "page" to page.toString()))
        return fromJson<SearchAnimeResponse>(json)?.animes.orEmpty().also {
            "searchAnime('$keyword', page=$page) -> ${it.size} 条".log(TAG)
        }
    }

    /** 番剧详情（含选集 eps）。 */
    suspend fun getBangumi(bangumiId: String): BangumiDetail? {
        val json = callGet("/api/v2/bangumi/$bangumiId")
        return fromJson<BangumiResponse>(json)?.bangumi.also { d ->
            if (d == null) "getBangumi($bangumiId) -> null".log(TAG)
            else "getBangumi($bangumiId) -> '${d.animeTitle}' episodes=${d.episodeList.size}".log(TAG)
        }
    }

    /** 新番列表（首页 / 周表用）。直接返回 bangumiList 数组。 */
    suspend fun getShin(): List<AnimeSummary> {
        val json = callGet("/api/v2/bangumi/shin")
        return fromJson<BangumiListResponse>(json)?.bangumiList.orEmpty().also {
            "getShin -> ${it.size} 条".log(TAG)
        }
    }

    /** 全站热播榜。period ∈ {day, week, month}。 */
    suspend fun getTrendingHot(period: String = "week"): List<AnimeSummary> {
        val json = callGet("/api/v2/trending/all/hot/$period")
        return fromJson<BangumiListResponse>(json)?.bangumiList.orEmpty().also {
            "getTrendingHot($period) -> ${it.size} 条".log(TAG)
        }
    }

    /** 全站飙升榜。period ∈ {day, week, month}。 */
    suspend fun getTrendingRising(period: String = "week"): List<AnimeSummary> {
        val json = callGet("/api/v2/trending/all/rising/$period")
        return fromJson<BangumiListResponse>(json)?.bangumiList.orEmpty().also {
            "getTrendingRising($period) -> ${it.size} 条".log(TAG)
        }
    }

    /** 新番热播榜。scope ∈ {today, week, month, quarter, year}。 */
    suspend fun getNewAnimeHot(scope: String = "week"): List<AnimeSummary> {
        val json = callGet("/api/v2/trending/new-anime/hot/$scope")
        return fromJson<BangumiListResponse>(json)?.bangumiList.orEmpty().also {
            "getNewAnimeHot($scope) -> ${it.size} 条".log(TAG)
        }
    }

    /** 按 episodeId 拉弹幕库（withRelated=true 含第三方整合弹幕）。 */
    suspend fun getComment(episodeId: Long, withRelated: Boolean = true): List<Comment> {
        val json = callGet("/api/v2/comment/$episodeId", mapOf("withRelated" to withRelated.toString()))
        return fromJson<CommentResponse>(json)?.comments.orEmpty().also {
            "getComment($episodeId, withRelated=$withRelated) -> ${it.size} 条".log(TAG)
        }
    }

    /**
     * 连通性自检：以搜索接口探活，返回 (是否成功, 说明文案)。
     * 用于「设置 → 弹幕源管理」里手动测试当前弹弹play 配置（AppId/AppSecret/地址）是否有效。
     */
    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val path = "/api/v2/search/anime"
            val url = DandanplayConfig.baseUrl.trimEnd('/') + path + "?keyword=test&page=1"
            val req = Request.Builder().url(url).apply {
                buildHeaders(path).forEach { (k, v) -> header(k, v) }
            }.build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    true to "连接正常：已成功访问弹弹play 弹幕 API"
                } else {
                    val err = resp.header("X-Error-Message").orEmpty().ifBlank { "HTTP ${resp.code}" }
                    false to "连接失败：$err"
                }
            }
        }.fold(
            onSuccess = { it },
            onFailure = { false to "网络异常：${it.message ?: "未知错误"}" },
        )
    }

    // ───────────────────────── 响应模型 ─────────────────────────

    data class SearchAnimeResponse(
        @SerializedName("success") val success: Boolean = false,
        @SerializedName("errorCode") val errorCode: Int = 0,
        @SerializedName("errorMessage") val errorMessage: String = "",
        @SerializedName("animes") val animes: List<AnimeSummary>? = null,
    )

    data class BangumiListResponse(
        @SerializedName("bangumiList") val bangumiList: List<AnimeSummary>? = null,
        @SerializedName("summary") val summary: Any? = null,
        @SerializedName("success") val success: Boolean = false,
        @SerializedName("errorCode") val errorCode: Int = 0,
        @SerializedName("errorMessage") val errorMessage: String = "",
    )

    data class BangumiResponse(
        @SerializedName("bangumi") val bangumi: BangumiDetail? = null,
        @SerializedName("success") val success: Boolean = false,
        @SerializedName("errorCode") val errorCode: Int = 0,
        @SerializedName("errorMessage") val errorMessage: String = "",
    )

    data class CommentResponse(
        @SerializedName("count") val count: Int = 0,
        @SerializedName("comments") val comments: List<Comment>? = null,
    )

    /** 作品摘要：搜索 / 新番 / 排行共用。 */
    data class AnimeSummary(
        @SerializedName("bangumiId") val bangumiId: String = "",
        @SerializedName("animeId") val animeId: Long = 0,
        @SerializedName("animeTitle") val animeTitle: String = "",
        @SerializedName("imageUrl") val imageUrl: String = "",
        @SerializedName("type") val type: String = "",
        @SerializedName("year") val year: String = "",
        @SerializedName("summary") val summary: String = "",
        @SerializedName("tags") val tags: List<String>? = null,
    )

    /** 番剧详情（含选集）。 */
    data class BangumiDetail(
        @SerializedName("bangumiId") val bangumiId: String = "",
        @SerializedName("animeId") val animeId: Long = 0,
        @SerializedName("animeTitle") val animeTitle: String = "",
        @SerializedName("imageUrl") val imageUrl: String = "",
        @SerializedName("summary") val summary: String = "",
        @SerializedName("type") val type: String = "",
        @SerializedName("airDate") val airDate: String = "",
        @SerializedName("rating") val rating: String = "",
        @SerializedName("eps") val eps: List<Episode>? = null,
        @SerializedName("episodes") val episodes: List<Episode>? = null,
        // 真实结构为 [{id, name, count}]；若声明成 List<String> 会导致整个 bangumi 解析抛异常 → 详情空白。
        @SerializedName("tags") val tags: List<BangumiTag>? = null,
    ) {
        val episodeList: List<Episode> get() = eps ?: episodes ?: emptyList()
    }

    /** 番剧标签：真实结构为 [{id, name, count}]，仅关心 name。 */
    data class BangumiTag(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("name") val name: String = "",
        @SerializedName("count") val count: Int = 0,
    )

    data class Episode(
        @SerializedName("episodeId") val episodeId: Long = 0,
        @SerializedName("episodeTitle") val episodeTitle: String = "",
        @SerializedName("airDate") val airDate: String = "",
    )

    /**
     * 单条弹幕。弹弹play 真实结构为 {cid, p, m}：
     * - cid：弹幕唯一 id（长整型，序列化为字符串）
     * - p："时间,模式,颜色,哈希"，例如 "836.77,1,16777215,cb68560c"
     * - m：弹幕文本
     * 这里拆解出渲染所需的 time / mode / color。
     */
    data class Comment(
        @SerializedName("cid") val cid: String = "",
        @SerializedName("p") val p: String = "",
        @SerializedName("m") val m: String = "",
    ) {
        /** 弹幕出现时间（秒）。 */
        val time: Double get() = p.substringBefore(',').toDoubleOrNull() ?: 0.0

        /** 弹幕模式（1=滚动, 4=底部, 5=顶部）。 */
        val mode: Int get() = p.split(",").getOrNull(1)?.toIntOrNull() ?: 1

        /** 弹幕颜色，十六进制 #RRGGBB。 */
        val color: String
            get() {
                val c = p.split(",").getOrNull(2)?.toLongOrNull() ?: 0xFFFFFF
                return "#%06X".format(c and 0xFFFFFF)
            }

        /** 弹幕文本。 */
        val text: String get() = m
    }
}
