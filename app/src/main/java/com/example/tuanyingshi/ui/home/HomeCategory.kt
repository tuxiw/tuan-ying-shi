package com.example.tuanyingshi.ui.home

/**
 * 首页顶部横滑分类标签（精简版：仅保留推荐 / TV番组 / 剧场番组 三类）。
 * - label：展示文案
 * - showHeart：仅 RECOMMEND 在选中时左侧带粉色心形（与参考图一致）
 */
enum class HomeCategory(val label: String) {
    RECOMMEND("推荐"),
    TV("TV番组"),
    THEATER("剧场番组"),
}