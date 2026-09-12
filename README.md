# 团影视 (Tuanyingshi) · Android 客户端

> 一款基于 Jetpack Compose 的安卓动漫 / 番剧影视客户端。欢迎二次开发（二开）。

---

## 简介

团影视是一款主打动漫、番剧在线观看的 Android App，采用全量 Jetpack Compose (Material 3) 构建。
内置内容源规则引擎与订阅机制，可对接公开站点（如次元城等，经 WebView 渲染 + 解析，失败自动降级），
同时也支持对接自建的专属后端（账号、内容、视频、弹幕、设备上报、用户分析），实现完整的私服闭环。

本项目与后端（`springboot`，Spring Boot 3 + Java 17）、管理后台（`admin-web`，Vue3）配套使用。

---

## 功能特性

- **四大主模块**：首页 / 排期表 / 排行榜 / 我的（底部导航）
- **在线播放**：HLS (`m3u8`) 播放，基于 Media3 ExoPlayer
- **弹幕**：播放页弹幕渲染与发送
- **个人中心**：追番、观看历史、评论（需后端模式 + 已登录）
- **内容源规则引擎 + 订阅源**：`SourceRule` + `RuleExecutor`（Jsoup / CSS / 正则 / WebView），支持自定义站点与订阅
- **账号体系**：邮箱验证 + 账号密码 + JWT
- **扫码登录**：App 端「出码 / 扫一扫确认」+ 后台登录页「扫码登录」完整闭环
- **专属后端模式**（实验性，默认未开启）：对接自建后端，启用账号、内容托管、弹幕、设备上报与分析
- **新手引导**（实验性）

---

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose (Material 3) |
| 最低 / 目标 SDK | minSdk 24 / targetSdk 35 |
| 播放器 | Media3 ExoPlayer（HLS / m3u8） |
| 网络 | Retrofit + Gson |
| 扫码 | zxing-android-embedded |
| 依赖管理 | Gradle Version Catalog (`gradle/libs.versions.toml`) |
| 包名 | `com.example.tuanyingshi` |

---

## 模块结构

```
android/
├── app/          主应用模块（页面、导航、账号、扫码登录等）
├── danmaku/      弹幕模块
├── download/     下载模块
├── video-player/ 视频播放器模块
├── gradle/       Gradle Wrapper
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 环境要求

- **Android Studio** Hedgehog (2023.1) 或更高版本
- **Android SDK**：compileSdk / targetSdk 35，minSdk 24

---

## 构建与运行

```bash
# 克隆后，用 Android Studio 打开 android/ 目录（即本仓库根目录）

# 或命令行直接构建：
./gradlew assembleDebug        # 生成 Debug APK
./gradlew assembleRelease      # 生成 Release APK（需自行配置签名）
```

> 首次构建需联网下载 Gradle 与依赖。构建产物 `app/build/` 已被 `.gitignore` 忽略。

---

## 配置说明

### 专属后端模式（可选，实验性）

默认**未开启**。在 App 内「设置 → 数据 → 后端模式」填写后端地址并开启后，App 会切换为对接自建
Spring Boot 后端，启用账号登录、内容托管、弹幕、设备上报与用户分析等功能。

未开启时，App 使用内置的公开内容源（经规则引擎解析，失败降级为空集合）。

### 扫码登录

- **出码端**：登录页「扫码登录」标签，或后台登录页右上角二维码图标
- **扫码端**：已登录设备「我的 → 扫一扫」，扫描 `tuanyingshi://qrlogin?t=<ticket>` 后在手机上确认
- 仅后端模式 + 已登录的**管理员**账号可完成扫码登录

---

## 版本

| 组件 | 版本 |
|---|---|
| Android App | `v0.6.0` |
| 后端 (springboot) | `1.0.0` |
| 管理后台 (admin-web) | `1.0.0` |

---

## 开源与二次开发

本仓库为 Android 客户端源码，欢迎基于此进行二次开发（二开）。
配套的后端与后台代码位于项目仓库：[团影视 后端服务](https://gitee.com/fs529/tuan-ying-shi-backend)。

