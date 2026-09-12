package com.example.tuanyingshi.util

import com.example.tuanyingshi.data.remote.parse.AnimeSource
import com.example.tuanyingshi.data.remote.parse.BackendAnimeSource
import com.example.tuanyingshi.data.remote.parse.CycanimeSource
import com.example.tuanyingshi.data.remote.parse.DandanplaySource
import com.example.tuanyingshi.data.remote.parse.GirigiriSource
import com.example.tuanyingshi.data.remote.parse.RuleBasedAnimeSource
import com.example.tuanyingshi.data.remote.parse.RuleAggregateAnimeSource
import com.example.tuanyingshi.data.remote.parse.SilisiliSource
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.source_rule.SourceRule
import com.example.tuanyingshi.util.source_rule.SourceRuleRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.util.UUID

enum class SourceMode {
    Cycanime,
    Silisili,
    Girigiri,
    /** 规则/CSS 自定义数据源：由 [SourceRuleRepository] 中的 [SourceRule] 驱动。 */
    Rule,
}

/**
 * 资源源管理中心。
 *
 * 职责：
 * 1. 持有**全部资源源**（内置 + 用户自定义）的 [StateFlow]，并持久化到 SharedPreferences；
 * 2. 提供统一的获取方法 [getResourceSources]，供设置页 / 诊断页读取；
 * 3. 提供增删改 [addSource] / [updateSource] / [deleteSource]（内置源不可删除）；
 * 4. 切源 [switchSource] 时把选中源的自定义域名写入对应解析器单例，使抓取走该域名。
 *
 * 下游（仓库 / 分页 / 各业务 ViewModel）仍只依赖 [currentSourceMode]（解析器类型）与
 * [SourceMode]，本重构对它们零侵入。
 */
object SourceHolder {
    private val preferences = TuanyingApp.getInstance().preferences

    // ── 持久化 key ──
    private const val KEY_SOURCE_ID = "source_id"                 // 当前选中资源源 id
    private const val KEY_RESOURCE_SOURCES = "resource_sources"   // 全部资源源（含自定义）编码串

    /** 当前选中资源源的 id（供 UI 高亮 / ViewModel 使用）。 */
    private val _currentSourceIdFlow = MutableStateFlow(DEFAULT_SOURCE_ID)
    val currentSourceIdFlow: StateFlow<String> = _currentSourceIdFlow.asStateFlow()

    /** 当前数据源可观察流（切源后自动更新，供 UI 刷新数据）。 */
    private val _sourceModeFlow = MutableStateFlow(SourceMode.Silisili)
    val sourceModeFlow: StateFlow<SourceMode> = _sourceModeFlow.asStateFlow()

    val DEFAULT_ANIME_SOURCE = SourceMode.Silisili
    const val DEFAULT_SOURCE_ID = "builtin_silisili"

    /** 「弹弹play（API 源）」聚合源 id：选中后搜索/选集/播放走全部 CSS 规则源。 */
    const val EXTERNAL_CSS_SOURCE_ID = "external_css_all"

    /** 内置资源源（始终存在，不可删除）。声明在 init 之前，避免初始化顺序导致 NPE。 */
    private val BUILT_IN = listOf(
        ResourceSource("builtin_cycani", "次元城", SourceMode.Cycanime, "https://www.cycani.org", true),
        ResourceSource("builtin_silisili", "嘶哩嘶哩", SourceMode.Silisili, "https://www.silisili.link", true),
        ResourceSource("builtin_girigiri", "Girigiri", SourceMode.Girigiri, "https://anime.girigirilove.com", true),
    )

    private lateinit var _currentSource: AnimeSource
    private lateinit var _currentSourceMode: SourceMode
    private var _currentSourceId: String = DEFAULT_SOURCE_ID

    /** 全部资源源（内置在前，自定义在后）。 */
    private val _allSources = MutableStateFlow<List<ResourceSource>>(emptyList())
    val allSourcesFlow: StateFlow<List<ResourceSource>> = _allSources.asStateFlow()

    val currentSource: AnimeSource
        get() = if (BackendPrefs.isBackendMode()) BackendAnimeSource else _currentSource

    val currentSourceMode: SourceMode
        get() = _currentSourceMode

    val currentSourceId: String
        get() = _currentSourceId

    var isSourceChanged = false

    init {
        val merged = buildAll(decode(preferences.getString(KEY_RESOURCE_SOURCES, "") ?: ""))
        _allSources.value = merged
        persist(merged)

        // 迁移：旧版本仅存 SourceMode，映射到对应内置源 id
        val id = preferences.getString(KEY_SOURCE_ID, null)
            ?: mapOldMode(preferences.getString(KEY_SOURCE_MODE, DEFAULT_ANIME_SOURCE.name))
        val rs = merged.firstOrNull { it.id == id } ?: merged.first()
        switchInternal(rs)

        // 规则源（SourceRuleRepository）变更时自动合并进资源源列表，新增/编辑/删除即时生效
        SourceRuleRepository.rules
            .onEach { syncRules() }
            .launchIn(GlobalScope)
    }

    // ───────────────────────── 资源源 CRUD ─────────────────────────

