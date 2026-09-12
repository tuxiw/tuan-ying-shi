package com.example.tuanyingshi.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.local.entity.HistoryEntity
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.data.remote.backend.HistoryItemVO
import com.example.tuanyingshi.data.repository.HistoryRepository
import com.example.tuanyingshi.util.BackendPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * 浏览历史 ViewModel：后端模式下从自建后端 /user/history 拉取（云端同步），
 * 否则读取本地 Room。统一映射为 [HistoryItemUi] 供界面渲染，删除 / 清空亦按当前模式分流。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _history = MutableStateFlow<List<HistoryItemUi>>(emptyList())
    val history: StateFlow<List<HistoryItemUi>> = _history.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _history.value = if (BackendPrefs.isBackendMode()) {
                runCatching { BackendClient.api.history(1, 100).data?.records.orEmpty() }
                    .getOrDefault(emptyList())
                    .map { it.toUi() }
            } else {
                historyRepository.observeHistory().first().map { it.toUi() }
            }
        }
    }

    fun remove(itemId: String) {
        viewModelScope.launch {
            // 后端模式下由 HistoryRepository 内部按 animeId 分流到 /user/history 删除；
            // itemId 传入的是 HistoryItemUi.detailUrl（后端模式即 animeId）。
            historyRepository.remove(itemId)
            load()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            if (BackendPrefs.isBackendMode()) {
                runCatching { BackendClient.api.clearHistory() }
            } else {
                historyRepository.deleteAll()
            }
            load()
        }
    }
}

/** 历史条目的统一展示模型（本地 / 后端共用）。 */
data class HistoryItemUi(
    val id: String,
    val detailUrl: String,
    val title: String,
    val imgUrl: String?,
    val episodeName: String?,
    val updatedAt: Long,
)

private fun HistoryEntity.toUi() = HistoryItemUi(
    id = detailUrl,
    detailUrl = detailUrl,
    title = title,
    imgUrl = imgUrl,
    episodeName = lastEpisodeName,
    updatedAt = updatedAt,
)

private fun HistoryItemVO.toUi() = HistoryItemUi(
    id = (id?.toString() ?: detailUrl).orEmpty(),
    detailUrl = (animeId?.toString() ?: detailUrl).orEmpty(),
    title = title ?: "未命名",
    imgUrl = imgUrl ?: img,
    episodeName = episodeName,
    // 后端时间字段是 watchedAt（updatedAt/createdAt 后端并不返回，之前取到 null 被解析成 0）
    updatedAt = parseTime(watchedAt ?: watchedAtText),
)

private fun parseTime(s: String?): Long {
    if (s.isNullOrBlank()) return 0L
    return runCatching {
        val normalized = s.replace(" ", "T").replace("Z", "")
        // 后端下发的是服务器本地时间（无时区后缀），按系统时区解析；用 UTC 会差 8 小时
        LocalDateTime.parse(normalized).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrDefault(0L)
}
