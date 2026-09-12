package com.example.tuanyingshi.util

import com.example.tuanyingshi.data.remote.dto.AnimeBean
import java.util.concurrent.ConcurrentHashMap

/**
 * 搜索结果内存缓存（对标 animeko 的 searchCacheTtl）。
 *
 * 痛点：每次输入（防抖后）都会重新用 WebView/OkHttp 抓页；同一关键词反复搜索
 * （来回切页、返回重搜、聚合 CSS 源重新抓取全部规则）会重复发起昂贵的抓取。
 * 这里按 (数据源 id, 关键词, 页码) 缓存搜索结果，命中直接复用，避免重复网络请求。
 *
 * 设计要点：
 * - 仅缓存**非空**结果，避免把「需要验证 / 暂时失败」的空结果缓存住
 *   （尤其 Girigiri 验证码场景：空结果不入缓存，验证后重试能正常重新抓取）。
 * - 带 TTL（默认 5 分钟），过期自动失效；切源 / 重搜不同词触发 miss。
 * - 纯内存 ConcurrentHashMap，进程级单例，不落盘。
 */
object SearchResultCache {
    private data class Entry(val data: List<AnimeBean>, val expireAt: Long)
    private val map = ConcurrentHashMap<String, Entry>()

    /** 缓存有效期。animeko 默认约 2h，这里取较短的 5min 以兼顾数据新鲜度与省流量。 */
    private const val TTL_MS = 5 * 60_000L

    private fun key(query: String, sourceId: String, page: Int): String =
        "$sourceId ${query.trim().lowercase()} $page"

    fun get(query: String, sourceId: String, page: Int): List<AnimeBean>? {
        val k = key(query, sourceId, page)
        val e = map[k] ?: return null
        if (e.expireAt < System.currentTimeMillis()) {
            map.remove(k)
            return null
        }
        return e.data
    }

    fun put(query: String, sourceId: String, page: Int, data: List<AnimeBean>) {
        if (data.isEmpty()) return // 不缓存空结果（见类注释）
        map[key(query, sourceId, page)] = Entry(data, System.currentTimeMillis() + TTL_MS)
    }

    fun clear() = map.clear()
}
