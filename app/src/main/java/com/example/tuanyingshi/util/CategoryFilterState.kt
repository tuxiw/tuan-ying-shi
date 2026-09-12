package com.example.tuanyingshi.util

import androidx.compose.runtime.mutableStateOf

/**
 * 分类浏览筛选的跨导航持久状态（题材 / 年份 / 排序）。
 *
 * - 默认随进程存在；当 [ContentPrefs.filterResetOnExit] 为 true 时，筛选在导航间保持，
 *   仅在「退出 App」或「切换数据源」时由调用方重置（见 AppNavigation 与 SourceHolder）。
 * - [reset] 在退出 App（AppNavigation 拦截返回键）与切换数据源（SourceHolder.switchSource）时调用。
 *
 * 选项文案/默认值与 CategoryBrowse 中的首项保持一致（题材=全部 / 年份=全部 / 排序=更新时间）。
 */
object CategoryFilterState {
    val genre = mutableStateOf("全部")
    val year = mutableStateOf("全部")
    val sort = mutableStateOf("更新时间")

    fun reset() {
        genre.value = "全部"
        year.value = "全部"
        sort.value = "更新时间"
    }
}
