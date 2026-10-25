package com.example.tuanyingshi.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.local.entity.RatingEntity
import com.example.tuanyingshi.data.repository.UserRatingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 本地「我的评分」ViewModel：读取本机评分表（详情页评分在非后端模式下写入）。
 */
@HiltViewModel
class LocalRatingsViewModel @Inject constructor(
    private val repository: UserRatingRepository,
) : ViewModel() {

    val ratings: StateFlow<List<RatingEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(detailUrl: String) {
        viewModelScope.launch { repository.remove(detailUrl) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}
