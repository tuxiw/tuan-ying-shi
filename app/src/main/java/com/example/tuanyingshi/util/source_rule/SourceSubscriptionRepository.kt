package com.example.tuanyingshi.util.source_rule

import android.content.SharedPreferences
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.util.DownloadManager
import com.example.tuanyingshi.util.preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.UUID

/**
 * 数据源订阅仓库。
 *
 * - 持久化订阅列表（SharedPreferences JSON），并提供 [StateFlow] 供设置页实时刷新；
 * - 默认内置订阅种子：**[团影视] css_v0.1_01.json** 与 **[团影视 XPath] xpath_v0.1_01.json**（均来自 tuanyingshi.pages.dev）；
 * - 订阅下的数据源以 [SourceRule] 形式写入 [SourceRuleRepository]（[subscriptionId] 关联）；
 * - 每 1 小时自动更新全部订阅（[startAutoUpdate] 在 [TuanyingApp] 启动时调用一次）。
 *
 * 更新逻辑：[refresh] 拉取远程 JSON → [SubscriptionImporter] 解析为规则 → 以稳定 id
 * `sub_<订阅id>_<序号>` 整体覆盖该订阅旧规则 → 更新订阅记录的更新时间 / 成败 / 数据源数。
 */
object SourceSubscriptionRepository {

    private const val KEY_SUBSCRIPTIONS = "source_subscriptions_json"
    private const val UPDATE_INTERVAL_MS = 60 * 60 * 1000L // 1 小时
    private const val DEFAULT_SUB_ID = "sub_tuanyingshi"
    private const val DEFAULT_SUB_URL = "https://tuanyingshi.pages.dev/v1/css_v0.1_01.json"
    private const val DEFAULT_XPATH_SUB_ID = "sub_tuanyingshi_xpath"
    private const val DEFAULT_XPATH_SUB_URL = "https://tuanyingshi.pages.dev/v1/xpath_v0.1_01.json"

    /** 内置默认订阅种子：首次运行 / 缺失时自动补齐（不立即拉取，交由 startAutoUpdate 触发首更）。 */
    private val DEFAULT_SEEDS = listOf(
        SourceSubscription(id = DEFAULT_SUB_ID, name = "团影视", url = DEFAULT_SUB_URL),
        SourceSubscription(id = DEFAULT_XPATH_SUB_ID, name = "团影视 XPath", url = DEFAULT_XPATH_SUB_URL),
    )

    /** 仅用于清理旧版遗留的 KazumiRules 订阅记录（新版本已不再使用外部 KazumiRules 订阅）。 */
    private const val KAZUMI_SUB_ID = "sub_kazumi"

    private val prefs: SharedPreferences
        get() = TuanyingApp.getInstance().preferences

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _subscriptions = MutableStateFlow<List<SourceSubscription>>(emptyList())
    val subscriptions: StateFlow<List<SourceSubscription>> = _subscriptions.asStateFlow()

    private var autoUpdateStarted = false

    init {
        // 不再使用外部 KazumiRules 订阅：移除已持久化的旧 Kazumi 订阅记录，并清空其名下规则源，
        // 避免升级后残留「0 数据源 / 失效」的订阅与孤立源。自建 XPath 源改由内置 assets 提供。
        val fromPrefs = load()
        val cleaned = fromPrefs.filter { it.id != KAZUMI_SUB_ID }
        if (cleaned.size != fromPrefs.size) {
            SourceRuleRepository.replaceBySubscription(KAZUMI_SUB_ID, emptyList())
        }
        _subscriptions.value = cleaned
        // 补齐缺失的默认订阅种子（首次运行全补；已装用户只补齐新增的 XPath 种子）
        val seeds = DEFAULT_SEEDS.filter { seed -> cleaned.none { it.id == seed.id } }
        if (seeds.isNotEmpty()) {
            _subscriptions.value = seeds + _subscriptions.value
            persist(_subscriptions.value)
        }
    }

    fun getById(id: String): SourceSubscription? = _subscriptions.value.firstOrNull { it.id == id }

    /** 新增订阅（按 URL 自动命名，命名可被 UI 覆盖）。返回新建实例并立即触发一次更新。 */
    fun add(url: String, name: String, format: String = "dandanplay"): SourceSubscription {
        val sub = SourceSubscription(
            id = "sub_${UUID.randomUUID()}",
            name = name.takeIf { it.isNotBlank() } ?: hostName(url),
            url = url.trim(),
            format = format,
        )
        _subscriptions.value = _subscriptions.value + sub
        persist(_subscriptions.value)
        scope.launch { refresh(sub) }
        return sub
    }

