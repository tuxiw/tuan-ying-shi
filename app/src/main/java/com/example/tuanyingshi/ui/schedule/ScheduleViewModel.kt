package com.example.tuanyingshi.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.repository.AnimeRepository
import com.example.tuanyingshi.data.repository.ContentFilterRepository
import com.example.tuanyingshi.util.Resource
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.filterContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val contentFilter: ContentFilterRepository,
) : ViewModel() {

    /** 当前选中的星期（1=周一 ... 7=周日），默认今天；作为标签高亮与初始页。 */
    private val _selectedWeekday = MutableStateFlow(currentWeekday())
    val selectedWeekday: StateFlow<Int> = _selectedWeekday.asStateFlow()

    /** 当前数据源（StateFlow），供页面在切源后触发按需刷新。 */
    val currentSourceId: StateFlow<SourceMode> = SourceHolder.sourceModeFlow

    /** 下拉刷新进行中（不覆盖已有数据，仅驱动 RefreshIndicator）。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 上一次已加载数据的源；用于切源后按需刷新、避免同源重复加载。 */
    private var loadedMode: SourceMode? = null

    /** 全量排期（原始，未过滤）：索引 0..6 对应 weekday 1..7。 */
    private val _allSchedules = MutableStateFlow<List<List<Anime>>>(emptyList())

    /** 排期（已按 NSFW 屏蔽 / 隐藏看过与抛弃 过滤）。 */
    val allSchedules: StateFlow<List<List<Anime>>> = combine(
        _allSchedules,
        contentFilter.nsfwBlock,
        contentFilter.hiddenDetailUrls,
    ) { week, block, hidden ->
        week.map { day -> day.filterContent(block, hidden) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadedMode = SourceHolder.currentSourceMode
        loadAll()
    }

    /** 仅当当前数据源与上次加载的不同时才重新拉取（供页面在 sourceMode 变化时调用）。 */
    fun loadIfSourceChanged() {
        val current = SourceHolder.currentSourceMode
        if (loadedMode == current) return
        loadedMode = current
        loadAll()
    }

    /** 仅更新选中星期（驱动标签高亮 / pager 初始页）；数据已由 allSchedules 预加载。 */
    fun setWeekday(w: Int) {
        _selectedWeekday.value = w
    }

    fun loadAll() {
        viewModelScope.launch { fetchWeek() }
    }

    /** 下拉刷新：保留当前数据，仅显示刷新指示器，完成后收起。 */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            fetchWeek()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchWeek() {
        when (val res = animeRepository.getWeekData()) {
            is Resource.Success -> {
                val week = res.data.orEmpty()
                // 源层约定：key 0=周一 .. 6=周日（Cycanime/Silisili/Girigiri 一致）。
                // 页面 pager 索引同为 0..6，直接按下标对齐。
                _allSchedules.value = (0..6).map { day -> week[day].orEmpty() }
            }
            else -> _allSchedules.value = emptyList()
        }
    }

    companion object {
        /** 返回 1=周一 ... 7=周日（与 getWeekData 的 key 约定一致）。 */
        fun currentWeekday(): Int {
            val d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1=周日 .. 7=周六
            return if (d == Calendar.SUNDAY) 7 else d - 1
        }

        val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}
