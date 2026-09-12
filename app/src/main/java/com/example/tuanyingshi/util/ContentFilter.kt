package com.example.tuanyingshi.util

import com.example.tuanyingshi.domain.model.Anime

/** NSFW 识别关键词（大小写不敏感匹配番剧标签）。 */
private val NSFW_KEYWORDS = listOf("成人", "肉番", "NSFW", "R18")

/** 给定标签集合是否命中 NSFW。 */
fun isNsfw(tags: List<String>): Boolean =
    tags.any { tag -> NSFW_KEYWORDS.any { kw -> tag.contains(kw, ignoreCase = true) } }

/** 番剧是否命中 NSFW（标签大小写不敏感）。 */
fun Anime.isNsfw(): Boolean = isNsfw(tags)

/**
 * 按内容过滤规则过滤番剧列表：
 * - [blockNsfw] 为真时剔除 NSFW 番剧；
 * - [hidden] 为需隐藏的 detailUrl 集合（看过 ∪ 抛弃）时剔除对应番剧。
 */
fun List<Anime>.filterContent(blockNsfw: Boolean, hidden: Set<String>): List<Anime> =
    filterNot { (blockNsfw && it.isNsfw()) || it.detailUrl in hidden }