    /** 公共获取方法：返回当前全部资源源（内置 + 自定义）的快照。 */
    fun getResourceSources(): List<ResourceSource> = _allSources.value

    /** 按 id 取资源源（不存在返回 null）。 */
    fun getResourceSource(id: String): ResourceSource? = _allSources.value.firstOrNull { it.id == id }

    /** 新增一个用户自定义资源源，返回新建的实例。 */
    fun addSource(name: String, type: SourceMode, baseUrl: String): ResourceSource {
        val rs = ResourceSource(
            id = "custom_${UUID.randomUUID()}",
            name = name.trim(),
            type = type,
            baseUrl = normalizeUrl(baseUrl),
            isBuiltIn = false,
        )
        val newList = _allSources.value + rs
        _allSources.value = newList
        persist(newList)
        return rs
    }

    /**
     * 修改资源源（名称 / 类型 / 域名）。内置源也可改（仅不能删）。
     * 若修改的正是当前选中源，立即重新应用域名配置。
     */
    fun updateSource(rs: ResourceSource) {
        val newList = _allSources.value.map { if (it.id == rs.id) rs.copy(baseUrl = normalizeUrl(rs.baseUrl)) else it }
        _allSources.value = newList
        persist(newList)
        if (rs.id == _currentSourceId) switchInternal(rs)
    }

    /**
     * 删除资源源。内置源（[ResourceSource.isBuiltIn] = true）**不可删除**，返回 false。
     * 删除当前选中源时自动回退到默认内置源。
     */
    fun deleteSource(id: String): Boolean {
        val target = _allSources.value.firstOrNull { it.id == id } ?: return false
        if (target.isBuiltIn) return false
        val newList = _allSources.value.filter { it.id != id }
        _allSources.value = newList
        persist(newList)
        if (id == _currentSourceId) switchInternal(newList.first { it.id == DEFAULT_SOURCE_ID })
        return true
    }

    // ───────────────────────── 切源 ─────────────────────────

    /** 按 id 切换资源源（找不到则忽略）。 */
    fun switchSource(id: String) {
        val rs = getResourceSource(id) ?: return
        switchInternal(rs)
    }

    /** 兼容旧调用：切到指定解析器类型的「当前选中源」（其类型需匹配）。 */
    fun switchSource(mode: SourceMode) {
        val rs = _allSources.value.firstOrNull { it.id == _currentSourceId && it.type == mode }
            ?: _allSources.value.firstOrNull { it.type == mode }
            ?: return
        switchInternal(rs)
    }

    private fun switchInternal(rs: ResourceSource) {
        if (::_currentSource.isInitialized) _currentSource.onExit()
        applyConfig(rs)
        _currentSource = getSource(rs)
        _currentSourceMode = rs.type
        _currentSourceId = rs.id
        _currentSource.onEnter()
        // 切换数据源：重置分类浏览筛选（与「筛选仅在退出/切源时重置」偏好一致）
        CategoryFilterState.reset()
        _sourceModeFlow.value = rs.type
        _currentSourceIdFlow.value = rs.id
        isSourceChanged = true
        preferences.edit().putString(KEY_SOURCE_ID, rs.id).apply()
    }

    /** 把资源源域名写入对应解析器单例（baseUrl + 作为请求 Host 的 WEB_URL）。 */
    private fun applyConfig(rs: ResourceSource) {
        val parser = getSource(rs)
        parser.baseUrl = normalizeUrl(rs.baseUrl)
        runCatching { parser.WEB_URL = URI(rs.baseUrl).host ?: parser.WEB_URL }
    }

    fun getSource(rs: ResourceSource): AnimeSource {
        // 后端模式：所有数据绕过本地/规则源，统一走后端 API（对当前模式零侵入）。
        if (BackendPrefs.isBackendMode()) return BackendAnimeSource
        // 「弹弹play（API 源）」聚合源 = 弹弹play 混合源：找番/弹幕/元数据/排行走弹弹play，视频借 CSS 规则源。
        if (rs.aggregateCss) return DandanplaySource
        return when (rs.type) {
            SourceMode.Cycanime -> CycanimeSource
            SourceMode.Silisili -> SilisiliSource
            SourceMode.Girigiri -> GirigiriSource
            SourceMode.Rule -> ruleBased(rs)
        }
    }

    /**
     * 兼容旧调用（诊断页 / [AnimeApiImpl] 仍按 [SourceMode] 取源）。
     * 调用方传入的 mode 恒等于当前选中源类型（均传 [currentSourceMode]），
     * 故对内置类型返回对应单例、对 Rule 类型直接返回当前激活源（无法仅凭模式重建具体规则）。
     */
    fun getSource(mode: SourceMode): AnimeSource {
        // 后端模式：覆盖按模式取源的逻辑，统一走后端 API。
        if (BackendPrefs.isBackendMode()) return BackendAnimeSource
        return when (mode) {
            SourceMode.Cycanime -> CycanimeSource
            SourceMode.Silisili -> SilisiliSource
            SourceMode.Girigiri -> GirigiriSource
            SourceMode.Rule -> _currentSource
        }
    }

