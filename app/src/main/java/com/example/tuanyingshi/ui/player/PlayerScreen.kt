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
import com.example.tuanyingshi.ui.detail.ChannelTabs
import com.example.tuanyingshi.ui.components.MediaSourceSheet
import com.example.tuanyingshi.ui.components.SourceIcon
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.ContentPrefs
import com.example.tuanyingshi.util.DanmakuPrefs
import com.example.tuanyingshi.util.PlaybackPrefs
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
    val showSourcePicker by viewModel.showSourcePicker.collectAsStateWithLifecycle()
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
            currentSourceId = anime?.sourceId ?: "",
            currentChannelIndex = currentChannelIndex,
            onSelect = { candidate, line ->
                viewModel.selectSourceVideo(candidate, line)
                showSourceSheet = false
                viewModel.clearShowSourcePicker()
            },
            onDismiss = {
                showSourceSheet = false
                viewModel.onSourceSheetDismissed()
            },
        )
    }

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
    // player 生命周期由本屏幕掌管：跨平板两栏/全屏分支切换时复用同一 player，
    // 不在 VideoPlayer 自身 dispose 时释放，故底层传 releaseOnDispose = false；
    // 屏幕整体退出时在此统一释放，避免泄漏。
    DisposableEffect(Unit) {
        onDispose { playerState.player.release() }
    }
    val isFullscreen by playerState.isFullscreen

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

    // 当前集自然播放结束后，按「自动换集」设置自动连播下一集
    LaunchedEffect(isEnded) {
        if (isEnded && autoNextEpisode) {
            val idx = video?.currentEpisodeIndex ?: 0
            val next = episodes.getOrNull(idx + 1)
            if (next != null) {
                viewModel.recordProgress(playerState.player.currentPosition)
                viewModel.selectEpisode(next)
            }
        }
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
                    // 快进/快退：清空屏幕并以附近弹幕重新装填
                    danmakuHostState.clearPresentDanmaku()
                    event.list.forEach { danmakuHostState.trySend(DanmakuPresentation(it, false)) }
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

    val isTabletDevice = isTablet()

    @Composable
    fun VideoSurface(modifier: Modifier) {
        Box(modifier = modifier.background(Color.Black)) {
            when {
                // 出错：视频区显示内联错误（含重试 / 看已下载），不再整页替换，信息面板仍可见
                error != null -> ErrorOverlay(
                    message = error,
                    downloaded = downloaded,
                    onRetry = viewModel::retry,
                    onWatchDownloaded = { dl ->
                        navController.navigate(Screen.LocalPlayer.create(dl.filePath, detailUrl, dl.episodeUrl))
                    },
                )
                // 直链就绪：渲染播放器
                playUrl != null -> VideoPlayer(
                modifier = Modifier.fillMaxSize(),
                url = playUrl,
                videoPosition = 0L,
                playerState = playerState,
                headers = video?.headers ?: emptyMap(),
                releaseOnDispose = false,
                onBackPress = { handleBack() },
                onError = viewModel::onPlaybackError,
                controller = {
                    VideoPlayerControl(
                        state = playerState,
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
                        // 选集面板数据与回调（索引 → 切集）
                        episodes = episodes.map { it.name },
                        onEpisodeClick = { index ->
                            val ep = episodes.getOrNull(index)
                            if (ep != null) {
                                viewModel.recordProgress(playerState.player.currentPosition)
                                viewModel.selectEpisode(ep)
                            }
                        },
                    )
                }
                )
                // 加载中：视频区显示局部加载动画（用户已「进入页面」，仅此区域在转圈）
                else -> LoadingIndicator("视频加载中…")
            }
            // 弹幕覆盖层：与视频画面共用 video-player 的 adaptiveLayout 定尺居中。
            // 之前是弹幕层自己按「假设的宽高比」算 letterbox 内边距，而视频由 adaptiveLayout
            // 按「真实 videoSize + 当前 ResizeMode」定尺，两套算法一旦不一致
            // （片源非 16:9，或用户选了铺满/全屏/4:3 等），弹幕就会相对画面错位、
            // 表现为“悬在画面中间”。改成同源修饰器后，任何比例/缩放模式下都精确贴合。
            // Canvas 不拦截点击，底层控制层仍可操作。仅在弹幕开启且视频就绪时显示。
            if (danmakuOn && playUrl != null && error == null) {
                // videoSize 尚未就绪时传 0f：adaptiveLayout 内部与视频同样回退 16:9，保持同步。
                val vs = playerState.videoSize.value
                val ar = if (vs.width > 0 && vs.height > 0) (vs.width * vs.pixelWidthHeightRatio) / vs.height else 0f
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
                PlayerTab.COMMENTS -> CommentsList(comments, onAddComment = viewModel::addComment)
            }
        }
    }

    // 全屏切换时务必让 VideoSurface（即 VideoPlayer）停留在组合树的同一槽位：
    // 否则平板下会从 Row 分支切到 else(Column) 分支，导致 VideoPlayer 被重挂，
    // 触发库内 LaunchedEffect(url) 再次 prepare()+seekTo(0) —— 表现为「退出全屏播放位置归零 / 切换卡顿」。
    // 故全屏只改播放器尺寸与信息面板显隐，不切换根容器。
    Box(Modifier.fillMaxSize()) {
        if (isTabletDevice) {
            // 平板：左右分栏；全屏时播放器占满整行、隐藏信息面板（VideoSurface 始终在 Row 同一槽位）。
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                VideoSurface(
                    Modifier
                        .weight(if (isFullscreen) 1f else 0.58f)
                        .fillMaxHeight(),
                )
                if (!isFullscreen) {
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
            // 手机：竖排；全屏时播放器占满、隐藏信息面板（VideoSurface 始终在 Column 同一槽位）。
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                VideoSurface(
                    Modifier
                        .fillMaxWidth()
                        .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f)),
                )
                if (!isFullscreen && showSecurityTip) {
                    SecurityWarningBanner(onDismiss = { showSecurityTip = false })
                }
                if (!isFullscreen) {
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
            // 来源标识 + 换源入口（animeko：播放页可切换 mediaSource）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                if ((anime?.sourceName ?: "").isNotEmpty()) {
                    SourceIcon(iconUrl = anime?.iconUrl ?: "", size = 18.dp)
                    Text(
                        "来源：${anime?.sourceName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onOpenSourceSheet) {
                    Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("换源")
                }
            }
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
            SectionHeader("分集")
            if (channelNames.size > 1) {
                ChannelTabs(
                    names = channelNames,
                    selected = currentChannelIndex,
                    onSelect = onChannelClick,
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                itemsIndexed(episodes, key = { index, ep -> "${index}_${ep.url}" }) { _, ep ->
                    EpisodeChip(
                        title = ep.name,
                        selected = ep.url == currentEpisodeUrl,
                        onClick = { onEpisodeClick(ep) },
                    )
                }
            }
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
