package com.example.tuanyingshi.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.Build
import com.example.tuanyingshi.BuildConfig
import com.example.tuanyingshi.data.local.entity.HistoryEntity
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.data.remote.backend.FeedbackRequestDTO
import com.example.tuanyingshi.data.remote.backend.RatingRequestDTO
import com.example.tuanyingshi.data.repository.FavoriteRepository
import com.example.tuanyingshi.data.repository.HistoryRepository
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.AnimeDetail
import com.example.tuanyingshi.domain.model.Episode
import com.example.tuanyingshi.domain.model.SourceCandidate
import com.example.tuanyingshi.domain.repository.AnimeRepository
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.Resource
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.source_rule.SourceCandidateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val animeRepository: AnimeRepository,
    private val favoriteRepository: FavoriteRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    /** 详情页地址（导航路由参数，URL 编码后传递，此处为解码后的原始链接）。 */
    private val detailUrl: String = savedStateHandle.get<String>("detailUrl").orEmpty()

    /** 来源 CSS 规则 id（聚合搜索结果携带，详情页据此直达对应规则，避免回退查全部 CSS）。 */
    private val sourceId: String = savedStateHandle.get<String>("sourceId").orEmpty()

    private val _detail = MutableStateFlow<AnimeDetail?>(null)
    val detail: StateFlow<AnimeDetail?> = _detail.asStateFlow()

    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()

    /** 全部线路（key=线路序号，value=该线路选集），供详情页线路切换 Tab 使用。 */
    private val _channels = MutableStateFlow<Map<Int, List<Episode>>>(emptyMap())
    val channels: StateFlow<Map<Int, List<Episode>>> = _channels.asStateFlow()

    /** 各线路名称（与 channels 排序后 key 对齐）。 */
    private val _channelNames = MutableStateFlow<List<String>>(emptyList())
    val channelNames: StateFlow<List<String>> = _channelNames.asStateFlow()

    /** 当前选中线路索引（标签页高亮 / 选集数据源）。 */
    private val _selectedChannel = MutableStateFlow(0)
    val selectedChannel: StateFlow<Int> = _selectedChannel.asStateFlow()

    /** 提供本详情数据的来源名（CSS 规则源名 / 回退到当前资源源名）。 */
    private val _sourceName = MutableStateFlow("")
    val sourceName: StateFlow<String> = _sourceName.asStateFlow()

    /** 提供本详情数据的来源图标 URL（CSS 规则源才有，回退到当前资源源图标）。 */
    private val _iconUrl = MutableStateFlow("")
    val iconUrl: StateFlow<String> = _iconUrl.asStateFlow()

    /**
     * 选集（线路）是否仍在加载中：非 API 源下元数据先用次元城 API 快显，
     * 选集（CSS 线路）后台补全，期间此标志为真，详情页选集区显示加载动画（部分 UI 加载）。
     */
    private val _channelsLoading = MutableStateFlow(false)
    val channelsLoading: StateFlow<Boolean> = _channelsLoading.asStateFlow()

    /** 所有可用 CSS 源候选（「数据源」选择器用），由 [loadSourceCandidates] 填充。 */
    private val _sourceCandidates = MutableStateFlow<List<SourceCandidate>>(emptyList())
    val sourceCandidates: StateFlow<List<SourceCandidate>> = _sourceCandidates.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // 用户评分（星级 1-5）。后端模式且已登录时与后端同步（存入 10 分制），否则仅本地会话级。
    private val _userRating = MutableStateFlow<Float?>(null)
    val userRating: StateFlow<Float?> = _userRating.asStateFlow()

    /** 一次性提示（评分 / 报错结果），UI 展示后调用 [consumeMessage] 清空。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    /** 该番剧的最近观看记录（用于「继续观看」按钮）。 */
    val lastWatched: StateFlow<HistoryEntity?> = historyRepository.observeHistory()
        .map { list -> list.find { it.detailUrl == detailUrl } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // 非 API 源（聚合 CSS）+ 次元城 URL：先用次元城 API 取元数据（封面/标题/简介，快），
        // 让详情页立刻填充「真内容」，选集（CSS 线路）走下方完整请求异步补全并显示加载动画，
        // 避免整页被「查全部 CSS 源」阻塞（用户先进入页面、部分 UI 显示加载）。
        val isCycaniUrl = detailUrl.contains("cycani", ignoreCase = true)
        if (isCycaniUrl && SourceHolder.currentSourceMode == SourceMode.Rule) {
            viewModelScope.launch {
                // 仅当完整详情尚未到达时才用快显元数据占位，避免慢请求先回时覆盖完整数据
                if (_detail.value != null) return@launch
                val metaRes = animeRepository.getAnimeDetail(detailUrl, SourceMode.Cycanime, "")
                if (metaRes is Resource.Success) {
                    val meta = metaRes.data?.copy(
                        channels = emptyMap(),
                        episodes = emptyList(),
                        channelNames = emptyList(),
                        sourceId = "", sourceName = "", iconUrl = "",
                    )
                    if (meta != null && meta.title.isNotBlank() && _detail.value == null) {
                        _detail.value = meta
                        _channelsLoading.value = true
                        val rs = SourceHolder.getResourceSource(SourceHolder.currentSourceId)
                        _sourceName.value = rs?.name ?: ""
                        _iconUrl.value = rs?.iconUrl ?: ""
                        recordBrowse(meta)
                    }
                }
            }
        }

        viewModelScope.launch {
            when (val res = animeRepository.getAnimeDetail(detailUrl, SourceHolder.currentSourceMode, sourceId)) {
                is Resource.Success -> {
                    val d = res.data
                    val channels = d?.channels.orEmpty()
                    _detail.value = d
                    _channelsLoading.value = false
                    _channels.value = channels
                    _channelNames.value = d?.channelNames.orEmpty()
                    val initialChannel = if (channels.isEmpty()) 0
                        else (d?.channelIndex ?: 0).coerceIn(0, channels.size - 1)
                    _selectedChannel.value = initialChannel
                    _episodes.value = channels[initialChannel] ?: d?.episodes.orEmpty()
                    // 来源标识：优先用详情自带（CSS 规则源），否则回退当前资源源（如「次元城」）
                    val rs = SourceHolder.getResourceSource(SourceHolder.currentSourceId)
                    _sourceName.value = d?.sourceName?.takeIf { it.isNotBlank() } ?: rs?.name ?: ""
                    _iconUrl.value = d?.iconUrl?.takeIf { it.isNotBlank() } ?: rs?.iconUrl ?: ""
                    d?.let { recordBrowse(it) }
                }
                is Resource.Error -> {
                    // 已有快显元数据时仅标记选集加载失败，不整体清空页面
                    if (_detail.value == null) _detail.value = null
                    _channelsLoading.value = false
                }
                is Resource.Loading -> Unit
            }
            _isFavorite.value = favoriteRepository.isFavorite(detailUrl)
        }

        // 后端模式 + 已登录：回填「我的评分」，让详情页评分入口显示历史评分
        loadMyRating()
    }

    /** 拉取当前用户对该番剧的评分（后端模式 + 已登录时有效）。 */
    private fun loadMyRating() {
        if (!BackendPrefs.isBackendMode() || !BackendPrefs.isLoggedIn || detailUrl.isBlank()) return
        viewModelScope.launch {
            val score = runCatching { BackendClient.api.myRating(detailUrl = detailUrl) }
                .getOrNull()
                ?.takeIf { it.ok }
                ?.data?.score
                ?: return@launch
            // 后端存 10 分制，UI 用 5 星制
            _userRating.value = (score / 2.0).toFloat()
        }
    }

    /** 切换线路：更新选中索引并刷新选集（保持当前播放进度不清空）。 */
    fun selectChannel(index: Int) {
        val ch = _channels.value
        if (index !in ch.keys) return
        _selectedChannel.value = index
        _episodes.value = ch[index].orEmpty()
    }

    /** 为「数据源」选择器构建所有可用 CSS 源候选（按标题搜索各源并取回线路）。 */
    fun loadSourceCandidates() {
        val detail = _detail.value ?: return
        val title = detail.title
        if (title.isBlank()) return
        // 当前源（详情页已成功加载）作为兜底候选，保证选择器至少 1 个可用源
        val current = if (detail.sourceId.isNotBlank() && _channels.value.isNotEmpty()) {
            SourceCandidate(
                sourceId = detail.sourceId,
                sourceName = detail.sourceName.ifBlank {
                    SourceHolder.getResourceSource(detail.sourceId)?.name ?: detail.sourceId
                },
                iconUrl = detail.iconUrl,
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

    /** 在「数据源」选择器中选择某 CSS 源的某线路：用该源的选集替换当前展示。 */
    fun selectDetailSource(candidate: SourceCandidate, line: Int) {
        val ch = candidate.channels
        if (line !in ch.keys) return
        _channels.value = ch
        _channelNames.value = candidate.channelNames
        _selectedChannel.value = line
        _episodes.value = ch[line].orEmpty()
        _sourceName.value = candidate.sourceName
        _iconUrl.value = candidate.iconUrl
        _channelsLoading.value = false
    }

    fun toggleFavorite() {
        val d = _detail.value ?: return
        val anime = Anime(
            title = d.title,
            img = d.img,
            detailUrl = detailUrl,
        )
        viewModelScope.launch {
            favoriteRepository.toggle(anime, SourceHolder.currentSourceMode)
            _isFavorite.value = favoriteRepository.isFavorite(detailUrl)
        }
    }

    /**
     * 提交用户评分（星级 1-5）。
     *
     * <p>后端模式且已登录时上报后端（转 10 分制存储并跨设备同步）；否则仅本地会话级，
     * 并通过 [message] 告知用户未同步。
     */
    fun setUserRating(rating: Float) {
        _userRating.value = rating
        if (!BackendPrefs.isBackendMode() || !BackendPrefs.isLoggedIn) {
            _message.value = "已记录评分（本地模式，未同步到账号）"
            return
        }
        val d = _detail.value
        viewModelScope.launch {
            val res = runCatching {
                BackendClient.api.setRating(
                    RatingRequestDTO(
                        detailUrl = detailUrl,
                        title = d?.title,
                        imgUrl = d?.img,
                        // 5 星制 → 10 分制
                        score = (rating * 2.0),
                    ),
                )
            }.getOrNull()
            _message.value = when {
                res == null -> "评分失败，请检查网络"
                res.ok -> "评分成功"
                else -> res.message ?: "评分失败"
            }
        }
    }

    /** 打开详情即记一条浏览历史（仅首次写入，不覆盖已有的播放进度）。 */
    private fun recordBrowse(d: AnimeDetail) {
        viewModelScope.launch {
            historyRepository.recordBrowse(
                anime = Anime(title = d.title, img = d.img, detailUrl = detailUrl),
                mode = SourceHolder.currentSourceMode,
            )
        }
    }

    /**
     * 提交「报错」反馈。
     *
     * <p>后端模式且已登录时，复用后端反馈接口（{@code POST /api/v1/ops/feedback}，
     * category=CONTENT），内容里带上番剧标题、报错类型与详情地址，管理端「用户反馈」可查。
     * 未登录 / 非后端模式时通过 [message] 提示需要登录。
     */
    fun submitReport(type: String) {
        if (!BackendPrefs.isBackendMode() || !BackendPrefs.isLoggedIn) {
            _message.value = if (BackendPrefs.isBackendMode()) {
                "请先登录后端账号后再提交"
            } else {
                "请开启后端模式并登录后再提交反馈"
            }
            return
        }
        val title = _detail.value?.title.orEmpty()
        val content = buildString {
            append("【番剧报错】")
            if (title.isNotBlank()) append("《$title》")
            append("问题类型：").append(type)
            if (detailUrl.isNotBlank()) append("\n详情页：").append(detailUrl)
        }
        viewModelScope.launch {
            val res = runCatching {
                BackendClient.api.submitFeedback(
                    FeedbackRequestDTO(
                        content = content,
                        category = "CONTENT",
                        deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    ),
                )
            }.getOrNull()
            _message.value = when {
                res == null -> "提交失败，请检查网络"
                res.ok -> "已提交反馈，感谢您的反馈"
                else -> res.message ?: "提交失败"
            }
        }
    }
}
