package com.example.tuanyingshi.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.repository.LocalProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 本地 / 已下载播放页的续播进度桥接 ViewModel：
 * 把 [LocalProgressRepository] 注入到 Composable（本地/外部播放页用 hiltViewModel 取用）。
 *
 * 读写都透传仓储，但额外维护一份「本次会话最新进度」的内存快照（见 [latestPositions]）：
 * 从播放页打开「播放设置」再返回时，NavHost 会销毁播放页组合，回来后立刻重新读进度，
 * 而 [save] 是异步落库的 —— 只读仓储很可能读回最多 15 秒前的旧值（表现为回去后进度倒退）。
 */
@HiltViewModel
class LocalPlayerProgressViewModel @Inject constructor(
    private val repository: LocalProgressRepository,
) : ViewModel() {

    /** 本次会话内最近一次写入的进度（key → 毫秒），同步写入，读取时优先于仓储。 */
    private val latestPositions = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** 取续播进度（毫秒）：优先返回本次会话已写入的最新值，没有再查仓储。 */
    suspend fun getPosition(key: String): Long = latestPositions[key] ?: repository.getPosition(key)

    /** 保存进度（仅本地，不同步后端）。 */
    fun save(key: String, name: String, position: Long, duration: Long) {
        latestPositions[key] = position
        viewModelScope.launch {
            repository.save(key, name, position, duration)
        }
    }

    /** 删除进度记录。 */
    fun remove(key: String) {
        latestPositions.remove(key)
        viewModelScope.launch {
            repository.remove(key)
        }
    }
}
