package com.example.tuanyingshi.util

const val CROSSFADE_DURATION = 500
const val VIDEO_ASPECT_RATIO = 1.778f
const val LOW_CONTENT_ALPHA = 0.35f

val TABS = listOf("一", "二", "三", "四", "五", "六", "日")

const val CRASH_LOG_FILE = "anime_crash_logs.txt"

const val ANIME_DATABASE = "tuanying_db"
const val FAVOURITE_TABLE = "favourite_table"
const val HISTORY_TABLE = "history_table"
const val USER_ANIME_STATUS_TABLE = "user_anime_status_table"
/** 本地评分表（非后端模式下的「我的评分」）。 */
const val RATING_TABLE = "rating_table"
const val EPISODE_TABLE = "episode_table"
const val DOWNLOAD_TABLE = "download_table"
const val DOWNLOAD_DETAIL_TABLE = "download_detail_table"

const val SEARCH_PAGE_SIZE = 10

const val KEY_DOWNLOAD_UPDATE_URL = "downloadUpdateUrl"
const val KEY_SOURCE_MODE = "source_mode"
const val KEY_SOURCE_DOMAIN = "source_domain"
const val KEY_DOWNLOAD_LOCATION_URI = "download_location_uri"

const val DefaultUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3"

/**
 * Cloudflare 验证专用的现代移动端 Chrome UA。
 *
 * 上面那个 58 版桌面 UA 早在 CF 眼里「不支持」——5 秒盾会给老浏览器持续发挑战或直接拒绝。
 * 过盾时统一改用这个 UA，并且由 SourceCookieManager 把 UA 与 clearance 一起记住，
 * 后续 OkHttp 请求自动沿用同一 UA（UA 不一致会让 clearance 立即失效）。
 */
const val ChromeMobileUserAgent =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
