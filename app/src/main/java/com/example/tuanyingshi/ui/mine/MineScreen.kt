package com.example.tuanyingshi.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.tuanyingshi.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.data.remote.api.cycani.CycaniProfile
import com.example.tuanyingshi.data.remote.backend.BackendAccount
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.components.maxContentWidth
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.ui.theme.LocalThemePackAssets
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.ThemeMode
import com.example.tuanyingshi.util.ThemePrefs
import kotlinx.coroutines.launch

/**
 * 我的页面（头图式布局，参考 B 站「我的」页）：
 *  - 头图区：整宽大图延伸到状态栏之下（edge-to-edge）；右上角白色图标
 *    （主题套装 / 明暗切换 / 设置）；左下角圆形描边头像 + 昵称 + ID
 *    +（次元城）用户组/邮箱，可点击时右侧出现箭头（登录 / 查看次元城账号详情）
 *  - 快捷入口卡：浏览历史 / 收藏 / 追番 / 评分（后端 4 项，本地 3 项）
 *  - 功能网格卡：帮助反馈 / 我的下载 /（后端已登录）扫一扫 / 退出登录
 *
 * 平板适配（sw600dp 判定见 [isTablet]）：
 *  - 内容整体限宽 [maxContentWidth] 并 TopCenter 居中，避免超宽屏拉曝；
 *  - 头图加高到 260dp；下方双栏：左=快捷入口卡，右=功能网格卡（3 列）；
 *  - 手机端头图 220dp、单栏纵排，功能网格 3 列。
 *
 * 未应用主题套装时，头图用主色 → 次色渐变兜底，保证页面始终有头图区。
 * 后端模式下：资料区展示登录用户并支持点击登录；无后端支持的功能直接隐藏
 * （见 [BackendAccount] / [BackendPrefs]）。开发者选项入口在「设置 → 其他」，
 * 连点版本号 7 次解锁后显示。
 */
