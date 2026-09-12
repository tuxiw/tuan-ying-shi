package com.example.tuanyingshi.util.source_rule

import android.content.SharedPreferences
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.util.preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 资源源规则仓库：负责规则的持久化、增删改查。
 *
 * 规则以 JSON 数组形式存在 SharedPreferences 中，按 [SourceRule.id] 去重。
 * 项目不再内置（assets 打包的）XPath 规则 —— XPath 源统一由「数据源订阅」提供
 * （默认订阅 [团影视 XPath] 拉取同一份 `xpath_v0.1_01.json`），因此 [builtInRules] 返回空。
 */
object SourceRuleRepository {

    private const val KEY_SOURCE_RULES = "source_rules_json"

    private val prefs: SharedPreferences
        get() = TuanyingApp.getInstance().preferences

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val _rules = MutableStateFlow<List<SourceRule>>(emptyList())
    val rules: StateFlow<List<SourceRule>> = _rules.asStateFlow()

    init {
        _rules.value = mergeBuiltIn(load())
    }

    /** 返回全部规则快照。 */
    fun getAll(): List<SourceRule> = _rules.value

    /** 按 id 查询。 */
    fun getById(id: String): SourceRule? = _rules.value.firstOrNull { it.id == id }

    /**
     * 新增规则。返回带新 id 的实例。
     */
    fun add(rule: SourceRule): SourceRule {
        val newRule = rule.copy(id = "custom_${UUID.randomUUID()}", isBuiltIn = false)
        val newList = _rules.value + newRule
        _rules.value = newList
        persist(newList)
        return newRule
    }

    /**
     * 更新规则。内置源可编辑；若试图把内置源改成非内置会被忽略。
     */
    fun update(rule: SourceRule): Boolean {
        val existing = _rules.value.firstOrNull { it.id == rule.id } ?: return false
        val merged = rule.copy(isBuiltIn = existing.isBuiltIn)
        val newList = _rules.value.map { if (it.id == rule.id) merged else it }
        _rules.value = newList
        persist(newList)
        return true
    }

    /**
     * 删除规则。内置源不可删除，返回 false。
     */
    fun delete(id: String): Boolean {
        val target = _rules.value.firstOrNull { it.id == id } ?: return false
        if (target.isBuiltIn) return false
        val newList = _rules.value.filter { it.id != id }
        _rules.value = newList
        persist(newList)
        return true
    }

    /** 重置所有规则为默认内置规则（会清空用户自定义规则）。 */
    fun resetToBuiltIn() {
        _rules.value = builtInRules()
        persist(_rules.value)
    }

    /** 返回某订阅下的全部规则。 */
    fun getBySubscription(subId: String): List<SourceRule> =
        _rules.value.filter { it.subscriptionId == subId }

    /**
     * 用订阅最新拉取的规则整体替换该订阅名下的旧规则（订阅规则由远程 JSON 拥有，更新即全量覆盖）。
     * 非订阅规则（[SourceRule.subscriptionId] 为空）与内置规则不受影响。
     */
    fun replaceBySubscription(subId: String, newRules: List<SourceRule>) {
        val kept = _rules.value.filter { it.subscriptionId != subId }
        val merged = kept + newRules.map { it.copy(subscriptionId = subId) }
        _rules.value = merged
        persist(merged)
    }

    /** 从 SharedPreferences 加载原始规则列表。 */
    private fun load(): List<SourceRule> {
        val raw = prefs.getString(KEY_SOURCE_RULES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SourceRule>>(raw) }.getOrDefault(emptyList())
    }

    /** 持久化规则列表。 */
    private fun persist(list: List<SourceRule>) {
        runCatching {
            prefs.edit().putString(KEY_SOURCE_RULES, json.encodeToString(list)).apply()
        }
    }

    /**
     * 合并内置规则：当前无内置规则（[builtInRules] 为空），直接返回已存储的规则
     * （订阅源 + 用户自定义源）。保留此函数以便后续若需恢复内置规则时统一在此处理。
     */
    private fun mergeBuiltIn(stored: List<SourceRule>): List<SourceRule> {
        val defaults = builtInRules().associateBy { it.id }.toMutableMap()
        stored.filter { it.isBuiltIn }.forEach { defaults[it.id] = it }
        val custom = stored.filter { !it.isBuiltIn }
        return defaults.values.toList() + custom
    }

    /** 默认内置规则：当前为空。XPath 源统一由「数据源订阅」提供，不再内置打包。 */
    private fun builtInRules(): List<SourceRule> = emptyList()
}
