package com.example.tuanyingshi.ui.settings

import com.example.tuanyingshi.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.data.remote.api.cycani.CycaniLoginState
import com.example.tuanyingshi.util.source_rule.KazumiWebViewVerifier
import com.example.tuanyingshi.util.source_rule.SourceRule
import com.example.tuanyingshi.util.source_rule.RuleEngineKind
import com.example.tuanyingshi.util.source_rule.CaptchaType
import com.example.tuanyingshi.util.source_rule.SourceSubscription
import com.example.tuanyingshi.util.ResourceSource
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.label
import com.example.tuanyingshi.ui.components.rememberCaptchaInputHost
import com.example.tuanyingshi.ui.navigation.Screen

/**
 * 数据管理页 —— 数据源配置子页。
 * 列出全部资源源（内置 + 自定义），点击即设为默认源（立即生效并持久化）；
 * 支持新增 / 编辑 / 删除（内置源可编辑但不可删除）。
 * 次元城（cycani）类源需登录，未登录点击弹出登录框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourceScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        // 外层 AppNavigation 已按 innerPadding 预留状态栏/导航栏，内层不再重复扣除 systemBars
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("数据源管理", color = MaterialTheme.colorScheme.onBackground) },
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
    ) { inner ->
    DataSourceScreenContent(
        viewModel = viewModel,
        onOpenRuleEditor = { ruleId, engine ->
            navController.navigate(Screen.SourceEditor.create(ruleId, engine))
        },
        onAnalyze = { ruleId ->
            navController.navigate(Screen.SourceAnalysis.create(ruleId))
        },
        modifier = Modifier.fillMaxSize().padding(inner),
    )
    }
}

/**
 * 数据源管理内容区（供手机独立页与平板设置左右布局右侧共用）。
 * 不含 TopAppBar，由外层容器提供返回/标题。
 */
