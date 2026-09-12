package com.example.tuanyingshi.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.repository.ContentFilterRepository
import com.example.tuanyingshi.data.repository.DownloadRepository
import com.example.tuanyingshi.data.repository.HistoryRepository
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.Comment
import com.example.tuanyingshi.domain.model.Episode
import com.example.tuanyingshi.domain.model.SourceCandidate
import com.example.tuanyingshi.domain.model.Video
import com.example.tuanyingshi.domain.model.WebVideo
import com.example.tuanyingshi.domain.repository.AnimeRepository
import com.example.tuanyingshi.util.Resource
import com.example.tuanyingshi.util.Result
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.VideoUrlChecker
import com.example.tuanyingshi.util.NetworkMonitor
import com.example.tuanyingshi.util.PlaybackPrefs
import com.example.tuanyingshi.util.log
import com.example.tuanyingshi.util.DebugLogCollector
import com.example.tuanyingshi.util.DebugLogLevel
import com.example.tuanyingshi.util.source_rule.SourceCandidateProvider
import com.example.tuanyingshi.util.source_rule.SourceRuleRepository
import com.example.tuanyingshi.data.remote.parse.DandanplaySource
import com.example.tuanyingshi.data.remote.parse.DandanplaySource.DanmakuResult
import com.example.tuanyingshi.data.remote.parse.DandanplaySource.DanmakuSearchEntry
import com.example.tuanyingshi.data.remote.parse.BackendAnimeSource
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.data.remote.backend.CommentVO
import com.example.tuanyingshi.data.remote.backend.CommentRequestDTO
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.dandanplay.DanmakuPrefs
import com.example.tuanyingshi.data.remote.backend.DanmakuRequestDTO
import com.example.tuanyingshi.data.remote.dandanplay.DanmakuItem
import com.example.tuanyingshi.util.filterContent
import com.example.tuanyingshi.util.onError
import com.example.tuanyingshi.util.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** 当前集自动匹配到的弹幕番剧/集信息（播放页展示 + 手动切换用）。 */
data class DanmakuMatchInfo(
    val animeTitle: String,
    val episodeTitle: String,
    val bangumiId: String,
    val episodeId: Long,
)

