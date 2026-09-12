package com.example.tuanyingshi.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import com.example.tuanyingshi.ui.player.ExternalPlayerScreen
import com.example.tuanyingshi.util.ExternalIntentBus
import com.example.tuanyingshi.util.NavIntentBus
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.navArgument
import com.example.tuanyingshi.ui.components.BottomNavBar
import com.example.tuanyingshi.ui.components.NavigationRailBar
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.detail.DetailScreen
import com.example.tuanyingshi.ui.home.HomeScreen
import com.example.tuanyingshi.ui.schedule.ScheduleScreen
import com.example.tuanyingshi.ui.mine.MineScreen
import com.example.tuanyingshi.ui.player.PlayerScreen
import com.example.tuanyingshi.ui.player.LocalPlayerScreen
import com.example.tuanyingshi.ui.ranking.RankingScreen
import com.example.tuanyingshi.ui.settings.SettingsScreen
import com.example.tuanyingshi.ui.settings.GeneralSettingsScreen
import com.example.tuanyingshi.ui.settings.DownloadSettingsScreen
import com.example.tuanyingshi.ui.settings.PlaybackSettingsScreen
import com.example.tuanyingshi.ui.settings.DanmakuSettingsScreen
import com.example.tuanyingshi.ui.settings.NotificationSettingsScreen
import com.example.tuanyingshi.ui.settings.ProxySettingsScreen
import com.example.tuanyingshi.ui.settings.SoftwareUpdateScreen
import com.example.tuanyingshi.ui.settings.BackupRestoreScreen
import com.example.tuanyingshi.ui.personalization.PersonalizationScreen
import com.example.tuanyingshi.ui.personalization.SplashCoverSettingsScreen
import com.example.tuanyingshi.ui.settings.DataSourceScreen
import com.example.tuanyingshi.ui.settings.DanmakuDataScreen
import com.example.tuanyingshi.ui.settings.BackendSettingsScreen
import com.example.tuanyingshi.ui.settings.AboutScreen
import com.example.tuanyingshi.ui.settings.source_editor.SourceEditorScreen
import com.example.tuanyingshi.ui.settings.source_editor.SourceAnalysisScreen
import com.example.tuanyingshi.ui.settings.ThemeSettingsScreen
import com.example.tuanyingshi.ui.settings.UpdateDialogHost
import com.example.tuanyingshi.ui.settings.diagnostics.DiagnosticsScreen
import com.example.tuanyingshi.ui.settings.debug.DebugScreen
import com.example.tuanyingshi.ui.search.SearchScreen
import com.example.tuanyingshi.ui.history.HistoryScreen
import com.example.tuanyingshi.ui.download.DownloadScreen
import com.example.tuanyingshi.ui.download.DownloadGroupScreen
import com.example.tuanyingshi.ui.account.LoginScreen
import com.example.tuanyingshi.ui.account.QrScanScreen
import com.example.tuanyingshi.ui.account.FavoritesScreen
import com.example.tuanyingshi.ui.account.MarksScreen
import com.example.tuanyingshi.ui.account.FeedbackScreen
import com.example.tuanyingshi.ui.account.AccountManageScreen
import com.example.tuanyingshi.ui.components.AnnouncementDialog
import com.example.tuanyingshi.ui.components.BackendAnnouncementDialog
import com.example.tuanyingshi.data.remote.backend.AnnouncementVO
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.ui.onboarding.OnboardingScreen
import com.example.tuanyingshi.util.AnnouncePrefs
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.OnboardingPrefs
import com.example.tuanyingshi.util.NetworkMonitor
import com.example.tuanyingshi.util.NotifPrefs
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import com.example.tuanyingshi.util.CategoryFilterState
import com.example.tuanyingshi.util.ContentPrefs

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = Screen.bottomRoutes.contains(currentRoute)

    // 二级退出确认：首页（根路由）按返回键时，连按两次才退出 App；退出前重置分类浏览筛选。
    val context = LocalContext.current
    val activity = context.findActivity()
    val exitConfirm by ContentPrefs.exitConfirm.collectAsStateWithLifecycle()
    var lastBackPressTime by remember { mutableStateOf(0L) }

    val atRoot = currentRoute == Screen.Home.route
    BackHandler(enabled = atRoot) {
        if (!exitConfirm) {
            CategoryFilterState.reset()
            activity?.finish()
            return@BackHandler
        }
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime <= 2000L) {
            CategoryFilterState.reset()
            activity?.finish()
        } else {
            lastBackPressTime = now
            Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show()
        }
    }

    // 首次进入 App：展示「免费说明 / 官方下载地址」公告，需阅读到底才能关闭。
    var showAnnounce by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 若尚未看过新手引导，则先展示引导（引导结束/下次启动再弹公告），避免两者在首屏叠在一起。
        // 后端模式下由后端公告接管，不再弹本地「免费说明」公告
        if (!BackendPrefs.isBackendMode() && !AnnouncePrefs.hasShown() && OnboardingPrefs.hasShown()) {
            showAnnounce = true
        }
    }

    // 后端模式：启动时拉取后端弹窗公告（forceShow=1 每次都弹，否则同一条只弹一次）
    var backendAnnounce by remember { mutableStateOf<AnnouncementVO?>(null) }
    LaunchedEffect(Unit) {
        // 与本地公告一致：先看新手引导，避免首屏两个弹窗叠在一起
        if (!BackendPrefs.isBackendMode() || !OnboardingPrefs.hasShown()) return@LaunchedEffect
        val ann = runCatching { BackendClient.api.announcementPopup() }
            .getOrNull()
            ?.takeIf { it.ok }
            ?.data
        if (ann != null && (ann.forced || !AnnouncePrefs.hasRead(ann.id))) {
            backendAnnounce = ann
        }
    }

    // 接收「用本 App 打开视频」的系统意图，导航到外部播放页。
    // SharedFlow(replay = 1) 会把最近一次意图回放给后到的收集者，避免冷启动时丢失。
    LaunchedEffect(Unit) {
        ExternalIntentBus.flow.collect { req ->
            navController.navigate(Screen.ExternalPlayer.create(req.uri.toString(), req.title ?: "")) {
                launchSingleTop = true
            }
        }
    }

    // 接收来自系统通知的「应用内跳转」请求（如「前往查看」下载列表）。
    LaunchedEffect(Unit) {
        for (route in NavIntentBus.channel) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    // 平板/折叠屏：只在底部 4 个主 Tab 页面显示左侧 NavigationRail；
    // 二级页面（详情/播放/设置/搜索等）与手机一致，不显示 Rail，靠返回按钮导航。
    val isRail = isTablet() && Screen.bottomRoutes.contains(currentRoute)
    Row(Modifier.fillMaxSize()) {
        if (isRail) {
            NavigationRailBar(navController, currentRoute)
        }
        Scaffold(
            bottomBar = {
                if (!isRail && showBottomBar) BottomNavBar(navController, currentRoute)
            }
        ) { innerPadding ->
        Column(Modifier.fillMaxSize()) {
            // 离线模式横幅：无网络时提示用户仅可观看已下载内容。
            // 顶部横幅与断网/恢复 Toast 均可在「设置 → 消息通知 → 离线模式」中单独关闭。
            val online by NetworkMonitor.isOnline.collectAsStateWithLifecycle()
            val offlineBanner by NotifPrefs.offlineBannerEnabled.collectAsStateWithLifecycle()

            // 使用途中网络状态发生变化时给一次提示（首帧不提示，避免与启动提示重复）
            var lastOnline by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(online) {
                val prev = lastOnline
                lastOnline = online
                if (prev == null || prev == online) return@LaunchedEffect
                if (!NotifPrefs.isOfflineToastEnabled()) return@LaunchedEffect
                Toast.makeText(
                    context,
                    if (online) {
                        "网络已恢复，已退出离线模式"
                    } else {
                        "网络已断开，已进入离线模式：仅可观看已下载内容"
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }

            if (!online && offlineBanner) {
                Text(
                    text = "离线模式：无网络连接，仅可观看已下载内容",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFB23A48))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // 首次启动（未看过引导）时以引导页作为起始页；否则正常从首页进入。
            val startDestination = if (OnboardingPrefs.hasShown()) Screen.Home.route else Screen.Onboarding.route
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                // 二级页面（历史/下载/我的各入口/详情/播放等）统一从右侧滑入、返回时向右滑出
                enterTransition = { slideInHorizontally { it } },
                exitTransition = { slideOutHorizontally { -it } },
                popEnterTransition = { slideInHorizontally { -it } },
                popExitTransition = { slideOutHorizontally { it } },
            ) {
            // 底部 4 个 Tab 之间切换保持原地，不做横向滑动
            composable(
                Screen.Home.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { HomeScreen(navController) }
            composable(Screen.Onboarding.route) { OnboardingScreen(navController) }
            composable(
                Screen.Schedule.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { ScheduleScreen(navController) }
            composable(
                Screen.Ranking.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { RankingScreen(navController) }
            composable(
                Screen.Mine.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { MineScreen(navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController) }
            composable(Screen.General.route) { GeneralSettingsScreen(navController) }
            composable(Screen.DownloadSettings.route) { DownloadSettingsScreen(navController) }
            composable(Screen.PlaybackSettings.route) { PlaybackSettingsScreen(navController) }
            composable(Screen.DanmakuSettings.route) { DanmakuSettingsScreen(navController) }
            composable(Screen.BackupRestore.route) { BackupRestoreScreen(navController) }
            composable(Screen.SoftwareUpdate.route) { SoftwareUpdateScreen(navController) }
            composable(Screen.Notification.route) { NotificationSettingsScreen(navController) }
            composable(Screen.ProxySettings.route) { ProxySettingsScreen(navController) }
            composable(Screen.Personalization.route) { PersonalizationScreen(navController) }
            composable(Screen.Theme.route) { ThemeSettingsScreen(navController) }
            composable(Screen.SplashCoverSettings.route) { SplashCoverSettingsScreen(navController) }
            composable(Screen.DataSource.route) { DataSourceScreen(navController) }
            composable(Screen.DanmakuData.route) { DanmakuDataScreen(navController) }
            composable(Screen.BackendSettings.route) { BackendSettingsScreen(navController) }
            composable(
                route = Screen.SourceEditor.route,
                arguments = listOf(
                    navArgument("ruleId") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                    navArgument("engine") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) { backStack ->
                val ruleId = backStack.arguments?.getString("ruleId")
                val engineArg = backStack.arguments?.getString("engine")
                val engine = engineArg?.let { runCatching { com.example.tuanyingshi.util.source_rule.RuleEngineKind.valueOf(it) }.getOrNull() }
                SourceEditorScreen(navController, ruleId, engine)
            }
            composable(
                route = Screen.SourceAnalysis.route,
                arguments = listOf(
                    navArgument("ruleId") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) { backStack ->
                val ruleId = backStack.arguments?.getString("ruleId")
                SourceAnalysisScreen(navController, ruleId)
            }
            composable(Screen.Diagnostics.route) { DiagnosticsScreen(navController) }
            composable(Screen.Debug.route) { DebugScreen(navController) }
            composable(Screen.About.route) { AboutScreen(navController) }
            composable(Screen.Search.route) { SearchScreen(navController) }
            composable(Screen.History.route) { HistoryScreen(navController) }

            composable(Screen.Login.route) { LoginScreen(navController) }
            composable(Screen.QrScan.route) { QrScanScreen(navController) }
            composable(Screen.Favorites.route) { FavoritesScreen(navController) }
            composable(Screen.Marks.route) { MarksScreen(navController) }
            composable(Screen.Feedback.route) { FeedbackScreen(navController) }
            composable(Screen.AccountManage.route) { AccountManageScreen(navController) }

            composable(Screen.Downloads.route) { DownloadScreen(navController) }

            composable(
                route = Screen.DownloadGroup.route,
                arguments = listOf(navArgument("detailUrl") { type = NavType.StringType }),
            ) { backStack ->
                val groupDetailUrl = android.net.Uri.decode(backStack.arguments?.getString("detailUrl").orEmpty())
                DownloadGroupScreen(navController, groupDetailUrl)
            }

            composable(
                route = Screen.Detail.route,
                arguments = listOf(
                    navArgument("detailUrl") { type = NavType.StringType },
                    navArgument("sourceId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStack ->
                val detailUrl = android.net.Uri.decode(backStack.arguments?.getString("detailUrl").orEmpty())
                DetailScreen(navController, detailUrl)
            }

            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("detailUrl") { type = NavType.StringType },
                    navArgument("episodeUrl") { type = NavType.StringType },
                ),
            ) { backStack ->
                val detailUrl = android.net.Uri.decode(backStack.arguments?.getString("detailUrl").orEmpty())
                val episodeUrl = android.net.Uri.decode(backStack.arguments?.getString("episodeUrl").orEmpty())
                PlayerScreen(navController, detailUrl, episodeUrl)
            }

            composable(
                route = Screen.LocalPlayer.route,
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType },
                    navArgument("detailUrl") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                    navArgument("episodeUrl") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) { backStack ->
                val filePath = android.net.Uri.decode(backStack.arguments?.getString("filePath").orEmpty())
                val groupDetailUrl = android.net.Uri.decode(backStack.arguments?.getString("detailUrl").orEmpty()).ifBlank { "" }
                val groupEpisodeUrl = android.net.Uri.decode(backStack.arguments?.getString("episodeUrl").orEmpty()).ifBlank { "" }
                LocalPlayerScreen(navController, filePath, groupDetailUrl, groupEpisodeUrl)
            }

            composable(
                route = Screen.ExternalPlayer.route,
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType },
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) { backStack ->
                val uri = android.net.Uri.decode(backStack.arguments?.getString("uri").orEmpty())
                val title = backStack.arguments?.getString("title").orEmpty().ifBlank { null }
                ExternalPlayerScreen(navController, uri, title)
            }
        }
        }
    }
    }

    if (showAnnounce) {
        AnnouncementDialog(
            onDismiss = {
                AnnouncePrefs.markShown()
                showAnnounce = false
            },
        )
    }

    // 后端公告弹窗（forceShow 的不写已读，保证每次启动都提醒）
    backendAnnounce?.let { ann ->
        BackendAnnouncementDialog(
            announcement = ann,
            onDismiss = {
                if (!ann.forced) AnnouncePrefs.markRead(ann.id)
                backendAnnounce = null
            },
        )
    }

    // 更新检查对话框（全局挂载）：发现新版本 / 手动检查结果都会在此弹窗反馈
    val updateDialog by com.example.tuanyingshi.util.UpdateChecker.dialogState.collectAsStateWithLifecycle()
    UpdateDialogHost(
        state = updateDialog,
        onDismiss = { com.example.tuanyingshi.util.UpdateChecker.dismiss() },
    )
}

/** 从 Compose 的 Context 向上回溯拿到宿主 Activity（兼容 ContextWrapper 包装）。 */
private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