    /** 删除订阅：同时清掉它名下的全部规则（从规则仓库移除，对应可选源一并消失）。 */
    fun remove(id: String) {
        _subscriptions.value = _subscriptions.value.filter { it.id != id }
        persist(_subscriptions.value)
        SourceRuleRepository.replaceBySubscription(id, emptyList())
    }

    /**
     * 手动 / 定时刷新单条订阅。成功或失败都会更新订阅记录的更新时间。
     *
     * 解析时会**根据返回内容自动识别格式**（覆盖 [SourceSubscription.format] 的声明）：
     * - JSON 顶层为数组 -> Kazumi 规则数组 -> [KazumiRuleConverter.convertList]
     * - 含 `exportedMediaSourceDataList` -> 弹弹 play 导出格式 -> [SubscriptionImporter.parse]
     * - 其余情况按 [SourceSubscription.format] 走 [KazumiRulesImporter] 或 [SubscriptionImporter]
     *
     * @return 是否成功
     */
    suspend fun refresh(sub: SourceSubscription): Boolean {
        val (rules, detectedFormat) = runCatching {
            fetchAndParse(sub)
        }.getOrElse {
            android.util.Log.w("SubscriptionRepo", "订阅更新失败 ${sub.name}: ${it.message}")
            updateRecord(sub.id) { copy(lastUpdateTime = System.currentTimeMillis(), lastUpdateSuccess = false) }
            return false
        }
        val renamed = rules.mapIndexed { i, r -> r.copy(id = "sub_${sub.id}_$i") }
        SourceRuleRepository.replaceBySubscription(sub.id, renamed)
        updateRecord(sub.id) {
            copy(
                lastUpdateTime = System.currentTimeMillis(),
                lastUpdateSuccess = true,
                sourceIds = renamed.map { it.id },
                format = detectedFormat,
            )
        }
        return true
    }

    /**
     * 下载订阅内容并按内容特征识别格式。
     * @return Pair(规则列表, 识别到的格式)
     */
    private suspend fun fetchAndParse(sub: SourceSubscription): Pair<List<SourceRule>, String> {
        val raw = DownloadManager.getHtml(sub.url, hostOf(sub.url))
        val trimmed = raw.trim()
        return when {
            // Kazumi 规则数组：自建/内置 XPath 源清单（如 xpath_v0.1_01.json）
            trimmed.startsWith("[") -> KazumiRuleConverter.convertList(raw) to "kazumi"
            // 弹弹 play 导出格式
            trimmed.contains("\"exportedMediaSourceDataList\"") -> SubscriptionImporter.parse(raw) to "dandanplay"
            // 兜底：按声明的格式处理
            sub.format == "kazumi" -> KazumiRulesImporter.fetchSubscription(sub.url) to "kazumi"
            else -> SubscriptionImporter.parse(raw) to "dandanplay"
        }
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host ?: "" }.getOrDefault("")

    /** 刷新全部订阅（供定时任务调用）。 */
    suspend fun refreshAll() {
        _subscriptions.value.forEach { refresh(it) }
    }

    /**
     * 启动每 1 小时自动更新。仅可在 Application 启动时调用一次。
     * 启动后会立即补更「从未更新过」的订阅（首次运行 / 新增后未联网的情况）。
     */
    fun startAutoUpdate() {
        if (autoUpdateStarted) return
        autoUpdateStarted = true
        scope.launch {
            // 首次：补更从未更新过的订阅
            _subscriptions.value.filter { it.lastUpdateTime == 0L }.forEach { refresh(it) }
            // 之后每 1 小时轮询
            while (isActive) {
                delay(UPDATE_INTERVAL_MS)
                refreshAll()
            }
        }
    }

    // ───────────────────────── 持久化 ─────────────────────────

    private fun updateRecord(id: String, transform: SourceSubscription.() -> SourceSubscription) {
        _subscriptions.value = _subscriptions.value.map { if (it.id == id) it.transform() else it }
        persist(_subscriptions.value)
    }

    private fun load(): List<SourceSubscription> {
        val raw = prefs.getString(KEY_SUBSCRIPTIONS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SourceSubscription>>(raw) }.getOrDefault(emptyList())
    }

    private fun persist(list: List<SourceSubscription>) {
        runCatching { prefs.edit().putString(KEY_SUBSCRIPTIONS, json.encodeToString(list)).apply() }
    }

    private fun hostName(url: String): String = runCatching {
        val h = java.net.URI(url).host ?: url
        h.removePrefix("www.")
    }.getOrDefault(url)
}
