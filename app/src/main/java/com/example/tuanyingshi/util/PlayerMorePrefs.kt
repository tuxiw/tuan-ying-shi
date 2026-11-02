package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp
import com.lanlinju.videoplayer.MoreItemKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「更多」面板的条目可见性（由「编辑播放器」维护）。
 *
 * 只存「被隐藏的条目」：新增功能默认可见，老用户升级不会被一堆新开关淹没；
 * 存 name 而不是序号，枚举改名 / 插入新项也不会错位。
 */
object PlayerMorePrefs {
    private const val KEY_HIDDEN_ITEMS = "player_more_hidden_items"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _hiddenItems =
        MutableStateFlow(readHidden(prefs.getString(KEY_HIDDEN_ITEMS, "")))
    val hiddenItems: StateFlow<Set<MoreItemKey>> = _hiddenItems.asStateFlow()

    private fun readHidden(raw: String?): Set<MoreItemKey> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',').mapNotNull { name ->
            runCatching { MoreItemKey.valueOf(name) }.getOrNull()
        }.toSet()
    }

    /** 切换某个条目的显示 / 隐藏。 */
    fun toggle(item: MoreItemKey, hidden: Boolean) {
        val next = if (hidden) _hiddenItems.value + item else _hiddenItems.value - item
        persist(next)
    }

    /** 全部恢复显示。 */
    fun showAll() = persist(emptySet())

    private fun persist(set: Set<MoreItemKey>) {
        prefs.edit().putString(KEY_HIDDEN_ITEMS, set.joinToString(",") { it.name }).apply()
        _hiddenItems.value = set
    }
}
