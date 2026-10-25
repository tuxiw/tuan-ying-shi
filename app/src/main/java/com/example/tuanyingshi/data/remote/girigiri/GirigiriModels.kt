package com.example.tuanyingshi.data.remote.girigiri

/**
 * Girigiri（girigirilove.com）API 资源数据模型。
 *
 * 站点为 macCMS 架构，接口统一返回 `{"code":1,"msg":"...","info":...}`：
 * - 列表类（get_list / vodSortSearch / likeList）info 为 `{total, rows[]}`；
 * - 周日程（weekList）info 为 `[{week, video[]}]`；
 * - 详情（get_detail）info 为完整 vod 记录（播放地址在 vod_play_from / vod_play_url）。
 *
 * 注意：这是「API 资源」型数据源（对照 [com.example.tuanyingshi.util.SourceMode.Girigiri]），
 * 不走通用 CSS 规则引擎；数据由 [GirigiriApi] 直连 m3u8 接口、[GirigiriParser] 解析得到。
 */

/**
 * 列表行（get_list / vodSortSearch / likeList / weekList 内 video 共用）。
 *
 * @param vodTime 周日程专有的更新文案（"更新至01集 \/ 23點30分"）；列表行为空，用 [vodRemarks]。
 */
data class GirigiriVod(
    val vodId: Long,
    val vodName: String,
    val vodPic: String,
    val vodRemarks: String = "",
    val vodScore: String = "",
    val vodArea: String = "",
    val vodYear: String = "",
    val vodHits: Long = 0L,
    val vodBlurb: String = "",
    val vodVersion: String = "",
    val vodState: String = "",
    val vodClass: String = "",
    val vodActor: String = "",
    val vodTime: String = "",
)

/** 周日程一天。[week]：1=周一 .. 7=周日（源层再映射为 0..6）。 */
data class GirigiriWeekDay(
    val week: Int,
    val videos: List<GirigiriVod>,
)

/** 分页列表结果（get_list / vodSortSearch）。 */
data class GirigiriListPage(
    val total: Int,
    val rows: List<GirigiriVod>,
)

/** 播放线路内的一集（`名称$地址` 段）。 */
data class GirigiriEpisode(
    val name: String,
    val url: String,
)

/**
 * 一条播放线路（[from] 如 cht / chs，即 vod_play_from 的一个 `$$$` 段）。
 * 整站均为 m3u8 直链，无需二次解析。
 */
data class GirigiriPlayLine(
    val from: String,
    val episodes: List<GirigiriEpisode>,
)

/** 番剧详情（get_detail）。 */
data class GirigiriDetail(
    val vodId: Long,
    val vodName: String,
    val vodPic: String,
    val vodBlurb: String = "",
    val vodContent: String = "",
    val vodRemarks: String = "",
    val vodScore: String = "",
    val vodYear: String = "",
    val vodArea: String = "",
    val vodLang: String = "",
    val vodVersion: String = "",
    val vodState: String = "",
    val vodClass: String = "",
    val vodTag: String = "",
    val vodActor: String = "",
    val vodDirector: String = "",
    val vodWriter: String = "",
    val vodTotal: Int = 0,
    val vodIsend: Int = 0,
    val playLines: List<GirigiriPlayLine> = emptyList(),
)

/**
 * 筛选项（getAllTagOption + get_year + get_area + get_class 四个接口合并）。
 *
 * - [classes]：类型（喜剧 / 奇幻 / …），对应 get_class 与 getAllTagOption.vod_class；
 * - [years]：年份（含 "2000至90年代" 这类非数字值，故用 String）；
 * - [areas]：季度（十月 / 一月 / 七月 / 四月）；
 * - [langs] / [versions] / [states]：语言 / 版本（TV番 / 泡面番 / OVA）/ 原作（小说改 / 漫画改 / …）。
 */
data class GirigiriFilterOptions(
    val classes: List<String> = emptyList(),
    val years: List<String> = emptyList(),
    val areas: List<String> = emptyList(),
    val langs: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
    val states: List<String> = emptyList(),
)
