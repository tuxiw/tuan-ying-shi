package com.example.tuanyingshi.data.remote.girigiri

import org.json.JSONObject

/**
 * Girigiri API JSON 解析（org.json，零依赖）。
 *
 * 覆盖：
 * - weekList：`info:[{week, video[]}]`
 * - vodSortSearch / get_list：`info:{total, rows[]}`
 * - get_detail：`info:{...vod 全字段, vod_play_from, vod_play_url}`
 * - getAllTagOption：`info:{vod_lang[], vod_version[], vod_state[], vod_class[]}`
 * - get_year / get_area / get_class：`info:{total, rows[]}`（rows 为字符串数组）
 * - likeList：`info:[{...vod}]`
 */

/** macCMS 成功码。 */
internal fun JSONObject.isApiOk(): Boolean = optInt("code", 0) == 1

/** 取字符串字段，兼容 JSONObject.NULL 与数字型字段。 */
internal fun JSONObject.str(key: String): String {
    if (isNull(key)) return ""
    val v = optString(key, "")
    return if (v.isEmpty() || v == "null") "" else v.trim()
}

/** 取 Long 字段，兼容 "123" 字符串与 null。 */
internal fun JSONObject.longOrZero(key: String): Long = str(key).toLongOrNull() ?: 0L

/** 取 Int 字段，兼容 "12" 字符串与 null。 */
internal fun JSONObject.intOrZero(key: String): Int = str(key).toIntOrNull() ?: 0

/** info 为对象时返回。 */
internal fun JSONObject.infoObj(): JSONObject? = optJSONObject("info")

/** info 为数组时返回元素列表。 */
internal fun JSONObject.infoArray(): List<JSONObject>? =
    optJSONArray("info")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }

// ───────────────────────── 列表行 ─────────────────────────

internal fun parseVod(o: JSONObject): GirigiriVod = GirigiriVod(
    vodId = o.longOrZero("vod_id"),
    vodName = o.str("vod_name"),
    vodPic = o.str("vod_pic"),
    vodRemarks = o.str("vod_remarks"),
    vodScore = o.str("vod_score"),
    vodArea = o.str("vod_area"),
    vodYear = o.str("vod_year"),
    vodHits = o.longOrZero("vod_hits"),
    vodBlurb = o.str("vod_blurb"),
    vodVersion = o.str("vod_version"),
    vodState = o.str("vod_state"),
    vodClass = o.str("vod_class"),
    vodActor = o.str("vod_actor"),
    vodTime = o.str("vod_time"),
)

// ───────────────────────── 周日程 ─────────────────────────

/** weekList：`info:[{week, video[]}]`。week 1=周一 .. 7=周日。 */
internal fun parseWeekList(body: String): List<GirigiriWeekDay> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    if (!root.isApiOk()) return emptyList()
    return root.infoArray()?.mapNotNull { day ->
        val week = day.intOrZero("week")
        val videos = day.optJSONArray("video")
            ?.let { arr -> (0 until arr.length()).map { parseVod(arr.getJSONObject(it)) } }
            .orEmpty()
        if (week <= 0) null else GirigiriWeekDay(week = week, videos = videos)
    }.orEmpty()
}

// ───────────────────────── 分页列表 ─────────────────────────

/** get_list / vodSortSearch：`info:{total, rows[]}`。 */
internal fun parseListPage(body: String): GirigiriListPage {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return GirigiriListPage(0, emptyList())
    if (!root.isApiOk()) return GirigiriListPage(0, emptyList())
    val info = root.infoObj() ?: return GirigiriListPage(0, emptyList())
    val rows = info.optJSONArray("rows")
        ?.let { arr -> (0 until arr.length()).map { parseVod(arr.getJSONObject(it)) } }
        .orEmpty()
    return GirigiriListPage(total = info.intOrZero("total"), rows = rows)
}

/** likeList：`info:[{...vod}]`。 */
internal fun parseLikeList(body: String): List<GirigiriVod> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    if (!root.isApiOk()) return emptyList()
    return root.infoArray()?.map { parseVod(it) }.orEmpty()
}

// ───────────────────────── 详情 ─────────────────────────

