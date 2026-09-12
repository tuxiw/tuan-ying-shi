package com.example.tuanyingshi.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.domain.model.Episode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 下载页 / 详情页共用的 ViewModel：委托 DownloadManager 完成真正的下载调度，
 * 并对外暴露分组列表与实时下载列表。
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
) : ViewModel() {

    val groups: StateFlow<List<DownloadGroupUi>> = downloadManager.groups
    val items: StateFlow<List<DownloadItemUi>> = downloadManager.items

    fun enqueue(animeTitle: String, episodes: List<Episode>, detailUrl: String, animeImg: String = "") {
        viewModelScope.launch { downloadManager.enqueue(animeTitle, episodes, detailUrl, animeImg) }
    }

    fun retry(item: DownloadItemUi) = downloadManager.retry(item)

    fun remove(item: DownloadItemUi) = downloadManager.remove(item)

    fun removeGroup(detailUrl: String) = downloadManager.removeGroup(detailUrl)

    fun clearFinished() = downloadManager.clearFinished()
}
