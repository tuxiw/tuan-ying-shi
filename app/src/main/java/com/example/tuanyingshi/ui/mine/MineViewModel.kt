package com.example.tuanyingshi.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.remote.api.cycani.CycaniAuthManager
import com.example.tuanyingshi.data.remote.api.cycani.CycaniProfile
import com.example.tuanyingshi.data.repository.FavoriteRepository
import com.example.tuanyingshi.data.repository.HistoryRepository
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MineViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    val favorites = favoriteRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = historyRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前数据源类型（切源后自动更新）：仅「次元城」源才展示源站账号卡片。 */
    val sourceMode: StateFlow<SourceMode> = SourceHolder.sourceModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SourceHolder.currentSourceMode)

    /** 次元城账号资料（登录失效 / 退出后为 null）。 */
    val cycaniProfile: StateFlow<CycaniProfile?> = CycaniAuthManager.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CycaniAuthManager.profile.value)

    /** 是否已登录次元城（token 有效且持有资料）。 */
    val cycaniLoggedIn: StateFlow<Boolean> = cycaniProfile
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CycaniAuthManager.profile.value != null)

    /** 上次登录账号（失效后保留，用于登录页回填）。 */
    val cycaniLastUsername: String? get() = CycaniAuthManager.username

    init {
        // 进入「我的」页时补全一次资料：老版本登录可能没落 ID / 头像
        refreshCycaniProfile()
    }

    /**
     * 拉取源站账号资料。
     * @param force true 时无论本地是否已有完整资料都重新拉取。
     */
    fun refreshCycaniProfile(force: Boolean = false) {
        if (!CycaniAuthManager.isLoggedIn) return
        if (!force && CycaniAuthManager.profile.value?.userId != null) return
        viewModelScope.launch {
            runCatching { CycaniAuthManager.fetchProfile() }
        }
    }

    /** 退出次元城源站账号（不影响 app 内其它数据）。 */
    fun logoutCycani() {
        CycaniAuthManager.clearSession()
    }
}
