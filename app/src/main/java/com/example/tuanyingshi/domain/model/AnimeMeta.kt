package com.example.tuanyingshi.domain.model

/**
 * 团影视扩展枚举——分类筛选用（LaQoo 无此概念）。
 * 仅用于 AnimeDetail 的扩展字段和 AnimeRepository 的扩展方法。
 */
enum class Region(val label: String) { JP("日本"), CN("国漫"), US("欧美") }
enum class AnimeType(val label: String) { SERIES("番剧"), DONGHUA("国漫"), OVA("OVA") }
enum class AnimeStatus(val label: String) { ONGOING("连载中"), FINISHED("已完结") }
