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
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.data.remote.backend.BackendAccount
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.BackendPrefs
import kotlinx.coroutines.launch

/**
 * 我的页面：
 *  - 顶部右上 Tune 入口
 *  - 用户资料（头像 + 编辑徽标 + 点击登录 / 用户名 + ID）
 *  - 3 列快捷入口（浏览历史 / 我的收藏 / 我的追番）
 *  - 功能网格（后端模式下仅保留有后端支持的项 + 退出登录）
 *
 * 后端模式下：资料区展示登录用户并支持点击登录；会员中心、我的团队、邀请好友、
 * 联系客服、我要合作 等无后端支持的功能直接隐藏（见 [BackendAccount] / [BackendPrefs]）。
 */
@Composable
fun MineScreen(
    navController: NavController,
) {
    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()
    val user by BackendAccount.userState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // 顶部右侧设置按钮
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }

        // 用户资料
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = backendMode && user == null) {
                        navController.navigate(Screen.Login.route)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(72.dp)) {
                    // 后端模式下展示后端保存的头像（相对路径需拼后端根地址）
                    val avatarUrl = if (backendMode) BackendPrefs.absoluteUrl(user?.avatar) else null
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "头像",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
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
                                    .size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }

                Spacer(Modifier.size(16.dp))

                Column {
                    if (backendMode && user != null) {
                        Text(
                            text = user!!.nickname ?: user!!.username,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text(
                            text = "点击登录",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (backendMode && user != null) "ID: ${user!!.id}" else "ID: 57775",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // 影视会员展示区域（仅非后端模式展示；后端模式下无对应后端能力，隐藏）
        if (!backendMode) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(132.dp)
                        .clip(RoundedCornerShape(12.dp))
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "影视畅享SVIP",
                                    color = Color(0xFFFFD700),
                                    fontSize = 18.sp,
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
                                    fontSize = 13.sp,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "过期时间：长期有效",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }

        // 3 列快捷入口
        item {
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    QuickEntry(
                        icon = Icons.Filled.History,
                        label = "浏览历史",
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(Screen.History.route) },
                    )
                    if (backendMode) {
                        QuickEntry(
                            icon = Icons.Filled.FavoriteBorder,
                            label = "我的收藏",
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.Favorites.route) },
                        )
                        QuickEntry(
                            icon = Icons.Filled.StarBorder,
                            label = "我的追番",
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.Marks.route) },
                        )
                        QuickEntry(
                            icon = Icons.Filled.Star,
                            label = "我的评分",
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.MyRatings.route) },
                        )
                    } else {
                        QuickEntry(
                            icon = Icons.Filled.FavoriteBorder,
                            label = "我的收藏",
                            modifier = Modifier.weight(1f),
                        )
                        QuickEntry(
                            icon = Icons.Filled.StarBorder,
                            label = "我的点赞",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 功能网格（后端模式与非后端模式不同）
        item {
            SectionCard {
                val fnItems = if (backendMode) {
                    buildList {
                        add(FnItem(Icons.Filled.Edit, "帮助反馈") { navController.navigate(Screen.Feedback.route) })
                        add(FnItem(Icons.Filled.Download, "我的下载") { navController.navigate(Screen.Downloads.route) })
                        if (user != null) {
                            add(FnItem(Icons.Filled.QrCodeScanner, "扫一扫") { navController.navigate(Screen.QrScan.route) })
                            add(
                                FnItem(Icons.AutoMirrored.Filled.ExitToApp, "退出登录") {
                                    scope.launch { BackendAccount.logout() }
                                },
                            )
                        }
                    }
                } else {
                    listOf(
                        FnItem(Icons.Outlined.Groups, "我的团队"),
                        FnItem(Icons.AutoMirrored.Filled.OpenInNew, "邀请好友"),
                        FnItem(Icons.Filled.SupportAgent, "联系客服"),
                        FnItem(Icons.Filled.Edit, "帮助反馈"),
                        FnItem(Icons.Filled.Handshake, "我要合作"),
                        FnItem(Icons.Filled.Download, "我的下载") { navController.navigate(Screen.Downloads.route) },
                    )
                }
                FunGrid(fnItems)
            }
        }
    }
}

/** 功能网格数据项。 */
private data class FnItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit = {},
)

/** 将功能项按每行 3 个布局（不足补空白占位）。 */
@Composable
private fun FunGrid(items: List<FnItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { fn ->
                    FunctionItem(icon = fn.icon, label = fn.label, modifier = Modifier.weight(1f), onClick = fn.onClick)
                }
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 通用暗色卡片容器，12dp 圆角 + 主题 surfaceVariant 底色。 */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

/** 3 列快捷入口单元（在 Row 内通过 weight 平分宽度）。 */
@Composable
private fun RowScope.QuickEntry(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
        )
    }
}

/** 2×3 功能网格单元。 */
@Composable
private fun RowScope.FunctionItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(30.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
        )
    }
}
