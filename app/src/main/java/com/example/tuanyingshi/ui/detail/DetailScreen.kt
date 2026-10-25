package com.example.tuanyingshi.ui.detail

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.ui.components.DetailInfoSkeleton
import com.example.tuanyingshi.ui.components.EpisodesLoading
import com.example.tuanyingshi.ui.components.ExpandableText
import com.example.tuanyingshi.ui.components.MediaSourceSheet
import com.example.tuanyingshi.ui.components.EpisodeGrid
import com.example.tuanyingshi.ui.components.SourceSwitchCard
import com.example.tuanyingshi.ui.components.SectionTitle
import com.example.tuanyingshi.ui.components.SourceIcon
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.detail.hanime1.Hanime1DetailContent
import com.example.tuanyingshi.domain.model.Episode
import com.example.tuanyingshi.ui.components.LocalUserStatusRepository
import com.example.tuanyingshi.ui.components.NsfwBlockedScaffold
import com.example.tuanyingshi.ui.detail.DownloadSheet
import com.example.tuanyingshi.ui.download.DownloadViewModel
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.ui.components.effectiveDetailColumns
import com.example.tuanyingshi.util.NotifPrefs
import com.example.tuanyingshi.util.isNsfw
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    navController: NavController,
    detailUrl: String,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val channelNames by viewModel.channelNames.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val sourceName by viewModel.sourceName.collectAsStateWithLifecycle()
    val iconUrl by viewModel.iconUrl.collectAsStateWithLifecycle()
    // 当前生效数据源 id：播放页导航带上它，保证两页按同一个源解析
    val currentSourceId by viewModel.currentSourceId.collectAsStateWithLifecycle()
    val sourceCandidates by viewModel.sourceCandidates.collectAsStateWithLifecycle()
    val queryStates by viewModel.queryStates.collectAsStateWithLifecycle()
    val queriedCount by viewModel.queriedCount.collectAsStateWithLifecycle()
    val queryTotal by viewModel.queryTotal.collectAsStateWithLifecycle()
    val channelsLoading by viewModel.channelsLoading.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val userRating by viewModel.userRating.collectAsStateWithLifecycle()
    val actionMessage by viewModel.message.collectAsStateWithLifecycle()
    val lastWatched by viewModel.lastWatched.collectAsStateWithLifecycle()
    // 布局设置中的详情页列表列数：自适应开启时按屏幕宽度实时算，否则用手动值（2-6，默认 3）
    val detailColumns = effectiveDetailColumns()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 评分 / 报错的后端结果统一用 Snackbar 反馈；展示后清空，保证同样的文案下次还能再弹
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    val downloadViewModel: DownloadViewModel = hiltViewModel()
    val context = LocalContext.current
    val online by com.example.tuanyingshi.util.NetworkMonitor.isOnline.collectAsStateWithLifecycle()
    // 抛弃状态（通过 CompositionLocal 注入的仓储；未注入则恒为 false，不显示抛弃入口）
    val userStatusRepo = LocalUserStatusRepository.current
    val isAbandoned by (userStatusRepo?.observeAbandoned(detailUrl) ?: flowOf(false))
        .collectAsStateWithLifecycle(initialValue = false)
    // 通知权限请求（Android 13+ 需要运行时授权才能显示下载进度通知）
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 授权结果不阻塞下载；未授权则下载通知不显示 */ }

    var showRating by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.title ?: "详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                // 抛弃 / 取消抛弃（长按列表卡片亦可触发，此处提供明确的顶栏入口）
                actions = {
                    if (userStatusRepo != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (isAbandoned) {
                                        userStatusRepo.removeAbandoned(detailUrl)
                                    } else {
                                        userStatusRepo.setAbandoned(
                                            detailUrl = detailUrl,
                                            title = detail?.title ?: "",
                                            imgUrl = detail?.img ?: "",
                                        )
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = if (isAbandoned) "取消抛弃" else "抛弃",
                                tint = if (isAbandoned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                // 顶栏底色与正文统一为 background，消除 surface 浅色带
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                windowInsets = WindowInsets(0),
            )
        }
    ) { inner ->
        // Hanime1 主数据源：整页切换到 Han1meViewer 风格详情（横版封面 / 标签 / 系列 / 推荐）。
        if (com.example.tuanyingshi.util.SourceHolder.currentSourceMode == com.example.tuanyingshi.util.SourceMode.Hanime1) {
            Hanime1DetailContent(
                navController = navController,
                detailUrl = detailUrl,
                modifier = Modifier.padding(inner),
            )
            return@Scaffold
        }
        val anime = detail
        if (anime == null) {
            if (!online) {
                CenteredMessage(
                    "离线模式：无法联网加载，请前往「我的下载」观看已下载内容",
                    Modifier.fillMaxSize().padding(inner),
                )
            } else {
                // 元数据未到：先渲染页面骨架（带加载动画），用户「先进页面」而非整页阻塞
                DetailInfoSkeleton(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    tablet = isTablet(),
                )
            }
            return@Scaffold
        }
        // NSFW 直达屏蔽：开启屏蔽且番剧命中 NSFW 标签时，不展示任何内容（含封面/选集/播放入口）
        if (ContentPrefs.isNsfwBlocked() && isNsfw(anime.tags)) {
            NsfwBlockedScaffold(navController = navController)
            return@Scaffold
        }
        if (isTablet()) {
            // 平板：参考截图，左侧封面 + 继续观看 / 已看按钮，右侧信息 + 选集网格
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            ) {
                // 左侧面板
                Column(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = anime.img,
                        contentDescription = anime.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(20.dp))
                    val continueEpisode = lastWatched?.lastEpisodeUrl?.let { url ->
                        episodes.find { it.url == url }
                    } ?: episodes.firstOrNull()
                    Button(
                        onClick = {
                            val ep = continueEpisode ?: return@Button
                            navController.navigate(Screen.Player.create(detailUrl, ep.url, currentSourceId))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = continueEpisode != null,
                    ) {
                        Text("继续观看 ${continueEpisode?.name ?: ""}")
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("已标记为已看")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("已看")
                    }
                }

                // 右侧面板
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Text(
                        anime.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    SourceBadge(sourceName = sourceName, iconUrl = iconUrl)
                    Spacer(Modifier.height(12.dp))
                    // 操作图标行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = { showRating = true },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = if (userRating != null) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "评分",
                                tint = if (userRating != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = viewModel::toggleFavorite,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { showDownloadSheet = true },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "下载",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { showReport = true },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Report,
                                contentDescription = "报错",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.loadSourceCandidates()
                                showSourceSheet = true
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = "数据源",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${anime.region.label} · ${anime.type.label} · ${anime.status.label} · ${anime.year}" +
                            if (anime.totalEpisodes > 0) " · 全${anime.totalEpisodes}集" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.1f".format(anime.rating),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        StarRatingDisplay(rating = (anime.rating / 2).toInt().coerceIn(0, 5))
                    }
                    Spacer(Modifier.height(12.dp))
                    ExpandableText(
                        text = anime.desc,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                    SourceSwitchCard(
                        sourceName = sourceName,
                        iconUrl = iconUrl,
                        onClick = {
                            viewModel.loadSourceCandidates()
                            showSourceSheet = true
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    SectionTitle("选集")
                    if (channels.size > 1) {
                        ChannelTabs(
                            names = channelNames,
                            selected = selectedChannel,
                            onSelect = viewModel::selectChannel,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (channelsLoading && episodes.isEmpty()) {
                        // 选集（CSS 线路）后台补全中：仅此区域显示加载动画，其余元数据已真实显示
                        EpisodesLoading("选集加载中…")
                    } else {
                        EpisodeGrid(
                            episodes = episodes,
                            columns = detailColumns,
                            // 上次看到的一集高亮（与播放页当前集高亮同款）
                            currentEpisodeUrl = lastWatched?.lastEpisodeUrl,
                            onPlay = { ep -> navController.navigate(Screen.Player.create(detailUrl, ep.url, currentSourceId)) },
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState()),
            ) {
                AsyncImage(
                    model = anime.img,
                    contentDescription = anime.title,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    anime.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    "${anime.region.label} · ${anime.type.label} · ${anime.status.label} · ${anime.year} · ★ ${"%.1f".format(anime.rating)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SourceBadge(sourceName = sourceName, iconUrl = iconUrl, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Text(anime.tags.joinToString("  "), modifier = Modifier.padding(16.dp, 8.dp))

                // 操作栏：评分 / 收藏 / 报错
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DetailActionItem(
                        icon = Icons.Filled.Star,
                        label = userRating?.let { "我评 %.1f".format(it) } ?: "评分",
                        onClick = { showRating = true },
                    )
                    DetailActionItem(
                        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label = if (isFavorite) "已收藏" else "收藏",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = viewModel::toggleFavorite,
                    )
                    DetailActionItem(
                        icon = Icons.Filled.Download,
                        label = "下载",
                        onClick = { showDownloadSheet = true },
                    )
                    DetailActionItem(
                        icon = Icons.Filled.Report,
                        label = "报错",
                        onClick = { showReport = true },
                    )
                }

                ExpandableText(
                    text = anime.desc,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                SourceSwitchCard(
                    sourceName = sourceName,
                    iconUrl = iconUrl,
                    onClick = {
                        viewModel.loadSourceCandidates()
                        showSourceSheet = true
                    },
                )
                Spacer(Modifier.height(8.dp))
                SectionTitle("选集")
                if (channels.size > 1) {
                    ChannelTabs(
                        names = channelNames,
                        selected = selectedChannel,
                        onSelect = viewModel::selectChannel,
                    )
                }
                if (channelsLoading && episodes.isEmpty()) {
                    EpisodesLoading("选集加载中…", modifier = Modifier.padding(vertical = 10.dp))
                } else {
                    EpisodeGrid(
                        episodes = episodes,
                        columns = detailColumns,
                        // 上次看到的一集高亮（与播放页当前集高亮同款）
                        currentEpisodeUrl = lastWatched?.lastEpisodeUrl,
                        onPlay = { ep -> navController.navigate(Screen.Player.create(detailUrl, ep.url, currentSourceId)) },
                    )
                }
            }
        }
    }

    // 评分对话框
    if (showRating) {
        var ratingInput by remember(showRating) { mutableStateOf(userRating?.toInt() ?: 0) }
        AlertDialog(
            onDismissRequest = { showRating = false },
            title = { Text("为《${detail?.title ?: ""}》评分") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StarRatingRow(rating = ratingInput, onRatingChanged = { ratingInput = it })
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (ratingInput > 0) "您选择了 $ratingInput 星" else "点击星星选择评分",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ratingInput > 0) viewModel.setUserRating(ratingInput.toFloat())
                    showRating = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRating = false }) { Text("取消") }
            },
        )
    }

    // 报错对话框
    if (showReport) {
        val reportOptions = listOf("播放卡顿/无法播放", "资源失效/链接错误", "剧情信息有误", "其他")
        var selected by remember(showReport) { mutableStateOf(reportOptions[0]) }
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("反馈问题") },
            text = {
                Column {
                    Text("请选择问题类型：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    reportOptions.forEach { opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { selected = opt }
                                .padding(4.dp),
                        ) {
                            RadioButton(selected = selected == opt, onClick = { selected = opt })
                            Text(opt, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.submitReport(selected)
                    showReport = false
                }) { Text("提交") }
            },
            dismissButton = {
                TextButton(onClick = { showReport = false }) { Text("取消") }
            },
        )
    }

    if (showDownloadSheet) {
        DownloadSheet(
            episodes = episodes,
            onDismiss = { showDownloadSheet = false },
            onConfirm = { selected ->
                // Android 13+：若开启下载通知且尚未授权，则请求 POST_NOTIFICATIONS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && NotifPrefs.isDownloadNotificationEnabled()
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                downloadViewModel.enqueue(detail?.title ?: "", selected, detailUrl, detail?.img ?: "")
                showDownloadSheet = false
                scope.launch {
                    snackbarHostState.showSnackbar("已加入下载队列（${selected.size}）")
                }
            },
        )
    }

    if (showSourceSheet) {
        MediaSourceSheet(
            candidates = sourceCandidates,
            currentSourceId = detail?.sourceId ?: "",
            currentChannelIndex = selectedChannel,
            queryStates = queryStates,
            queriedCount = queriedCount,
            queryTotal = queryTotal,
            searchTitle = viewModel.currentSearchTitle(),
            onSelect = { candidate, line ->
                viewModel.selectDetailSource(candidate, line)
                showSourceSheet = false
            },
            onRequery = { title, aliases ->
                viewModel.loadSourceCandidates(titleOverride = title, aliases = aliases, force = true)
            },
            onRefresh = { viewModel.loadSourceCandidates(force = true) },
            onDismiss = { showSourceSheet = false },
        )
    }
}


@Composable
private fun DetailActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(12.dp, 8.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** 详情页「来源」行：图标 + "来源：名称"（animeko 结果始终携带 mediaSource 信息）。 */
@Composable
private fun SourceBadge(sourceName: String, iconUrl: String, modifier: Modifier = Modifier) {
    if (sourceName.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        SourceIcon(iconUrl = iconUrl, size = 16.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            "来源：$sourceName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StarRatingDisplay(rating: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            Icon(
                imageVector = if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StarRatingRow(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.Center) {
        for (i in 1..5) {
            IconButton(onClick = { onRatingChanged(i) }) {
                Icon(
                    imageVector = if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "$i 星",
                    tint = if (i <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 详情页 / 播放器通用：多线路切换 Tab（线路数 > 1 时显示）。 */
@Composable
fun ChannelTabs(
    names: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(names, key = { index, _ -> index }) { index, name ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(index) }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    name,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * 数据源快捷卡片已上移到 `ui/components/SourceSwitchCard.kt`（播放页换源入口共用同一组件）。
 */