@Composable
fun MineScreen(
    navController: NavController,
    viewModel: MineViewModel = hiltViewModel(),
) {
    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()
    val user by BackendAccount.userState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // ── 次元城源站账号（仅当前数据源为次元城时展示）──
    val sourceMode by viewModel.sourceMode.collectAsStateWithLifecycle()
    val cycaniMode = !backendMode && sourceMode == SourceMode.Cycanime
    val cycaniProfile by viewModel.cycaniProfile.collectAsStateWithLifecycle()
    var showCycaniAccount by remember { mutableStateOf(false) }

    // 资料区点击（保持原语义：仅「后端模式未登录」或「次元城模式」可点）
    val profileClickable = (backendMode && user == null) || cycaniMode
    val onProfileClick: () -> Unit = {
        when {
            cycaniMode && cycaniProfile != null -> showCycaniAccount = true
            // 次元城源未登录：跳转全屏登录页
            cycaniMode -> navController.navigate(
                Screen.CycaniLogin.create(username = viewModel.cycaniLastUsername.orEmpty()),
            )
            backendMode && user == null -> navController.navigate(Screen.Login.route)
        }
    }

    // 资料文案
    val profileName = when {
        cycaniMode && cycaniProfile != null -> cycaniProfile!!.displayName
        cycaniMode -> "点击登录次元城"
        backendMode && user != null -> user!!.nickname ?: user!!.username
        else -> "点击登录"
    }
    val profileIdLine = when {
        cycaniMode && cycaniProfile != null ->
            "ID: ${cycaniProfile!!.userId ?: "—"} · 次元城"
        cycaniMode -> "未登录 · 次元城"
        backendMode && user != null -> "ID: ${user!!.id}"
        else -> "ID: 57775"
    }
    // 次元城：补充用户组 / 邮箱
    val profileExtra = if (cycaniMode) {
        listOfNotNull(
            cycaniProfile?.groupName,
            cycaniProfile?.email?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").takeIf { it.isNotBlank() }
    } else null
    // 后端头像为相对路径，需拼后端根地址
    val backendAvatarUrl = if (backendMode) BackendPrefs.absoluteUrl(user?.avatar) else null

    // 快捷入口（后端 4 项 / 本地 3 项）
    val quickEntries = buildList {
        add(QuickEntry(Icons.Filled.History, "浏览历史") { navController.navigate(Screen.History.route) })
        if (backendMode) {
            add(QuickEntry(Icons.Filled.FavoriteBorder, "我的收藏") { navController.navigate(Screen.Favorites.route) })
            add(QuickEntry(Icons.Filled.StarBorder, "我的追番") { navController.navigate(Screen.Marks.route) })
            add(QuickEntry(Icons.Filled.Star, "我的评分") { navController.navigate(Screen.MyRatings.route) })
        } else {
            // 非后端（本地数据源）：收藏与评分保存在本机，「我的评分」即本地评分
            add(QuickEntry(Icons.Filled.FavoriteBorder, "我的收藏") { navController.navigate(Screen.LocalFavorites.route) })
            add(QuickEntry(Icons.Filled.StarBorder, "我的评分") { navController.navigate(Screen.LocalRatings.route) })
        }
    }

    // 功能网格（后端模式且已登录时补充扫一扫 / 退出登录）
    val fnItems = if (backendMode) {
        buildList {
            add(FnItem(Icons.Filled.Edit, "帮助反馈") { navController.navigate(Screen.Feedback.route) })
            add(FnItem(Icons.Filled.Download, "我的下载") { navController.navigate(Screen.Downloads.route) })
            if (user != null) {
                add(FnItem(Icons.Filled.QrCodeScanner, "扫一扫") { navController.navigate(Screen.QrScan.route) })
                add(
                    FnItem(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        label = "退出登录",
                        tint = MaterialTheme.colorScheme.error,
                    ) { scope.launch { BackendAccount.logout() } },
                )
            }
        }
    } else {
        listOf(
            FnItem(Icons.Filled.Edit, "帮助反馈") { navController.navigate(Screen.Feedback.route) },
            FnItem(Icons.Filled.Download, "我的下载") { navController.navigate(Screen.Downloads.route) },
        )
    }

    val tablet = isTablet()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = maxContentWidth),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 头图区：整宽大图延伸到状态栏之下，右上角白色操作图标，
            // 头像 + 昵称 + ID 叠在图上（参考 B 站「我的」页视觉）。
            item {
                MineHeaderSection(
                    tablet = tablet,
                    name = profileName,
                    idLine = profileIdLine,
                    extra = profileExtra,
                    cycaniProfile = cycaniProfile,
                    cycaniMode = cycaniMode,
                    backendAvatarUrl = backendAvatarUrl,
                    clickable = profileClickable,
                    onClick = onProfileClick,
                    onThemeStore = { navController.navigate(Screen.ThemeStore.route) },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                )
            }

            if (tablet) {
                // 平板双栏：左=快捷入口，右=功能网格（资料已上移到头图区）
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SectionCard {
                                QuickEntriesRow(quickEntries)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            SectionCard {
                                FunGrid(fnItems, columns = 3)
                            }
                        }
                    }
                }
            } else {
                // 手机单栏纵排
                item {
                    SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        QuickEntriesRow(quickEntries)
                    }
                }
                item {
                    SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        FunGrid(fnItems, columns = 3)
                    }
                }
            }
        }
    }

    // 次元城账号详情（含退出登录）
    if (showCycaniAccount && cycaniProfile != null) {
        CycaniAccountDialog(
            profile = cycaniProfile!!,
            onDismiss = { showCycaniAccount = false },
            onLogout = {
                viewModel.logoutCycani()
                showCycaniAccount = false
            },
        )
    }
}

/**
 * 头图区（参考 B 站「我的」页视觉）：整宽大图一直延伸到状态栏之下（edge-to-edge），
 * 右上角一排白色操作图标（主题套装 / 明暗切换 / 设置），
 * 左下角圆形描边头像 + 昵称 + ID 叠加在图上；未应用主题套装时用主色渐变兜底。
 *
 * 头像优先级：次元城源站头像 > 后端头像 > Person 占位（与改造前资料卡一致）。
 * 点击行为沿用原资料卡语义：仅「后端模式未登录」或「次元城模式」可点（登录 / 账号详情）。
 */
