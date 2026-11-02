package com.example.tuanyingshi.ui.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.data.remote.api.cycani.CycaniAuthManager
import com.example.tuanyingshi.domain.model.Comment
import com.example.tuanyingshi.domain.model.Episode
import com.example.tuanyingshi.ui.player.DownloadedEpisode
import com.example.tuanyingshi.ui.components.AbandonDialog
import com.example.tuanyingshi.ui.components.LoadingIndicator
import com.example.tuanyingshi.ui.components.PlayerInfoSkeleton
import com.example.tuanyingshi.ui.components.ExpandableText
import com.example.tuanyingshi.ui.components.LocalUserStatusRepository
import com.example.tuanyingshi.ui.components.NsfwBlockedScaffold
import com.example.tuanyingshi.ui.components.isTablet
import com.example.tuanyingshi.ui.components.smallestWidthDp
import com.example.tuanyingshi.ui.detail.ChannelTabs
import com.example.tuanyingshi.ui.components.MediaSourceSheet
import com.example.tuanyingshi.ui.components.EpisodeGrid
import com.example.tuanyingshi.ui.components.SourceIcon
import com.example.tuanyingshi.ui.components.SourceSwitchCard
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.util.DanmakuPrefs
import com.example.tuanyingshi.util.PlaybackPrefs
import com.example.tuanyingshi.util.PlayerMorePrefs
import com.example.tuanyingshi.util.PipController
import com.example.tuanyingshi.util.PlayerEnhancementPrefs
import com.example.tuanyingshi.util.ScreenshotNotifier
import com.example.tuanyingshi.util.resolveScreenshotDir
import com.lanlinju.videoplayer.LoopMode
import com.lanlinju.videoplayer.PlayerMoreConfig
import com.lanlinju.videoplayer.enhancement.VideoEnhancementMode
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.isNsfw
import com.example.tuanyingshi.util.log
import com.lanlinju.videoplayer.VideoPlayer
import com.lanlinju.videoplayer.VideoPlayerControl
import com.lanlinju.videoplayer.adaptiveLayout
import com.lanlinju.videoplayer.rememberVideoPlayerState
import android.widget.Toast
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.tuanyingshi.danmaku.api.Danmaku
import com.example.tuanyingshi.danmaku.api.DanmakuEvent
import com.example.tuanyingshi.danmaku.api.DanmakuLocation
import com.example.tuanyingshi.danmaku.api.DanmakuPresentation
import com.example.tuanyingshi.danmaku.api.DanmakuSession
import com.example.tuanyingshi.danmaku.api.TimeBasedDanmakuSession
import com.example.tuanyingshi.danmaku.ui.DanmakuConfig
import com.example.tuanyingshi.danmaku.ui.DanmakuHost
import com.example.tuanyingshi.danmaku.ui.DanmakuStyle
import com.example.tuanyingshi.danmaku.ui.DanmakuTrackProperties
import com.example.tuanyingshi.danmaku.ui.rememberDanmakuHostState
import com.example.tuanyingshi.data.remote.dandanplay.DanmakuItem
import kotlin.time.Duration.Companion.milliseconds

private enum class PlayerTab(val label: String) { INTRO("简介"), COMMENTS("评论") }

