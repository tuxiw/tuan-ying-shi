package com.example.tuanyingshi.data.remote.backend

/**
 * 后端 /api/v1 接口的响应模型。
 *
 * 字段命名刻意对齐 Spring Boot 端 VO（camelCase），便于 Gson 直接反序列化；
 * 可能为 null 的字段全部声明为可空，缺失时由映射层兜底，避免 Gson 构造异常。
 * 注意：后端 Map<Integer, ...> 经 JSON 序列化后键为 String，故这里用 [Map<String, *>]。
 */

/** 统一响应体：{ code, message, data, timestamp }，成功时 code == 0。 */
data class ApiResp<T>(
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null,
    val timestamp: Long = 0,
) {
    val ok: Boolean get() = code == 0
}

/** 统一分页结构：{ records, total, page, size, pages, hasMore }。 */
data class ApiPage<T>(
    val records: List<T>? = null,
    val total: Long = 0,
    val page: Long = 1,
    val size: Long = 20,
    val pages: Long = 0,
    val hasMore: Boolean = false,
)

// ───────────────────────── 轮播 / 首页 ─────────────────────────

data class BannerVO(
    val id: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val image: String? = null,
    val actionType: String? = null,
    val actionValue: String? = null,
    val animeId: Long? = null,
)

data class HomeVO(
    val banners: List<BannerVO>? = null,
    val sections: List<HomeSectionVO>? = null,
)

data class HomeSectionVO(
    val key: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val style: String? = null,
    val scrollable: Boolean? = null,
    val animeList: List<AnimeCardVO>? = null,
    val animes: List<AnimeCardVO>? = null,
    val moreUrl: String? = null,
    val moreQuery: Map<String, Any?>? = null,
)

// ───────────────────────── 番剧卡片 / 详情 ─────────────────────────

data class AnimeCardVO(
    val id: Long? = null,
    val title: String? = null,
    val titleOriginal: String? = null,
    val img: String? = null,
    val imgUrl: String? = null,
    val detailUrl: String? = null,
    val url: String? = null,
    val episodeName: String? = null,
    val tags: List<String>? = null,
    val categories: List<String>? = null,
    val year: Int? = null,
    val zoneId: Int? = null,
    val hits: Long? = null,
    val score: Double? = null,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val iconUrl: String? = null,
    val cover: String? = null,
    val banner: String? = null,
    val latestEpisodeLabel: String? = null,
    val region: String? = null,
    val type: String? = null,
    val status: String? = null,
    val airWeekday: Int? = null,
    val totalEpisodes: Int? = null,
    val currentEpisode: Int? = null,
    val followCount: Int? = null,
    val rating: Double? = null,
    val channelId: Long? = null,
    val channelName: String? = null,
)

data class AnimeDetailVO(
    val id: Long? = null,
    val title: String? = null,
    val titleOriginal: String? = null,
    val aliases: List<String>? = null,
    val img: String? = null,
    val imgUrl: String? = null,
    val cover: String? = null,
    val banner: String? = null,
    val desc: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val categories: List<String>? = null,
    val detailUrl: String? = null,
    val region: String? = null,
    val regionLabel: String? = null,
    val type: String? = null,
    val typeLabel: String? = null,
    val status: String? = null,
    val statusLabel: String? = null,
    val year: Int? = null,
    val zoneId: Int? = null,
    val episodeName: String? = null,
    val season: String? = null,
    val airWeekday: Int? = null,
    val airTime: String? = null,
    val rating: Double? = null,
    val score: Double? = null,
    val ratingCount: Int? = null,
    val hits: Long? = null,
    val followCount: Int? = null,
    val totalEpisodes: Int? = null,
    val currentEpisode: Int? = null,
    val latestEpisodeLabel: String? = null,
    val studio: String? = null,
    val director: String? = null,
    val cvList: List<String>? = null,
    val channel: ChannelVO? = null,
    val updatedAt: String? = null,
    val lastPosition: Int? = null,
    val episodes: List<EpisodeVO>? = null,
    val channels: Map<String, List<EpisodeVO>>? = null,
    val channelNames: List<String>? = null,
    val channelIndex: Int? = null,
    val relatedAnimes: List<AnimeCardVO>? = null,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val iconUrl: String? = null,
    val favorited: Boolean? = null,
    val favoriteStatus: Int? = null,
    val userMark: String? = null,
    val lastEpisodeNo: Double? = null,
)

data class ChannelVO(
    val id: Long? = null,
    val name: String? = null,
    val code: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val description: String? = null,
)

data class EpisodeVO(
    val id: Long? = null,
    val animeId: Long? = null,
    val name: String? = null,
    val title: String? = null,
    val episodeNo: Double? = null,
    val duration: Int? = null,
    val publishTime: String? = null,
    val url: String? = null,
    val videoUrl: String? = null,
    val headers: Map<String, String>? = null,
    val lineIndex: Int? = null,
    val lastPlayPosition: Long? = null,
    val isPlayed: Boolean? = null,
    val isDownloaded: Boolean? = null,
    val historyId: Long? = null,
    val isFree: Boolean? = null,
    val lines: List<EpisodeLineVO>? = null,
    val lineNames: List<String>? = null,
    val lineUrls: Map<String, String>? = null,
)

