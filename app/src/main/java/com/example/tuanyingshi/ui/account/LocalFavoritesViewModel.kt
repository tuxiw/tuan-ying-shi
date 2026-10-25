package com.example.tuanyingshi.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.local.entity.FavoriteEntity
import com.example.tuanyingshi.data.repository.FavoriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 本地「我的收藏」ViewModel：直接读本机收藏表，与后端登录无关。
 */
@HiltViewModel
class LocalFavoritesViewModel @Inject constructor(
    private val repository: FavoriteRepository,
) : ViewModel() {

    val favorites: StateFlow<List<FavoriteEntity>> = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(detailUrl: String) {
        viewModelScope.launch { repository.remove(detailUrl) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}