@Composable
private fun MineHeaderSection(
    tablet: Boolean,
    name: String,
    idLine: String,
    extra: String?,
    cycaniProfile: CycaniProfile?,
    cycaniMode: Boolean,
    backendAvatarUrl: String?,
    clickable: Boolean,
    onClick: () -> Unit,
    onThemeStore: () -> Unit,
    onSettings: () -> Unit,
) {
    val header = LocalThemePackAssets.current?.headMyselfBg
    val themeConfig by ThemePrefs.config.collectAsStateWithLifecycle()
    val isDark = when (themeConfig.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val headerHeight = if (tablet) 260.dp else 220.dp
    val avatarSize = if (tablet) 76.dp else 64.dp
    val nameSize = if (tablet) 21.sp else 18.sp

    Box(modifier = Modifier.fillMaxWidth().height(headerHeight)) {
        // 背景：主题套装头图；未应用套装时主色渐变兜底，保证「我的」页始终有头图区
        if (header != null) {
            AsyncImage(
                model = header,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to MaterialTheme.colorScheme.primary,
                            1.0f to MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
            )
        }

        // 顶部渐隐暗化：edge-to-edge 下保证状态栏图标（时间/电量）在亮色头图上仍可读
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x73000000),
                        0.22f to Color.Transparent,
                    ),
                ),
        )
        // 底部渐隐暗化：保证叠加在图上的头像与白色文字始终有足够对比度
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1.0f to Color(0xB3000000),
                    ),
                ),
        )

        // 右上角操作图标（白色线形，与 B 站一致）；statusBarsPadding 避开状态栏
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 4.dp),
        ) {
            IconButton(onClick = onThemeStore) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = "主题套装",
                    tint = Color.White,
                )
            }
            // 明暗切换：若当前正跟随主题套装的明暗模式，先自动关掉「跟随套装明暗模式」，
            // 否则套装的 color_mode 会覆盖这次手动选择，点了没反应。
            IconButton(onClick = {
                if (ThemePrefs.getConfig().packFollowColorMode) ThemePrefs.setPackFollowColorMode(false)
                ThemePrefs.setMode(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK)
            }) {
                Icon(
                    imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = if (isDark) "切换到亮色" else "切换到暗色",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "设置",
                    tint = Color.White,
                )
            }
        }

        // 左下角资料行：头像 + 昵称 + ID（+ 次元城用户组/邮箱），可点时有右箭头
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .clickable(enabled = clickable, onClick = onClick)
                .padding(start = 20.dp, end = 16.dp, bottom = if (tablet) 22.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MineAvatar(
                size = avatarSize,
                cycaniMode = cycaniMode,
                cycaniProfile = cycaniProfile,
                backendAvatarUrl = backendAvatarUrl,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = nameSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = idLine,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = if (tablet) 14.sp else 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (extra != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = extra,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = if (tablet) 13.sp else 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (clickable) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(if (tablet) 28.dp else 22.dp),
                )
            }
        }
    }
}

/** 头图上的圆形头像：白色描边，与 B 站「我的」页一致。 */
@Composable
private fun MineAvatar(
    size: androidx.compose.ui.unit.Dp,
    cycaniMode: Boolean,
    cycaniProfile: CycaniProfile?,
    backendAvatarUrl: String?,
) {
    Box(
        modifier = Modifier
            .size(size)
            .border(2.dp, Color.White, CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            cycaniMode -> CycaniAvatar(
                profile = cycaniProfile,
                modifier = Modifier.fillMaxSize(),
            )
            backendAvatarUrl != null -> AsyncImage(
                model = backendAvatarUrl,
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.6f),
                    tint = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/**
 * 次元城头像：有 avatar_url 时加载网络图（相对路径自动补全源站域名），
 * 否则用用户名首字母生成主色圆形占位（源站大量账号头像为空）。
 */
@Composable
private fun CycaniAvatar(
    profile: CycaniProfile?,
    modifier: Modifier = Modifier,
) {
    val raw = profile?.avatarUrl?.takeIf { it.isNotBlank() }
    val url = raw?.let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "https://www.cycani.org${if (it.startsWith("/")) it else "/$it"}"
    }
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = "头像",
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val letter = (profile?.displayName ?: "?").trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(
                text = letter,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 次元城账号详情弹窗：展示完整资料并提供退出登录。 */
@Composable
private fun CycaniAccountDialog(
    profile: CycaniProfile,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onLogout) { Text("退出登录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text("次元城账号") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CycaniAvatar(profile = profile, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                InfoRow("昵称", profile.displayName)
                profile.username.takeIf { it.isNotBlank() }?.let { InfoRow("用户名", it) }
                InfoRow("ID", profile.userId?.toString() ?: "—")
                profile.email?.takeIf { it.isNotBlank() }?.let { InfoRow("邮箱", it) }
                profile.groupName?.takeIf { it.isNotBlank() }?.let { InfoRow("用户组", it) }
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 快捷入口数据项。 */
private data class QuickEntry(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** 快捷入口行：各项 weight 平分宽度（后端 4 项 / 本地 3 项）。 */
@Composable
private fun QuickEntriesRow(entries: List<QuickEntry>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        entries.forEach { entry ->
            QuickEntryItem(entry = entry, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickEntryItem(entry: QuickEntry, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = entry.onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = entry.label,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = entry.label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        )
    }
}

/** 功能网格数据项。 */
private data class FnItem(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val onClick: () -> Unit = {},
)

/** 将功能项按每行 [columns] 个布局（不足补空白占位）。 */
@Composable
private fun FunGrid(items: List<FnItem>, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { fn ->
                    FunctionItem(
                        icon = fn.icon,
                        label = fn.label,
                        tint = fn.tint,
                        modifier = Modifier.weight(1f),
                        onClick = fn.onClick,
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 功能网格单元。 */
@Composable
private fun RowScope.FunctionItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(30.dp),
            tint = tint ?: MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        )
    }
}

/** 通用卡片容器，20dp 圆角 + 主题 surfaceVariant 底色。 */
@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
