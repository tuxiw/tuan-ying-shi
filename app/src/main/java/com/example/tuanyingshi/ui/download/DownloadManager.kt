package com.example.tuanyingshi.ui.download

import android.content.Context
import com.example.tuanyingshi.data.local.entity.DownloadEntity
import com.example.tuanyingshi.data.repository.DownloadRepository
import com.example.tuanyingshi.domain.model.AnimeDetail
import com.example.tuanyingshi.domain.model.Episode
import com.example.tuanyingshi.domain.repository.AnimeRepository
import com.example.tuanyingshi.download.Progress
import com.example.tuanyingshi.download.State
import com.example.tuanyingshi.download.core.DownloadConfig
import com.example.tuanyingshi.download.core.DownloadParam
import com.example.tuanyingshi.download.download
import com.example.tuanyingshi.download.concurrent.ConfigurableDownloadQueue
import com.example.tuanyingshi.data.remote.parse.DandanplaySource
import com.example.tuanyingshi.data.remote.parse.RuleBasedAnimeSource
import com.example.tuanyingshi.util.DownloadNotifier
import com.example.tuanyingshi.util.DownloadPrefs
import com.example.tuanyingshi.util.source_rule.SourceRuleRepository
import com.example.tuanyingshi.util.Result
import com.example.tuanyingshi.util.Resource
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.resolveDownloadDir
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadState { QUEUED, DOWNLOADING, DONE, FAILED, PAUSED }

data class DownloadItemUi(
    val detailUrl: String,
    val episodeUrl: String,
    val animeTitle: String,
    val animeImg: String = "",
    val episodeName: String,
    val savePath: String,
    val saveName: String,
    val status: DownloadState,
    val progress: Int,
    val speed: String = "",
)

/**
 * 下载分组（同一番剧 = 同一 detailUrl 的全部集数）。
 * 用于「我的下载」首页按番剧聚合展示，点进组页查看每集进度。
 */
data class DownloadGroupUi(
    val detailUrl: String,
    val animeTitle: String,
    val animeImg: String,
    val items: List<DownloadItemUi>,
) {
    val total: Int get() = items.size
    val doneCount: Int get() = items.count { it.status == DownloadState.DONE }
    val failedCount: Int get() = items.count { it.status == DownloadState.FAILED }
    val activeCount: Int get() = items.count {
        it.status == DownloadState.QUEUED || it.status == DownloadState.DOWNLOADING
    }
    val overallProgress: Int
        get() = if (total == 0) 0 else items.sumOf { it.progress } / total
    val allDone: Boolean get() = activeCount == 0 && doneCount == total && total > 0
}

/**
 * 下载管理器（对标 LaQoo）：持有独立 CoroutineScope，负责
 * 1) 调用数据源解析真实 m3u8 直链（getVideoData，与播放器同源）；
 * 2) 委托 download 模块执行 m3u8 分片下载与合并；
 * 3) 通过 StateFlow 实时暴露下载列表（进度/状态），并持久化到 Room；
 * 4) 按 detailUrl 聚合成「番剧组」，供分组 UI 展示；
 * 5) 启动时 reconcile 数据库记录，修复「下载完成后概率找不到番剧」的脏状态。
 */
