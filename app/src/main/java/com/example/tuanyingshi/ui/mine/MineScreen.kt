package com.example.tuanyingshi.ui.mine

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
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
import com.example.tuanyingshi.data.remote.api.cycani.CycaniProfile
import com.example.tuanyingshi.data.remote.backend.BackendAccount
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.components.maxContentWidth
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.SourceMode
import kotlinx.coroutines.launch

/**
 * 我的页面（重设计版）：
 *  - 顶部标题栏：「我的」+ 右上设置入口
 *  - 资料卡：头像（次元城源站头像 / 后端头像 / Person 占位）+ 编辑徽标 + 昵称 + ID
 *    +（次元城）用户组/邮箱；可点击时右侧出现箭头（登录 / 查看次元城账号详情）
 *  - 影视会员卡（仅非后端模式；后端模式下无对应能力，隐藏）
 *  - 快捷入口卡：浏览历史 / 收藏 / 追番 / 评分（后端 4 项，本地 3 项）
 *  - 功能网格卡：帮助反馈 / 我的下载 /（后端已登录）扫一扫 / 退出登录
 *
 * 平板适配（sw600dp 判定见 [isTablet]）：
 *  - 内容整体限宽 [maxContentWidth] 并 TopCenter 居中，避免超宽屏拉曝；
 *  - 双栏布局：左栏（0.42）资料卡 + 会员卡，右栏（0.58）快捷入口卡 + 功能网格卡（4 列）；
 *  - 手机端单栏纵排，功能网格 3 列；头像 / 会员卡 / 字号按档位放大。
 *
 * 后端模式下：资料区展示登录用户并支持点击登录；会员中心等无后端支持的功能直接隐藏
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
            // 顶部标题栏：「我的」+ 设置
            item {
                MineTopBar(onSettings = { navController.navigate(Screen.Settings.route) })
            }

            if (tablet) {
                // 平板双栏：左=资料+会员，右=快捷入口+功能网格
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(0.42f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ProfileCard(
                                name = profileName,
                                idLine = profileIdLine,
                                extra = profileExtra,
                                cycaniProfile = cycaniProfile,
                                cycaniMode = cycaniMode,
                                backendAvatarUrl = backendAvatarUrl,
                                showEditBadge = !cycaniMode,
                                clickable = profileClickable,
                                onClick = onProfileClick,
                                tablet = true,
                            )
                            if (!backendMode) {
                                SvipCard(tablet = true)
                            }
                        }
                        Column(
                            modifier = Modifier.weight(0.58f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SectionCard {
                                QuickEntriesRow(quickEntries)
                            }
                            SectionCard {
                                FunGrid(fnItems, columns = 4)
                            }
                        }
                    }
                }
            } else {
                // 手机单栏纵排
                item {
                    ProfileCard(
                        name = profileName,
                        idLine = profileIdLine,
                        extra = profileExtra,
                        cycaniProfile = cycaniProfile,
                        cycaniMode = cycaniMode,
                        backendAvatarUrl = backendAvatarUrl,
                        showEditBadge = !cycaniMode,
                        clickable = profileClickable,
                        onClick = onProfileClick,
                        tablet = false,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                if (!backendMode) {
                    item {
                        SvipCard(
                            tablet = false,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
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

/** 顶部标题栏：「我的」标题 + 右上设置入口。 */
@Composable
private fun MineTopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * 资料卡：头像 + 昵称 + ID（+ 次元城用户组/邮箱），可点击时右侧箭头。
 * 头像优先级：次元城源站头像 > 后端头像 > Person 占位。
 */
@Composable
private fun ProfileCard(
    name: String,
    idLine: String,
    extra: String?,
    cycaniProfile: CycaniProfile?,
    cycaniMode: Boolean,
    backendAvatarUrl: String?,
    showEditBadge: Boolean,
    clickable: Boolean,
    onClick: () -> Unit,
    tablet: Boolean,
    modifier: Modifier = Modifier,
) {
    val avatarBoxSize = if (tablet) 96.dp else 72.dp
    val avatarSize = if (tablet) 84.dp else 64.dp
    val badgeSize = if (tablet) 26.dp else 22.dp
    val corner = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = corner,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier
                .clip(corner)
                // 顶部一点主色渐隐，让资料卡在卡片群里更有「头部」感
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(corner)
                    .clickable(enabled = clickable, onClick = onClick)
                    .padding(horizontal = 20.dp, vertical = if (tablet) 22.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(avatarBoxSize)) {
                    when {
                        // 次元城源站账号：展示源站头像，无头像时用用户名首字母占位
                        cycaniMode -> CycaniAvatar(
                            profile = cycaniProfile,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(avatarSize),
                        )
                        // 后端模式：展示后端保存的头像
                        backendAvatarUrl != null -> AsyncImage(
                            model = backendAvatarUrl,
                            contentDescription = "头像",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(avatarSize)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                        else -> Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(avatarSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(if (tablet) 48.dp else 40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // 源站账号不可在 app 内编辑，隐藏编辑徽标
                    if (showEditBadge) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(badgeSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "编辑资料",
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .size(if (tablet) 14.dp else 12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.size(if (tablet) 20.dp else 16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = if (tablet) 20.sp else 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = idLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = if (tablet) 13.sp else 12.sp,
                    )
                    if (extra != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = extra,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (tablet) 13.sp else 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 可点击时给一个右侧箭头，增强可点性暗示
                if (clickable) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (tablet) 28.dp else 22.dp),
                    )
                }
            }
        }
    }
}

/** 影视会员卡（仅非后端模式展示；TODO: 会员中心）。 */
@Composable
private fun SvipCard(tablet: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (tablet) 160.dp else 132.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = { /* TODO: 会员中心 */ }),
    ) {
        AsyncImage(
            model = R.drawable.svip_card,
            contentDescription = "影视会员",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xCC000000),
                        0.55f to Color(0x66000000),
                        1.0f to Color.Transparent,
                    )
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "影视畅享SVIP",
                        color = Color(0xFFFFD700),
                        fontSize = if (tablet) 20.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "生效中",
                        color = Color.White,
                        fontSize = if (tablet) 14.sp else 13.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "过期时间：长期有效",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = if (tablet) 14.sp else 13.sp,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(if (tablet) 32.dp else 28.dp),
            )
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
