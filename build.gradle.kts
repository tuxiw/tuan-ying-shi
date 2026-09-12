// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    // 与 Hilt 同作用域声明 KSP(apply false), 否则 Hilt 插件在其类加载器中找不到 KSP 的 KspTaskJvm (dagger #3965)
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
}