    /** 规则源：从规则仓库取出对应 [SourceRule]，包装成 [RuleBasedAnimeSource]。规则缺失时回退默认源。 */
    private fun ruleBased(rs: ResourceSource): AnimeSource {
        val rule = rs.ruleId?.let { SourceRuleRepository.getById(it) }
        if (rule == null) {
            android.util.Log.w("SourceHolder", "规则源丢失：${rs.id}，回退到嘶哩嘶哩")
            return SilisiliSource
        }
        return RuleBasedAnimeSource(rule)
    }

    // ───────────────────────── 内置源 / 持久化 ─────────────────────────

    /** 内置源始终存在：缺失的按默认补齐；用户已编辑的内置源保留其版本；规则源动态派生追加。 */
    private fun buildAll(stored: List<ResourceSource>): List<ResourceSource> {
        // 规则源不落库到本仓库，仅合并非规则源（避免与 SourceRuleRepository 重复）
        val storedNonRule = stored.filter { it.ruleId == null }
        val custom = storedNonRule.filter { !it.isBuiltIn }
        val result = BUILT_IN.toMutableList()
        // 用存储中的内置源覆盖默认（用户改过域名/名称的情况）
        storedNonRule.filter { it.isBuiltIn }.associateBy { it.id }.forEach { (id, rs) ->
            val idx = result.indexOfFirst { it.id == id }
            if (idx >= 0) result[idx] = rs
        }
        result.addAll(custom)
        // 把规则仓库中的全部规则派生为可选资源源（内置/自定义一并合并展示）
        result.addAll(SourceRuleRepository.getAll().map { ruleToResourceSource(it) })
        // 「弹弹play（API 源）」聚合源：内置虚拟源，元数据走弹弹play API，视频借全部 CSS 规则源
        result.add(
            ResourceSource(
                id = EXTERNAL_CSS_SOURCE_ID,
                name = "弹弹play",
                type = SourceMode.Rule,
                baseUrl = "",
                isBuiltIn = true,
                aggregateCss = true,
            )
        )
        return result
    }

    /** 把一条 [SourceRule] 映射成可选资源源（id 加 `rule_` 前缀避免与内置源冲突）。 */
    private fun ruleToResourceSource(rule: SourceRule): ResourceSource {
        return ResourceSource(
            id = "rule_${rule.id}",
            name = rule.name.ifBlank { "未命名规则源" },
            type = SourceMode.Rule,
            baseUrl = rule.search.baseUrl,
            isBuiltIn = rule.isBuiltIn,
            ruleId = rule.id,
            subscriptionId = rule.subscriptionId,
            iconUrl = rule.iconUrl,
            engine = rule.engine,
        )
    }

    /**
     * 规则源（SourceRuleRepository）变更后调用：重建资源源列表并持久化；
     * 若当前选中的源已不存在（如规则被删），自动回退到默认源。
     */
    fun syncRules() {
        val merged = buildAll(decode(preferences.getString(KEY_RESOURCE_SOURCES, "") ?: ""))
        _allSources.value = merged
        persist(merged)
        if (merged.none { it.id == _currentSourceId }) {
            val def = merged.firstOrNull { it.id == DEFAULT_SOURCE_ID } ?: merged.first()
            switchInternal(def)
        }
    }

    private fun persist(list: List<ResourceSource>) {
        // 规则源动态派生、聚合源为虚拟源，均不写入本仓库持久化
        preferences.edit().putString(
            KEY_RESOURCE_SOURCES,
            encode(list.filter { it.ruleId == null && !it.aggregateCss }),
        ).apply()
    }

    private fun encode(list: List<ResourceSource>): String =
        list.joinToString("\u001F") { "${it.id}\u0001${it.name}\u0001${it.type.name}\u0001${it.baseUrl}\u0001${if (it.isBuiltIn) 1 else 0}\u0001${it.ruleId ?: ""}\u0001${if (it.aggregateCss) 1 else 0}" }

    private fun decode(text: String): List<ResourceSource> {
        if (text.isBlank()) return emptyList()
        return text.split("\u001F").mapNotNull { line ->
            val p = line.split("\u0001")
            if (p.size < 5) return@mapNotNull null
            runCatching {
                ResourceSource(
                    id = p[0],
                    name = p[1],
                    type = SourceMode.valueOf(p[2]),
                    baseUrl = p[3],
                    isBuiltIn = p[4] == "1",
                    ruleId = if (p.size >= 6) p[5].ifBlank { null } else null,
                    aggregateCss = if (p.size >= 7) p[6] == "1" else false,
                )
            }.getOrNull()
        }
    }

    private fun mapOldMode(modeName: String?): String {
        val mode = runCatching { SourceMode.valueOf(modeName ?: DEFAULT_ANIME_SOURCE.name) }
            .getOrDefault(DEFAULT_ANIME_SOURCE)
        return when (mode) {
            SourceMode.Cycanime -> "builtin_cycani"
            SourceMode.Silisili -> "builtin_silisili"
            SourceMode.Girigiri -> "builtin_girigiri"
            SourceMode.Rule -> DEFAULT_SOURCE_ID
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}
