package com.example.tuanyingshi.data.remote.api.cycani

import com.google.gson.annotations.SerializedName

/**
 * 次元城 API 通用包装响应。
 */
data class CycaniResponse<T>(
    val code: Int,
    val msg: String,
    val data: T?,
) {
    fun isSuccess(): Boolean = code == 0

    /**
     * 是否属于「登录态失效 / 需要登录」类错误。
     *
     * 源站对失效 token 的返回并不统一（见过 HTTP 401，也见过 200 包体里 code=401 的情况），
     * 因此这里同时按 **业务码** 与 **错误文案关键词** 双重判定，宁可宽松也不要漏判——
     * 漏判的代价是用户看到「解析失败」却不知要重新登录。
     */
    fun isAuthError(): Boolean {
        if (code in AUTH_ERROR_CODES) return true
        val lower = msg.lowercase()
        return AUTH_ERROR_KEYWORDS.any { lower.contains(it) }
    }

    private companion object {
        /** 常见的登录失效业务码：HTTP 语义 401/403 + 站方自定义码。 */
        val AUTH_ERROR_CODES = setOf(401, 403, 1001, 1002, 1003, 2001, 3001)

        /** 错误文案里的登录关键词（中英文都覆盖）。 */
        val AUTH_ERROR_KEYWORDS = listOf(
            "登录", "登陆", "未登录", "请登录", "重新登录", "token",
            "授权", "鉴权", "身份", "凭证", "credential",
            "unauthorized", "unauthorised", "forbidden", "expire", "login",
        )
    }
}

/**
 * 登录请求体。
 */
data class CycaniLoginRequest(
    val username: String,
    val password: String,
)

/**
 * 邮箱验证码请求体（POST /api/auth/send-code）。
 *
 * @param type 业务类型：注册传 "register"。
 */
data class CycaniSendCodeRequest(
    val email: String,
    val type: String,
)

/**
 * 注册请求体（POST /api/v2/auth/register）。
 */
data class CycaniRegisterRequest(
    val username: String,
    val password: String,
    val email: String,
    @SerializedName("email_code")
    val emailCode: String,
)

/**
 * 登录成功后的 data 节点。
 */
data class CycaniLoginData(
    val token: String,
    @SerializedName("expires_at")
    val expiresAt: String?,
    val user: CycaniUser?,
)

/**
 * 登录返回的用户信息。
 */
data class CycaniUser(
    val id: Long,
    val username: String,
    val nickname: String?,
    val email: String?,
    @SerializedName("avatar_url")
    val avatarUrl: String?,
)

/**
 * 账号资料接口 GET /api/user/me 的 data 节点。
 *
 * 返回示例：
 * ```json
 * { "id": 1521518, "username": "shenyun758", "nickname": null,
 *   "email": "758427981@qq.com", "avatar_url": "", "group_name": "普通用户" }
 * ```
 * 说明：nickname / avatar_url / group_name 都可能是空串或 null，UI 需自行回退。
 */
data class CycaniUserProfile(
    val id: Long?,
    val username: String?,
    val nickname: String?,
    val email: String?,
    @SerializedName("avatar_url")
    val avatarUrl: String?,
    @SerializedName("group_name")
    val groupName: String?,
)

/**
 * 视频列表接口 data 节点。
 */
data class CycaniRankData(
    val list: List<CycaniVideoDto> = emptyList(),
)

/**
 * 视频列表项（/api/ranks/{zone_id}/videos 返回）。
 */
data class CycaniVideoDto(
    @SerializedName("video_id")
    val videoId: Long,
    @SerializedName("zone_id")
    val zoneId: Int,
    val title: String,
    val description: String?,
    @SerializedName("cover_url")
    val coverUrl: String?,
    val remarks: String?,
    val area: String?,
    val year: Int?,
    val version: String?,
    val score: Double?,
    val hits: Long?,
    val total: Int?,
    val tags: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
)

