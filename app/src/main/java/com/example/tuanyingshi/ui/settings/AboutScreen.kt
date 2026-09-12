package com.example.tuanyingshi.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.BuildConfig
import com.example.tuanyingshi.R
import com.example.tuanyingshi.data.remote.backend.AnnouncementVO
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.DebugPrefs

/** 项目主页（Gitee 开放下载仓库）。点击 logo 跳转浏览器打开。 */
private const val DEV_HOMEPAGE = "https://gitee.com/tuxiw/tuan-ying-shi"

/** 意见反馈（Gitee Issues）。 */
private const val FEEDBACK_ISSUES = "https://gitee.com/tuxiw/tuan-ying-shi/issues"

/** GitHub 仓库地址。 */
private const val GITHUB_REPO = "https://github.com/tuxiw/tuan-ying-shi/"

/**
 * 关于页面：点击真实应用图标打开开发者主页。
 * 不展示开发人员 / 开源地址 / 联系方式等显式信息行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        // 与外层 AppNavigation 约定一致：不再重复吃 systemBars inset
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text("关于 团影视", color = MaterialTheme.colorScheme.onBackground)
                },
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            AboutSettingsContent()
        }
    }
}

/**
 * 关于页内容区（供手机页与平板左右布局右侧共用）。
 */
@Composable
fun AboutSettingsContent() {
    val context = LocalContext.current
    // 连点版本号开启开发者选项：默认隐藏，需连点达到阈值
    var tapCount by remember { mutableStateOf(0) }
    val debugModeEnabled by DebugPrefs.debugModeEnabled.collectAsStateWithLifecycle()
    val developerOptionsThreshold = 7

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        // 真实应用图标（mipmap 自适应资源，AGP 按设备密度自动选）——点击
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    // 用户点击 logo，自动用浏览器打开开发者主页
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(DEV_HOMEPAGE)),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                // 用 drawable 里的 PNG 位图：painterResource 不支持 adaptive icon XML（mipmap-anydpi-v26）
                painter = painterResource(R.drawable.ic_launcher_image),
                contentDescription = "团影视",
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "团影视",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "聚合追番 · 在线播放 · 离线下载",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "版本 ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable {
                    if (debugModeEnabled) {
                        // 已开启：仅给出提示，不再累加
                        android.widget.Toast.makeText(
                            context,
                            "开发者选项已开启",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        return@clickable
                    }
                    tapCount++
                    val remain = developerOptionsThreshold - tapCount
                    if (remain > 0) {
                        android.widget.Toast.makeText(
                            context,
                            "再点击 $remain 次开启开发者选项",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        DebugPrefs.setDebugModeEnabled(true)
                        tapCount = 0
                        android.widget.Toast.makeText(
                            context,
                            "开发者选项已开启",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
        )

        // 后端公告（仅后端模式展示）
        Spacer(Modifier.height(28.dp))
        AnnouncementSection()

        // 更新日志（最近一次更新内容）
        Spacer(Modifier.height(24.dp))
        UpdateLogCard()

        // 支持与反馈：赏个好评 / 意见反馈
        Spacer(Modifier.height(24.dp))
        FeedbackSection(context = context)

        Spacer(Modifier.height(32.dp))
        Text(
            "© 2026 团影视 · 仅供学习交流，请勿用于商业用途",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** 单条版本更新记录。 */
private data class ChangeLog(
    val version: String,
    val changes: List<String>,
)

/**
 * 更新日志（由新到旧）。最新版本号会随 BuildConfig.VERSION_NAME 自动同步，
 * 历史版本号以文本固定保留，便于用户查看累积变更。
 */
private val changeLogs = listOf(
    ChangeLog(
        version = BuildConfig.VERSION_NAME,
        changes = listOf(
            "本次更新后APP端+后端代码开源同时会减少更新频率，欢迎二开",
            "修复滑动播放进度条弹幕渲染问题",
            "新增新手引导 (实验)",
            "新增专属APP后端模式 (实验) - 默认未配置",
        ),
    ),
)

/**
 * 后端公告列表（仅后端模式展示）：拉取 `GET /ops/announcement/list`。
 * 加载中或接口不可用时静默不展示，避免影响关于页其它内容。
 */
@Composable
private fun AnnouncementSection() {
    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()
    if (!backendMode) return

    var items by remember { mutableStateOf<List<AnnouncementVO>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(backendMode) {
        runCatching { BackendClient.api.announcements() }
            .onSuccess { resp ->
                if (resp.ok) items = resp.data.orEmpty() else error = resp.message ?: "公告加载失败"
            }
            .onFailure { error = it.message }
    }

    // 还在加载：先不占位，避免关于页出现空卡片
    if (items == null && error == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "公告",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                when {
                    error != null -> Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    items.isNullOrEmpty() -> Text(
                        "暂无公告",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> items!!.forEachIndexed { index, ann ->
                        if (index > 0) {
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        Text(
                            text = ann.title?.takeIf { it.isNotBlank() } ?: "公告",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = ann.content.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val time = ann.createdAt?.take(10)
                        if (!time.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 更新日志卡片：默认只展示最新版本，可展开查看历史版本。 */
@Composable
private fun UpdateLogCard() {
    var expanded by remember { mutableStateOf(false) }
    val visibleLogs = if (expanded) changeLogs else changeLogs.take(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "更新日志",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                visibleLogs.forEachIndexed { index, log ->
                    Text(
                        "v${log.version}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    log.changes.forEach { line ->
                        Text(
                            "· $line",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    if (index != visibleLogs.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }

                if (changeLogs.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = if (expanded) "收起" else "查看历史版本",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                imageVector = if (expanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 支持与反馈区：赏个好评（Gitee 仓库）/ 意见反馈（Gitee Issues）/ GitHub 仓库。 */
@Composable
private fun FeedbackSection(context: android.content.Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "支持与反馈",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
        ) {
            Column(Modifier.fillMaxWidth()) {
                FeedbackRow(
                    icon = Icons.Filled.ThumbUp,
                    title = "赏个好评",
                    onClick = { openUrl(context, DEV_HOMEPAGE) },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                )
                FeedbackRow(
                    icon = Icons.Filled.Email,
                    title = "意见反馈",
                    onClick = { openUrl(context, FEEDBACK_ISSUES) },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                )
                FeedbackRow(
                    icon = Icons.Filled.Code,
                    title = "GitHub 仓库",
                    onClick = { openUrl(context, GITHUB_REPO) },
                )
            }
        }
    }
}

/** 单行反馈项（图标 + 标题 + 右箭头）。 */
@Composable
private fun FeedbackRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 在浏览器中打开指定链接（失败则提示）。 */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        android.widget.Toast.makeText(context, "无法打开链接：$url", android.widget.Toast.LENGTH_SHORT).show()
    }
}