data class EpisodeLineVO(
    val id: Long? = null,
    val lineIndex: Int? = null,
    val lineName: String? = null,
    val url: String? = null,
    val rawUrl: String? = null,
    val format: String? = null,
    val quality: String? = null,
    val headers: Map<String, String>? = null,
    val referer: String? = null,
    val isDefault: Boolean? = null,
    val isAvailable: Boolean? = null,
)

data class EpisodesVO(
    val animeId: Long? = null,
    val animeTitle: String? = null,
    val episodes: List<EpisodeVO>? = null,
    val channels: Map<String, List<EpisodeVO>>? = null,
    val channelNames: List<String>? = null,
    val channelIndexes: List<Int>? = null,
    val channelIndex: Int? = null,
    val totalEpisodes: Int? = null,
)

// ───────────────────────── 筛选 ─────────────────────────

data class FilterOptionsVO(
    val brands: List<OptionVO>? = null,
    val genres: List<OptionVO>? = null,
    val years: List<OptionVO>? = null,
    val regions: List<OptionVO>? = null,
    val types: List<OptionVO>? = null,
    val statuses: List<OptionVO>? = null,
    val orders: List<OptionVO>? = null,
    val weekdays: List<OptionVO>? = null,
) {
    data class OptionVO(
        val label: String? = null,
        val value: String? = null,
        val extra: String? = null,
        val color: String? = null,
    )
}

// ───────────────────────── 弹幕（兼容弹弹play {count, comments:[{cid,p,m}]}）─────────────────────────

data class DanmakuListVO(
    val count: Int = 0,
    val comments: List<DanmakuItemVO>? = null,
)

data class DanmakuItemVO(
    val cid: String? = null,
    val p: String? = null,
    val m: String? = null,
)

/**
 * 发送弹幕请求（对应后端 DanmakuRequest，需登录）。
 *
 * - [time] 出现时间，单位**秒**（播放器 position 是毫秒，调用处需自行换算）；
 * - [mode] 1 滚动 / 4 底部 / 5 顶部；[color] **十进制 RGB**（0xFFFFFF = 16777215）。
 */
data class DanmakuRequestDTO(
    val animeId: Long? = null,
    val detailUrl: String? = null,
    val episodeId: Long? = null,
    val time: Double,
    val mode: Int = 1,
    val color: Int = 16777215,
    val text: String,
)

// ───────────────────────── 账号 / 用户态 ─────────────────────────

/** 登录 / 注册 / 刷新令牌统一返回（对应后端 LoginVO）。 */
data class LoginResultVO(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = null,
    val expiresIn: Long? = null,
    val refreshExpiresIn: Long? = null,
    val user: UserVO? = null,
)

/** 用户信息（对应后端 UserVO）。 */
data class UserVO(
    val id: Long? = null,
    val username: String? = null,
    val email: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
    val emailVerified: Boolean? = null,
)

// ───────────────────────── 扫码登录 ─────────────────────────

/** 创建二维码票据返回（对应后端 QrCreateVO）。 */
data class QrCreateVO(
    val ticket: String? = null,
    val content: String? = null,
    val expireSeconds: Long? = null,
    val pollIntervalSeconds: Long? = null,
)

/** 轮询状态返回（对应后端 QrStatusVO）。 */
data class QrStatusVO(
    val status: String? = null,
    val expireAt: Long? = null,
    val username: String? = null,
    val login: LoginResultVO? = null,
)

/** 扫码 / 确认 / 取消请求体（对应后端 QrTicketRequest）。 */
data class QrTicketDTO(val ticket: String)

/**
 * 公告（对应后端 `t_announcement` / Announcement 实体）。
 *
 * 时间字段统一用 String 接收：后端返回 `2026-09-11T12:00:00` 这类 ISO 串，
 * Gson 默认没有 LocalDateTime 适配器，用字符串最稳妥（展示时按需截取即可）。
 */
data class AnnouncementVO(
    val id: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val type: String? = null,
    val level: String? = null,
    val versionTag: String? = null,
    val linkUrl: String? = null,
    val forceShow: Int? = null,
    val popup: Int? = null,
    val enabled: Int? = null,
    val sort: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    /** 是否每次启动都强制弹出（forceShow=1）。 */
    val forced: Boolean get() = forceShow == 1
}

/**
 * 版本检查结果（对应后端 VersionCheckVO）。
 *
 * 后端刻意不返回错误码：无更新时 [hasUpdate] 为 false，客户端按「已是最新」处理即可。
 * [publishTime] 用字符串接收（Gson 无 LocalDateTime 适配器）。
 */