/**
 * 视频详情接口 /api/videos/{id} 的 data 节点。
 */
data class CycaniVideoInfoData(
    val id: Long,
    @SerializedName("zone_id")
    val zoneId: Int,
    val title: String,
    val description: String?,
    @SerializedName("cover_url")
    val coverUrl: String?,
    @SerializedName("publish_date")
    val publishDate: String?,
    @SerializedName("bangumi_id")
    val bangumiId: Long?,
    val subtitle: String?,
    @SerializedName("english_title")
    val englishTitle: String?,
    val state: String?,
    val director: List<String>?,
    val actor: List<String>?,
    val writer: String?,
    val remarks: String?,
    val weekday: Int?,
    val area: String?,
    val language: String?,
    val year: Int?,
    val version: String?,
    val score: Double?,
    val hits: Long?,
    @SerializedName("hits_day")
    val hitsDay: Long?,
    @SerializedName("hits_week")
    val hitsWeek: Long?,
    @SerializedName("hits_month")
    val hitsMonth: Long?,
    @SerializedName("score_all")
    val scoreAll: Int?,
    @SerializedName("score_num")
    val scoreNum: Int?,
    val total: Int?,
    val completed: Boolean?,
    val tags: List<String>?,
    val categories: List<String>?,
    @SerializedName("play_from")
    val playFrom: List<CycaniPlayFrom>?,
)

/**
 * 播放源（如 CYC_Main / cychub）。
 */
data class CycaniPlayFrom(
    val code: String,
    val title: String,
    val count: Int,
)

/**
 * 选集列表接口 /api/videos/{id}/sections 的 data 节点。
 */
data class CycaniSectionsData(
    val list: List<CycaniSectionItem> = emptyList(),
    val pager: CycaniPager? = null,
)

/**
 * 单个选集（section）。
 */
data class CycaniSectionItem(
    val id: Long,
    val title: String,
)

/**
 * 选集分页信息。
 */
data class CycaniPager(
    val page: Int,
    @SerializedName("page_size")
    val pageSize: Int,
    val total: Int,
)

/**
 * 播放地址接口 /api/v2/sections/{id}/play-url 的 data 节点。
 */
data class CycaniPlayUrlData(
    val name: String,
    val url: String,
)

/**
 * 首页推荐接口 /api/index/recommend 的 data 节点。
 */
data class CycaniRecommendData(
    val list: List<CycaniRecommendGroup> = emptyList(),
)

/**
 * 推荐分组（如 TV番组 / 剧场版）。
 */
data class CycaniRecommendGroup(
    val id: Int,
    val name: String,
    @SerializedName("link_type")
    val linkType: String?,
    @SerializedName("zone_id")
    val zoneId: Int?,
    val videos: List<CycaniVideoDto> = emptyList(),
)

/**
 * 广告/轮播图接口 /api/app/adverts 的 data 节点。
 */
data class CycaniAdvertData(
    val list: List<CycaniAdvert> = emptyList(),
)

/**
 * 单个轮播图条目。
 */
data class CycaniAdvert(
    val id: Int,
    val name: String,
    val position: String?,
    val content: String?,
    @SerializedName("action_type")
    val actionType: String?,
    @SerializedName("action_value")
    val actionValue: String?,
    val config: Any?,
)

/**
 * 排期表接口 /api/index/weekday?weekday=1..7 的 data 节点。
 */
data class CycaniWeekdayData(
    val list: List<CycaniWeekdayGroup> = emptyList(),
)

/**
 * 排期表某一天的分组。
 */
data class CycaniWeekdayGroup(
    val weekday: Int,
    val videos: List<CycaniVideoDto> = emptyList(),
)

/**
 * 搜索接口 /api/videos/search 的 data 节点。
 */
data class CycaniSearchData(
    val list: List<CycaniVideoDto> = emptyList(),
    val pager: CycaniPager? = null,
)
