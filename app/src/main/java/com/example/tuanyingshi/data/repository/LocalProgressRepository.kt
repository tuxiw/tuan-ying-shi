package com.example.tuanyingshi.data.repository

/**
 * 本地 / 已下载视频的播放进度（续播位置）仓储。
 *
 * 仅落本地库、绝不与后端同步（本地文件不对应任何在线 history）。
 * key 为文件绝对路径或播放用的稳定 url（content:// 已映射到真实文件或缓存文件后即为稳定路径）。
 */
interface LocalProgressRepository {
    /** 取续播进度（毫秒），无记录返回 0。 */
    suspend fun getPosition(key: String): Long

    /**
     * 保存进度（仅本地）。position 为毫秒。
     * 与在线一致：观看不足 5s 不写（避免刚打开就被记成 0 把续播位置洗掉）；
     * 进度已接近片尾（末尾 10s 内）视为看完，删除记录让下次从头播放。
     */
    suspend fun save(key: String, name: String, position: Long, duration: Long = 0L)

    /** 删除某文件的进度记录（如删除本地文件或看完清档）。 */
    suspend fun remove(key: String)
}