@Singleton
class DownloadManager @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val downloadRepository: DownloadRepository,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val items: StateFlow<List<DownloadItemUi>> = _items

    private val _groups = MutableStateFlow<List<DownloadGroupUi>>(emptyList())
    val groups: StateFlow<List<DownloadGroupUi>> = _groups

    init {
        // 启动时把数据库里的下载记录 reconcile 后载入列表，修复中断/脏数据导致的「找不到番剧」
        scope.launch { reconcileFromDb() }
    }

    // ── 对外 API ────────────────────────────────────────────────────────────

    /** 批量把选中的集数加入下载队列（解析 m3u8 → 交给 download 模块）。 */
    suspend fun enqueue(
        animeTitle: String,
        episodes: List<Episode>,
        detailUrl: String,
        animeImg: String = "",
    ) {
        val dir = resolveDownloadDir(context)
        episodes.forEach { ep ->
            downloadOne(animeTitle, ep.name, ep.url, detailUrl, animeImg, dir)
        }
        DownloadNotifier.notifyQueued(context, animeTitle, episodes.size)
    }

    /** 重新下载单集（先清除旧记录与文件，再走一遍下载流程）。 */
    fun retry(item: DownloadItemUi) {
        remove(item)
        scope.launch {
            val dir = resolveDownloadDir(context)
            downloadOne(item.animeTitle, item.episodeName, item.episodeUrl, item.detailUrl, item.animeImg, dir)
        }
    }

    /** 删除一条下载（同时删除磁盘文件与数据库记录）。 */
    fun remove(item: DownloadItemUi) {
        val file = resolvePlayFile(context, item)
        if (file?.exists() == true) file.delete()
        scope.launch { runCatching { downloadRepository.delete(item.episodeUrl) } }
        _items.update { it.filter { i -> i.episodeUrl != item.episodeUrl } }
        syncGroups()
    }

    /** 删除整个番剧组（所有集 + 文件 + 数据库记录）。 */
    fun removeGroup(detailUrl: String) {
        _items.value.filter { it.detailUrl == detailUrl }.forEach { item ->
            val file = resolvePlayFile(context, item)
            if (file?.exists() == true) file.delete()
        }
        scope.launch { runCatching { downloadRepository.deleteByDetailUrl(detailUrl) } }
        _items.update { it.filter { i -> i.detailUrl != detailUrl } }
        syncGroups()
    }

    /** 清空已完成的下载（文件 + 记录）。 */
    fun clearFinished() {
        _items.value
            .filter { it.status == DownloadState.DONE || it.status == DownloadState.FAILED }
            .forEach { item ->
                val file = resolvePlayFile(context, item)
                if (file?.exists() == true) file.delete()
                scope.launch { runCatching { downloadRepository.delete(item.episodeUrl) } }
            }
        _items.update { it.filter { i -> i.status != DownloadState.DONE && i.status != DownloadState.FAILED } }
        syncGroups()
    }

    /**
     * 解析某条下载对应的本地文件（容错）：先按记录的 savePath/saveName 找，
     * 找不到再回退到当前下载目录按 saveName 查找，避免「切换下载位置后找不到文件」。
     */
    fun resolvePlayFile(context: Context, item: DownloadItemUi): File? {
        val exact = File(item.savePath, item.saveName)
        if (exact.exists() && exact.length() > 0) return exact
        val fallback = File(resolveDownloadDir(context), item.saveName)
        if (fallback.exists() && fallback.length() > 0) return fallback
        return null
    }

    // ── 内部实现 ──────────────────────────────────────────────────────────────

    /**
     * 启动时从数据库读取全部记录并 reconcile：
     * - DONE 但文件丢失 → 标记 FAILED（可展示/删除，避免「点开找不到」）；
     * - 上次被中断（QUEUED/DOWNLOADING）→ 文件完整则恢复为 DONE，否则 FAILED。
     * 这样无论 App 是否被强杀，重启后下载记录都与磁盘真实状态一致。
     */
    private suspend fun reconcileFromDb() {
        runCatching {
            val rows = downloadRepository.getAll()
            val reconciled = rows.mapNotNull { entity ->
                val file = File(entity.savePath, entity.saveName)
                val exists = file.exists() && file.length() > 0
                when {
                    entity.status == STATUS_DONE && !exists -> {
                        downloadRepository.updateStatus(entity.episodeUrl, STATUS_FAILED, 0)
                        entity.toUi(DownloadState.FAILED)
                    }
                    entity.status == STATUS_QUEUED || entity.status == STATUS_DOWNLOADING -> {
                        if (exists) {
                            downloadRepository.updateStatus(entity.episodeUrl, STATUS_DONE, 100)
                            entity.toUi(DownloadState.DONE)
                        } else {
                            downloadRepository.updateStatus(entity.episodeUrl, STATUS_FAILED, 0)
                            entity.toUi(DownloadState.FAILED)
                        }
                    }
                    else -> entity.toUi()
                }
            }
            _items.value = reconciled
            syncGroups()
        }
    }

    private suspend fun downloadOne(
        animeTitle: String,
        episodeName: String,
        episodeUrl: String,
        detailUrl: String,
        animeImg: String,
        dir: File,
    ) {
        // 去重：内存中已存在，或数据库里已下载完成的跳过
        if (_items.value.any { it.episodeUrl == episodeUrl }) return
        if (downloadRepository.getByEpisode(episodeUrl)?.status == STATUS_DONE) return

        // 非 API（CSS/外部资源）源：必须「钉死」到具体的规则源解析，否则走聚合源会对全部
        // 规则源并发重匹配，且失败时静默返回空直链，导致下载任务拿到空 URL 一直卡在「排队/下载中」。
        // 弹弹play（API 聚合源）与内置 API 源不钉死，走默认 getVideoData。
        val pinnedRuleId = computePinnedRuleId()
        val res = if (pinnedRuleId != null) {
            animeRepository.getVideoDataByRule(pinnedRuleId, episodeUrl)
        } else {
            animeRepository.getVideoData(episodeUrl, SourceHolder.currentSourceMode)
        }
        val web = when (res) {
            is Result.Success -> res.data
            is Result.Error -> {
                val failed = DownloadItemUi(
                    detailUrl = detailUrl,
                    episodeUrl = episodeUrl,
                    animeTitle = animeTitle,
                    animeImg = animeImg,
                    episodeName = episodeName,
                    savePath = dir.absolutePath,
                    saveName = "${makeSafeFileName("$animeTitle - $episodeName")}.ts",
                    status = DownloadState.FAILED,
                    progress = 0,
                )
                addItem(failed)
                downloadRepository.upsert(failed.toEntity(STATUS_FAILED))
                return
            }
        }
        // 空直链：非 API 源解析未命中时的常见结果。必须显式判空并标记失败，
        // 否则空串会被当作下载地址交给下载库（Retrofit 落到假域名），表现为一直「下载中/排队」。
        if (web.url.isBlank()) {
            val failed = DownloadItemUi(
                detailUrl = detailUrl,
                episodeUrl = episodeUrl,
                animeTitle = animeTitle,
                animeImg = animeImg,
                episodeName = episodeName,
                savePath = dir.absolutePath,
                saveName = "${makeSafeFileName("$animeTitle - $episodeName")}.ts",
                status = DownloadState.FAILED,
                progress = 0,
            )
            addItem(failed)
            downloadRepository.upsert(failed.toEntity(STATUS_FAILED))
            return
        }

        val saveName = "${makeSafeFileName("$animeTitle - $episodeName")}.ts"
        val item = DownloadItemUi(
            detailUrl = detailUrl,
            episodeUrl = episodeUrl,
            animeTitle = animeTitle,
            animeImg = animeImg,
            episodeName = episodeName,
            savePath = dir.absolutePath,
            saveName = saveName,
            status = DownloadState.QUEUED,
            progress = 0,
        )
        addItem(item)
        downloadRepository.upsert(item.toEntity(STATUS_DOWNLOADING))

        // download 扩展在 CoroutineScope 上创建任务；m3u8 由 download 模块内部判断
        // 关键修复：把视频直链附带的请求头（Referer/User-Agent 等）透传给下载库，
        // 否则 CDN 会因缺少防盗链头返回 403，导致下载失败。
        // rangeCurrency = 单任务线程数：普通视频按 Range 分片并发、m3u8 并发拉 ts；
        // 源站不支持 Range 时下载库会自动退化为单线程。
        val config = DownloadConfig(
            queue = ConfigurableDownloadQueue,
            customHeader = web.headers,
            rangeCurrency = DownloadPrefs.getThreadPerTask(),
        )
        val task = scope.download(DownloadParam(web.url, saveName, dir.absolutePath), config)

        // 实时进度（m3u8 的 progress 以 已下载分片数 / 总分片数 计百分比）
        // finished 在任务进入终态后置位，避免进度收集器把已完成的 DONE 状态覆盖回 DOWNLOADING，
        // 否则同一会话内界面会一直停在「下载中」，导致组详情页的播放按钮不显示。
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        scope.launch {
            runCatching {
                var lastShownPercent = -1
                task.progress().collect { p ->
                    val percent = if (p.totalSize > 0) ((p.downloadSize * 100) / p.totalSize).toInt() else 0
                    updateItem(episodeUrl) {
                        it.copy(
                            progress = percent,
                            status = if (finished.get()) it.status else DownloadState.DOWNLOADING,
                        )
                    }
                    if (percent != lastShownPercent) {
                        lastShownPercent = percent
                        DownloadNotifier.notifyProgress(context, animeTitle, episodeName, percent)
                    }
                }
            }
        }
        // 状态变化（排队/下载中/完成/失败）
        scope.launch {
            runCatching {
                task.state().collect { s ->
                    val isTerminal = s is State.Succeed || s is State.Failed || s is State.Stopped
                    if (isTerminal) finished.set(true)
                    val st = when (s) {
                        is State.Waiting, is State.Downloading -> DownloadState.DOWNLOADING
                        is State.Succeed -> DownloadState.DONE
                        is State.Failed -> DownloadState.FAILED
                        is State.Stopped -> DownloadState.PAUSED
                        else -> DownloadState.QUEUED
                    }
                    updateItem(episodeUrl) { it.copy(status = st) }
                    when (s) {
                        is State.Succeed -> {
                            downloadRepository.updateStatus(episodeUrl, STATUS_DONE, 100)
                            DownloadNotifier.notifyDone(context, animeTitle, episodeName)
                        }
                        is State.Failed -> {
                            downloadRepository.updateStatus(episodeUrl, STATUS_FAILED, 0)
                            DownloadNotifier.notifyFailed(context, animeTitle, episodeName)
                        }
                        else -> Unit
                    }
                }
            }
        }
        task.start()
    }

    private fun addItem(item: DownloadItemUi) = _items.update { it + item }.also { syncGroups() }

    /**
     * 计算当前下载应「钉死」的规则源 id（对齐 PlayerViewModel.loadVideo 的同源切集优化）：
     * - 当前选中的是具体某条 CSS 规则源（资源源 id 形如 `rule_<id>`）→ 返回其 ruleId，
     *   走 [AnimeRepository.getVideoDataByRule] 单源解析，避免聚合源对全部规则并发重匹配、且静默返回空直链；
     * - 当前是弹弹play（API 聚合源，id = [SourceHolder.EXTERNAL_CSS_SOURCE_ID]）或内置 API 源 → 返回 null，
     *   走默认 [AnimeRepository.getVideoData]（聚合/内置解析器）。
     */
    private fun computePinnedRuleId(): String? {
        val id = SourceHolder.currentSourceId
        if (id == SourceHolder.EXTERNAL_CSS_SOURCE_ID) return null
        val ruleId = id.removePrefix("rule_")
        if (ruleId == id) return null // 非规则源（内置 API 源），不钉死
        return ruleId.takeIf { SourceRuleRepository.getById(it) != null }
    }

    private fun updateItem(episodeUrl: String, transform: (DownloadItemUi) -> DownloadItemUi) {
        _items.update { list -> list.map { if (it.episodeUrl == episodeUrl) transform(it) else it } }
        syncGroups()
    }

    /** 由扁平列表按 detailUrl 聚合成分组列表（保持番剧首次出现顺序）。 */
    private fun syncGroups() {
        val map = LinkedHashMap<String, MutableList<DownloadItemUi>>()
        for (item in _items.value) {
            map.getOrPut(item.detailUrl) { mutableListOf() }.add(item)
        }
        val groups = map.map { (detailUrl, list) ->
            val head = list.first()
            DownloadGroupUi(
                detailUrl = detailUrl,
                animeTitle = head.animeTitle,
                animeImg = head.animeImg,
                items = list,
            )
        }
        _groups.value = groups
        // 封面为空的老记录（anime_img 列在该字段加入前下载）：按需从源站补拉一次
        groups.forEach { g -> if (g.animeImg.isBlank()) enrichCoverIfNeeded(g.detailUrl) }
    }

    /**
     * 为封面缺失的番剧组补拉封面：仅当组内任一集 animeImg 为空时触发，
     * 且同一 detailUrl 在进程内只补拉一次，避免反复请求。成功后写回内存与数据库，
     * 让「我的下载」与「组详情」两处都能展示封面（无需重新下载）。
     */
    private val enrichingCovers = mutableSetOf<String>()
    private fun enrichCoverIfNeeded(detailUrl: String) {
        if (detailUrl.isBlank()) return
        if (!_items.value.any { it.detailUrl == detailUrl && it.animeImg.isBlank() }) return
        if (!enrichingCovers.add(detailUrl)) return
        scope.launch {
            runCatching {
                val res = animeRepository.getAnimeDetail(detailUrl, SourceHolder.currentSourceMode)
                val img = (res as? Resource.Success)?.data?.img?.takeIf { it.isNotBlank() } ?: return@runCatching
                _items.update { list -> list.map { if (it.detailUrl == detailUrl) it.copy(animeImg = img) else it } }
                runCatching { downloadRepository.updateAnimeImgByDetailUrl(detailUrl, img) }
                syncGroups()
            }
        }
    }

    companion object {
        const val STATUS_QUEUED = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_DONE = 2
        const val STATUS_FAILED = 3

        /** 去掉文件名里的非法字符，并限制长度。 */
        fun makeSafeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "download" }
    }
}

private fun DownloadEntity.toUi(statusOverride: DownloadState? = null) = DownloadItemUi(
    detailUrl = detailUrl,
    episodeUrl = episodeUrl,
    animeTitle = animeTitle,
    animeImg = animeImg,
    episodeName = episodeName,
    savePath = savePath,
    saveName = saveName,
    status = statusOverride ?: when (status) {
        DownloadManager.STATUS_DONE -> DownloadState.DONE
        DownloadManager.STATUS_FAILED -> DownloadState.FAILED
        DownloadManager.STATUS_DOWNLOADING -> DownloadState.DOWNLOADING
        else -> DownloadState.QUEUED
    },
    progress = progress,
)

private fun DownloadItemUi.toEntity(status: Int) = DownloadEntity(
    animeTitle = animeTitle,
    animeImg = animeImg,
    episodeName = episodeName,
    detailUrl = detailUrl,
    episodeUrl = episodeUrl,
    savePath = savePath,
    saveName = saveName,
    status = status,
    progress = progress,
)