@Composable
fun DataSourceScreenContent(
    viewModel: SettingsViewModel,
    onOpenRuleEditor: (String?, RuleEngineKind?) -> Unit = { _, _ -> },
    onAnalyze: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorOpen by viewModel.editorOpen.collectAsStateWithLifecycle()
    val editingSource by viewModel.editingSource.collectAsStateWithLifecycle()
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    val verifyCandidates by viewModel.verifyCandidates.collectAsStateWithLifecycle()
    val verifyingIds by viewModel.verifyingIds.collectAsStateWithLifecycle()
    val verifyMessage by viewModel.verifyMessage.collectAsStateWithLifecycle()
    var showAddTypeDialog by remember { mutableStateOf(false) }
    var showAddSubDialog by remember { mutableStateOf(false) }
    var sortAscending by remember { mutableStateOf<Boolean?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    // 验证码输入宿主：验证器在协程中挂起等待用户输入时弹出对话框
    val captchaHost = rememberCaptchaInputHost()
    LaunchedEffect(Unit) {
        KazumiWebViewVerifier.captchaInputProvider = { base64 -> captchaHost.request(base64) }
    }
    DisposableEffect(Unit) {
        onDispose { KazumiWebViewVerifier.captchaInputProvider = null }
    }

    Box(modifier) {
        // 选择数据（API 源 + 「弹弹play」聚合虚拟源）；聚合源 type==Rule 但 aggregateCss=true
        val apiSources = state.sources.filter { it.type != SourceMode.Rule || it.aggregateCss }
        // CSS 源：全部规则源（订阅 + 用户添加），聚合虚拟源除外
        val cssSources = state.sources.filter { it.type == SourceMode.Rule && !it.aggregateCss }
        // 选择数据：默认按角标标签分组排序（API源 在前，CSS源 在后，标签相同的保持原有顺序），
        // 点击排序按钮时再切换为按名称升 / 降序。
        val sortFn: (List<ResourceSource>) -> List<ResourceSource> = { list ->
            when (sortAscending) {
                true -> list.sortedBy { it.name }
                false -> list.sortedByDescending { it.name }
                null -> list.withIndex()
                    .sortedWith(
                        compareBy<IndexedValue<ResourceSource>> { apiTagPriority(it.value) }
                            .thenBy { it.index }
                    )
                    .map { it.value }
            }
        }
        val displayedApiSources = remember(apiSources, sortAscending) { sortFn(apiSources) }
        val displayedCssSources = remember(cssSources, sortAscending) { sortFn(cssSources) }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ───────────────── 数据源订阅区域 ─────────────────
            item {
                SectionHeader(
                    title = "数据源订阅",
                    subtitle = "可通过订阅添加多个数据源，自动定时更新订阅",
                    actions = {
                        IconButton(onClick = { showAddSubDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "添加订阅",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        IconButton(onClick = { viewModel.refreshAllSubscriptions() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "刷新全部订阅",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
            items(state.subscriptions, key = { it.id }) { sub ->
                SubscriptionRow(
                    sub = sub,
                    onUpdate = { viewModel.refreshSubscription(sub.id) },
                    onRemove = { viewModel.removeSubscription(sub.id) },
                )
            }

            // ───────────────── 数据源列表（选择数据 / 外部资源列表 Tab） ─────────────────
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionHeader(
                    title = "数据源列表",
                    subtitle = "选择数据后将做为番剧资源主要获取方式，外部资源将在无播放源时提供备选方案",
                actions = {
                    IconButton(
                        onClick = { viewModel.checkVerification() },
                        enabled = !isChecking,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = "检查验证",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(onClick = { showAddTypeDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加数据源",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = {
                            sortAscending = when (sortAscending) {
                                null -> true
                                true -> false
                                false -> null
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                )
            }

            // Tab 切换：选择数据（API 源 + 弹弹play 聚合源）/ 外部资源列表（订阅 + 用户添加）
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("选择数据 (${displayedApiSources.size})") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("外部资源列表 (${displayedCssSources.size})") },
                    )
                }
            }

            when (selectedTab) {
                0 -> items(displayedApiSources, key = { it.id }) { source ->
                    SourceItem(
                        source = source,
                        isDefault = source.id == state.currentSourceId,
                        selectable = true,
                        cycaniLoginState = if (source.type == SourceMode.Cycanime) state.cycaniLoginState else null,
                        onSelect = { viewModel.selectSource(source.id) },
                        onEdit = { viewModel.requestEdit(source) },
                        onDelete = { viewModel.requestDelete(source) },
                        onAnalyze = {},
                    )
                }
                1 -> items(displayedCssSources, key = { it.id }) { source ->
                    SourceItem(
                        source = source,
                        isDefault = false,
                        selectable = false,
                        cycaniLoginState = null,
                        onSelect = {},
                        onEdit = { onOpenRuleEditor(source.ruleId, null) },
                        onDelete = { viewModel.deleteRuleSource(source.ruleId ?: "") },
                        onAnalyze = { onAnalyze(source.ruleId) },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        if (state.showLoginDialog) {
            CycaniLoginDialog(
                isLoading = state.isLoggingIn,
                error = state.loginError,
                onDismiss = { viewModel.dismissLoginDialog() },
                onLogin = { username, password -> viewModel.login(username, password) },
            )
        }

        if (editorOpen) {
            SourceEditorDialog(
                editing = editingSource,
                onDismiss = { viewModel.dismissEditor() },
                onSave = { name, type, baseUrl -> viewModel.confirmSave(name, type, baseUrl) },
            )
        }

        if (showAddTypeDialog) {
            AddTypeDialog(
                onDismiss = { showAddTypeDialog = false },
                onChooseApi = {
                    showAddTypeDialog = false
                    viewModel.requestAdd()
                },
                onChooseCss = {
                    showAddTypeDialog = false
                    onOpenRuleEditor(null, RuleEngineKind.Css)
                },
                onChooseXpath = {
                    showAddTypeDialog = false
                    onOpenRuleEditor(null, RuleEngineKind.Xpath)
                },
            )
        }

        if (showAddSubDialog) {
            SubscriptionAddDialog(
                onDismiss = { showAddSubDialog = false },
                onConfirm = { url, name, fmt ->
                    showAddSubDialog = false
                    viewModel.addSubscription(
                        url,
                        name,
                        format = if (fmt == "auto") "dandanplay" else fmt,
                    )
                },
            )
        }

        // ───────────────── 一键验证检查 / 用户选择验证 ─────────────────
        if (isChecking) {
            AlertDialog(
                onDismissRequest = { /* 检查中不可取消 */ },
                confirmButton = {},
                title = { Text("检查验证状态") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("正在逐个探测资源是否需要验证…")
                    }
                },
            )
        }

        if (verifyCandidates.isNotEmpty() && !isChecking) {
            SourceVerifyDialog(
                candidates = verifyCandidates,
                captchaTypeOf = { it.antiCrawler.captchaType },
                onDismiss = { viewModel.dismissVerifyDialog() },
                onVerify = { ids -> viewModel.verifySelected(ids) },
            )
        }

        if (verifyingIds.isNotEmpty()) {
            val current = verifyCandidates.firstOrNull { it.id == verifyingIds.last() }
            AlertDialog(
                onDismissRequest = { /* 验证中不可取消 */ },
                confirmButton = {},
                title = { Text("正在验证资源") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("正在验证：${current?.name ?: "资源"}（${verifyingIds.size} 个进行中）")
                    }
                },
            )
        }

        if (verifyMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearVerifyMessage() },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearVerifyMessage() }) { Text("知道了") }
                },
                title = { Text("验证结果") },
                text = { Text(verifyMessage ?: "") },
            )
        }

        // 验证码输入弹窗必须最后渲染，确保位于验证进度弹窗之上
        captchaHost.Render()
    }
}

/** 「选择数据」Tab 默认分组依据：返回该源在默认排序中的优先级（越小越靠前）。
 * 与 [SourceItem] 的角标逻辑保持一致：API源=0，XPATH源=1，CSS源=2。 */
private fun apiTagPriority(source: ResourceSource): Int = when {
    source.aggregateCss -> 0
    source.engine == RuleEngineKind.Xpath -> 1
    source.engine == RuleEngineKind.Css -> 2
    source.type == SourceMode.Cycanime -> 0
    else -> 2
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/**
 * 添加数据源的类型选择弹窗：内置 API 源（次元城镜像等）或 CSS 规则源。
 */
@Composable
private fun AddTypeDialog(
    onDismiss: () -> Unit,
    onChooseApi: () -> Unit,
    onChooseCss: () -> Unit,
    onChooseXpath: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("添加数据源") },
        text = {
            Column {
                Text(
                    text = "请选择要添加的数据源类型：",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChooseApi() },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "内置 API 源",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = "次元城镜像等，直接填域名即可",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChooseCss() },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CSS 源（外部资源）",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = "用 CSS 选择器 + 正则自定义抓取规则",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChooseXpath() },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "XPATH 源（外部资源）",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = "Kazumi 风格 XPath 规则，支持 @keyword 占位符",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * 单个资源源卡片：
 * - 选中态：primary 淡底色 + 「默认」徽标 + 对勾图标
 * - 未选中态：圆点占位（radio）
 * - 内置源显示锁图标（不可删除）；自定义源可编辑 / 删除
 * - 次元城已登录时显示用户名，并提供退出登录按钮
 */
@Composable
private fun SourceItem(
    source: ResourceSource,
    isDefault: Boolean,
    selectable: Boolean,
    cycaniLoginState: CycaniLoginState?,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAnalyze: () -> Unit,
) {
    val isLoggedIn = cycaniLoginState is CycaniLoginState.LoggedIn
    val accountLabel = (cycaniLoginState as? CycaniLoginState.LoggedIn)?.let {
        "已登录：${it.username}"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = selectable) { onSelect() },
        shape = RoundedCornerShape(12.dp),
        color = if (isDefault) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 数据源图标：CSS 规则源显示远程图标，其余显示默认占位
            if (source.iconUrl.isNotBlank()) {
                AsyncImage(
                    model = source.iconUrl,
                    contentDescription = source.name,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    if (isDefault) {
                        Spacer(Modifier.width(8.dp))
                        TagBadge(
                            text = "默认",
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    // 类型角标：XPATH源（Xpath 引擎规则）/ CSS源（Css 引擎规则）/ API源（次元城 或 弹弹play 聚合源）
                    TagBadge(
                        text = when {
                            source.engine == RuleEngineKind.Xpath -> "XPATH源"
                            source.aggregateCss -> "API源"
                            source.engine == RuleEngineKind.Css -> "CSS源"
                            source.type == SourceMode.Cycanime -> "API源"
                            else -> "CSS源"
                        },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (source.type != SourceMode.Rule || source.aggregateCss) {
                        Spacer(Modifier.width(6.dp))
                        // 来源角标：内置 / 自定义（API 源与弹弹play 聚合源展示，内置标识置于第二位）
                        TagBadge(
                            text = if (source.isBuiltIn) "内置" else "自定义",
                            color = if (source.isBuiltIn) {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            contentColor = if (source.isBuiltIn) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                        )
                    }
                }
                if (source.baseUrl.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = source.baseUrl,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                    Text(
                        text = accountLabel
                            ?: when {
                                source.aggregateCss -> "API 获取番剧资源，视频借外部 CSS 源"
                                source.engine == RuleEngineKind.Xpath -> "XPATH源（自定义规则）"
                                source.type == SourceMode.Rule -> "CSS源（自定义规则）"
                                else -> source.type.label()
                            },
                        color = if (accountLabel != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 12.sp,
                    )
            }

            // 右侧统一用三点菜单：CSS 源不可选故不显示「选择资源」；
            // 次元城已登录时菜单里额外提供「退出登录」。
            SourceItemMoreMenu(
                isDefault = isDefault,
                isBuiltIn = source.isBuiltIn,
                selectable = selectable,
                isLoggedIn = isLoggedIn,
                sourceType = source.type,
                isAggregate = source.aggregateCss,
                ruleId = source.ruleId,
                onSelect = onSelect,
                onEdit = onEdit,
                onDelete = onDelete,
                onAnalyze = onAnalyze,
            )
        }
    }
}

/**
 * 单个数据源项的「更多」菜单：
 * - API 源：选择资源 / 弹弹play（API 源）/ 编辑 / 删除 /（次元城已登录）退出登录
 * - CSS 源：编辑 / 删除
 */
@Composable
private fun SourceItemMoreMenu(
    isDefault: Boolean,
    isBuiltIn: Boolean,
    selectable: Boolean,
    isLoggedIn: Boolean,
    sourceType: SourceMode,
    isAggregate: Boolean,
    ruleId: String?,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAnalyze: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "更多操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            if (selectable && !isDefault) {
                DropdownMenuItem(
                    text = { Text("选择资源") },
                    onClick = {
                        menuExpanded = false
                        onSelect()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("编辑") },
                enabled = !isBuiltIn && !isAggregate,
                onClick = {
                    menuExpanded = false
                    onEdit()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            // 调试模式（仅 Debug 构建）专属：CSS 单条规则源的深度检查工具入口
            if (BuildConfig.DEBUG && sourceType == SourceMode.Rule && !isAggregate && !ruleId.isNullOrBlank()) {
                DropdownMenuItem(
                    text = { Text("调试分析") },
                    onClick = {
                        menuExpanded = false
                        onAnalyze()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(if (isBuiltIn) "内置源不可删除" else "删除") },
                enabled = !isBuiltIn,
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            if (isLoggedIn && sourceType == SourceMode.Cycanime) {
                DropdownMenuItem(
                    text = { Text("退出登录") },
                    onClick = {
                        menuExpanded = false
                        onSelect()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }
    }
}

/**
 * 小标签（角标）：用于展示来源类型 / 内置·自定义 / 默认 等。
 */
@Composable
private fun TagBadge(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(shape = RoundedCornerShape(6.dp), color = color) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * 单个订阅行：参考弹弹 play / CreamPlayer 风格。
 * - 标题显示订阅地址；
 * - 副标题显示「每 1h 自动更新，xx分钟前更新成功/失败，包含 xx 个数据源」；
 * - 右侧三点菜单提供「刷新」「删除」。
 */
@Composable
private fun SubscriptionRow(
    sub: SourceSubscription,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sub.url,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatUpdateStatus(sub),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("刷新") },
                        onClick = {
                            menuExpanded = false
                            onUpdate()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 订阅更新状态文案：每1h自动更新，xx分钟前更新成功/失败，包含xx个数据源（格式：xxx）。 */
private fun formatUpdateStatus(sub: SourceSubscription): String {
    val count = sub.sourceIds.size
    val formatLabel = when (sub.format) {
        "kazumi" -> "Kazumi"
        "dandanplay" -> "弹弹play"
        else -> "自动"
    }
    val prefix = "每1h自动更新，包含 $count 个数据源，格式：$formatLabel"
    return if (sub.lastUpdateTime == 0L) {
        "$prefix，尚未更新"
    } else {
        val mins = ((System.currentTimeMillis() - sub.lastUpdateTime) / 60000).toInt()
        val ago = when {
            mins < 1 -> "刚刚"
            mins < 60 -> "${mins}分钟前"
            else -> "${mins / 60}小时前"
        }
        val result = if (sub.lastUpdateSuccess) "更新成功" else "更新失败"
        "每1h自动更新,${ago}${result},包含 $count 个数据源,格式:$formatLabel"
    }
}

/**
 * 新增订阅弹窗：输入订阅 JSON 地址、可选自定义名称、选择格式（自动 / 弹弹 play / Kazumi）。
 */
@Composable
private fun SubscriptionAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("auto") }
    var error by remember { mutableStateOf<String?>(null) }
    var formatMenuExpanded by remember { mutableStateOf(false) }

    val formatOptions = listOf(
        "auto" to "自动识别",
        "dandanplay" to "弹弹 play",
        "kazumi" to "Kazumi",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isBlank()) {
                        error = "请输入订阅地址"
                        return@TextButton
                    }
                    onConfirm(url.trim(), name.trim(), format)
                }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("添加订阅") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("订阅地址") },
                    placeholder = { Text("https://example.com/v1/css.json") },
                    singleLine = true,
                    isError = error != null && url.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（可选）") },
                    placeholder = { Text("留空则自动用域名命名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = formatOptions.first { it.first == format }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("订阅格式") },
                        trailingIcon = {
                            IconButton(onClick = { formatMenuExpanded = true }) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "选择格式")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = formatMenuExpanded,
                        onDismissRequest = { formatMenuExpanded = false },
                    ) {
                        formatOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    format = key
                                    formatMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

/**
 * 验证选择弹窗：列出「检查」后判定需要验证的资源，用户勾选要验证哪些。
 * 点击「验证所选」即按勾选项逐个触发 WebView 验证（图片验证码弹输入、自动点击/脚本自动完成）。
 */
@Composable
private fun SourceVerifyDialog(
    candidates: List<SourceRule>,
    captchaTypeOf: (SourceRule) -> Int,
    onDismiss: () -> Unit,
    onVerify: (List<String>) -> Unit,
) {
    var selected by remember(candidates) { mutableStateOf(candidates.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onVerify(selected.toList()) },
                enabled = selected.isNotEmpty(),
            ) { Text("验证所选 (${selected.size})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("以下资源需要验证") },
        text = {
            Column {
                Text(
                    text = "检测到 ${candidates.size} 个资源当前弹出验证页，请勾选要验证的资源：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(candidates, key = { it.id }) { rule ->
                        val typeLabel = when (captchaTypeOf(rule)) {
                            CaptchaType.IMAGE_CAPTCHA -> "（图片验证码，需手动输入）"
                            CaptchaType.AUTO_CLICK_BUTTON -> "（自动点击验证）"
                            CaptchaType.CUSTOM_JAVASCRIPT -> "（脚本自动验证）"
                            else -> ""
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (selected.contains(rule.id)) {
                                        selected - rule.id
                                    } else {
                                        selected + rule.id
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected.contains(rule.id),
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + rule.id else selected - rule.id
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    rule.name,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                )
                                if (typeLabel.isNotBlank()) {
                                    Text(
                                        typeLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

/**
 * 资源源新增 / 编辑弹窗。
 * [editing] 为 null 表示新增；非 null 表示编辑（预填名称 / 类型 / 域名，保留内置标记）。
 */
@Composable
private fun SourceEditorDialog(
    editing: ResourceSource?,
    onDismiss: () -> Unit,
    onSave: (String, SourceMode, String) -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var baseUrl by remember { mutableStateOf(editing?.baseUrl ?: "") }
    var type by remember { mutableStateOf(editing?.type ?: SourceMode.Silisili) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        nameError = "请输入名称"
                        return@TextButton
                    }
                    if (baseUrl.isBlank()) {
                        nameError = "请输入域名"
                        return@TextButton
                    }
                    onSave(name.trim(), type, baseUrl.trim())
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text(if (editing == null) "添加资源源" else "编辑资源源") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("名称") },
                    singleLine = true,
                    isError = nameError != null && name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                // 解析器类型选择
                Box {
                    OutlinedTextField(
                        value = type.label(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("解析器类型") },
                        trailingIcon = {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { typeMenuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                    ) {
                        SourceMode.entries.filter { it != SourceMode.Rule }.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label()) },
                                onClick = {
                                    type = mode
                                    typeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("域名") },
                    placeholder = { Text("例如 https://www.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nameError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = nameError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (editing?.isBuiltIn == true) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "该源为内置源，可修改名称与域名，但不可删除。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

/**
 * 次元城登录弹窗：账号 + 密码 + 错误提示 + 加载圈。
 */
@Composable
private fun CycaniLoginDialog(
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        confirmButton = {
            TextButton(
                onClick = { onLogin(username, password) },
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("登录")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
            ) { Text("取消") }
        },
        title = { Text("登录次元城") },
        text = {
            Column {
                Text(
                    text = "切换到次元城数据源需要先登录源站账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("账号") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}
