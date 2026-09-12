plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "com.example.tuanyingshi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tuanyingshi"
        minSdk = 24
        targetSdk = 35
        versionCode = 60
        versionName = "0.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 按 ABI 拆分 APK：同时产出 universal（全架构）与 arm64-v8a / armeabi-v7a / x86_64 单架构包
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // 自定义打包产物名称：tu_v<版本名>-<架构>-release.apk
    // （含 ABI 段以避免多架构包重名；如需单包可关闭上面的 splits.abi）
    applicationVariants.all {
        if (buildType.name == "release") {
            val vName = defaultConfig.versionName
            outputs.all {
                val abi = (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                    .getFilters()
                    .firstOrNull { it.filterType == "ABI" }
                    ?.identifier ?: "universal"
                outputFileName = "tu_v${vName}-${abi}-release.apk"
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.material.m2)

    // jsoup
    implementation(libs.jsoup)
    // JsoupXpath：XPath 执行引擎（用于 Kazumi 式 XPath 规则与反爬验证页检测）
    implementation(libs.jsoupxpath)

    // navigation
    implementation(libs.androidx.navigation.compose)

    // image loading
    implementation(libs.coil.compose)

    // network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    // room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // state
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // serialization
    implementation(libs.kotlinx.serialization.json)

    // video player
    implementation(project(":video-player"))

    // danmaku
    implementation(project(":danmaku"))

    // download
    implementation(project(":download"))

    // 扫码登录：相机扫码端（zxing-android-embedded 含 zxing core + 扫码 Activity）
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
