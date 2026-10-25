package com.example.tuanyingshi.util

import com.example.tuanyingshi.data.remote.parse.AnimeSource
import com.example.tuanyingshi.data.remote.parse.BackendAnimeSource
import com.example.tuanyingshi.data.remote.parse.CycanimeSource
import com.example.tuanyingshi.data.remote.parse.DandanplaySource
import com.example.tuanyingshi.data.remote.parse.GirigiriSource
import com.example.tuanyingshi.data.remote.parse.Hanime1Source
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
    /** Hanime1（hanime1.me）API 资源数据源：专用 API 客户端直连站点、专用解析，与 CSS 规则引擎无关。 */
    Hanime1,
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

    /** 内置资源源（始终存在，不可删除）。声明在 init 之前，避免初始化顺序导致 NPE。
     *
     * 顺序即「选择数据」Tab 的默认展示顺序：次元城 > Girigiri > 嘶哩嘶哩 > Hanime1
     * （弹弹play 聚合源在 [buildAll] 里插在嘶哩嘶哩之后、Hanime1 之前）。
     * 该顺序同时是 [DEFAULT_SOURCE_ID]（嘶哩嘶哩）与兜底 `merged.first()` 的参照。 */
    private val BUILT_IN = listOf(
        ResourceSource("builtin_cycani", "次元城", SourceMode.Cycanime, "https://www.cycani.org", true),
        // Girigiri：API 资源数据源（macCMS JSON 接口直连），成人内容以外的主力 API 源。
        // ⚠️ Web 域是 ani.girigirilove.com（不是 anime.*）：接口域由本域派生成 m3u8.girigirilove.com，
        // Origin/Referer 也用本域；早期误写 anime.girigirilove.com，靠 migrateLegacyBaseUrls 修正存量。
        ResourceSource("builtin_girigiri", "Girigiri", SourceMode.Girigiri, "https://ani.girigirilove.com", true),
        ResourceSource("builtin_silisili", "嘶哩嘶哩", SourceMode.Silisili, "https://www.silisili.link", true),
        // Hanime1：API 资源数据源（非 CSS 规则源），成人内容源，默认开启「NSFW 内容屏蔽」时不进入列表。
        ResourceSource("builtin_hanime1", "Hanime1", SourceMode.Hanime1, "https://hanime1.me", true, nsfw = true),
    )

    /**
     * 内置源历史遗留的**错误**默认域（id → 错值）：用于 [migrateLegacyBaseUrls] 修正存量存档。
     * 新增条目仅当「默认域本身写错了、且已随版本发布出去」时才需要。
     */
    private val LEGACY_WRONG_BUILTIN_BASE_URLS = mapOf(
        "builtin_girigiri" to "https://anime.girigirilove.com",
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
        val merged = buildAll(migrateLegacyBaseUrls(decode(preferences.getString(KEY_RESOURCE_SOURCES, "") ?: "")))
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
        // NSFW 屏蔽开关切换：成人规则源要即时从列表出现/消失，不必重启 App
        ContentPrefs.nsfwBlock
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
            SourceMode.Hanime1 -> Hanime1Source
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
            SourceMode.Hanime1 -> Hanime1Source
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
        // 「NSFW 内容屏蔽」开启：成人源（内置 Hanime1 / 自建 nsfw 源）整体不进可选列表，
        // 与 SourceRuleRepository.getVisible() 对规则源的过滤口径一致；
        // 开关切换由 init 里的 ContentPrefs.nsfwBlock.onEach { syncRules() } 即时刷新，
        // 若当前选中源被隐藏，syncRules 的兜底逻辑会自动回退到默认源。
        val blockNsfw = ContentPrefs.isNsfwBlocked()
        val result = BUILT_IN.filterNot { blockNsfw && it.nsfw }.toMutableList()
        // 用存储中的内置源覆盖默认（用户改过域名/名称的情况）。
        // ⚠️ nsfw 是内置源的代码级属性（成人源标记），旧版存档未持久化该字段、
        // 解码后恒为 false，直接整条覆盖会把 Hanime1 的 NSFW 标记洗掉 ——
        // 因此覆盖时始终保留 [BUILT_IN] 默认的 nsfw 值。
        storedNonRule.filter { it.isBuiltIn }.associateBy { it.id }.forEach { (id, rs) ->
            val idx = result.indexOfFirst { it.id == id }
            if (idx >= 0) result[idx] = rs.copy(nsfw = result[idx].nsfw)
        }
        // 「弹弹play（API 源）」聚合源：内置虚拟源，元数据走弹弹play API，视频借全部 CSS 规则源。
        // 展示顺序：次元城 > Girigiri > 嘶哩嘶哩 > 弹弹play > Hanime1（NSFW 屏蔽时 Hanime1 缺席），
        // 故插在「最后一个非成人内置源」之后，而不是简单 append 到队尾。
        val anchor = result.indexOfLast { !it.nsfw }
        result.add(
            if (anchor >= 0) anchor + 1 else result.size,
            ResourceSource(
                id = EXTERNAL_CSS_SOURCE_ID,
                name = "弹弹play",
                type = SourceMode.Rule,
                baseUrl = "",
                isBuiltIn = true,
                aggregateCss = true,
            )
        )
        result.addAll(custom.filterNot { blockNsfw && it.nsfw })
        // 把规则仓库中的全部规则派生为可选资源源（内置/自定义一并合并展示）
        result.addAll(SourceRuleRepository.getVisible().map { ruleToResourceSource(it) })
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
            nsfw = rule.nsfw,
        )
    }

    /**
     * 规则源（SourceRuleRepository）变更后调用：重建资源源列表并持久化；
     * 若当前选中的源已不存在（如规则被删），自动回退到默认源。
     */
    fun syncRules() {
        val merged = buildAll(migrateLegacyBaseUrls(decode(preferences.getString(KEY_RESOURCE_SOURCES, "") ?: "")))
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
        list.joinToString("\u001F") {
            "${it.id}\u0001${it.name}\u0001${it.type.name}\u0001${it.baseUrl}\u0001${if (it.isBuiltIn) 1 else 0}" +
                "\u0001${it.ruleId ?: ""}\u0001${if (it.aggregateCss) 1 else 0}\u0001${if (it.nsfw) 1 else 0}"
        }

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
                    // 第 8 字段 nsfw 为后加：旧存档（7 字段）缺省时解码为 false，
                    // 由 buildAll 的内置源覆盖逻辑用 BUILT_IN 默认值兜底修复。
                    nsfw = if (p.size >= 8) p[7] == "1" else false,
                )
            }.getOrNull()
        }
    }

    /**
     * 历史遗留错误默认域修正：内置源的默认域名改过后，旧安装的存档里仍持久化着**改之前**的错值
     * （init 每次启动都会 persist，错值会被一直带着走；buildAll 的「存储覆盖内置源」又优先用存档值，
     * 光改 [BUILT_IN] 对存量无效）。
     *
     * 当前仅一例：Girigiri 默认域 `anime.girigirilove.com`（错）→ `ani.girigirilove.com`（对，
     * 与 GirigiriSource.DEFAULT_DOMAIN / GirigiriApi.DEFAULT_WEB_URL 一致）。
     * 只修「存档值 == 已知错值」的内置源，用户显式改过域名的（镜像等）不受影响。
     */
    private fun migrateLegacyBaseUrls(stored: List<ResourceSource>): List<ResourceSource> {
        if (stored.isEmpty()) return stored
        val fixes = BUILT_IN.mapNotNull { def ->
            val legacy = LEGACY_WRONG_BUILTIN_BASE_URLS[def.id] ?: return@mapNotNull null
            def.id to legacy
        }.toMap()
        if (fixes.isEmpty()) return stored
        if (stored.none { fixes[it.id] == it.baseUrl }) return stored
        return stored.map { rs ->
            val def = BUILT_IN.firstOrNull { it.id == rs.id }
            if (def != null && fixes[rs.id] == rs.baseUrl) rs.copy(baseUrl = def.baseUrl) else rs
        }
    }

    private fun mapOldMode(modeName: String?): String {
        val mode = runCatching { SourceMode.valueOf(modeName ?: DEFAULT_ANIME_SOURCE.name) }
            .getOrDefault(DEFAULT_ANIME_SOURCE)
        return when (mode) {
            SourceMode.Cycanime -> "builtin_cycani"
            SourceMode.Silisili -> "builtin_silisili"
            SourceMode.Girigiri -> "builtin_girigiri"
            SourceMode.Hanime1 -> "builtin_hanime1"
            SourceMode.Rule -> DEFAULT_SOURCE_ID
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}