/** get_detail：`info:{...}`。 */
internal fun parseDetail(body: String): GirigiriDetail? {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
    if (!root.isApiOk()) return null
    val info = root.infoObj() ?: return null
    val vodId = info.longOrZero("vod_id")
    if (vodId <= 0L) return null
    return GirigiriDetail(
        vodId = vodId,
        vodName = info.str("vod_name"),
        vodPic = info.str("vod_pic"),
        vodBlurb = info.str("vod_blurb"),
        vodContent = info.str("vod_content"),
        vodRemarks = info.str("vod_remarks"),
        vodScore = info.str("vod_score"),
        vodYear = info.str("vod_year"),
        vodArea = info.str("vod_area"),
        vodLang = info.str("vod_lang"),
        vodVersion = info.str("vod_version"),
        vodState = info.str("vod_state"),
        vodClass = info.str("vod_class"),
        vodTag = info.str("vod_tag"),
        vodActor = info.str("vod_actor"),
        vodDirector = info.str("vod_director"),
        vodWriter = info.str("vod_writer"),
        vodTotal = info.intOrZero("vod_total"),
        vodIsend = info.intOrZero("vod_isend"),
        playLines = parsePlayLines(info.str("vod_play_from"), info.str("vod_play_url")),
    )
}

/**
 * macCMS 播放地址拆分：
 * - 线路（播放器）：vod_play_from 以 `$$$` 分隔，与 vod_play_url 的 `$$$` 段按索引对应；
 * - 集：每段内以 `#` 分隔，单集格式 `名称$地址`。
 */
internal fun parsePlayLines(from: String, url: String): List<GirigiriPlayLine> {
    if (url.isBlank()) return emptyList()
    val froms = from.split("$$$")
    return url.split("$$$").mapIndexedNotNull { index, line ->
        if (line.isBlank()) return@mapIndexedNotNull null
        val episodes = line.split("#").mapNotNull { segment ->
            if (segment.isBlank()) return@mapNotNull null
            val name = segment.substringBefore("$").trim()
            val episodeUrl = segment.substringAfter("$", "").trim()
            if (episodeUrl.isBlank()) null else GirigiriEpisode(name = name, url = episodeUrl)
        }
        if (episodes.isEmpty()) {
            null
        } else {
            GirigiriPlayLine(from = froms.getOrNull(index)?.trim().orEmpty(), episodes = episodes)
        }
    }
}

// ───────────────────────── 筛选项 ─────────────────────────

/**
 * 合并四个筛选项接口的返回：
 * - getAllTagOption：`info:{vod_lang[], vod_version[], vod_state[], vod_class[]}`
 * - get_year / get_area / get_class：`info:{total, rows[]}`（rows 为字符串数组）
 */
internal fun parseFilterOptions(
    tagBody: String?,
    yearBody: String?,
    areaBody: String?,
    classBody: String?,
): GirigiriFilterOptions {
    fun obj(body: String?): JSONObject? =
        body?.let { runCatching { JSONObject(it) }.getOrNull() }?.takeIf { it.isApiOk() }?.infoObj()

    val tag = obj(tagBody)
    fun stringArray(o: JSONObject?, key: String): List<String> =
        o?.optJSONArray(key)
            ?.let { arr -> (0 until arr.length()).map { arr.optString(it).trim() } }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    return GirigiriFilterOptions(
        classes = obj(classBody)?.infoStringArray()
            ?: stringArray(tag, "vod_class"),
        years = obj(yearBody)?.infoStringArray().orEmpty(),
        areas = obj(areaBody)?.infoStringArray().orEmpty(),
        langs = stringArray(tag, "vod_lang"),
        versions = stringArray(tag, "vod_version"),
        states = stringArray(tag, "vod_state"),
    )
}

/** info.rows 为字符串数组（get_year / get_area / get_class 形态）。 */
private fun JSONObject.infoStringArray(): List<String>? {
    val info = infoObj() ?: return null
    return info.optJSONArray("rows")
        ?.let { arr -> (0 until arr.length()).map { arr.optString(it).trim() } }
        ?.filter { it.isNotEmpty() }
}
