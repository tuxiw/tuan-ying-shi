package com.example.tuanyingshi.util

import com.example.tuanyingshi.R

/**
 * 系统内置开屏封面（启动图）注册表。
 *
 * 每项对应一个 drawable 资源，设置页以缩略图形式展示供用户单选；
 * [SplashActivity] 据此把 id 解析成具体 drawable 展示。
 */
data class BuiltInCover(
    val id: String,
    val drawableRes: Int,
    val name: String,
)

object BuiltInCovers {
    const val DEFAULT_ID = "default"

    val covers: List<BuiltInCover> = listOf(
        BuiltInCover(DEFAULT_ID, R.drawable.splash_screen, "默认"),
        BuiltInCover("sakura", R.drawable.splash_cover_sakura, "樱花"),
        BuiltInCover("cyber", R.drawable.splash_cover_cyber, "赛博"),
    )

    /** id → drawable 资源，找不到时回退默认封面。 */
    fun getDrawableRes(id: String): Int =
        covers.firstOrNull { it.id == id }?.drawableRes ?: R.drawable.splash_screen

    fun getById(id: String): BuiltInCover =
        covers.firstOrNull { it.id == id } ?: covers.first()
}
