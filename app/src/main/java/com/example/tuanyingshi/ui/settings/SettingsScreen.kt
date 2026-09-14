package com.example.tuanyingshi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.tuanyingshi.data.remote.backend.BackendAccount
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.components.maxContentWidth
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.ui.personalization.PersonalizationSettingsContent
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.DebugPrefs
import com.example.tuanyingshi.util.ProxyHolder
import com.example.tuanyingshi.util.UpdateChecker

/**
 * 设置页 —— 手机端为列表式布局，平板端为左侧分类菜单 + 右侧内容的左右布局。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
) {
    if (isTablet()) {
        SettingsTwoPaneScreen(navController)
    } else {
        SettingsListScreen(navController)
    }
}

/**
 * 手机端：列表式设置页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsListScreen(navController: NavController) {
    val debugModeEnabled by DebugPrefs.debugModeEnabled.collectAsStateWithLifecycle()
    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()
    val user by BackendAccount.userState.collectAsStateWithLifecycle()
    val loggedIn = user != null
    var proxySummary by remember { mutableStateOf(ProxyHolder.summary()) }
    // 新手引导入口易误触：用确认弹窗二次确认，避免一次轻点就全屏弹出引导页
    var showOnboardingConfirm by remember { mutableStateOf(false) }

    Scaffold(
        // 外层 AppNavigation 的 Scaffold 已按 innerPadding 预留了状态栏/导航栏高度，
        // 这里不能再重复扣除 systemBars（WindowInsets 是屏幕全局值，不会因父布局偏移变小），
        // 否则会出现双重预留，标题被推到状态栏下方很远、顶部产生错位空白带。
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                // 同理：不再重复吃一次 statusBars，避免与外层叠加
                windowInsets = WindowInsets(0),
                title = { Text("设置", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = maxContentWidth)
                .padding(innerPadding),
        ) {
            // ===== 应用 =====
            SettingsSection(title = "应用") {
                // 账号管理依赖后端账号接口，仅后端模式且已登录时才提供
                if (backendMode && loggedIn) {
                    SettingListItem(
                        icon = Icons.Filled.Person,
                        title = "账号管理",
                        onClick = { navController.navigate(Screen.AccountManage.route) },
                    )
                    SectionDivider()
                }
                SettingListItem(
                    icon = Icons.Filled.CleanHands,
                    title = "通用",
                    onClick = { navController.navigate(Screen.General.route) },
                )
                SectionDivider()
                SettingListItem(
                    icon = Icons.Filled.LightMode,
                    title = "消息通知",
                    onClick = { navController.navigate(Screen.Notification.route) },
                )
                SectionDivider()
                SettingListItem(
                    icon = Icons.Filled.Tune,
                    title = "个性功能",
                    onClick = { navController.navigate(Screen.Personalization.route) },
                )
                SectionDivider()
                SettingListItem(
                    icon = Icons.Filled.VpnKey,
                    title = "网络代理",
                    trailing = {
                        Text(
                            text = proxySummary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    onClick = { navController.navigate(Screen.ProxySettings.route) },
                )
                SectionDivider()
                SettingListItem(
                    icon = Icons.Filled.Download,
                    title = "备份与恢复",
                    onClick = { navController.navigate(Screen.BackupRestore.route) },
                )
                SectionDivider()
                SettingListItem(
                    icon = Icons.Filled.Star,
                    title = "新手引导",
                    onClick = { showOnboardingConfirm = true },
                )
            }

            // ===== 数据（后端模式 + 数据源 + 弹幕源）=====
            // 后端模式入口始终可见（便于随时开启 / 关闭）；开启后端模式后，
            // 本地数据源 / 弹幕源切换被禁用，入口隐藏（见 SourceHolder / BackendPrefs）。
            SettingsSection(title = "数据") {
                SettingListItem(
                    icon = Icons.Filled.Cloud,
                    title = "后端模式",
                    trailing = {
                        Text(
                            text = if (backendMode) "已开启" else "未开启",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    onClick = { navController.navigate(Screen.BackendSettings.route) },
                )
                if (!backendMode) {
                    SectionDivider()
                    SettingListItem(
                        icon = Icons.Filled.Info,
                        title = "数据源管理",
                        onClick = { navController.navigate(Screen.DataSource.route) },
                    )
                    SectionDivider()
                    SettingListItem(
                        icon = Icons.Filled.Subtitles,
                        title = "弹幕源管理",
                        onClick = { navController.navigate(Screen.DanmakuData.route) },
                    )
                }
            }

            // ===== 其他 =====
            SettingsSection(title = "其他") {
                SettingListItem(
                    icon = Icons.Filled.Info,
                    title = "关于 团影视",
                    onClick = { navController.navigate(Screen.About.route) },
                )
                SectionDivider()
                SettingListItem(
                    icon = Icons.Filled.SystemUpdate,
                    title = "软件更新",
                    trailing = {
                        Text(
                            text = "v${UpdateChecker.currentVersionName}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    onClick = { navController.navigate(Screen.SoftwareUpdate.route) },
                )
                if (debugModeEnabled) {
                    SectionDivider()
                    SettingListItem(
                        icon = Icons.Filled.Tune,
                        title = "调试模式",
                        onClick = { navController.navigate(Screen.Debug.route) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // 新手引导二次确认弹窗（防止误触）
        if (showOnboardingConfirm) {
            AlertDialog(
                onDismissRequest = { showOnboardingConfirm = false },
                title = { Text("查看新手引导") },
                text = { Text("是否重新查看新手引导？引导页会覆盖全屏，需看完或主动跳过才能返回。") },
                confirmButton = {
                    Button(onClick = {
                        showOnboardingConfirm = false
                        navController.navigate(Screen.Onboarding.route)
                    }) { Text("查看") }
                },
                dismissButton = {
                    TextButton(onClick = { showOnboardingConfirm = false }) { Text("取消") }
                },
            )
        }
    }
}

/**
 * 平板端：左侧设置分类菜单 + 右侧内容面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTwoPaneScreen(navController: NavController) {
    val debugModeEnabled by DebugPrefs.debugModeEnabled.collectAsStateWithLifecycle()
    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()
    val user by BackendAccount.userState.collectAsStateWithLifecycle()
    val loggedIn = user != null
    // 新手引导入口易误触：用确认弹窗二次确认
    var showOnboardingConfirm by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf("general") }
    val settingsVm: SettingsViewModel = hiltViewModel()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 左侧菜单
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(8.dp))

            MenuSectionTitle("应用")
            // 账号管理依赖后端账号接口，仅后端模式且已登录时才提供
            if (backendMode && loggedIn) {
                SettingsMenuItem(
                    id = "account",
                    selectedId = selectedId,
                    icon = Icons.Filled.Person,
                    title = "账号管理",
                    onClick = { navController.navigate(Screen.AccountManage.route) },
                )
            }
            SettingsMenuItem(
                id = "general",
                selectedId = selectedId,
                icon = Icons.Filled.CleanHands,
                title = "通用",
                onClick = { selectedId = "general" },
            )
            SettingsMenuItem(
                id = "notification",
                selectedId = selectedId,
                icon = Icons.Filled.LightMode,
                title = "消息通知",
                onClick = { selectedId = "notification" },
            )
            SettingsMenuItem(
                id = "personalization",
                selectedId = selectedId,
                icon = Icons.Filled.Tune,
                title = "个性功能",
                onClick = { selectedId = "personalization" },
            )
            SettingsMenuItem(
                id = "proxy",
                selectedId = selectedId,
                icon = Icons.Filled.VpnKey,
                title = "网络代理",
                onClick = { selectedId = "proxy" },
            )
            SettingsMenuItem(
                id = "backup",
                selectedId = selectedId,
                icon = Icons.Filled.Download,
                title = "备份与恢复",
                onClick = { selectedId = "backup" },
            )
            SettingsMenuItem(
                id = "onboarding",
                selectedId = selectedId,
                icon = Icons.Filled.Star,
                title = "新手引导",
                onClick = { showOnboardingConfirm = true },
            )

            // 后端模式入口始终可见；后端模式下禁用数据源 / 弹幕源切换，入口隐藏（见 SourceHolder / BackendPrefs）。
            MenuSectionTitle("数据")
            SettingsMenuItem(
                id = "backend",
                selectedId = selectedId,
                icon = Icons.Filled.Cloud,
                title = "后端模式",
                onClick = { selectedId = "backend" },
            )
            if (!backendMode) {
                SettingsMenuItem(
                    id = "data",
                    selectedId = selectedId,
                    icon = Icons.Filled.Info,
                    title = "数据源管理",
                    onClick = { selectedId = "data" },
                )
                SettingsMenuItem(
                    id = "danmaku",
                    selectedId = selectedId,
                    icon = Icons.Filled.Subtitles,
                    title = "弹幕源管理",
                    onClick = { selectedId = "danmaku" },
                )
            }

            MenuSectionTitle("其他")
            SettingsMenuItem(
                id = "about",
                selectedId = selectedId,
                icon = Icons.Filled.Info,
                title = "关于",
                onClick = { selectedId = "about" },
            )
            SettingsMenuItem(
                id = "software_update",
                selectedId = selectedId,
                icon = Icons.Filled.SystemUpdate,
                title = "软件更新",
                onClick = { selectedId = "software_update" },
            )
            if (debugModeEnabled) {
                SettingsMenuItem(
                    id = "debug",
                    selectedId = selectedId,
                    icon = Icons.Filled.Tune,
                    title = "调试模式",
                    onClick = { navController.navigate(Screen.Debug.route) },
                )
            }
        }

        // 右侧内容
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (selectedId) {
                "general" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    GeneralSettingsContent(navController)
                }
                "notification" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    NotificationSettingsContent()
                }
                "personalization" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PersonalizationSettingsContent(navController)
                }
                "about" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AboutSettingsContent()
                }
                "data" -> DataSourceScreenContent(
                    viewModel = settingsVm,
                    onOpenRuleEditor = { ruleId, _ ->
                        navController.navigate(Screen.SourceEditor.create(ruleId))
                    },
                    onAnalyze = { ruleId ->
                        navController.navigate(Screen.SourceAnalysis.create(ruleId))
                    },
                )
                "backend" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    BackendSettingsContent(navController)
                }
                "danmaku" -> DanmakuDataScreenContent(modifier = Modifier.fillMaxSize())
                "proxy" -> ProxySettingsContent(navController = navController, inline = true)
                "software_update" -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    SoftwareUpdateContent()
                }
                "backup" -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    BackupRestoreContent()
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    GeneralSettingsContent(navController)
                }
            }
        }

        // 新手引导二次确认弹窗（防止误触）
        if (showOnboardingConfirm) {
            AlertDialog(
                onDismissRequest = { showOnboardingConfirm = false },
                title = { Text("查看新手引导") },
                text = { Text("是否重新查看新手引导？引导页会覆盖全屏，需看完或主动跳过才能返回。") },
                confirmButton = {
                    Button(onClick = {
                        showOnboardingConfirm = false
                        navController.navigate(Screen.Onboarding.route)
                    }) { Text("查看") }
                },
                dismissButton = {
                    TextButton(onClick = { showOnboardingConfirm = false }) { Text("取消") }
                },
            )
        }
    }
}

/** 平板左侧菜单项。 */
@Composable
private fun SettingsMenuItem(
    id: String,
    selectedId: String,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    val selected = id == selectedId
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 单个设置项：图标 + 标题 + (可选尾部) + 右箭头。 */
@Composable
private fun SettingListItem(
    icon: ImageVector,
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 平板左侧菜单分组标题。 */
@Composable
private fun MenuSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * 设置分组：标题 + 圆角卡片容器。
 * 参考样式：分组标题位于卡片上方，卡片内项目以细线分隔。
 */
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                content()
            }
        }
    }
}

/**
 * 卡片内项目分隔线（左右缩进，与列表项图标/文字对齐）。
 */
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