/** 发弹幕可选颜色（#RRGGBB），点击色块循环切换：白 / 红 / 橙 / 黄 / 绿 / 青 / 蓝 / 紫。 */
private val DANMAKU_COLORS = listOf(
    "#FFFFFF", "#FF5252", "#FFB300", "#FFFF00",
    "#4CAF50", "#00E5FF", "#2196F3", "#E040FB",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    detailUrl: String,
    episodeUrl: String,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val video by viewModel.video.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val anime by viewModel.anime.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val related by viewModel.related.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val channelNames by viewModel.channelNames.collectAsStateWithLifecycle()
    val currentChannelIndex by viewModel.currentChannelIndex.collectAsStateWithLifecycle()
    val sourceCandidates by viewModel.sourceCandidates.collectAsStateWithLifecycle()
    val queryStates by viewModel.queryStates.collectAsStateWithLifecycle()
    val queriedCount by viewModel.queriedCount.collectAsStateWithLifecycle()
    val queryTotal by viewModel.queryTotal.collectAsStateWithLifecycle()
    val showSourcePicker by viewModel.showSourcePicker.collectAsStateWithLifecycle()
    // 当前生效数据源（换源后同步更新；与详情页选择保持一致）
    val currentSourceName by viewModel.currentSourceName.collectAsStateWithLifecycle()
    val currentSourceIcon by viewModel.currentSourceIcon.collectAsStateWithLifecycle()
    // 选择面板「当前源」高亮：与数据源卡同一个 id（资源源回退全局 currentSourceId）
    val sheetSourceId by viewModel.currentSourceId.collectAsStateWithLifecycle()
    // 记忆播放：当前集从 episode_table 读出的续播进度（毫秒），交给 VideoPlayer 作初始 seek
    val resumePosition by viewModel.resumePosition.collectAsStateWithLifecycle()
    // 次元城登录是否已失效（用于在错误页放「重新登录」入口，点击导航到登录页）
    val cycaniExpired by CycaniAuthManager.sessionExpired.collectAsStateWithLifecycle()
    // 弹幕数据（统一来自弹弹play 弹幕库，所有数据源的剧集都按番名+集名匹配加载）
    val danmakuItems by viewModel.danmakuItems.collectAsStateWithLifecycle()
    // 当前集自动匹配到的弹幕番剧/集信息（播放页展示 + 手动切换）
    val danmakuMatch by viewModel.danmakuMatch.collectAsStateWithLifecycle()
    val showDanmakuSearchDialog by viewModel.showDanmakuSearch.collectAsStateWithLifecycle()
    val danmakuSearchResults by viewModel.danmakuSearchResults.collectAsStateWithLifecycle()
    val danmakuSearching by viewModel.danmakuSearching.collectAsStateWithLifecycle()
    var danmakuQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    var showSourceSheet by remember { mutableStateOf(false) }
    // 投屏：设备选择面板 + 当前正在投屏的设备（用于面板里显示「停止投屏」）
    var showCastSheet by remember { mutableStateOf(false) }
    var castingDevice by remember { mutableStateOf<com.example.tuanyingshi.ui.player.cast.CastDevice?>(null) }
    // 续播提示条：切到新视频且检测到历史进度时，展示「已从 mm:ss 继续播放 · 从头播放」
    var showResumeBanner by remember { mutableStateOf(false) }
    var resumeBannerPosition by remember { mutableStateOf(0L) }
    // 视频页安全提醒横幅：提示视频内广告/链接非本应用提供，防止用户上当受骗。可关闭（仅隐藏本次）。
    var showSecurityTip by remember { mutableStateOf(true) }

    var tab by remember { mutableStateOf(PlayerTab.INTRO) }
    var danmakuOn by remember { mutableStateOf(PlaybackPrefs.isDanmakuDefaultOn()) }

    // 发弹幕：仅在「后端模式 + 已登录」可用（弹幕落在自建后端）。
    // 发送时取当前播放位置作为弹幕出现的时间点，因此不必让用户手动填时间。
    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()
    val danmakuSendEnabled = backendMode && BackendPrefs.isLoggedIn
    var danmakuInput by remember { mutableStateOf("") }
    var danmakuColor by remember { mutableStateOf(DANMAKU_COLORS.first()) }
    var danmakuSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val playUrl = video?.url
    // ViewModel 请求展示「选择数据源 / 线路」面板（播放链接不可播放 / 解析失败 / 播放出错时）：自动弹出，
    // 把选择权交给用户，而非静默自动遍历其它资源。
    LaunchedEffect(showSourcePicker) {
        if (showSourcePicker) {
            showSourceSheet = true
        }
    }

    // 资源选择面板（优先展示当前源的其它线路，再渐进列出其它 CSS 源）。
    // 放在 early-return 之前，确保加载中 / 播放失败时也能弹出供用户挑选。
    if (showSourceSheet) {
        MediaSourceSheet(
            candidates = sourceCandidates,
            currentSourceId = sheetSourceId,
            currentChannelIndex = currentChannelIndex,
            queryStates = queryStates,
            queriedCount = queriedCount,
            queryTotal = queryTotal,
            searchTitle = viewModel.currentSearchTitle(),
            onSelect = { candidate, line ->
                viewModel.selectSourceVideo(candidate, line)
                showSourceSheet = false
                viewModel.clearShowSourcePicker()
            },
            onRequery = { title, aliases ->
                viewModel.loadSourceCandidates(titleOverride = title, aliases = aliases, force = true)
            },
            onRefresh = { viewModel.loadSourceCandidates(force = true) },
            onDismiss = {
                showSourceSheet = false
                viewModel.onSourceSheetDismissed()
            },
        )
    }

    // 次元城登录失效：不再弹窗——错误页的「重新登录次元城」按钮导航到全屏登录页，
    // 登录成功返回后 ViewModel 检测到 sessionExpired 复位会自动重试解析。

    // 弹幕手动匹配搜索面板：展示当前自动匹配的弹幕番剧/集，并允许按关键词搜索、手动选定为当前集弹幕来源。
    if (showDanmakuSearchDialog) {
        // 打开时若已有查询词（来自当前匹配/番名），自动搜一次，让用户立即看到候选
        LaunchedEffect(Unit) {
            if (danmakuQuery.isNotBlank()) viewModel.searchDanmaku(danmakuQuery)
        }
        AlertDialog(
            onDismissRequest = { viewModel.closeDanmakuSearch() },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.closeDanmakuSearch() }) { Text("关闭") }
            },
            title = { Text("选择弹幕匹配") },
            text = {
                Column {
                    OutlinedTextField(
                        value = danmakuQuery,
                        onValueChange = { danmakuQuery = it },
                        label = { Text("番名 / 集名关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { if (danmakuQuery.isNotBlank()) viewModel.searchDanmaku(danmakuQuery) },
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { if (danmakuQuery.isNotBlank()) viewModel.searchDanmaku(danmakuQuery) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("搜索") }
                    Spacer(Modifier.height(8.dp))
                    when {
                        danmakuSearching -> Box(
                            Modifier.fillMaxWidth().height(140.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                        danmakuSearchResults.isEmpty() -> Text(
                            if (danmakuQuery.isBlank()) "输入关键词后搜索弹幕对应的番剧与集数"
                            else "未找到匹配的弹幕",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                            items(danmakuSearchResults) { entry ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectDanmakuMatch(entry)
                                            danmakuOn = true
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                ) {
                                    Text(
                                        entry.animeTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                    )
                                    Text(
                                        entry.episodeTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
        )
    }

    // NSFW 直达屏蔽：开启屏蔽且番剧命中 NSFW 标签时，不解析、不播放，直接展示屏蔽页
    if (ContentPrefs.isNsfwBlocked() && anime?.tags?.let { isNsfw(it) } == true) {
        NsfwBlockedScaffold(navController = navController)
        return
    }

    // 使用 video-player 库管理播放器实例、控件与手势
    val playerState = rememberVideoPlayerState(isAutoOrientation = false)

    // 应用「播放参数 → 默认倍速」偏好：进入播放页即用用户设置的倍速。
    val defaultSpeed by PlaybackPrefs.defaultSpeed.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        // 倍速
        playerState.control.setPlaybackSpeed(defaultSpeed)
        // 默认视频比例 → ResizeMode 常量（auto/16:9/4:3/full → Fit/16:9/4:3/Full）。
        // ResizeMode 构造函数 private，只能从其 companion 取预设值，所以用 when 直接选常量。
        val resolved = when (PlaybackPrefs.defaultVideoResizeModeIndex()) {
            4 -> com.lanlinju.videoplayer.ResizeMode.Full
            6 -> com.lanlinju.videoplayer.ResizeMode.FixedRatio_16_9
            7 -> com.lanlinju.videoplayer.ResizeMode.FixedRatio_4_3
            else -> com.lanlinju.videoplayer.ResizeMode.Fit
        }
        playerState.control.setVideoResize(resolved)
    }

    // 画质增强：读取「视频增强」偏好，进入播放页即应用超分模式，并随设置页变更实时生效。
    val enhancementMode by PlayerEnhancementPrefs.enhancementMode.collectAsStateWithLifecycle()
    LaunchedEffect(enhancementMode) {
        playerState.setEnhancementMode(enhancementMode)
    }
    // 画质增强自动降级：随设置页开关实时生效。
    val autoEnhancementOptimize by PlayerEnhancementPrefs.autoEnhancementOptimize.collectAsStateWithLifecycle()
    LaunchedEffect(autoEnhancementOptimize) {
        playerState.autoEnhancementOptimize = autoEnhancementOptimize
    }
    val autoEnhancementDowngradeFloor by PlayerEnhancementPrefs.autoEnhancementDowngradeFloor.collectAsStateWithLifecycle()
    LaunchedEffect(autoEnhancementDowngradeFloor) {
        playerState.autoEnhancementDowngradeFloor = autoEnhancementDowngradeFloor
    }
    // 截图 / 进度帧预览 / 播放信息层 / 手势 开关，供控件层与渲染层消费。
    val gestureEnabled by PlayerEnhancementPrefs.gestureEnabled.collectAsStateWithLifecycle()
    val screenshotEnabled by PlayerEnhancementPrefs.screenshotEnabled.collectAsStateWithLifecycle()
    val framePreviewEnabled by PlayerEnhancementPrefs.framePreviewEnabled.collectAsStateWithLifecycle()
    val statsEnabled by PlayerEnhancementPrefs.statsEnabled.collectAsStateWithLifecycle()
    // 截图落盘位置：保存到「用户选择的下载目录 /img」，并在此发状态栏通知。
    // 用 lambda 每次现取，用户在设置里改了下载位置后立刻生效。
    DisposableEffect(playerState) {
        playerState.screenshotDirProvider = { resolveScreenshotDir(context) }
        playerState.onScreenshotSaved = { file, _ -> ScreenshotNotifier.notify(context, file) }
        onDispose {
            playerState.screenshotDirProvider = null
            playerState.onScreenshotSaved = null
        }
    }
    // player 生命周期由本屏幕掌管：跨平板两栏/全屏分支切换时复用同一 player，
    // 不在 VideoPlayer 自身 dispose 时释放，故底层传 releaseOnDispose = false；
    // 屏幕整体退出时在此统一释放，避免泄漏。
    DisposableEffect(Unit) {
        onDispose {
            // 退出播放页（含切后台被杀/直接关闭）：先把当前进度落库，再释放播放器。
            // viewModelScope 独立于组合生命周期，recordProgress 内的协程可正常执行。
            viewModel.recordProgress(playerState.player.currentPosition)
            playerState.player.release()
        }
    }
    val isFullscreen by playerState.isFullscreen

    // ── 色彩辅助：偏好 → 播放器；面板里直接改播放器状态时回写偏好，保证换集 / 下次进入仍生效 ──
    val colorAssistPref by PlayerEnhancementPrefs.colorAssistMode.collectAsStateWithLifecycle()
    LaunchedEffect(colorAssistPref) {
        playerState.setColorAssistMode(colorAssistPref)
    }
    val runtimeColorAssist by playerState.colorAssistMode
    LaunchedEffect(runtimeColorAssist) {
        if (PlayerEnhancementPrefs.colorAssistMode.value != runtimeColorAssist) {
            PlayerEnhancementPrefs.setColorAssistMode(runtimeColorAssist)
        }
    }

    // ── 小窗播放（画中画）：进入小窗后隐藏整套控制层（系统悬浮按钮接管播放/暂停），
    //    退出小窗再交还；同时把视频比例同步给系统，避免小窗里出现大片黑边。 ──
    val inPip by PipController.inPip.collectAsStateWithLifecycle()
    val videoSize by playerState.videoSize
    val videoAspectRatio = if (videoSize.height > 0) {
        videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
    } else {
        16f / 9f
    }
    LaunchedEffect(inPip) {
        // 退出小窗后复位（进入小窗的置位在 onPip 里，必须早于系统 ON_STOP）
        if (!inPip) playerState.inPipMode = false
        if (inPip) playerState.hideControlUi() else playerState.showControlUi()
    }
    LaunchedEffect(inPip, videoAspectRatio) {
        val activity = context as? Activity ?: return@LaunchedEffect
        // 小窗里双击 → 切换「贴合画面 / 大窗」并记住选择（下次进小窗沿用同一档位）。
        // Android 12+ 的双击放大是系统手势（事件不进应用），这条是给会分发事件的机型兜底。
        playerState.pipToggleSize = if (inPip) {
            {
                val large = PipController.toggleSize(activity, videoAspectRatio)
                Toast.makeText(context, if (large) "小窗已放大" else "小窗已还原", Toast.LENGTH_SHORT).show()
            }
        } else {
            null
        }
        PipController.syncParams(activity, PipController.effectiveRatio(videoAspectRatio))
    }

    // 投屏（DLNA）：选择局域网内的电视 / 盒子并把当前片源推过去。
    // 投屏成功后暂停本机播放，避免手机与电视同时出声；电视端自己去拉流，
    // 因此无法携带 App 内的请求头（需要登录态 / 防盗链的源可能播不出来，面板上已提示）。
    if (showCastSheet) {
        com.example.tuanyingshi.ui.player.cast.CastDeviceSheet(
            onDismiss = { showCastSheet = false },
            activeDeviceName = castingDevice?.name,
            onStop = {
                val device = castingDevice
                castingDevice = null
                if (device != null) {
                    scope.launch {
                        com.example.tuanyingshi.ui.player.cast.DlnaCastManager.stop(device)
                        Toast.makeText(context, "已停止投屏", Toast.LENGTH_SHORT).show()
                        // 回到本机续播：投屏时手机是暂停在原进度上的，直接从那里接着播
                        playerState.control.play()
                    }
                }
            },
            onCast = { device ->
                val url = playUrl
                if (url.isNullOrBlank()) {
                    Toast.makeText(context, "当前没有可投屏的播放地址", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        // 续播：把本机当前进度一起推给电视（DLNA 无起始位置参数，靠 Play 后补 Seek）
                        val startAt = playerState.player.currentPosition
                        val err = com.example.tuanyingshi.ui.player.cast.DlnaCastManager.cast(
                            device = device,
                            videoUrl = url,
                            title = video?.episodeName ?: anime?.title ?: "投屏视频",
                            startPositionMs = startAt,
                        )
                        if (err == null) {
                            castingDevice = device
                            // 本机暂停，避免双声道；进度照常记录，回来还能续播
                            playerState.control.pause()
                            viewModel.recordProgress(startAt)
                            Toast.makeText(context, "已投屏到「${device.name}」", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "投屏失败：$err", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
        )
    }

    // ── 弹幕设置：从 DanmakuPrefs 读取全部偏好并组装 DanmakuConfig ──
    // 任一偏好变化都会触发重组 → rememberDanmakuHostState(config) 重建弹幕引擎，
    // 新参数（字号/不透明度/描边/行高/区域…）即时生效。
    val displayAreaPercent by DanmakuPrefs.displayAreaPercent.collectAsStateWithLifecycle()
    val presentDurationMs by DanmakuPrefs.presentDurationMs.collectAsStateWithLifecycle()
    val lineHeightMultiplier by DanmakuPrefs.lineHeightMultiplier.collectAsStateWithLifecycle()
    val followPlaybackSpeed by DanmakuPrefs.followPlaybackSpeed.collectAsStateWithLifecycle()
    val enableTop by DanmakuPrefs.enableTop.collectAsStateWithLifecycle()
    val enableBottom by DanmakuPrefs.enableBottom.collectAsStateWithLifecycle()
    val enableFloating by DanmakuPrefs.enableFloating.collectAsStateWithLifecycle()
    val allowOverlap by DanmakuPrefs.allowOverlap.collectAsStateWithLifecycle()
    val dedupEnabled by DanmakuPrefs.dedupEnabled.collectAsStateWithLifecycle()
    val enableStroke by DanmakuPrefs.enableStroke.collectAsStateWithLifecycle()
    val enableColor by DanmakuPrefs.enableColor.collectAsStateWithLifecycle()
    val strokeWidth by DanmakuPrefs.strokeWidth.collectAsStateWithLifecycle()
    val fontSizeSp by DanmakuPrefs.fontSizeSp.collectAsStateWithLifecycle()
    val fontWeightIndex by DanmakuPrefs.fontWeightIndex.collectAsStateWithLifecycle()
    val alphaPercent by DanmakuPrefs.alphaPercent.collectAsStateWithLifecycle()
    val keywordFilterEnabled by DanmakuPrefs.keywordFilterEnabled.collectAsStateWithLifecycle()
    // 关键词列表：开关 ON 时生效；为空列表时即等于关闭过滤。
    val keywordFilter = remember(keywordFilterEnabled) {
        if (keywordFilterEnabled) DanmakuPrefs.keywordFilterListParsed() else emptyList()
    }
    // 字体字重 1..9 → W100..W900
    val danmakuFontWeight = when (fontWeightIndex) {
        1 -> FontWeight.W100; 2 -> FontWeight.W200; 3 -> FontWeight.W300; 4 -> FontWeight.W400
        5 -> FontWeight.W500; 6 -> FontWeight.W600; 7 -> FontWeight.W700; 8 -> FontWeight.W800
        else -> FontWeight.W900
    }
    val danmakuConfig = DanmakuConfig(
        style = DanmakuStyle(
            fontSize = fontSizeSp.sp,
            fontWeight = danmakuFontWeight,
            alpha = (alphaPercent / 100f).coerceIn(0.2f, 1f),
            strokeWidth = strokeWidth,
            enableStroke = enableStroke,
        ),
        displayArea = (displayAreaPercent / 100f).coerceIn(0.1f, 1f),
        enableColor = enableColor,
        enableTop = enableTop,
        enableFloating = enableFloating,
        enableBottom = enableBottom,
        followPlaybackSpeed = followPlaybackSpeed,
        allowOverlap = allowOverlap,
        dedupEnabled = dedupEnabled,
        keywordFilter = keywordFilter,
        danmakuTrackProperties = DanmakuTrackProperties(
            verticalPadding = 1,
            speedMultiplier = 1.14f,
            fixedDanmakuPresentDuration = presentDurationMs.toLong(),
            lineHeightMultiplier = lineHeightMultiplier,
        ),
    )
    val danmakuHostState = rememberDanmakuHostState(danmakuConfig)

    // 当前激活的弹幕会话（由下方 LaunchedEffect 在弹幕数据就绪时注入），
    // 供“跳转进度”监听器（onPositionDiscontinuity）在 seek 后通知引擎以新位置重排。
    // 用可变 State 持有，监听器回调与弹幕协程都能读到最新会话。
    var danmakuSession by remember { mutableStateOf<TimeBasedDanmakuSession?>(null) }

    // 跟随视频倍速：监听 ExoPlayer.playbackParameters.speed，写回 danmakuHostState。
    DisposableEffect(Unit) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                danmakuHostState.playbackSpeed = playbackParameters.speed
            }

            // 跳转进度（拖动/点击进度条、快进快退按钮等任意 seekTo）后，通知弹幕引擎以新位置重排，
            // 修复「滑动进度条后弹幕叠加 / 缺失 / 陈旧」的问题（默认 3 秒启发式对小幅拖动无感知）。
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK
                    || reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    danmakuSession?.reset(newPosition.positionMs)
                }
            }
        }
        playerState.player.addListener(listener)
        // 初始同步一次（监听器只在变化时触发）
        danmakuHostState.playbackSpeed = playerState.player.playbackParameters.speed
        onDispose { playerState.player.removeListener(listener) }
    }
    // 视频画面宽高比：从 ExoPlayer 真实 videoSize 动态读取（不再写死 16:9），
    // 片源是 4:3 / 21:9 等非 16:9 比例时，弹幕层也能精确约束到视频画面区，不悬中间。
    val isPlaying by playerState.isPlaying
    // 是否正在拖动进度条（scrubbing）：拖动时为 true，松手后为 false；
    // 实际 seekTo 仅在松手（onSeeked）时执行，拖动期间播放器仍停在旧位置。
    val isSeeking by playerState.isSeeking
    // 播放结束状态（当前集自然播完）与「自动换集」偏好，供自动连播下一集判定
    val isEnded by playerState.isEnded
    val autoNextEpisode by PlaybackPrefs.autoNextEpisode.collectAsStateWithLifecycle()
    // 「更多」面板里的循环播放 / 后台听视频 / 条目显隐（全部持久化，跨集跨页保持）
    val loopMode by PlaybackPrefs.loopMode.collectAsStateWithLifecycle()
    val backgroundPlay by PlaybackPrefs.backgroundPlayEnabled.collectAsStateWithLifecycle()
    val hiddenMoreItems by PlayerMorePrefs.hiddenItems.collectAsStateWithLifecycle()
    var showEditPlayerDialog by remember { mutableStateOf(false) }

    // 「编辑播放器」：自定义「更多」面板里显示哪些条目（显隐持久化到 PlayerMorePrefs）
    if (showEditPlayerDialog) {
        EditPlayerPanelDialog(onDismiss = { showEditPlayerDialog = false })
    }

    // 循环模式同步给播放器：单集循环由播放器内置实现，列表循环在下面的播完回调里处理
    LaunchedEffect(loopMode) { playerState.setLoopMode(loopMode) }
    // 后台听视频：应用退到后台时不暂停（VideoPlayer 的 ON_STOP 分支据此决定是否暂停）
    LaunchedEffect(backgroundPlay) { playerState.backgroundAudioEnabled = backgroundPlay }

    // 仅进入时按开关初始化一次全屏状态，不随屏幕旋转自动切换，
    // 避免“点击全屏按钮后自动横屏并放大”的问题。
    // 平板端由「平板端进入播放页自动全屏」控制，手机端由「进入播放页自动全屏」控制；
    // 开关关闭时不自动全屏（即便横屏也保持普通播放），仅用户手动点全屏按钮才进入全屏。
    val autoFullscreenOnEnter = if (isTablet()) PlaybackPrefs.isAutoFullscreenTablet() else PlaybackPrefs.isAutoFullscreen()
    LaunchedEffect(Unit) { playerState.control.setFullscreen(autoFullscreenOnEnter) }

    // 播放期间保持屏幕常亮，避免系统长时间无操作后自动息屏
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // 当前集自然播放结束后：
    // - 列表循环：播下一集，最后一集回到第一集（与「自动连播」独立，即便连播关了也要接着放）；
    // - 否则按「自动连播」设置播下一集（不回绕）。
    // 注意单集循环不会走到这里：播放器用 REPEAT_MODE_ONE 自己循环，不产生「播放结束」状态。
    LaunchedEffect(isEnded, loopMode, autoNextEpisode) {
        if (!isEnded) return@LaunchedEffect
        val idx = video?.currentEpisodeIndex ?: 0
        val next = when {
            loopMode == LoopMode.LIST -> episodes.getOrNull(idx + 1) ?: episodes.firstOrNull()
            autoNextEpisode -> episodes.getOrNull(idx + 1)
            else -> null
        }
        if (next != null && next.url != video?.episodeUrl) {
            viewModel.recordProgress(playerState.player.currentPosition)
            viewModel.selectEpisode(next)
        }
    }

    // 周期保存播放进度（每 15 秒）：覆盖「播放中退出 / 切后台被杀」等未走返回键的场景，
    // 保证记忆播放位置足够新鲜。仅在正在播放且未拖动时保存；走本地-only，避免后端进度接口被高频调用。
    LaunchedEffect(playUrl) {
        while (true) {
            delay(15_000)
            if (playerState.isPlaying.value && !playerState.isSeeking.value) {
                viewModel.recordProgress(playerState.player.currentPosition, syncToBackend = false)
            }
        }
    }

    // 切集 / 换源 / 重试导致 playUrl 变化时，用本次进入的续播位置决定是否显示「续播提示条」
    LaunchedEffect(playUrl) {
        if (resumePosition > 3_000L) {
            resumeBannerPosition = resumePosition
            showResumeBanner = true
        } else {
            showResumeBanner = false
        }
    }
    // 提示条自动消失：进入约 6 秒后收起（用户也可手动关闭 / 点「从头播放」/ 拖动进度条）
    LaunchedEffect(showResumeBanner) {
        if (showResumeBanner) {
            delay(6_000)
            showResumeBanner = false
        }
    }
    // 用户手动拖动进度条（scrubbing）时立即收起提示条
    LaunchedEffect(isSeeking) {
        if (isSeeking) showResumeBanner = false
    }

    // 把 ViewModel 加载到的弹幕数据喂给弹幕引擎，并按播放进度联动。
    // 仅当「弹幕开关」打开且视频已就绪时接入；切集/换源导致 danmakuItems / playUrl 变化时自动重建会话。
    LaunchedEffect(danmakuOn, danmakuItems, playUrl) {
        if (!danmakuOn || playUrl == null) {
            danmakuHostState.clearPresentDanmaku()
            danmakuSession = null
            return@LaunchedEffect
        }
        val items = danmakuItems
        if (items.isEmpty()) {
            danmakuSession = null
            return@LaunchedEffect
        }
        val session = TimeBasedDanmakuSession.create(
            items.mapIndexed { i, it -> it.toDanmaku(i) }.asSequence(),
        )
        danmakuSession = session
        // 暂停、关弹幕或拖动进度条时，引擎不应继续轮询发送弹幕。
        // 拖动期间 isPlayingFlow 为 false，避免弹幕在 scrubbing 过程中持续堆积/错位；
        // 松手后 onPositionDiscontinuity 会以新位置 reset，弹幕随之重新装填。
        val isPlayingFlow = combine(
            snapshotFlow { playerState.isPlaying.value },
            snapshotFlow { !playerState.isSeeking.value },
        ) { playing, notSeeking -> playing && notSeeking }
        session.at(
            curTimeMillis = { (playerState.player.currentPosition).milliseconds },
            isPlayingFlow = isPlayingFlow,
        ).collect { event ->
            when (event) {
                is DanmakuEvent.Add ->
                    danmakuHostState.trySend(DanmakuPresentation(event.danmaku, false))
                is DanmakuEvent.Repopulate -> {
                    // 快进/快退：清空屏幕并按「自然位置」重新装填附近弹幕。
                    // 交给 hostState 处理——它会按每条弹幕自己的播放时间推算此刻应在的位置，
                    // 已滚出屏幕的直接丢弃；绝不能在这里直接 forEach + trySend，否则整个窗口
                    // 的弹幕会同一帧从右边缘一起涌出、叠成一坨（拖动进度条后糊满屏幕的根因）。
                    danmakuHostState.repopulate(
                        list = event.list.map { DanmakuPresentation(it, isSelf = false) },
                        playTimeMillis = event.playTimeMillis,
                    )
                }
            }
        }
    }

    // 弹幕随视频播放/暂停/拖动进度条联动：暂停、关弹幕或拖动进度条时冻结弹幕动画。
    LaunchedEffect(danmakuOn, isPlaying, isSeeking) {
        if (danmakuOn && isPlaying && !isSeeking) danmakuHostState.play() else danmakuHostState.pause()
    }

    // 拖动进度条（scrubbing）开始时立即清空调幕弹幕：拖动期间进度条 UI 与播放器位置脱节，
    // 残留弹幕会显得错位/堆积；松手后弹幕会话以新位置 reset 并重新装填，画面干净。
    LaunchedEffect(isSeeking) {
        if (isSeeking) danmakuHostState.clearPresentDanmaku()
    }

    fun handleBack() {
        viewModel.recordProgress(playerState.player.currentPosition)
        if (isFullscreen) playerState.control.setFullscreen(false)
        else navController.popBackStack()
    }

    // 全应用统一为沉浸式（edge-to-edge）：窗口内容延伸到状态栏/导航栏之下，
    // 由「系统栏可见性」控制是否预留空间，而不是去切换 decorFitsSystemWindows。
    // 之前在竖屏时把 decorFitsSystemWindows 切回 true，会把系统栏 inset 清零，
    // 导致 Scaffold 的 innerPadding 不再预留状态栏高度，视频顶到状态栏底下。
    DisposableEffect(isFullscreen) {
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val w = (context as? Activity)?.window
            if (w != null) {
                WindowCompat.setDecorFitsSystemWindows(w, false)
                WindowCompat.getInsetsController(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 最终交付给 ExoPlayer 的直链 + 请求头（每次切集/换源都会重打一次），用于核对播放器实际拿到什么
    LaunchedEffect(playUrl) {
        "PlayerScreen: 交给 VideoPlayer url=${playUrl?.take(160)} headers=${video?.headers?.size ?: 0}".log("PlayerScreen")
    }

    // 进出小窗会改变「屏幕最小宽度」（小窗窗口只有两三百 dp），若让这个值随配置变化，
    // 播放页会在「平板左右分栏」与「手机上下竖排」两套布局之间来回切换 ——
    // VideoPlayer 被搬到另一个分支就会重新装载片源，进度被打回进入页面时的值
    // （表现为「一切到小窗就从头播放」）。这里按进入页面时的形态锁定，整页生命周期内不变。
    val smallestWidthDp = smallestWidthDp()
    val isTabletDevice = remember(Unit) { smallestWidthDp >= 600 }
    // 选中 Hanime1 主数据源时，播放页评论区走 Hanime1 评论接口、简介区附带「系列/相关推荐」。
    val isHanime1 = com.example.tuanyingshi.util.SourceHolder.currentSourceMode ==
        com.example.tuanyingshi.util.SourceMode.Hanime1

    @Composable
    fun VideoSurface(modifier: Modifier) {
        Box(modifier = modifier.background(Color.Black)) {
            val sourceFinding by viewModel.sourceFinding.collectAsStateWithLifecycle()
            val sourceFindRemaining by viewModel.sourceFindRemainingMs.collectAsStateWithLifecycle()
            val autoSwitchingSource by viewModel.autoSwitchingSource.collectAsStateWithLifecycle()
            val loadingMsg = when {
                autoSwitchingSource -> "正在自动切换数据源…"
                sourceFinding && sourceFindRemaining > 0 -> "正在寻找可播放链接… ${sourceFindRemaining / 1000}s"
                else -> "视频加载中…"
            }
            when {
                // 出错：视频区显示内联错误（含重试 / 看已下载），不再整页替换，信息面板仍可见
                error != null -> ErrorOverlay(
                    message = error,
                    downloaded = downloaded,
                    reloginText = if (cycaniExpired) "重新登录次元城" else null,
                    onRelogin = {
                        navController.navigate(
                            Screen.CycaniLogin.create(username = CycaniAuthManager.username.orEmpty()),
                        )
                    },
                    onRetry = viewModel::retry,
                    onWatchDownloaded = { dl ->
                        navController.navigate(Screen.LocalPlayer.create(dl.filePath, detailUrl, dl.episodeUrl))
                    },
                )
                // 直链就绪：渲染播放器
                playUrl != null -> VideoPlayer(
                modifier = Modifier.fillMaxSize(),
                url = playUrl,
                // 记忆播放：进入即跳到上次进度（无进度时为 0，从头播放）
                videoPosition = resumePosition,
                playerState = playerState,
                headers = video?.headers ?: emptyMap(),
                releaseOnDispose = false,
                onBackPress = { handleBack() },
                // 播放失败：带上当前进度，先落库再走失败处理
                onError = { viewModel.onPlaybackError(playerState.player.currentPosition) },
                gestureEnabled = gestureEnabled,
                // 弹幕层：挂在播放器「视频画面之上、控制层之下」的槽位里。
                // 这样弹幕既看得见（SurfaceView 会穿透窗口，画在它下面的内容不可见），
                // 又不会盖住顶部 / 底部按钮与「更多」等覆盖面板 —— 因此面板打开时
                // 不必再隐藏弹幕，弹幕照常按时间轴滚动，关掉面板也不会有跳帧。
                overlay = {
                    // 小窗里不挂弹幕：窗口只有两三百 dp，正常字号的弹幕会把整个画面糊满，
                    // 也会被缩得完全看不清（小窗只保留视频画面本身）。
                    if (danmakuOn && playUrl != null && error == null && !inPip) {
                        // 与视频画面共用 adaptiveLayout 定尺居中：弹幕若自己另算 letterbox 内边距，
                        // 一旦片源比例或 ResizeMode 与假设不符就会错位（“悬在画面中间”）。
                        // videoSize 尚未就绪时传 0f：adaptiveLayout 内部与视频同样回退 16:9，保持同步。
                        val vs = playerState.videoSize.value
                        val ar = if (vs.width > 0 && vs.height > 0) {
                            (vs.width * vs.pixelWidthHeightRatio) / vs.height
                        } else 0f
                        DanmakuHost(
                            state = danmakuHostState,
                            modifier = Modifier
                                .adaptiveLayout(
                                    aspectRatio = ar,
                                    resizeMode = playerState.videoResizeMode.value,
                                )
                                .fillMaxSize(),
                        )
                    }
                },
                controller = {
                    // 小窗（画中画）里不渲染控制层：系统悬浮按钮已提供播放 / 暂停，
                    // 且小窗尺寸下这套控件会挤成一团看不清。
                    if (!inPip) VideoPlayerControl(
                        state = playerState,
                        screenshotEnabled = screenshotEnabled,
                        framePreviewEnabled = framePreviewEnabled,
                        statsEnabled = statsEnabled,
                        // 「更多」面板：小窗 / 投屏 / 定时关闭 / 播放设置
                        moreConfig = PlayerMoreConfig(
                            // 小窗播放：进入系统画中画，继续在悬浮窗里看
                            onPip = {
                                val activity = context as? Activity
                                if (activity == null) {
                                    Toast.makeText(context, "当前页面不支持小窗播放", Toast.LENGTH_SHORT).show()
                                } else {
                                    playerState.hideMoreUi()
                                    // 先把进度落库：万一进小窗导致页面被重建，回来还能从此处续播
                                    viewModel.recordProgress(playerState.player.currentPosition)
                                    // 先置位再进入：部分机型进入小窗时 Activity 会收到 ON_STOP，
                                    // 播放器默认在 ON_STOP 暂停 —— 小窗里画面仍可见，那样就成了「切小窗就停」。
                                    playerState.inPipMode = true
                                    if (!PipController.enter(activity, videoAspectRatio)) {
                                        playerState.inPipMode = false
                                        Toast.makeText(context, "当前设备不支持小窗播放", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            // 投屏：打开局域网设备选择面板
                            onCast = {
                                playerState.hideMoreUi()
                                showCastSheet = true
                            },
                            autoPlayNext = autoNextEpisode,
                            onAutoPlayNextChange = { PlaybackPrefs.setAutoNextEpisode(it) },
                            loopMode = loopMode,
                            onLoopModeChange = { PlaybackPrefs.setLoopMode(it) },
                            backgroundAudio = backgroundPlay,
                            onBackgroundAudioChange = { PlaybackPrefs.setBackgroundPlayEnabled(it) },
                            onFeedback = {
                                playerState.hideMoreUi()
                                navController.navigate(Screen.Feedback.route)
                            },
                            onEditPlayer = {
                                playerState.hideMoreUi()
                                showEditPlayerDialog = true
                            },
                            // 选集：原先是播放器中间的选集面板，现在收进「更多」面板。
                            // 点选后自动关闭面板（onClose 由面板内部调用），立刻看到切集结果。
                            episodes = episodes.map { it.name },
                            currentEpisodeIndex = video?.currentEpisodeIndex ?: -1,
                            onEpisodeClick = { index ->
                                val ep = episodes.getOrNull(index)
                                if (ep != null) {
                                    viewModel.recordProgress(playerState.player.currentPosition)
                                    viewModel.selectEpisode(ep)
                                }
                            },
                            hiddenItems = hiddenMoreItems,
                        ),
                        // 快速打开「播放设置」：从播放页直达，离开时生命周期 ON_STOP 会自动暂停。
                        // 打开设置前先把当前进度落库——设置里可能改倍速 / 画质增强等会影响播放的项，
                        // 甚至从设置页直接返回上级，回来时（或下次进来）都能从此处续播。
                        onSettingsClick = {
                            viewModel.recordProgress(playerState.player.currentPosition)
                            // 当时正在播放 → 关掉设置回来后自动接着播（断点继播）；
                            // 本来就是暂停状态则保持暂停，不擅自替用户做决定。
                            if (playerState.isPlaying.value) playerState.resumeOnReturn = true
                            navController.navigate(Screen.PlaybackSettings.route)
                        },
                        title = anime?.title ?: "播放",
                        subtitle = video?.episodeName,
                        danmakuEnabled = danmakuOn,
                        onBackClick = { handleBack() },
                        onDanmakuClick = { danmakuOn = it },
                        // 下一集：跳到当前集后一集
                        onNextClick = {
                            val next = episodes.getOrNull((video?.currentEpisodeIndex ?: 0) + 1)
                            if (next != null) {
                                viewModel.recordProgress(playerState.player.currentPosition)
                                viewModel.selectEpisode(next)
                            }
                        },
                    )
                }
                )
                // 加载中：视频区显示局部加载动画（用户已「进入页面」，仅此区域在转圈）
                // 找源阶段显示 5 秒倒计时 / 自动切换提示，避免慢源时干等黑屏
                else -> LoadingIndicator(loadingMsg)
            }
            // 续播提示条：独立于控制层显隐，确保一进入就能看到「已从进度继续播放」并可一键从头播放
            // 小窗里不显示：窗口太小，这条提示会压掉画面，也没法点。
            if (showResumeBanner && playUrl != null && error == null && !inPip) {
                ResumePlaybackBanner(
                    positionMs = resumeBannerPosition,
                    onPlayFromStart = {
                        playerState.player.seekTo(0)
                        showResumeBanner = false
                    },
                    onDismiss = { showResumeBanner = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 92.dp),
                )
            }
        }
    }

    @Composable
    fun InfoPanel(modifier: Modifier, onOpenSourceSheet: () -> Unit = {}) {
        if (anime == null) {
            // 元数据未到：信息面板显示骨架（部分 UI 加载动画），与视频区加载并行进行
            PlayerInfoSkeleton(modifier = modifier)
            return
        }
        Column(modifier = modifier) {
            // 弹幕匹配信息条：常驻显示在信息面板顶部（选集/简介上方），展示当前自动匹配的弹幕番剧/集；
            // 未匹配到时提示用户点击自行匹配。点击打开弹幕匹配搜索面板。
            DanmakuMatchBar(
                match = danmakuMatch,
                modifier = Modifier.fillMaxWidth(),
                onSearch = {
                    danmakuQuery = danmakuMatch?.animeTitle ?: anime?.title ?: ""
                    viewModel.openDanmakuSearch()
                },
            )
            // 发弹幕：用当前播放位置作为弹幕出现时间点
            DanmakuSendBar(
                text = danmakuInput,
                colorHex = danmakuColor,
                sending = danmakuSending,
                enabled = danmakuSendEnabled,
                onTextChange = { danmakuInput = it },
                onColorChange = { danmakuColor = it },
                onSend = {
                    val content = danmakuInput
                    val color = danmakuColor
                    scope.launch {
                        danmakuSending = true
                        val err = viewModel.sendDanmaku(
                            text = content,
                            positionMs = playerState.player.currentPosition,
                            colorHex = color,
                        )
                        danmakuSending = false
                        if (err == null) {
                            danmakuInput = ""
                            // 弹幕关着的话发出去看不见效果，顺手打开
                            danmakuOn = true
                            Toast.makeText(context, "弹幕已发送", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
            PlayerTabBar(tab = tab, onChange = { tab = it }, commentCount = comments.size)

            // Hanime1 风格：简介区上方插入「系列 / 相关推荐」横向行（拉取该视频的 series + related）。
            if (isHanime1) {
                val hanime1Code = (video?.episodeUrl ?: detailUrl).substringAfterLast("/").substringBefore("?")
                com.example.tuanyingshi.ui.player.hanime1.Hanime1RelatedRow(videoCode = hanime1Code, navController = navController)
                Spacer(Modifier.height(8.dp))
            }

            when (tab) {
                PlayerTab.INTRO -> IntroContent(
                    anime = anime,
                    episodes = episodes,
                    currentEpisodeUrl = video?.episodeUrl.orEmpty(),
                    related = related,
                    downloaded = downloaded,
                    detailUrl = detailUrl,
                    channelNames = channelNames,
                    currentChannelIndex = currentChannelIndex,
                    sourceName = currentSourceName,
                    sourceIconUrl = currentSourceIcon,
                    onChannelClick = { viewModel.selectChannel(it) },
                    onEpisodeClick = { ep ->
                        // 切换集数前记录上一集播放进度
                        viewModel.recordProgress(playerState.player.currentPosition)
                        viewModel.selectEpisode(ep)
                    },
                    onDownloadedClick = { dl ->
                        navController.navigate(
                            Screen.LocalPlayer.create(dl.filePath, detailUrl, dl.episodeUrl),
                        )
                    },
                    onRelatedClick = { url -> navController.navigate(Screen.Detail.create(url)) },
                    onOpenSourceSheet = onOpenSourceSheet,
                )
                PlayerTab.COMMENTS -> if (isHanime1) {
                    val hanime1Code = (video?.episodeUrl ?: detailUrl).substringAfterLast("/").substringBefore("?")
                    com.example.tuanyingshi.ui.player.hanime1.Hanime1CommentsBlock(videoCode = hanime1Code)
                } else {
                    CommentsList(comments, onAddComment = viewModel::addComment)
                }
            }
        }
    }

    // 全屏切换时务必让 VideoSurface（即 VideoPlayer）停留在组合树的同一槽位：
    // 否则平板下会从 Row 分支切到 else(Column) 分支，导致 VideoPlayer 被重挂，
    // 触发库内 LaunchedEffect(url) 再次 prepare()+seekTo(0) —— 表现为「退出全屏播放位置归零 / 切换卡顿」。
    // 故全屏只改播放器尺寸与信息面板显隐，不切换根容器。
    // 「只留播放器」= 全屏 或 小窗。
    // 小窗（画中画）会把**整个 Activity** 塞进那个两三百 dp 的悬浮窗里，若还保留信息面板，
    // 视频就被挤到窗口的 58% 宽度，旁边挂着一整块「温馨提示 / 弹幕匹配 / 简介·评论 / 数据源」，
    // 在小窗里既看不清也用不上。小窗的目的是「一边干别的一边看画面」，所以与全屏同样处理：
    // 播放器占满、其余全部不渲染。
    // 注意：这里只是改尺寸与显隐，根容器分支仍按 isTabletDevice（进页时锁定）走，
    // VideoSurface 保持在组合树的同一槽位，不会触发重新装载片源（进度被打回的坑）。
    val playerOnly = isFullscreen || inPip
    Box(Modifier.fillMaxSize()) {
        if (isTabletDevice) {
            // 平板：左右分栏；全屏 / 小窗时播放器占满整行、隐藏信息面板（VideoSurface 始终在 Row 同一槽位）。
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                VideoSurface(
                    Modifier
                        .weight(if (playerOnly) 1f else 0.58f)
                        .fillMaxHeight(),
                )
                if (!playerOnly) {
                    Column(Modifier.weight(0.42f).fillMaxHeight()) {
                        if (showSecurityTip) {
                            SecurityWarningBanner(onDismiss = { showSecurityTip = false })
                        }
                        InfoPanel(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onOpenSourceSheet = {
                                viewModel.loadSourceCandidates()
                                showSourceSheet = true
                            },
                        )
                    }
                }
            }
        } else {
            // 手机：竖排；全屏 / 小窗时播放器占满、隐藏信息面板（VideoSurface 始终在 Column 同一槽位）。
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                VideoSurface(
                    Modifier
                        .fillMaxWidth()
                        .then(if (playerOnly) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f)),
                )
                if (!playerOnly && showSecurityTip) {
                    SecurityWarningBanner(onDismiss = { showSecurityTip = false })
                }
                if (!playerOnly) {
                    InfoPanel(
                        Modifier.fillMaxWidth().weight(1f),
                        onOpenSourceSheet = {
                            viewModel.loadSourceCandidates()
                            showSourceSheet = true
                        },
                    )
                }
            }
        }
    }
}

/**
 * 发弹幕输入条：颜色圆点 + 输入框 + 发送。
 *
 * 不可用时（未开后端模式或未登录）不给输入框，只留一行说明 ——
 * 否则用户填完点发送才发现发不出去，体验更差。
 */
@Composable
private fun DanmakuSendBar(
    text: String,
    colorHex: String,
    sending: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    if (!enabled) {
        Text(
            text = "开启后端模式并登录后，即可发送弹幕",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 颜色圆点：点击在预设色之间循环切换
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(android.graphics.Color.parseColor(colorHex)))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable {
                    val next = (DANMAKU_COLORS.indexOf(colorHex) + 1) % DANMAKU_COLORS.size
                    onColorChange(DANMAKU_COLORS[next])
                },
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            placeholder = { Text("发个弹幕…", maxLines = 1) },
            singleLine = true,
            enabled = !sending,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onSend, enabled = !sending && text.isNotBlank()) {
            if (sending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送弹幕")
            }
        }
    }
}

/**
 * 播放页弹幕匹配信息浮标：展示当前自动匹配到的弹弹play 弹幕番剧/集，点击打开手动搜索面板切换。
 */
@Composable
/**
 * 弹幕匹配信息条：常驻显示在信息面板顶部（选集 / 简介上方）。
 * - 已匹配：展示「番名 · 集名」，点击可更换匹配。
 * - 未匹配：提示用户点击自行选择对应的番剧 / 集。
 */
private fun DanmakuMatchBar(
    match: DanmakuMatchInfo?,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onSearch),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Subtitles,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                if (match != null) {
                    Text(
                        text = "弹幕匹配：${match.animeTitle} · ${match.episodeTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = "点击可更换弹幕对应的番剧 / 集",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = "未匹配到弹幕，点击选择对应的番剧 / 集",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "选择弹幕匹配",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 将弹弹play 的 [DanmakuItem] 转换为弹幕引擎使用的 [Danmaku]。
 * [mode] 沿用弹弹play 约定：1=滚动(NORMAL)，4=底部，5=顶部，其余按滚动处理；[time] 单位为秒。
 */
private fun DanmakuItem.toDanmaku(index: Int): Danmaku {
    val location = when (mode) {
        4 -> DanmakuLocation.BOTTOM
        5 -> DanmakuLocation.TOP
        else -> DanmakuLocation.NORMAL
    }
    val colorInt = runCatching { android.graphics.Color.parseColor(color) }
        .getOrDefault(0xFFFFFFFF.toInt())
    return Danmaku(
        id = "dm_$index",
        providerId = "dandanplay",
        playTimeMillis = (time * 1000.0).toLong(),
        senderId = "dandanplay",
        location = location,
        text = text,
        color = colorInt,
    )
}

@Composable
/** 播放失败内联提示：解析失败 / 链接不可播放时在视频区展示，可重试或看已下载；不整页替换。 */
private fun ErrorOverlay(
    message: String?,
    downloaded: List<DownloadedEpisode>,
    onRetry: () -> Unit,
    onWatchDownloaded: (DownloadedEpisode) -> Unit,
    /** 非空时额外展示一个「重新登录」按钮（次元城 token 失效场景）。 */
    reloginText: String? = null,
    onRelogin: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message ?: "播放失败",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        if (downloaded.isNotEmpty()) {
            Button(onClick = { onWatchDownloaded(downloaded.first()) }) {
                Text("观看已下载（${downloaded.size}）")
            }
            Spacer(Modifier.height(12.dp))
        }
        if (reloginText != null) {
            Button(onClick = onRelogin) { Text(reloginText) }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun PlayerTabBar(tab: PlayerTab, onChange: (PlayerTab) -> Unit, commentCount: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PlayerTab.values().forEach { t ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onChange(t) }
                    .padding(end = 24.dp),
            ) {
                val selected = t == tab
                Text(
                    text = if (t == PlayerTab.COMMENTS) "评论($commentCount)" else t.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .width(24.dp)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun IntroContent(
    anime: AnimeDetailLite?,
    episodes: List<Episode>,
    currentEpisodeUrl: String,
    related: List<Anime>,
    downloaded: List<DownloadedEpisode>,
    detailUrl: String,
    channelNames: List<String> = emptyList(),
    currentChannelIndex: Int = 0,
    sourceName: String = "",
    sourceIconUrl: String = "",
    onChannelClick: (Int) -> Unit = {},
    onEpisodeClick: (Episode) -> Unit,
    onDownloadedClick: (DownloadedEpisode) -> Unit,
    onRelatedClick: (String) -> Unit,
    onOpenSourceSheet: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Text(
                text = anime?.title ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            // 数据源快捷卡片：与详情页同一组件、同一选择（换源后实时更新），
            // 点击打开「选择数据源」面板（animeko：播放页可切换 mediaSource）
            SourceSwitchCard(
                sourceName = sourceName,
                iconUrl = sourceIconUrl,
                onClick = onOpenSourceSheet,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            ExpandableText(
                text = anime?.desc.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Text(
                text = "1000 万次播放",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        // 已下载（离线可看）分区：点按直接本地播放
        if (downloaded.isNotEmpty()) {
            item { SectionHeader("已下载（离线观看）") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    items(downloaded, key = { it.episodeUrl }) { dl ->
                        EpisodeChip(
                            title = dl.episodeName,
                            selected = dl.episodeUrl == currentEpisodeUrl,
                            onClick = { onDownloadedClick(dl) },
                        )
                    }
                }
            }
        }
        item {
            SectionHeader("选集")
            if (channelNames.size > 1) {
                ChannelTabs(
                    names = channelNames,
                    selected = currentChannelIndex,
                    onSelect = onChannelClick,
                )
            }
            // 与详情页同一选集网格组件（同列数自适应/同样式），当前播放集高亮
            EpisodeGrid(
                episodes = episodes,
                onPlay = onEpisodeClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                currentEpisodeUrl = currentEpisodeUrl,
            )
        }
        if (related.isNotEmpty()) {
            item { SectionHeader("相关动画") }
            itemsIndexed(related, key = { index, r -> "${index}_${r.detailUrl}" }) { _, r ->
                RelatedAnimeRow(anime = r, onClick = { onRelatedClick(r.detailUrl) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EpisodeChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(title, color = textColor, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RelatedAnimeRow(anime: Anime, onClick: () -> Unit) {
    var showAbandon by remember { mutableStateOf(false) }
    val canAbandon = LocalUserStatusRepository.current != null
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (canAbandon) showAbandon = true },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = anime.img,
            contentDescription = anime.title,
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                anime.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            if (anime.episodeName.isNotBlank()) {
                Text(
                    anime.episodeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "连载更新中",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("详情", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (showAbandon) {
        AbandonDialog(
            detailUrl = anime.detailUrl,
            title = anime.title,
            imgUrl = anime.img,
            onDismiss = { showAbandon = false },
        )
    }
}

@Composable
private fun CommentItem(c: Comment) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(c.avatarLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.username, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(c.timeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(c.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${c.likes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CommentsList(
    comments: List<Comment>,
    onAddComment: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("发表你的评论…") },
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 3,
            )
            IconButton(
                onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) {
                        onAddComment(t)
                        text = ""
                    }
                },
                enabled = text.trim().isNotEmpty(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (text.trim().isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(comments, key = { it.id }) { c -> CommentItem(c) }
        }
    }
}

/**
 * 视频页安全提醒横幅：视频内嵌的广告、弹窗与外部链接均非本应用提供，
 * 用于提醒用户不要轻信或点击，防止上当受骗。点击关闭按钮仅隐藏本次显示（下次进入仍会提醒）。
 */
@Composable
private fun SecurityWarningBanner(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "温馨提示：视频内的广告、弹窗及外部链接均非本应用提供，请勿轻信或点击，谨防诈骗。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭提醒",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
