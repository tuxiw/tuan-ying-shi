plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.lanlinju.videoplayer"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    // 播放器控件所需的圆角/直线图标（AutoAwesome / PhotoCamera / Info 等）
    implementation(libs.androidx.material.icons.extended)
    implementation(platform(libs.androidx.compose.bom))
//    api("com.google.android.exoplayer:exoplayer-core:2.19.1")
//    api("com.google.android.exoplayer:exoplayer-hls:2.19.1")
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.exoplayer.hls)
    // 视频画质增强（Anime4K 超分 / Lanczos 锐化）依赖 media3 effect 提供的 GlEffect 管线
    api(libs.androidx.media3.effect)
    // OkHttp 数据源：media3 1.4.1 的 DefaultHttpDataSource 不支持代理，
    // 用 OkHttpDataSource 挂动态 ProxySelector，让视频直链遵循 App 内代理设置。
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.okhttp)
}