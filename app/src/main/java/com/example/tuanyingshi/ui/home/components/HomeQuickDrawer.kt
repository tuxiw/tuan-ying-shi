package com.example.tuanyingshi.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.data.remote.backend.BackendAccount
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.util.PlaybackPrefs
import com.example.tuanyingshi.util.ThemeMode
import com.example.tuanyingshi.util.ThemePrefs
import com.example.tuanyingshi.util.UiPrefs

/**
 * 首页头像点开的左侧「快捷设置」抽屉。
 *
 * 组成（自上而下）：
 *  1. 账号区：真实头像 + 昵称，点击进入「我的」（未登录时进入登录页）；
 *  2. 外观：跟随系统 / 浅色 / 深色 三选一（改动即时生效，无需进设置页）；
 *  3. 快捷开关：首页与播放相关的常用开关，改完立即生效；
 *  4. 快捷入口：追番 / 收藏 / 历史 / 下载 / 设置（后端模式下额外提供扫码登录）。
 *
 * 所有开关都直接读写各自的 Prefs（`ContentPrefs` / `PlaybackPrefs` / `UiPrefs`），
 * 不复制状态，设置页与抽屉永远一致。
 */
@Composable
fun HomeQuickDrawerContent(
    navController: NavController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()
    val user by BackendAccount.userState.collectAsStateWithLifecycle()
    val themeConfig by ThemePrefs.config.collectAsStateWithLifecycle()
    val hideWatched by ContentPrefs.hideWatched.collectAsStateWithLifecycle()
    val hideHomeBanner by ContentPrefs.hideHomeBanner.collectAsStateWithLifecycle()
    val autoNext by PlaybackPrefs.autoNextEpisode.collectAsStateWithLifecycle()
    val danmakuDefaultOn by PlaybackPrefs.danmakuDefaultOn.collectAsStateWithLifecycle()
    val navLabels by UiPrefs.navLabelsVisible.collectAsStateWithLifecycle()

    ModalDrawerSheet(modifier = modifier.width(306.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            /* ═══ 1. 账号区 ═══ */
            val displayName = when {
                !backendMode -> "本地模式"
                user != null -> user?.nickname?.takeIf { it.isNotBlank() } ?: user!!.username
                else -> "未登录"
            }
            val subtitle = when {
                !backendMode -> "点击进入我的"
                user != null -> "ID: ${user!!.id} · 点击查看账号"
                else -> "点击登录后端账号"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onClose()
                        if (backendMode && user == null) {
                            navController.navigate(Screen.Login.route)
                        } else {
                            navController.navigate(Screen.Mine.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawerAvatar(
                    url = if (backendMode) BackendPrefs.absoluteUrl(user?.avatar) else null,
                    size = 48,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            DrawerDivider()

            /* ═══ 2. 外观 ═══ */
            DrawerSectionTitle("外观")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeModeChip(
                    label = "跟随系统",
                    selected = themeConfig.mode == ThemeMode.SYSTEM,
                    modifier = Modifier.weight(1f),
                ) { ThemePrefs.setMode(ThemeMode.SYSTEM) }
                ThemeModeChip(
                    label = "浅色",
                    selected = themeConfig.mode == ThemeMode.LIGHT,
                    modifier = Modifier.weight(1f),
                ) { ThemePrefs.setMode(ThemeMode.LIGHT) }
                ThemeModeChip(
                    label = "深色",
                    selected = themeConfig.mode == ThemeMode.DARK,
                    modifier = Modifier.weight(1f),
                ) { ThemePrefs.setMode(ThemeMode.DARK) }
            }

            DrawerDivider()

            /* ═══ 3. 快捷开关 ═══ */
            DrawerSectionTitle("快捷开关")
            DrawerSwitchRow(
                title = "隐藏已看",
                subtitle = "首页与分类里不显示已看过的番剧",
                checked = hideWatched,
                onCheckedChange = { ContentPrefs.setHideWatched(it) },
            )
            DrawerSwitchRow(
                title = "隐藏首页轮播",
                subtitle = "首页顶部大图推荐位不显示",
                checked = hideHomeBanner,
                onCheckedChange = { ContentPrefs.setHideHomeBanner(it) },
            )
            DrawerSwitchRow(
                title = "自动连播下一集",
                subtitle = "当前集播完自动播放下一集",
                checked = autoNext,
                onCheckedChange = { PlaybackPrefs.setAutoNextEpisode(it) },
            )
            DrawerSwitchRow(
                title = "弹幕默认开启",
                subtitle = "进入播放器时自动打开弹幕",
                checked = danmakuDefaultOn,
                onCheckedChange = { PlaybackPrefs.setDanmakuDefaultOn(it) },
            )
            DrawerSwitchRow(
                title = "显示导航文字",
                subtitle = "底部导航栏显示文字标签",
                checked = navLabels,
                onCheckedChange = { UiPrefs.setNavLabelsVisible(it) },
            )

            DrawerDivider()

            /* ═══ 4. 快捷入口 ═══ */
            DrawerSectionTitle("快捷入口")
            if (backendMode && user == null) {
                DrawerEntryRow(Icons.Filled.QrCodeScanner, "扫码登录") {
                    onClose()
                    navController.navigate(Screen.QrScan.route)
                }
            }
            if (backendMode) {
                DrawerEntryRow(Icons.Filled.StarBorder, "我的追番") {
                    onClose()
                    navController.navigate(Screen.Marks.route)
                }
                DrawerEntryRow(Icons.Filled.FavoriteBorder, "我的收藏") {
                    onClose()
                    navController.navigate(Screen.Favorites.route)
                }
            }
            DrawerEntryRow(Icons.Filled.History, "观看历史") {
                onClose()
                navController.navigate(Screen.History.route)
            }
            DrawerEntryRow(Icons.Filled.Download, "我的下载") {
                onClose()
                navController.navigate(Screen.Downloads.route)
            }
            DrawerEntryRow(Icons.Filled.Settings, "设置") {
                onClose()
                navController.navigate(Screen.Settings.route)
            }
        }
    }
}

/** 抽屉里的头像：优先加载后端头像（相对地址由调用方转成绝对地址），加载中/失败时回退到人形图标。 */
@Composable
private fun DrawerAvatar(url: String?, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // 兜底图标先画；头像加载失败时 Coil 不绘制任何内容，图标自然露出来
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size((size * 0.55f).dp),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DrawerSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun DrawerDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** 主题三选一的小胶囊（选中的用主题色实底）。 */
@Composable
private fun ThemeModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun DrawerSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DrawerEntryRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}
