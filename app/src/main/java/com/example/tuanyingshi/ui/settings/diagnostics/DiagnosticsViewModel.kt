package com.example.tuanyingshi.ui.settings.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.remote.parse.AnimeSource
import com.example.tuanyingshi.data.remote.parse.CycanimeSource
import com.example.tuanyingshi.data.remote.parse.GirigiriSource
import com.example.tuanyingshi.data.remote.parse.SilisiliSource
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Calendar
import javax.inject.Inject

/**
 * 数据源诊断：逐源跑通真实抓取链路，把每一步的状态 / 耗时 / 异常栈都暴露出来，
 * 方便在没有数据时快速定位是哪一步、什么错误（WAF 拦截 / 超时 / 选择器失效 / 空结果）。
 *
 * 直接驱动源层对象（CycanimeSource / SilisiliSource），不走仓库缓存，
 * 覆盖：初始化 → 根域名/WAF → 首页 → 排行 → 周表 → 详情 → 选集 → 视频解析。
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor() : ViewModel() {

    private val mutex = Mutex()

    /** 可诊断的内置数据源。 */
    val sources: List<DiagSource> = listOf(
        DiagSource(SourceMode.Cycanime, "次元城", CycanimeSource.DEFAULT_DOMAIN),
        DiagSource(SourceMode.Silisili, "嘶哩嘶哩", SilisiliSource.DEFAULT_DOMAIN),
        DiagSource(SourceMode.Girigiri, "Girigiri", GirigiriSource.DEFAULT_DOMAIN),
    )

    /** 初始诊断目标：跟随 App 当前默认源。 */
    private val initialSourceId: String = SourceHolder.currentSourceMode.name

    private val _uiState = MutableStateFlow(DiagnosticsUiState(selectedSourceId = initialSourceId))
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    /** 切换诊断目标源（重置结果并立即重新运行）。 */
    fun selectSource(id: String) {
        if (_uiState.value.phase == DiagStatus.RUNNING) return
        if (_uiState.value.selectedSourceId == id) return
        _uiState.value = DiagnosticsUiState(selectedSourceId = id)
//        run()
    }

    /** 手动启动一次完整诊断。 */
    fun run() {
        if (_uiState.value.phase == DiagStatus.RUNNING) return
        val sid = _uiState.value.selectedSourceId
        val mode = SourceMode.valueOf(sid)
        val source = SourceHolder.getSource(mode)
        val base = source.baseUrl

        viewModelScope.launch {
            val steps = listOf(
                DiagStep("init", "1. 数据源初始化"),
                DiagStep("waf", "2. 根域名抓取 / WAF 检测"),
                DiagStep("home", "3. 首页数据 (getHomeData)"),
                DiagStep("rank", "4. 排行榜（首页区块推导）"),
                DiagStep("schedule", "5. 周表 (getWeekData · 今天)"),
                DiagStep("detail", "6. 详情页 (getAnimeDetail)"),
                DiagStep("episodes", "7. 选集"),
                DiagStep("video", "8. 视频地址解析 (getVideoData)"),
            )
            _uiState.value = _uiState.value.copy(
                phase = DiagStatus.RUNNING,
                steps = steps,
                startedAt = System.currentTimeMillis(),
                finishedAt = 0,
            )

            // 探针：从首页数据里取第一个真实 detailUrl，供详情/选集/视频步骤使用。
            var probeDetailUrl: String? = null
            var firstEpisodeUrl: String? = null

            // 1) 初始化
            runStep("init") {
                source.onEnter()
                "onEnter 完成 ✓（${when (mode) {
                    SourceMode.Cycanime -> "WebView 内核可用，视频解析走拦截"
                    SourceMode.Silisili -> "OkHttp 直连，视频解析走 POST 解密"
                    SourceMode.Girigiri -> "OkHttp 直连，视频解析走 Base64 解码"
                    SourceMode.Rule -> "规则/CSS 解析：按规则选择器抓取，视频解析走对应策略"
                }}）"
            }

            // 2) 根域名 + WAF / 内容检测
            runStep("waf") {
                val html = source.getRootHtml()
                describeRoot(mode, html)
            }

            // 3) 首页
            runStep("home") {
                val homes = source.getHomeData()
                if (homes.isEmpty()) throwEmpty("首页返回 0 个区块 → 选择器未匹配到卡片")
                val all = homes.flatMap { it.animes }
                probeDetailUrl = all.firstOrNull()?.url
                if (all.isEmpty()) throwEmpty("首页区块均为空 → 卡片选择器失效")
                "共 ${homes.size} 个区块，${all.size} 条卡片\n区块：${homes.joinToString(" / ") { it.title }}"
            }

            // 4) 排行榜（首页最后一个区块近似榜单，展示其内容）
            runStep("rank") {
                val homes = source.getHomeData()
                val all = homes.flatMap { it.animes }.distinctBy { it.url }
                if (all.isEmpty()) throwEmpty("getHomeData 无卡片 → 无法推导榜单")
                if (probeDetailUrl == null) probeDetailUrl = all.first().url
                "首页共 ${all.size} 条；首条：${all.first().title}"
            }

            // 5) 周表（今天）
            runStep("schedule") {
                val today = todayWeekday()
                val week = source.getWeekData()
                val list = week[today].orEmpty()
                if (list.isEmpty()) throwEmpty("getWeekData(今天=$today) 返回 0 条 → 周表选区未匹配")
                "今天(周${weekdayCn(today)}) 返回 ${list.size} 条；首条：${list.first().title}"
            }

            // 6) 详情页（用前面步骤探测到的真实 detailUrl 做探针）
            runStep("detail") {
                val probe = probeDetailUrl ?: throwEmpty("无可用的探测 id（首页为空，无法测试详情）")
                val d = source.getAnimeDetail(probe)
                val eps = d.episodes.ifEmpty { d.channels.values.flatten() }
                "标题：${d.title}\n标签(${d.tags.size})：${d.tags.joinToString(" / ")}\n选集：${eps.size} 集（${d.channels.size} 个播放列表）"
            }

            // 7) 选集
            runStep("episodes") {
                val probe = probeDetailUrl ?: throwEmpty("无可用的探测 id")
                val bean = source.getAnimeDetail(probe)
                val eps = bean.episodes.ifEmpty { bean.channels.values.flatten() }
                if (eps.isEmpty()) throwEmpty("选集返回 0 条")
                firstEpisodeUrl = eps.first().url
                "共 ${eps.size} 集；首集：${eps.first().name}\n${eps.first().url}"
            }

            // 8) 视频地址解析（按源走各自的解析器）
            runStep("video") {
                val episodeUrl = firstEpisodeUrl ?: throwEmpty("没有可解析的选集 URL（选集为空）")
                val video = source.getVideoData(episodeUrl).videoUrl
                if (video.isNullOrBlank()) throwEmpty("getVideoData 未解析到 .m3u8/.mp4（播放页结构变更或未触发请求）")
                "解析成功 ✓\n$video"
            }

            _uiState.value = _uiState.value.copy(
                phase = DiagStatus.FINISHED,
                finishedAt = System.currentTimeMillis(),
            )
        }
    }

    /** 按源分析根域名 HTML：WAF 拦截 / 内容标记是否命中。 */
    private fun describeRoot(mode: SourceMode, html: String): String = when (mode) {
        SourceMode.Cycanime -> {
            val waf = html.contains("不提供服务") || html.contains("不提供")
            val hasContent = html.contains("box-width") || html.contains("public-list-box")
            buildString {
                appendLine("HTML 长度：${html.length}")
                appendLine("WAF 拦截页(不提供服务)：${if (waf) "是 ⚠" else "否"}")
                appendLine("含番剧内容标记(box-width/public-list-box)：${if (hasContent) "是 ✓" else "否 ⚠"}")
                if (waf || !hasContent) {
                    appendLine("→ 站点可能拦截了请求（WAF），或页面结构已变更导致选择器失效")
                }
            }.trimEnd()
        }
        SourceMode.Silisili -> {
            val blocked = html.contains("不提供服务") || html.contains("不提供")
            val hasContent = html.contains("conch-content") || html.contains("hl-vod-list")
            buildString {
                appendLine("HTML 长度：${html.length}")
                appendLine("疑似拦截/空壳页：${if (blocked) "是 ⚠" else "否"}")
                appendLine("含番剧内容标记(conch-content/hl-vod-list)：${if (hasContent) "是 ✓" else "否 ⚠"}")
                if (!hasContent) {
                    appendLine("→ 未匹配到内容标记：Cookie 失效 / 页面结构变更 / 选择器失效")
                }
            }.trimEnd()
        }
        SourceMode.Girigiri -> {
            val blocked = html.contains("不提供服务") || html.contains("不提供")
            val hasContent = html.contains("public-list-box") || html.contains("week-module-box")
            buildString {
                appendLine("HTML 长度：${html.length}")
                appendLine("疑似拦截/空壳页：${if (blocked) "是 ⚠" else "否"}")
                appendLine("含番剧内容标记(public-list-box/week-module-box)：${if (hasContent) "是 ✓" else "否 ⚠"}")
                if (!hasContent) {
                    appendLine("→ 未匹配到内容标记：站点被墙（建议在设置中开启代理）/ 页面结构变更 / 选择器失效")
                }
            }.trimEnd()
        }
        SourceMode.Rule -> {
            val hasContent = html.length > 500
            buildString {
                appendLine("HTML 长度：${html.length}")
                appendLine("含内容：${if (hasContent) "是 ✓" else "否 ⚠"}")
                if (!hasContent) {
                    appendLine("→ 页面内容过短：规则选择器未命中，或需调整 searchUrl/baseUrl")
                }
            }.trimEnd()
        }
    }

    /** 执行单个步骤：更新 RUNNING → OK / EMPTY / FAIL，并返回成功时的 detail（失败返回 null）。 */
    private suspend fun runStep(key: String, block: suspend () -> String): String? {
        setStep(key) { it.copy(status = DiagStatus.RUNNING, detail = "运行中…", error = null) }
        val start = System.currentTimeMillis()
        val result = runCatching { withContext(Dispatchers.IO) { block() } }
        val dur = System.currentTimeMillis() - start
        return result.fold(
            onSuccess = { detail ->
                setStep(key) { it.copy(status = DiagStatus.OK, detail = detail, durationMs = dur) }
                detail
            },
            onFailure = { e ->
                when (e) {
                    is EmptyResultException -> setStep(key) {
                        it.copy(status = DiagStatus.EMPTY, detail = e.message ?: "空结果", durationMs = dur)
                    }
                    else -> setStep(key) {
                        it.copy(status = DiagStatus.FAIL, detail = "", error = e.stackTraceString(), durationMs = dur)
                    }
                }
                null
            },
        )
    }

    private suspend fun setStep(key: String, transform: (DiagStep) -> DiagStep) {
        mutex.withLock {
            _uiState.value = _uiState.value.copy(
                steps = _uiState.value.steps.map { if (it.key == key) transform(it) else it },
            )
        }
    }

    private fun todayWeekday(): Int {
        val dw = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // SUNDAY=1 .. SATURDAY=7
        return ((dw + 5) % 7) + 1 // 1=周一 .. 7=周日
    }

    private fun weekdayCn(w: Int): String = when (w) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "日"; else -> "?"
    }

    private fun throwEmpty(msg: String): Nothing = throw EmptyResultException(msg)

    private class EmptyResultException(msg: String) : Exception(msg)

    private fun Throwable.stackTraceString(): String {
        val sw = StringWriter()
        printStackTrace(PrintWriter(sw))
        return sw.toString().lineSequence().take(50).joinToString("\n")
    }
}

enum class DiagStatus { IDLE, RUNNING, OK, EMPTY, FAIL, FINISHED }

data class DiagStep(
    val key: String,
    val title: String,
    val status: DiagStatus = DiagStatus.IDLE,
    val detail: String = "",
    val error: String? = null,
    val durationMs: Long = 0,
)

/** 可诊断的数据源描述（id 与 SourceMode.name 一致）。 */
data class DiagSource(
    val id: SourceMode,
    val name: String,
    val baseUrl: String,
)

data class DiagnosticsUiState(
    val phase: DiagStatus = DiagStatus.IDLE,
    val selectedSourceId: String = SourceMode.Cycanime.name,
    val steps: List<DiagStep> = emptyList(),
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
) {
    val passedCount: Int get() = steps.count { it.status == DiagStatus.OK }
    val problemCount: Int get() = steps.count { it.status == DiagStatus.FAIL || it.status == DiagStatus.EMPTY }
    val totalMs: Long get() = if (finishedAt > 0 && startedAt > 0) finishedAt - startedAt else 0
}
