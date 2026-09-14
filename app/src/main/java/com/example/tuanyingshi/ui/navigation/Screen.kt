package com.example.tuanyingshi.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Home : Screen("home")
    /** 新手引导：首次启动或设置「再看一次」时展示的多页图文引导。 */
    object Onboarding : Screen("onboarding")
    object Schedule : Screen("schedule")
    object Ranking : Screen("ranking")
    object Mine : Screen("mine")

    object Settings : Screen("settings")
    object General : Screen("general")
    /** 下载设置（下载位置 / 同时下载 / 下载线程数）。 */
    object DownloadSettings : Screen("download_settings")
    /** 播放设置（自动换集等）。 */
    object PlaybackSettings : Screen("playback_settings")
    /** 弹幕设置（屏蔽 / 显示 / 样式 等）。由 PlaybackSettings 进入。 */
    object DanmakuSettings : Screen("danmaku_settings")
    /** 备份与恢复：用户配置导出 / 导入（恢复）。 */
    object BackupRestore : Screen("backup_restore")
    /** 软件更新：检查更新 / 自动检查开关。 */
    object SoftwareUpdate : Screen("software_update")
    object Notification : Screen("notification")
    object ProxySettings : Screen("proxy_settings")
    object Personalization : Screen("personalization")
    object SplashCoverSettings : Screen("splash_cover_settings")
    object Theme : Screen("theme_settings")
    object DataSource : Screen("data_source")
    /** 弹幕源管理：展示并配置弹幕资源（弹弹play 等）。 */
    object DanmakuData : Screen("danmaku_data")
    /** 后端模式设置：开启后全部内容走自建后端 API，并禁用本地数据源 / 弹幕源切换。 */
    object BackendSettings : Screen("backend_settings")
    object SourceEditor : Screen("source_editor?ruleId={ruleId}&engine={engine}") {
        fun create(ruleId: String? = null, engine: com.example.tuanyingshi.util.source_rule.RuleEngineKind? = null): String {
            val base = if (ruleId == null) "source_editor" else "source_editor?ruleId=${Uri.encode(ruleId)}"
            return if (engine == null) base else "$base&engine=${engine.name}"
        }
    }
    /** 调试模式专用：CSS 数据源测试分析工具（仅 Debug 构建入口可达）。 */
    object SourceAnalysis : Screen("source_analysis?ruleId={ruleId}") {
        fun create(ruleId: String? = null) =
            if (ruleId == null) "source_analysis" else "source_analysis?ruleId=${Uri.encode(ruleId)}"
    }
    object Diagnostics : Screen("diagnostics")
    object Debug : Screen("debug")
    object About : Screen("about")

    object Search : Screen("search")

    object History : Screen("history")

    object Downloads : Screen("downloads")

    /** 下载番剧组详情页：展示同一番剧下每集的下载进度。 */
    object DownloadGroup : Screen("download_group/{detailUrl}") {
        fun create(detailUrl: String) = "download_group/${Uri.encode(detailUrl)}"
    }

    /** 后端模式：登录 / 注册（双 Tab）页。 */
    object Login : Screen("login")
    /** 后端模式：我的收藏。 */
    object Favorites : Screen("favorites")
    /** 后端模式：我的追番（在看 / 看过 / 抛弃）。 */
    object Marks : Screen("marks")
    /** 后端模式：我的评分。 */
    object MyRatings : Screen("my_ratings")
    /** 后端模式：帮助与反馈。 */
    object Feedback : Screen("feedback")
    /** 后端模式：账号管理（头像 / 昵称 / 签名 / 修改密码）。 */
    object AccountManage : Screen("account_manage")
    /** 扫码登录的扫码端：相机扫码并确认（需后端模式 + 已登录）。 */
    object QrScan : Screen("qr_scan")

    object Detail : Screen("detail/{detailUrl}?sourceId={sourceId}") {
        fun create(detailUrl: String, sourceId: String = "") =
            "detail/${Uri.encode(detailUrl)}" + if (sourceId.isNotBlank()) "?sourceId=${Uri.encode(sourceId)}" else ""
    }

    object Player : Screen("player/{detailUrl}/{episodeUrl}") {
        fun create(detailUrl: String, episodeUrl: String) =
            "player/${Uri.encode(detailUrl)}/${Uri.encode(episodeUrl)}"
    }

    /** 本地文件播放（下载完成后离线观看）。filePath 为文件绝对路径。 */
    object LocalPlayer : Screen("local_player/{filePath}?detailUrl={detailUrl}&episodeUrl={episodeUrl}") {
        fun create(filePath: String, detailUrl: String = "", episodeUrl: String = "") =
            "local_player/${Uri.encode(filePath)}" +
                (if (detailUrl.isNotBlank()) "?detailUrl=${Uri.encode(detailUrl)}" else "") +
                (if (episodeUrl.isNotBlank()) "&episodeUrl=${Uri.encode(episodeUrl)}" else "")
    }

    /**
     * 外部打开的视频播放（系统/文件管理器「用本 App 打开」）。
     * uri 为 content:// / file:// / http(s):// 等；
     * title 为可选的展示标题（缺失时回退到文件名/路径片段）。
     */
    object ExternalPlayer : Screen("external/{uri}?title={title}") {
        fun create(uri: String, title: String = "") =
            "external/${Uri.encode(uri)}?title=${Uri.encode(title)}"
    }

    companion object {
        // 底部 4 项：首页 / 排期表 / 排行榜 / 我的
        val bottomRoutes = listOf(Home.route, Schedule.route, Ranking.route, Mine.route)
    }
}