package com.example.tuanyingshi.util

import com.example.tuanyingshi.TuanyingApp

/**
 * 「免费说明 / 官方下载地址」公告的展示记录。
 * 仅首次进入 App 时弹出一次；用户阅读到底并点击「我知道了」后写入标记，之后不再弹出。
 */
object AnnouncePrefs {
    private const val KEY_ANNOUNCE_SHOWN = "announce_notice_shown"
    private const val KEY_ANNOUNCE_READ_IDS = "announce_backend_read_ids"

    private val prefs = TuanyingApp.getInstance().preferences

    /** 是否已经展示过公告（首次或曾被中断未读完时为 false）。 */
    fun hasShown(): Boolean = prefs.getBoolean(KEY_ANNOUNCE_SHOWN, false)

    /** 用户阅读到底并确认后调用，标记公告已展示。 */
    fun markShown() {
        prefs.edit().putBoolean(KEY_ANNOUNCE_SHOWN, true).apply()
    }

    /**
     * 后端公告的已读记录（按公告 id）。
     * 用于「同一条公告只弹一次」；`forceShow` 的公告调用方不应写入，以保证每次启动都弹。
     */
    private fun readIds(): Set<String> = prefs.getStringSet(KEY_ANNOUNCE_READ_IDS, emptySet()) ?: emptySet()

    /** 该后端公告是否已经弹过并确认过。 */
    fun hasRead(id: Long?): Boolean = id != null && readIds().contains(id.toString())

    /** 标记某条后端公告已读。 */
    fun markRead(id: Long?) {
        if (id == null) return
        val next = readIds().toMutableSet().apply { add(id.toString()) }
        prefs.edit().putStringSet(KEY_ANNOUNCE_READ_IDS, next).apply()
    }
}