data class VersionCheckVO(
    val hasUpdate: Boolean? = null,
    val forceUpdate: Boolean? = null,
    val currentVersionCode: Int? = null,
    val latestVersionCode: Int? = null,
    val latestVersionName: String? = null,
    val channel: String? = null,
    val releaseNotes: String? = null,
    val downloadUrl: String? = null,
    val fileSize: Long? = null,
    val md5: String? = null,
    val minSupported: Int? = null,
    val publishTime: String? = null,
)

// ───────────────────────── 收藏 / 历史 / 标记 ─────────────────────────

data class FavoriteVO(
    val id: Long? = null,
    val animeId: Long? = null,
    val title: String? = null,
    val imgUrl: String? = null,
    val img: String? = null,
    val detailUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class HistoryItemVO(
    val id: Long? = null,
    val animeId: Long? = null,
    val title: String? = null,
    val imgUrl: String? = null,
    val img: String? = null,
    val detailUrl: String? = null,
    val episodeName: String? = null,
    val position: Int? = null,
    val duration: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** 最近观看时间（对应后端 HistoryVO.watchedAt，格式 yyyy-MM-dd HH:mm:ss）。 */
    val watchedAt: String? = null,
    /** 最近观看时间的格式化文本，作 watchedAt 的兜底。 */
    val watchedAtText: String? = null,
)

data class RatingItemVO(
    val id: Long? = null,
    val animeId: Long? = null,
    val title: String? = null,
    val imgUrl: String? = null,
    val img: String? = null,
    val detailUrl: String? = null,
    /** 评分（10 分制，1.0 - 10.0）；UI 5 星制按 ÷2 换算展示。 */
    val score: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

// ───────────────────────── 评论 ─────────────────────────

data class CommentVO(
    val id: String? = null,
    val userId: Long? = null,
    val username: String? = null,
    val avatarLabel: String? = null,
    val content: String? = null,
    val likes: Int? = null,
    /** 创建时间（毫秒时间戳），由客户端格式化为「刚刚 / N 天前」。 */
    val createdAt: Long? = null,
    val parentId: String? = null,
    val replyCount: Int? = null,
)

data class CommentRequestDTO(
    val animeId: Long? = null,
    val detailUrl: String? = null,
    val parentId: Long? = null,
    val content: String,
)

// ───────────────────────── 请求体（与后端 DTO 对齐）─────────────────────────

data class LoginRequestDTO(
    val account: String,
    val password: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val appVersion: String? = null,
)

data class RegisterRequestDTO(
    val username: String,
    val email: String,
    val password: String,
    val code: String,
    val nickname: String? = null,
)

data class SendCodeRequestDTO(val email: String)

/** 修改个人资料：字段为 null 表示不修改（对应后端 UpdateProfileRequest）。 */
data class UpdateProfileRequestDTO(
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
)

/** 修改密码：需提供原密码（对应后端 ChangePasswordRequest）。 */
data class ChangePasswordRequestDTO(
    val oldPassword: String,
    val newPassword: String,
)

data class FavoriteRequestDTO(
    val animeId: Long? = null,
    val detailUrl: String? = null,
    val title: String? = null,
    val imgUrl: String? = null,
)

data class HistoryRequestDTO(
    val animeId: Long? = null,
    val detailUrl: String? = null,
    val title: String? = null,
    val imgUrl: String? = null,
    val position: Int? = null,
    val duration: Int? = null,
    val episodeId: Long? = null,
    val lastEpisodeName: String? = null,
    val lastEpisodeUrl: String? = null,
    val sourceId: String? = null,
    val sourceMode: String? = null,
    val finished: Boolean? = null,
)

/** 注册 / 更新设备请求（对应后端 DeviceRequest）。后台「用户分析 - 登录设备」的数据来源。 */
data class DeviceRequestDTO(
    val deviceId: String,
    val deviceName: String? = null,
    val platform: String = "android",
    val appVersion: String? = null,
    val clientTime: Long? = null,
)

data class DeviceVO(
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: String? = null,
    val appVersion: String? = null,
    val lastSyncAt: String? = null,
)

data class MarkRequestDTO(
    val animeId: Long? = null,
    val detailUrl: String? = null,
    val title: String? = null,
    val imgUrl: String? = null,
    val mark: String,
)

data class FeedbackRequestDTO(
    val content: String,
    val contact: String? = null,
    val category: String? = null,
    val images: String? = null,
    /** 设备信息（机型 + 系统版本），后台「客户端」列展示用。 */
    val deviceInfo: String? = null,
    /** App 版本（版本名 + 版本号），便于按版本定位问题。 */
    val appVersion: String? = null,
)

/** 番剧评分请求：detailUrl（外部源）与 animeId 至少一个；score 取 1.0 - 10.0。 */
data class RatingRequestDTO(
    val detailUrl: String? = null,
    val animeId: Long? = null,
    val title: String? = null,
    val imgUrl: String? = null,
    val score: Double,
)

/** 评分查询返回：未评分时 score 为 null。 */
data class ScoreVO(
    val score: Double? = null,
)
