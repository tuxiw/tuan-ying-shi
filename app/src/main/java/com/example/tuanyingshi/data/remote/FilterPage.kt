package com.example.tuanyingshi.data.remote

import com.example.tuanyingshi.data.remote.dto.AnimeBean

/**
 * 分类筛选分页结果。
 *
 * - [items]：本页番剧列表；
 * - [hasMore]：后端是否还有下一页（来自 [ApiPage.hasMore]）；本地/不支持分页的源恒为 false。
 *
 * 用泛型以便 API 层返回 [AnimeBean]、Repository 层映射为 domain [Anime]。
 */
data class FilterPage<T>(
    val items: List<T> = emptyList(),
    val hasMore: Boolean = false,
)