/**
 * 播放器 ViewModel（对标 LaQoo VideoPlayerViewModel）：
 * 从 SavedStateHandle 取 detailUrl + episodeUrl，加载详情 → 定位当前集 →
 * getVideoData() 解析真实视频流（WebView 拦截 / POST 解密）→ 组装完整 Video 上下文。
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val animeRepository: AnimeRepository,
    private val historyRepository: HistoryRepository,
    private val downloadRepository: DownloadRepository,
    private val contentFilter: ContentFilterRepository,
) : ViewModel() {

    private val detailUrl: String = savedStateHandle.get<String>("detailUrl").orEmpty()
    private val initialEpisodeUrl: String = savedStateHandle.get<String>("episodeUrl").orEmpty()

    /** 完整播放上下文：null = 加载中 / 失败。 */
    private val _video = MutableStateFlow<Video?>(null)
    val video: StateFlow<Video?> = _video.asStateFlow()

    /** 播放失败提示：解析失败 / 链接不可播放。null = 无错误。 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _anime = MutableStateFlow<AnimeDetailLite?>(null)
    val anime: StateFlow<AnimeDetailLite?> = _anime.asStateFlow()

    /** 弹幕数据：弹弹play 源播放某集时由 [DandanplaySource.getDanmaku] 填充，供弹幕引擎消费。 */
    private val _danmakuItems = MutableStateFlow<List<DanmakuItem>>(emptyList())
    val danmakuItems: StateFlow<List<DanmakuItem>> = _danmakuItems.asStateFlow()

    /** 当前集自动匹配到的弹幕番剧/集信息（播放页展示，并支持手动搜索切换）。null = 尚未匹配。 */
    private val _danmakuMatch = MutableStateFlow<DanmakuMatchInfo?>(null)
    val danmakuMatch: StateFlow<DanmakuMatchInfo?> = _danmakuMatch.asStateFlow()

    /** 弹幕手动匹配搜索面板开关。 */
    private val _showDanmakuSearch = MutableStateFlow(false)
    val showDanmakuSearch: StateFlow<Boolean> = _showDanmakuSearch.asStateFlow()

    /** 弹幕手动匹配搜索结果。 */
    private val _danmakuSearchResults = MutableStateFlow<List<DanmakuSearchEntry>>(emptyList())
    val danmakuSearchResults: StateFlow<List<DanmakuSearchEntry>> = _danmakuSearchResults.asStateFlow()

    /** 弹幕手动匹配搜索进行中标记。 */
    private val _danmakuSearching = MutableStateFlow(false)
    val danmakuSearching: StateFlow<Boolean> = _danmakuSearching.asStateFlow()

    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()

    /** 全部线路（key=线路序号），供自动切线路与线路 Tab 使用。 */
    private val _channels = MutableStateFlow<Map<Int, List<Episode>>>(emptyMap())
    val channels: StateFlow<Map<Int, List<Episode>>> = _channels.asStateFlow()

    /** 各线路名称（与 channels 排序后 key 对齐）。 */
    private val _channelNames = MutableStateFlow<List<String>>(emptyList())
    val channelNames: StateFlow<List<String>> = _channelNames.asStateFlow()

    /** 当前选中线路索引（Tab 高亮 + 选集数据源）。 */
    private val _currentChannelIndex = MutableStateFlow(0)
    val currentChannelIndex: StateFlow<Int> = _currentChannelIndex.asStateFlow()

    private val _rawRelated = MutableStateFlow<List<Anime>>(emptyList())

    /** 相关动画（已按 NSFW 屏蔽 / 隐藏看过与抛弃 过滤）。 */
    val related: StateFlow<List<Anime>> = combine(
        _rawRelated,
        contentFilter.nsfwBlock,
        contentFilter.hiddenDetailUrls,
    ) { list, block, hidden -> list.filterContent(block, hidden) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    /** 该番剧已下载完成的集数（供「已下载」分区离线播放）。 */
    private val _downloaded = MutableStateFlow<List<DownloadedEpisode>>(emptyList())
    val downloaded: StateFlow<List<DownloadedEpisode>> = _downloaded.asStateFlow()

    /** 所有可用 CSS 源候选（播放器「换源」选择器用），由 [loadSourceCandidates] 填充。 */
    private val _sourceCandidates = MutableStateFlow<List<SourceCandidate>>(emptyList())
    val sourceCandidates: StateFlow<List<SourceCandidate>> = _sourceCandidates.asStateFlow()

    /**
     * 需要展示「选择数据源 / 线路」面板（播放链接不可播放 / 解析失败 / 播放出错时由 ViewModel 主动请求）。
     * 替代旧版的「静默自动遍历其它线路/源」：把选择权交还用户，用户选中的能播就直接播。
     */
    private val _showSourcePicker = MutableStateFlow(false)
    val showSourcePicker: StateFlow<Boolean> = _showSourcePicker.asStateFlow()

    private var lastPlayPosition: Long = 0L

    /**
     * 自动换线路/源状态：记录本次播放上下文中已尝试过（已失败）的线路与源，
     * 避免自动切换时无限循环重试同一线路/源。每次用户主动发起新的播放（选集/切线路/换源/重试/初始加载）
     * 通过 [loadVideo]/[loadVideoByRule] 的 resetAuto=true 清空。
     */
    private val autoTriedChannels = mutableSetOf<Int>()
    private val autoTriedSources = mutableSetOf<String>()

    /** 自动切换进行中标记：防止切换过程中 ExoPlayer 连续报错导致并发重入。 */
    private var autoSwitching = false

    /**
     * 后台预匹配缓存：key=episodeUrl，value=已解析出的真实视频流。
     * 用户选中某集并匹配成功后，其余集数在后台并发解析并写入此表；
     * 之后用户切到这些集时直接命中缓存、瞬时播放，无需再次联网匹配。
     */
    private val playUrlCache = ConcurrentHashMap<String, WebVideo>()

    init {
        // 载入已下载集数（离线可看）
        viewModelScope.launch {
            runCatching {
                _downloaded.value = downloadRepository.getCompletedByDetailUrl(detailUrl).map {
                    DownloadedEpisode(
                        episodeUrl = it.episodeUrl,
                        episodeName = it.episodeName,
                        filePath = File(it.savePath, it.saveName).absolutePath,
                    )
                }
            }
        }

        // 非 API 源（聚合 CSS）+ 次元城 URL：先用次元城 API 取元数据（标题/封面/简介，快），
        // 让播放页信息面板「先进页面」显示真实内容，视频与选集（CSS 线路）后台异步加载。
        val isCycaniUrl = detailUrl.contains("cycani", ignoreCase = true)
        if (isCycaniUrl && SourceHolder.currentSourceMode == SourceMode.Rule) {
            viewModelScope.launch {
                // 仅当完整详情尚未到达时才用快显元数据占位，避免慢请求先回时覆盖完整数据
                if (_anime.value != null) return@launch
                val metaRes = animeRepository.getAnimeDetail(detailUrl, SourceMode.Cycanime, "")
                if (metaRes is Resource.Success) {
                    val m = metaRes.data
                    if (m != null && m.title.isNotBlank() && _anime.value == null) {
                        _anime.value = AnimeDetailLite(
                            title = m.title,
                            img = m.img,
                            desc = m.desc,
                            tags = m.tags,
                            sourceId = "", sourceName = "", iconUrl = "",
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            val offline = !NetworkMonitor.isOnline.value
            val detail = when (val res = animeRepository.getAnimeDetail(detailUrl, SourceHolder.currentSourceMode)) {
                is Resource.Success -> res.data
                else -> null
            }
            if (detail == null) {
                // 离线或解析失败：给出明确提示（已下载内容仍可在「已下载」分区离线观看）
                _error.value = if (offline) {
                    "离线模式：无法联网播放，请前往「已下载」观看已下载内容"
                } else {
                    "视频解析失败，请检查网络或代理设置后重试"
                }
                return@launch
            }
            val sortedChannels = detail.channels.toSortedMap()
            _channels.value = sortedChannels
            _channelNames.value = detail.channelNames

            // 初始线路：优先取包含 initialEpisodeUrl 的线路，否则用 channelIndex / 0
            val initialChannel = sortedChannels.entries
                .firstOrNull { (_, eps) -> eps.any { it.url == initialEpisodeUrl } }
                ?.key
                ?: detail.channelIndex.coerceIn(0, (sortedChannels.size - 1).coerceAtLeast(0))
            _currentChannelIndex.value = initialChannel
            val eps = sortedChannels[initialChannel] ?: detail.episodes
            _episodes.value = eps

            val index = eps.indexOfFirst { it.url == initialEpisodeUrl }.takeIf { it >= 0 } ?: 0
            val currentEpisode = eps.getOrNull(index) ?: return@launch

            _anime.value = AnimeDetailLite(
                title = detail.title,
                img = detail.img,
                desc = detail.desc,
                tags = detail.tags,
                sourceId = detail.sourceId,
                sourceName = detail.sourceName,
                iconUrl = detail.iconUrl,
            )
            _rawRelated.value = detail.relatedAnimes.orEmpty()
            loadComments()

            loadVideo(currentEpisode, index, initialChannel)
        }
    }

    /**
     * 视频匹配日志：同时写入通用调试日志（source=App）与「视频匹配」专用流（source=Match），
     * 供调试页「视频匹配日志」独立展示与导出。
     */
    private fun matchLog(msg: String, level: DebugLogLevel = DebugLogLevel.DEBUG) {
        msg.log("PlayerVM") // 通用流（source=App）
        DebugLogCollector.addMatch(level, "PlayerVM", msg) // 视频匹配专用流
    }

    /**
     * 解析当前集真实视频流并组装 Video 上下文。
     * @param channelIndex 当前线路索引（用于 Tab 高亮与跨线路映射选集）
     *
     * 设计：链接不可播放 / 解析失败时**不再静默自动遍历其它线路**，
     * 而是请求 UI 弹出「选择数据源 / 线路」面板，由用户手动挑选；用户选中的源若能播就直接播。
     */
    private fun loadVideo(
        episode: Episode,
        index: Int,
        channelIndex: Int = _currentChannelIndex.value,
        resetAuto: Boolean = true,
    ) {
        if (resetAuto) {
            autoTriedChannels.clear()
            autoTriedSources.clear()
        }
        retryEpisode = episode
        retryIndex = index
        retryChannel = channelIndex
        viewModelScope.launch {
            val tag = "PlayerVM"
            // 同源切集/切线路优化（非 API 源）：
            // 若当前视频来自某个具体的 CSS 规则源，则只查该源（getVideoDataByRule），
            // 不再走聚合源对所有 CSS 规则源「重新匹配全部集数」，避免每次切集都并发遍历全部源、卡顿且重复请求。
            // 同源切集优化（非 API 源）：仅解析当前正在播放的这一个 CSS 规则源，不重匹配全部集数。
            // 例外：弹弹play 源——其集 URL 是「番名+集名」编码，视频须始终走 DandanplaySource 的标题匹配，
            // 不能 pin 到解析出的 CSS 规则（那会拿 dandanplay 编码 URL 去查 CSS 规则导致失败）。
            val pinnedRuleId = if (SourceHolder.currentSource is DandanplaySource) {
                null
            } else {
                _video.value?.sourceId?.takeIf { SourceRuleRepository.getById(it) != null }
            }
            matchLog("loadVideo 入口 episode=${episode.url.take(80)} name=${episode.name} channel=$channelIndex mode=${SourceHolder.currentSourceMode} pinnedRule=$pinnedRuleId")
            _error.value = null
            val webVideoRes = if (pinnedRuleId != null) {
                // 非 API 源：仅解析当前正在播放的这一个 CSS 规则源（单源，不重匹配全部集数）
                animeRepository.getVideoDataByRule(pinnedRuleId, episode.url)
            } else {
                // API 源 / 首次进入（尚未确定具体源）：走默认/聚合源
                animeRepository.getVideoData(episode.url, SourceHolder.currentSourceMode)
            }
            webVideoRes
                .onSuccess { webVideo ->
                    matchLog("loadVideo 解析成功 url=${webVideo.url.take(140)} headers=${webVideo.headers.size} src=${webVideo.sourceName}", DebugLogLevel.INFO)
                    // 播放前预检直链是否可播放（与播放走同一代理链）
                    val playable = VideoUrlChecker.canPlay(webVideo.url, webVideo.headers)
                    matchLog("loadVideo 预检 canPlay=$playable")
                    if (playable) {
                        // 用户选中的这一集已优先匹配到 → 直接组装并播放
                        commitVideo(episode, index, channelIndex, webVideo)
                        // 随后在后台继续匹配剩余集数，命中缓存后切集即瞬时播放
                        prefetchRemaining(episode.url)
                    } else {
                        // 链接不可播放：开启自动换线路/源则自动尝试，否则交还用户选择
                        matchLog("loadVideo 链接不可播放 → 进入失败处理", DebugLogLevel.WARN)
                        onPlaybackFailure("该线路暂不可播放，请选择其它数据源 / 线路")
                    }
                }
                .onError { e ->
                    // 解析失败：开启自动换线路/源则自动尝试，否则交还用户选择
                    matchLog("loadVideo 解析失败 error=${e.message}", DebugLogLevel.ERROR)
                    onPlaybackFailure("该线路解析失败，请选择其它数据源 / 线路")
                }
        }
    }

    /**
     * 把已解析出的 [webVideo] 组装为完整播放上下文并提交给 UI（设置 _video）。
     * 被「优先匹配当前集」与「命中后台缓存直接播放」共用，保证切换集数时上下文一致。
     */
    private fun commitVideo(episode: Episode, index: Int, channelIndex: Int, webVideo: WebVideo) {
        _error.value = null
        _currentChannelIndex.value = channelIndex
        _episodes.value = _channels.value[channelIndex] ?: _episodes.value
        _video.value = Video(
            title = _anime.value?.title ?: "",
            url = webVideo.url,
            episodeName = episode.name,
            episodeUrl = episode.url,
            lastPlayPosition = episode.lastPlayPosition,
            currentEpisodeIndex = index,
            episodes = _episodes.value,
            headers = webVideo.headers,
            sourceId = webVideo.sourceId,
            sourceName = webVideo.sourceName,
            iconUrl = webVideo.iconUrl,
        )
        lastPlayPosition = episode.lastPlayPosition
        matchLog("commitVideo 已组装并播放 url=${_video.value?.url?.take(140)} src=${webVideo.sourceName}", DebugLogLevel.INFO)
        // 播放量上报：仅后端模式（detailUrl 即后端 animeId）才需要，播放开始即 +1。
        // 失败静默不影响播放；非后端模式（CSS/次元城源）无后端番剧 id，跳过。
        if (BackendPrefs.isBackendMode()) {
            val animeId = detailUrl.toLongOrNull()
            if (animeId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { BackendClient.api.recordPlay(animeId) }
                        .onFailure { it.log("PlayerVM", "recordPlay") }
                }
            }
        }
        // 弹幕：开启「启用弹幕拉取」后，所有数据源的剧集都尝试使用弹弹play 弹幕库。
        // - 弹弹play 源：集 URL 自带 episodeId，直接拉取；
        // - 其它源（次元城 / CSS 规则源等）：按「番名 + 集名」向弹弹play 反查 episodeId 后拉取。
        if (DanmakuPrefs.isEnabled()) {
            if (BackendPrefs.isBackendMode()) {
                // 后端模式：弹幕统一走自建后端（按 animeId 拉取），不再使用弹弹play。
                loadBackendDanmaku()
            } else {
                val isDandanEp = episode.url.startsWith("dandanplay://")
                val animeTitle = _anime.value?.title ?: ""
                if (isDandanEp) {
                    loadDanmaku(episode.url)
                } else if (animeTitle.isNotBlank()) {
                    loadDanmakuByTitle(animeTitle, episode.name)
                }
            }
        }
    }

    /** 拉取弹幕数据（弹弹play 源，按编码 URL 直取），失败静默不影响播放。同时记录匹配到的弹幕番剧/集。 */
    private fun loadDanmaku(episodeUrl: String) {
        _danmakuItems.value = emptyList()
        _danmakuMatch.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { DandanplaySource.getDanmaku(episodeUrl) }
                .onSuccess { res ->
                    _danmakuItems.value = res.items
                    _danmakuMatch.value = if (res.episodeId > 0) res.toMatchInfo() else null
                }
                .onFailure { it.log("PlayerVM", "loadDanmaku") }
        }
    }

    /** 按「番名 + 集名」拉取弹幕（非弹弹play 源，统一复用弹弹play 弹幕库），失败静默不影响播放。 */
    private fun loadDanmakuByTitle(animeTitle: String, episodeTitle: String) {
        _danmakuItems.value = emptyList()
        _danmakuMatch.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { DandanplaySource.getDanmakuByTitle(animeTitle, episodeTitle) }
                .onSuccess { res ->
                    _danmakuItems.value = res.items
                    _danmakuMatch.value = if (res.episodeId > 0) res.toMatchInfo() else null
                }
                .onFailure { it.log("PlayerVM", "loadDanmakuByTitle") }
        }
    }

    /**
     * 后端模式：弹幕统一走自建后端 /api/v1/danmaku（按 animeId 拉取）。
     * 后端模式下没有弹弹play 的 episodeId 编码，故直接用详情页带入的 animeId。
     */
    private fun loadBackendDanmaku() {
        _danmakuItems.value = emptyList()
        _danmakuMatch.value = null
        val animeId = detailUrl.toLongOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { BackendAnimeSource.getDanmaku(animeId) }
                .onSuccess { items -> _danmakuItems.value = items }
                .onFailure { it.log("PlayerVM", "loadBackendDanmaku") }
        }
    }

    /** 打开弹幕手动匹配搜索面板。 */
    fun openDanmakuSearch() {
        _showDanmakuSearch.value = true
    }

    /** 关闭弹幕手动匹配搜索面板。 */
    fun closeDanmakuSearch() {
        _showDanmakuSearch.value = false
    }

    /** 按关键词搜索弹幕候选（番剧 + 集），结果填入 [danmakuSearchResults]。 */
    fun searchDanmaku(keyword: String) {
        if (keyword.isBlank()) {
            _danmakuSearchResults.value = emptyList()
            return
        }
        _danmakuSearching.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching { DandanplaySource.searchDanmaku(keyword) }.getOrDefault(emptyList())
            _danmakuSearchResults.value = res
            _danmakuSearching.value = false
        }
    }

    /** 用户选定某个弹幕候选后，重新拉取该番剧/集的弹幕并切换当前匹配信息。 */
    fun selectDanmakuMatch(entry: DanmakuSearchEntry) {
        _showDanmakuSearch.value = false
        _danmakuItems.value = emptyList()
        _danmakuMatch.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { DandanplaySource.getDanmakuByEntry(entry) }
                .onSuccess { res ->
                    _danmakuItems.value = res.items
                    _danmakuMatch.value = if (res.episodeId > 0) {
                        DanmakuMatchInfo(res.animeTitle, res.episodeTitle, res.bangumiId, res.episodeId)
                    } else {
                        null
                    }
                }
                .onFailure { it.log("PlayerVM", "selectDanmakuMatch") }
        }
    }

    /** 把 [DanmakuResult] 转成 UI 用的匹配信息。 */
    private fun DanmakuResult.toMatchInfo(): DanmakuMatchInfo =
        DanmakuMatchInfo(animeTitle, episodeTitle, bangumiId, episodeId)

    /**
     * 后台预匹配剩余集数：用户选中的集已优先匹配并播放，这里用同一来源（非 API 源=已 pin 的 CSS 规则源，
     * API 源=当前 SourceMode）并发解析其余集数并写入 [playUrlCache]，限制并发避免瞬间打满网络。
     * 已缓存的集数会跳过；离线时直接跳过。命中缓存后用户切集即瞬时播放，无需再次联网。
     */
    private fun prefetchRemaining(excludeUrl: String) {
        if (!NetworkMonitor.isOnline.value) return
        val mode = SourceHolder.currentSourceMode
        // 弹弹play 源：集 URL 是「番名+集名」编码，视频须走 DandanplaySource 标题匹配，不能 pin CSS 规则。
        val pinnedRuleId = if (SourceHolder.currentSource is DandanplaySource) {
            null
        } else {
            _video.value?.sourceId?.takeIf { SourceRuleRepository.getById(it) != null }
        }
        val all = _channels.value.values.flatten().filter { it.url != excludeUrl }
        if (all.isEmpty()) return
        matchLog("prefetchRemaining 启动后台匹配 共${all.size}集 pinnedRule=$pinnedRuleId")
        viewModelScope.launch(Dispatchers.IO) {
            val sem = Semaphore(3)
            all.forEach { ep ->
                if (playUrlCache.containsKey(ep.url)) return@forEach
                launch {
                    sem.acquire()
                    try {
                        val res = if (pinnedRuleId != null) {
                            animeRepository.getVideoDataByRule(pinnedRuleId, ep.url)
                        } else {
                            animeRepository.getVideoData(ep.url, mode)
                        }
                        res.onSuccess { wv ->
                            if (wv.url.isNotBlank()) {
                                playUrlCache[ep.url] = wv
                                matchLog("prefetchRemaining 命中缓存 ${ep.name} src=${wv.sourceName}", DebugLogLevel.INFO)
                            }
                        }
                    } finally {
                        sem.release()
                    }
                }
            }
        }
    }

    /** 跨线路按「集数」匹配对应集：精确同名 > 同数字序号 > 无匹配。 */
    private fun findCorrespondingEpisode(name: String, list: List<Episode>): Episode? {
        list.firstOrNull { it.name == name }?.let { return it }
        val n = extractEpisodeNumber(name)
        if (n != null) list.firstOrNull { extractEpisodeNumber(it.name) == n }?.let { return it }
        return null
    }

    private fun extractEpisodeNumber(name: String): Int? {
        return Regex("(\\d+)").find(name)?.value?.toIntOrNull()
    }

    /** 手动切换线路：把当前播放集映射到目标线路的对应集并重载。
     * @param resetAuto 是否重置自动换线路/源的已尝试记录（用户主动点击线路 Tab 时用 true，
     * 自动切换内部递归调用时用 false，以保留已尝试记录避免重复重试）。 */
    fun selectChannel(index: Int, resetAuto: Boolean = true) {
        val ch = _channels.value
        if (index !in ch.keys) return
        val targetEps = ch[index].orEmpty()
        val sourceName = _video.value?.episodeName ?: retryEpisode?.name ?: return
        val sourceIndex = _video.value?.currentEpisodeIndex ?: 0
        val corresp = findCorrespondingEpisode(sourceName, targetEps)
            ?: targetEps.getOrNull(sourceIndex.coerceAtMost(targetEps.lastIndex))
            ?: return
        _currentChannelIndex.value = index
        _episodes.value = targetEps
        loadVideo(corresp, targetEps.indexOf(corresp), index, resetAuto)
    }

    /** 播放中（ExoPlayer 报错）触发：开启自动换线路/源则自动尝试，否则交还用户选择资源。 */
    fun onPlaybackError() {
        matchLog("onPlaybackError ExoPlayer 报告播放错误", DebugLogLevel.ERROR)
        val v = _video.value ?: return
        matchLog("onPlaybackError 当前 url=${v.url.take(140)} episode=${v.episodeName} channel=${_currentChannelIndex.value}", DebugLogLevel.ERROR)
        onPlaybackFailure("播放出错，请选择其它数据源 / 线路")
    }

    /**
     * 统一的播放失败入口（链接不可播放 / 解析失败 / 播放报错 均走这里）。
     * - 未开启「无法播放时自动换线路或源」：沿用旧行为，弹出「选择数据源 / 线路」面板交还用户。
     * - 已开启：进入 [attemptAutoSwitch]，在当前源内依次尝试其它线路，再尝试其它 CSS 源，
     *   全部不可用时才回退到用户选择面板。
     */
    private fun onPlaybackFailure(failMsg: String) {
        if (!PlaybackPrefs.isAutoSwitchOnFail()) {
            _error.value = failMsg
            requestSourcePick()
            return
        }
        viewModelScope.launch { attemptAutoSwitch(failMsg) }
    }

    /**
     * 自动换线路 / 源（仅当 [PlaybackPrefs.isAutoSwitchOnFail] 为 true 时由 [onPlaybackFailure] 调用）。
     * 尝试顺序：当前源内下一未尝试线路 → 下一个未尝试的 CSS 源 → 全部失败才交还用户。
     * 通过 [autoTriedChannels] / [autoTriedSources] 记录已失败项，避免无限循环重试；
     * [autoSwitching] 仅在同步执行期间防止并发重入（真正的终止保证来自上述已尝试集合）。
     */
    private suspend fun attemptAutoSwitch(failMsg: String) {
        if (autoSwitching) return
        autoSwitching = true
        try {
            // 1) 当前源内切换下一未尝试线路
            autoTriedChannels.add(_currentChannelIndex.value)
            val nextChannel = _channels.value.keys.sorted()
                .firstOrNull { it !in autoTriedChannels }
            if (nextChannel != null) {
                autoTriedChannels.add(nextChannel)
                matchLog("自动换线路 → 线路 $nextChannel（共 ${_channels.value.size} 条）", DebugLogLevel.WARN)
                _error.value = null
                selectChannel(nextChannel, resetAuto = false)
                return
            }

            // 2) 当前源线路已穷尽，尝试换源
            matchLog("自动换线路已穷尽，尝试自动换源", DebugLogLevel.WARN)
            val title = _anime.value?.title
            if (title.isNullOrBlank()) {
                finalAutoSwitchFailure(failMsg)
                return
            }
            val v = _video.value
            val a = _anime.value
            val srcId = v?.sourceId ?: a?.sourceId ?: ""
            val current = if (srcId.isNotBlank() && _channels.value.isNotEmpty()) {
                SourceCandidate(
                    sourceId = srcId,
                    sourceName = (v?.sourceName ?: a?.sourceName ?: "").ifBlank {
                        SourceHolder.getResourceSource(srcId)?.name ?: srcId
                    },
                    iconUrl = v?.iconUrl ?: a?.iconUrl ?: "",
                    channels = _channels.value,
                    channelNames = _channelNames.value,
                )
            } else {
                null
            }
            val candidates = SourceCandidateProvider.buildFor(title, current) { }
            // 标记当前（已失败的）源，避免下面又选回它
            if (srcId.isNotBlank()) autoTriedSources.add(srcId)
            autoTriedChannels.clear()
            val nextSource = candidates.firstOrNull {
                it.sourceId.isNotBlank() && it.sourceId !in autoTriedSources
            }
            if (nextSource != null) {
                autoTriedSources.add(nextSource.sourceId)
                matchLog("自动换源 → ${nextSource.sourceName}（${nextSource.sourceId}）", DebugLogLevel.WARN)
                _error.value = null
                val line = nextSource.channels.keys.sorted().firstOrNull() ?: 0
                selectSourceVideo(nextSource, line, resetAuto = false)
                return
            }

            // 3) 全部失败，交还用户
            finalAutoSwitchFailure(failMsg)
        } finally {
            autoSwitching = false
        }
    }

    /** 自动换线路/源全部不可用时：保留错误提示并弹出用户选择面板。 */
    private fun finalAutoSwitchFailure(failMsg: String) {
        matchLog("自动换线路/源全部失败，交还用户选择", DebugLogLevel.ERROR)
        _error.value = failMsg
        requestSourcePick()
    }

    private var retryEpisode: Episode? = null
    private var retryIndex: Int = 0
    private var retryChannel: Int = 0

    /** 失败后重试：重新解析并预检当前集（沿用失败时的线路）。 */
    fun retry() {
        val ep = retryEpisode ?: return
        loadVideo(ep, retryIndex, retryChannel)
    }

    /** 记录播放进度（本地观看时长 ≥5s 才写库，与 LaQoo 一致）。 */
    fun recordProgress(positionMs: Long) {
        if (positionMs < 5_000) return
        lastPlayPosition = positionMs
        val v = _video.value ?: return
        val a = _anime.value ?: return
        viewModelScope.launch {
            historyRepository.record(
                anime = Anime(title = a.title, img = a.img, detailUrl = detailUrl),
                episode = Episode(name = v.episodeName, url = v.episodeUrl, lastPlayPosition = positionMs),
                position = positionMs,
                mode = SourceHolder.currentSourceMode,
            )
        }
    }

    /** 原地切换集数（当前线路内）：若已被后台预匹配命中则直接播放（瞬时），否则联网解析。 */
    fun selectEpisode(episode: Episode) {
        val index = _episodes.value.indexOfFirst { it.url == episode.url }.takeIf { it >= 0 } ?: 0
        val cached = playUrlCache[episode.url]
        if (cached != null && cached.url.isNotBlank()) {
            // 后台已匹配到该集 → 直接播放，不再走网络；并继续把其余集数也匹配上
            matchLog("selectEpisode 命中缓存直接播放 ${episode.name}", DebugLogLevel.INFO)
            commitVideo(episode, index, _currentChannelIndex.value, cached)
            prefetchRemaining(episode.url)
        } else {
            loadVideo(episode, index, _currentChannelIndex.value)
        }
    }

    /** 为「换源」选择器构建所有可用 CSS 源候选（按标题搜索各源并取回线路）。 */
    fun loadSourceCandidates() {
        val title = _anime.value?.title ?: return
        if (title.isBlank()) return
        // 当前源（播放页已成功加载）作为兜底候选，保证选择器至少 1 个可用源
        val v = _video.value
        val a = _anime.value
        val srcId = v?.sourceId ?: a?.sourceId ?: ""
        val current = if (srcId.isNotBlank() && _channels.value.isNotEmpty()) {
            SourceCandidate(
                sourceId = srcId,
                sourceName = (v?.sourceName ?: a?.sourceName ?: "").ifBlank {
                    SourceHolder.getResourceSource(srcId)?.name ?: srcId
                },
                iconUrl = v?.iconUrl ?: a?.iconUrl ?: "",
                channels = _channels.value,
                channelNames = _channelNames.value,
            )
        } else {
            null
        }
        viewModelScope.launch(Dispatchers.IO) {
            SourceCandidateProvider.buildFor(title, current) { list ->
                _sourceCandidates.value = list
            }
        }
    }

    /**
     * 播放器「换源」：在 [candidate] 源的 [line] 线路上，加载当前集对应的视频流。
     * 同时把 channels 切换为该源，使「分集」网格与线路 Tab 同步更新（animeko 切换 mediaSource 的效果）。
     * 不再自动回退：选中源若能播就直接播，不能播则由 loadVideoByRule 再次请求用户选择。
     */
    fun selectSourceVideo(candidate: SourceCandidate, line: Int, resetAuto: Boolean = true) {
        // 初始加载失败时 _video 为 null，此时用 retryEpisode 作为「当前集」上下文
        val v = _video.value
        val episodeName = v?.episodeName ?: retryEpisode?.name ?: return
        val currentIndex = v?.currentEpisodeIndex ?: 0
        val targetEps = candidate.channels[line] ?: return
        // 把当前播放集映射到目标源该线路的对应集（同名 > 同集数序号 > 同下标）
        val corresp = findCorrespondingEpisode(episodeName, targetEps)
            ?: targetEps.getOrNull(currentIndex.coerceAtMost(targetEps.lastIndex))
            ?: return
        _channels.value = candidate.channels
        _channelNames.value = candidate.channelNames
        _currentChannelIndex.value = line
        _episodes.value = targetEps
        // 用户已明确选择该源，重置「选择面板」弹出标志，避免加载完又弹起
        _showSourcePicker.value = false
        loadVideoByRule(candidate.sourceId, corresp, targetEps.indexOf(corresp), line, resetAuto)
    }

    /** 请求 UI 弹出「选择数据源 / 线路」面板（构建候选源），不再静默遍历其它资源。 */
    fun requestSourcePick() {
        loadSourceCandidates()
        _showSourcePicker.value = true
    }

    /** 用户关闭「选择数据源 / 线路」面板：若仍无任何可播放视频，给出明确提示。 */
    fun onSourceSheetDismissed() {
        _showSourcePicker.value = false
        if (_video.value == null && _error.value == null) {
            _error.value = "请选择一个可播放的数据源 / 线路"
        }
    }

    /** 仅重置「选择面板」弹出标志（用户已成功选中源时调用）。 */
    fun clearShowSourcePicker() {
        _showSourcePicker.value = false
    }

    /** 按指定 CSS 规则源解析某一集视频流（换源专用，不做预检，失败由 ExoPlayer 报错回调处理）。 */
    private fun loadVideoByRule(
        ruleId: String,
        episode: Episode,
        index: Int,
        channelIndex: Int,
        resetAuto: Boolean = true,
    ) {
        if (resetAuto) {
            autoTriedChannels.clear()
            autoTriedSources.clear()
        }
        retryEpisode = episode
        retryIndex = index
        retryChannel = channelIndex
        viewModelScope.launch {
            val tag = "PlayerVM"
            matchLog("loadVideoByRule 换源 rule=$ruleId episode=${episode.url.take(80)} channel=$channelIndex")
            _error.value = null
            animeRepository.getVideoDataByRule(ruleId, episode.url)
                .onSuccess { webVideo ->
                    matchLog("loadVideoByRule 解析成功 url=${webVideo.url.take(140)} headers=${webVideo.headers.size} src=${webVideo.sourceName}", DebugLogLevel.INFO)
                    if (webVideo.url.isNotBlank()) {
                        commitVideo(episode, index, channelIndex, webVideo)
                        // 换源后同样把该源其余集数放入后台预匹配，保证后续切集瞬时
                        prefetchRemaining(episode.url)
                    } else {
                        _video.value = null
                        onPlaybackFailure("该数据源此集暂无可播放链接，请尝试其他数据源/线路")
                    }
                }
                .onError { e ->
                    matchLog("loadVideoByRule 解析失败 error=${e.message}", DebugLogLevel.ERROR)
                    _video.value = null
                    onPlaybackFailure("该数据源解析失败，请尝试其他数据源/线路")
                }
        }
    }

    /** 载入评论：后端模式走自建后端 /comments；否则用与剧名相关的占位文案。 */
    private fun loadComments() {
        if (BackendPrefs.isBackendMode()) {
            val animeId = detailUrl.toLongOrNull()
            viewModelScope.launch(Dispatchers.IO) {
                val list = runCatching {
                    BackendClient.api.comments(animeId = animeId, detailUrl = null)
                        .data?.records.orEmpty()
                        .mapNotNull { it.toComment() }
                }.getOrDefault(emptyList())
                _comments.value = list
            }
        } else {
            _comments.value = mockComments(_anime.value?.title ?: "")
        }
    }

    /** 用户发表评论：后端模式 POST 到 /comments（需登录）；否则本地乐观插入。 */
    fun addComment(text: String) {
        val content = text.trim()
        if (content.isBlank()) return
        if (BackendPrefs.isBackendMode() && BackendPrefs.isLoggedIn) {
            viewModelScope.launch(Dispatchers.IO) {
                val created = runCatching {
                    BackendClient.api.createComment(
                        CommentRequestDTO(
                            animeId = detailUrl.toLongOrNull(),
                            detailUrl = null,
                            content = content,
                        ),
                    ).data?.toComment()
                }.getOrNull()
                _comments.value = listOf(created ?: localComment(content)) + _comments.value
            }
        } else {
            _comments.value = listOf(localComment(content)) + _comments.value
        }
    }

    /**
     * 发送弹幕（需「后端模式 + 已登录」，弹幕落在自建后端 /api/v1/danmaku）。
     *
     * @param text 弹幕内容
     * @param positionMs 当前播放进度（毫秒），用于确定弹幕出现的时间点
     * @param mode 1 滚动 / 4 底部 / 5 顶部
     * @param colorHex 颜色 `#RRGGBB`
     * @return null = 发送成功；否则为可直接提示用户的错误文案
     */
    suspend fun sendDanmaku(
        text: String,
        positionMs: Long,
        mode: Int = 1,
        colorHex: String = "#FFFFFF",
    ): String? {
        val content = text.trim()
        if (content.isBlank()) return "弹幕内容不能为空"
        if (!BackendPrefs.isBackendMode()) return "仅后端模式支持发送弹幕"
        if (!BackendPrefs.isLoggedIn) return "登录后才能发送弹幕"
        val animeId = detailUrl.toLongOrNull()
        if (animeId == null) return "当前番剧未关联站内 ID，无法发送弹幕"
        val timeSec = positionMs / 1000.0
        return withContext(Dispatchers.IO) {
            val resp = runCatching {
                BackendClient.api.sendDanmaku(
                    DanmakuRequestDTO(
                        animeId = animeId,
                        time = timeSec,
                        mode = mode,
                        color = parseDanmakuColor(colorHex),
                        text = content,
                    ),
                )
            }.getOrNull()
            when {
                resp == null -> "弹幕发送失败"
                !resp.ok -> resp.message ?: "弹幕发送失败"
                else -> {
                    // 乐观插入：立刻出现在屏幕上，不必等下次重新拉取弹幕
                    _danmakuItems.value = _danmakuItems.value + DanmakuItem(
                        time = timeSec,
                        text = content,
                        color = colorHex,
                        mode = mode,
                    )
                    null
                }
            }
        }
    }

    /** `#RRGGBB` → 十进制 RGB（后端按十进制存储，白色为 16777215）。 */
    private fun parseDanmakuColor(hex: String): Int {
        val raw = hex.trim().removePrefix("#")
        return raw.toLongOrNull(16)?.toInt() ?: 16777215
    }

    private fun localComment(content: String) = Comment(
        id = "c_user_${System.currentTimeMillis()}",
        username = "我",
        avatarLabel = "我",
        content = content,
        likes = 0,
        timeText = "刚刚",
    )

    private fun CommentVO.toComment(): Comment? {
        val id = id ?: return null
        val text = content ?: return null
        return Comment(
            id = id,
            username = username ?: "匿名",
            avatarLabel = avatarLabel ?: (username?.firstOrNull()?.toString() ?: "匿"),
            content = text,
            likes = likes ?: 0,
            timeText = formatCommentTime(createdAt),
        )
    }

    /** 评论时间格式化：后端 createdAt 为毫秒时间戳 → 刚刚 / N 分钟前 / N 小时前 / N 天前。 */
    private fun formatCommentTime(createdAtMs: Long?): String {
        if (createdAtMs == null || createdAtMs <= 0) return "刚刚"
        val diff = System.currentTimeMillis() - createdAtMs
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
            diff < 7 * 86_400_000 -> "${diff / 86_400_000} 天前"
            else -> "${diff / (7 * 86_400_000)} 周前"
        }
    }

    // Mock 阶段（非后端模式）：评论用与剧名相关的原创文案，不依赖后端。
    private fun mockComments(animeTitle: String): List<Comment> = listOf(
        Comment(
            id = "c1",
            username = "弹幕党",
            avatarLabel = "弹",
            content = "终于等到这一集了，剧情走向越来越精彩，《${animeTitle}》不愧是口碑之作。",
            likes = 320,
            timeText = "2 天前",
        ),
        Comment(
            id = "c2",
            username = "影评人阿K",
            avatarLabel = "评",
            content = "OP 配乐配合画面信息量很大，建议配合原画弹幕一起看。",
            likes = 128,
            timeText = "5 小时前",
        ),
        Comment(
            id = "c3",
            username = "路人甲",
            avatarLabel = "路",
            content = "有同感的吗？前 30 秒的转场真的是教科书级别。",
            likes = 96,
            timeText = "1 天前",
        ),
        Comment(
            id = "c4",
            username = "深夜追番人",
            avatarLabel = "夜",
            content = "看完整集久久不能平静，最后十分钟 BGM 直接封神。",
            likes = 205,
            timeText = "3 天前",
        ),
    )
}

/** 播放器需要的精简详情信息（避免 UI 直接依赖完整 AnimeDetail）。 */
data class AnimeDetailLite(
    val title: String,
    val img: String,
    val desc: String,
    val tags: List<String> = emptyList(),
    /** 来源标识（CSS 规则源 id / 内置源 id）。 */
    val sourceId: String = "",
    /** 来源名（如某个 CSS 规则源名称 / "次元城"），用于播放器"来源"展示。 */
    val sourceName: String = "",
    /** 来源图标 URL（CSS 规则源才有）。 */
    val iconUrl: String = "",
)

/** 已下载的离线集数（供在线播放页「已下载」分区直接离线播放）。 */
data class DownloadedEpisode(
    val episodeUrl: String,
    val episodeName: String,
    val filePath: String,
)
