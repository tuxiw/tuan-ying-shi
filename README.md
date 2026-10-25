# 团影视 (Tuanyingshi) · Android 客户端

> 一款基于 Jetpack Compose 的安卓动漫 / 番剧影视客户端。欢迎二次开发（二开）。

---

## 简介

团影视是一款主打动漫、番剧在线观看的 Android App，采用全量 Jetpack Compose (Material 3) 构建。
内置多数据源：次元城（WebView 渲染 + Jsoup 解析，失败自动降级）、Girigiri / Hanime1（原生 API 客户端，
自动处理 Cloudflare 验证）、嘶哩嘶哩与弹弹play，以及自定义规则源（CSS / XPATH）与订阅机制；
同时也支持对接自建的专属后端（账号、内容、视频、弹幕、设备上报、用户分析），实现完整的私服闭环。

本项目与后端（`springboot`，Spring Boot 3 + Java 17）、管理后台（`admin-web`，Vue3）配套使用。

---

## 功能特性

- **四大主模块**：首页 / 排期表 / 排行榜 / 我的（底部导航，平板自动切换左侧 NavigationRail）
- **在线播放**：HLS (`m3u8`) 播放，基于 Media3 ExoPlayer，支持多播放源 / 线路切换
- **弹幕**：接入弹弹play 弹幕源，播放页弹幕渲染与发送
- **多数据源**：
  - 次元城：WebView 渲染 + 解析，支持源站账号登录 / 注册
  - Girigiri / Hanime1：原生 API 客户端，自动处理 Cloudflare 验证
  - 嘶哩嘶哩、弹弹play：内置聚合
  - 自定义规则源（CSS / XPATH）+ 订阅源（每小时自动更新）
  - 内置 API 源支持在「数据源管理」中设置镜像网址
- **智能数据源切换**：按 WiFi / 移动数据 / 以太网自动选择数据源，加载失败自动切换并提示
- **NSFW 内容屏蔽**：成人内容源打标，开启后自动隐藏数据源与相关内容
- **账号体系**：次元城源站账号；专属后端模式（JWT）账号；扫码登录完整闭环
- **个人中心**：浏览历史 / 收藏 / 追番 / 评分 / 我的下载
- **备份与恢复**：本地导出导入 + WebDAV 同步 + 设备直连，可按分类选择恢复范围
- **平板适配**：内容限宽居中、详情双栏布局、网格列数按屏幕宽度自适应
- **网络代理**：系统代理 / 自定义 HTTP、SOCKS 代理
- **其他**：新手引导、欢迎公告、软件更新检查、通知设置与离线模式提示

---

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言 | Kotlin 2.0 |
| UI | Jetpack Compose (Material 3) |
| 最低 / 目标 SDK | minSdk 24 / targetSdk 35（compileSdk 35） |
| 播放器 | Media3 ExoPlayer 1.4（HLS / m3u8） |
| 网络 | Retrofit 2.11 + OkHttp 4.12 + Gson |
| 依赖注入 | Hilt 2.51 |
| 本地存储 | Room 2.6 |
| 图片加载 | Coil 2.6 |
| 后台任务 | WorkManager 2.10 |
| 扫码 | zxing-android-embedded 4.3 |
| 构建 | AGP 8.9 + Gradle Version Catalog (`gradle/libs.versions.toml`) |
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

- **Android Studio** Koala (2024.1) 或更高版本（AGP 8.9）
- **JDK** 17
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

### 数据源与镜像

默认使用内置公开内容源。在 App 内「设置 → 数据 → 数据源管理」中可切换数据源；
次元城 / Girigiri / 嘶哩嘶哩 / Hanime1 等内置 API 源支持修改镜像网址（三点菜单 → 编辑），
规则源（CSS / XPATH）可自定义或通过订阅导入。

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
| Android App | `v0.8.4` |
| 后端 (springboot) | `1.0.0` |
| 管理后台 (admin-web) | `1.0.0` |

各版本安装包见 [Gitee Releases](https://gitee.com/tuxiw/tuan-ying-shi/releases)。

---

## 开源与二次开发

本仓库为 Android 客户端源码，欢迎基于此进行二次开发（二开）。
配套的后端与后台代码位于项目仓库：[团影视 后端服务](https://gitee.com/fs529/tuan-ying-shi-backend)。
