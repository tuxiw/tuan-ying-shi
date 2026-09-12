package com.example.tuanyingshi.util

import com.example.tuanyingshi.data.remote.dto.AnimeBean

/**
 * 流式搜索的中间状态模型（对齐 animeko 的「逐源到齐即显示」）。
 *
 * - [SearchChunk]：单个数据源的搜索进度。对聚合 CSS 源而言，每一条 CSS 规则是一条独立 chunk。
 *   先发一次 [done]=false 表示「正在搜索」，规则返回后再发一次 [done]=true 携带结果。
 * - [SearchStreamState]：所有 chunk 合并后的快照，供 UI 即时渲染——
 *   [items] 为当前已到的全部结果（去重），[pending] 为仍在加载的源名列表。
 */
data class SearchChunk(
    val name: String,
    val items: List<AnimeBean>,
    val done: Boolean,
)

data class SearchStreamState(
    val items: List<AnimeBean> = emptyList(),
    val pending: List<String> = emptyList(),
)

/**
 * 按**标准化标题**去重：同一番剧在多个 CSS 源出现（detailUrl 各异）时合并为一张卡片，
 * 保留首次出现者（含其 sourceId / sourceName / iconUrl），避免搜索页同番刷屏。
 * 标题完全相同才是同一番剧，不会误并不同作品。
 */
fun List<AnimeBean>.dedupeSearch(): List<AnimeBean> {
    val seen = LinkedHashSet<String>()
    val out = mutableListOf<AnimeBean>()
    for (b in this) {
        if (b.title.isBlank()) continue
        val key = b.title.lowercase().replace(Regex("\\s+"), "")
        if (seen.add(key)) out.add(b)
    }
    return out
}
