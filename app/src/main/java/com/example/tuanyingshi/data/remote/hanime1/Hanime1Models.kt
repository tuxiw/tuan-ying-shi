package com.example.tuanyingshi.data.remote.hanime1

/**
 * Hanime1（hanime1.me）专属数据模型。
 *
 * 注意：这是「API 资源」型数据源（对照 [com.example.tuanyingshi.util.SourceMode.Hanime1]），
 * 不走通用 CSS 规则引擎；数据由 [Hanime1Api] + [Hanime1Parser] 直接抓取并解析 HTML 得到。
 * 模型字段对齐 Han1meViewer：横版封面 + 标题 + 观看次数 / 时长 / 上传日期，贴合 Hanime1 卡片风格。
 */

/** 单个视频卡片（首页分区、系列、相关推荐、搜索结果共用）。 */
data class Hanime1Video(
    val title: String,
    val coverUrl: String,
    val videoCode: String,
    val duration: String = "",
    val views: String = "",
    val uploadTime: String = "",
    val genre: String = "",
    val chineseTitle: String = "",
    val artistName: String = "",
    val brand: String = "",
)

/** 首页头图（Hero 轮播项）。 */
data class Hanime1Banner(
    val title: String,
    val description: String?,
    val picUrl: String,
    val videoCode: String?,
)

/** 首页一个横向分区（标题 + 视频行）。 */
data class Hanime1Category(
    val title: String,
    val videos: List<Hanime1Video>,
)

/** 首页整体数据。 */
data class Hanime1Home(
    val banner: Hanime1Banner?,
    val categories: List<Hanime1Category>,
)

/** 视频详情（播放页 / 详情页共用）。 */
data class Hanime1Detail(
    val title: String,
    val coverUrl: String,
    val chineseTitle: String?,
    val introduction: String?,
    val uploadTime: String?,
    val views: String?,
    /** 清晰度 → 直链，如 {"1080P" -> "https://...mp4"}。 */
    val videoUrls: Map<String, String>,
    val tags: List<String>,
    val artistName: String?,
    val artistGenre: String?,
    val artistAvatar: String?,
    /** 系列视频（同系列其它集 / 系列影片）。 */
    val playlist: List<Hanime1Video>,
    /** 相关推荐。 */
    val related: List<Hanime1Video>,
)

/** 单条评论（loadComment 接口解析所得）。 */
data class Hanime1Comment(
    val id: String,
    val username: String?,
    val avatar: String?,
    val text: String,
    val time: String?,
    val likes: Int?,
)

/** 评论分页结果。 */
data class Hanime1CommentPage(
    val comments: List<Hanime1Comment>,
    val hasMore: Boolean,
)
