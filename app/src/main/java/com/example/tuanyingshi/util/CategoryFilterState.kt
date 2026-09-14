package com.example.tuanyingshi.util

import androidx.compose.runtime.mutableStateOf

/**
 * 分类浏览筛选的跨导航持久状态（题材 / 年份 / 排序 / 地区 / 状态）。
 *
 * 注：番剧「类型」（番组/剧场）由首页顶部 Tab 切换表达，不再作为筛选维度，故此处不含 type。
 *
 * - 默认随进程存在；当 [ContentPrefs.filterResetOnExit] 为 true 时，筛选在导航间保持，
 *   仅在「退出 App」或「切换数据源」时由调用方重置（见 AppNavigation 与 SourceHolder）。
 * - [reset] 在退出 App（AppNavigation 拦截返回键）与切换数据源（SourceHolder.switchSource）时调用。
 *
 * 存的是筛选「值」而非文案：空字符串代表「全部」（与后端筛选选项 value 约定一致）。
 */
object CategoryFilterState {
    val genre = mutableStateOf("")
    val year = mutableStateOf("")
    val sort = mutableStateOf("")
    val region = mutableStateOf("")
    val status = mutableStateOf("")

    /** 筛选区是否展开（跨导航记住，避免每次进入都重置）。 */
    val expanded = mutableStateOf(true)

    /**
     * 各分区（zone）的滚动位置记忆。
     *
     * 用普通 HashMap 而非 State 容器：滚动位置只是 UI 记忆，写入不应触发重组；
     * 仅在进入浏览页时于组合起点读取一次（用于 LazyGridState 的初始恢复），不参与渲染订阅。
     *
     * [SavedScroll.signature] 记录当时的筛选签名，仅当签名一致时才恢复位置；
     * 筛选变化时位置被重置为顶部（见 CategoryBrowse）。
     */
    val scrollPositions = HashMap<String, SavedScroll>()

    fun reset() {
        genre.value = ""
        year.value = ""
        sort.value = ""
        region.value = ""
        status.value = ""
        expanded.value = true
        scrollPositions.clear()
    }
}

/**
 * 浏览页滚动位置快照。
 *
 * @param index 首个可见项的下标
 * @param offset 首个可见项的滚动偏移(px)
 * @param signature 当时的筛选签名（题材/年份/排序/地区/状态 拼接），用于判断能否恢复
 */
data class SavedScroll(val index: Int, val offset: Int, val signature: String)
