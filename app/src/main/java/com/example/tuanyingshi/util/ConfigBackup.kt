package com.example.tuanyingshi.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Reader
import java.io.Writer

/**
 * 用户配置导入 / 导出工具。
 *
 * 整个 App 只有唯一一个 SharedPreferences 文件（"tuanying_prefs"），所有偏好 / 仓库类 /
 * 登录态都读写它。因此“用户配置”即该文件的全量键值。
 *
 * 支持「选择性备份」：按 [BackupScope] 将键值分为四类——个性化与播放设置、通知与提醒、
 * 数据源与订阅、账号会话。导出时可只导出选中的分类；导入时只写回文件里存在的键
 * （合并语义，不清除未包含的键）。
 *
 * 导入前可用 [parseMeta] 校验文件合法性（app 标识 + 版本），并在确认弹窗中展示范围与时间。
 *
 * 注意：导入后内存态（各 Prefs 对象的 StateFlow / 初始化字段）不会自动刷新，故导入完成
 * 后必须调用 [restartApp] 重启应用，让所有模块重新从磁盘加载配置。
 */
object ConfigBackup {

    /**
     * 导出格式版本：
     * - v1：无 scopes 字段，视为全量。
     * - v2：新增 scopes 声明（个性化 / 数据源与订阅 / 账号会话）。
     * - v3：拆出「通知与提醒」分类（含下载通知、离线模式提示、离线模式顶部提醒）。
     *   低版本备份仍可导入：缺失的通知键会保持本机默认值（三项默认开启）。
     */
    const val CURRENT_EXPORT_VERSION = 3
    private const val APP_TAG = "tuanyingshi"
    private const val MAX_SUPPORTED_VERSION = 3

    /** 备份范围（选择性导出 / 恢复的分类）。 */
    enum class BackupScope(val id: String, val label: String) {
        PERSONALIZATION("personalization", "个性化与播放设置"),
        NOTIFICATION("notification", "通知与提醒"),
        DATA_SOURCES("data_sources", "数据源与订阅"),
        ACCOUNT("account", "账号会话"),
    }

    /** 备份元信息（导入前先解析，用于校验与确认弹窗展示）。 */
    data class BackupMeta(
        val app: String,
        val version: Int,
        val exportedAt: Long,
        val scopes: Set<BackupScope>,
        val keyCount: Int,
    )

    /** 导入结果。 */
    data class ImportResult(
        val importedCount: Int,
        val meta: BackupMeta,
    )

    /**
     * 判定某个 prefs key 属于哪个分类。
     * - 账号会话：`cycani_*` 系列（token / 用户名 / 邮箱 / cookies）。
     * - 数据源与订阅：数据源规则、订阅列表、当前选中数据源。
     * - 通知与提醒：`notif_*` 前缀（离线模式提示 / 顶部提醒等）与下载进度通知开关。
     * - 其余（主题、代理、弹幕、播放、下载、更新、开屏、调试等）= 个性化与播放设置。
     *
     * 注意：新增通知类偏好请统一使用 `notif_` 前缀，即可自动归入本分类，无需改动此处。
     */
    fun categoryOf(key: String): BackupScope = when {
        key.startsWith("cycani_") -> BackupScope.ACCOUNT
        key == "source_rules_json" ||
        key == "source_subscriptions_json" ||
        key == "source_id" ||
        key.contains("subscription") ||
        key.contains("source_rule") -> BackupScope.DATA_SOURCES
        key.startsWith("notif_") ||
        key == "download_notification_enabled" -> BackupScope.NOTIFICATION
        else -> BackupScope.PERSONALIZATION
    }

    /** 统计各分类当前键数量，用于导出前展示。 */
    fun scopeCounts(prefs: SharedPreferences): Map<BackupScope, Int> {
        val map = BackupScope.entries.associateWith { 0 }.toMutableMap()
        prefs.all.keys.forEach { key -> map[categoryOf(key)] = map.getValue(categoryOf(key)) + 1 }
        return map
    }

    /** 全量导出（等同于选中所有分类）。 */
    fun exportAll(prefs: SharedPreferences, writer: Writer) {
        exportScoped(prefs, writer, BackupScope.entries.toSet())
    }

    /**
     * 按选中分类导出为带类型标记与范围声明的 JSON 并写入 [writer]。
     * 每个值编码为 { "t": 类型字母, "v": 原始值 }，支持 Boolean/Int/Long/Float/String/Set<String>。
     */
    fun exportScoped(prefs: SharedPreferences, writer: Writer, scopes: Set<BackupScope>) {
        val root = JSONObject()
        root.put("app", APP_TAG)
        root.put("type", "config")
        root.put("version", CURRENT_EXPORT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val scopeArr = JSONArray()
        scopes.forEach { scopeArr.put(it.id) }
        root.put("scopes", scopeArr)

        val prefsObj = JSONObject()
        for ((key, value) in prefs.all) {
            if (categoryOf(key) !in scopes) continue
            prefsObj.put(key, encodeValue(value))
        }
        root.put("prefs", prefsObj)

        val buffered = BufferedWriter(writer)
        buffered.write(root.toString(2))
        buffered.flush()
    }

    /**
     * 解析头部元信息并校验合法性；非法时抛出带说明的 [IllegalArgumentException]。
     * 返回 [BackupMeta] 供确认弹窗展示（应用标识、版本、导出时间、范围、键数量）。
     */
    fun parseMeta(text: String): BackupMeta {
        val root = JSONObject(text)
        val app = root.optString("app", "")
        if (app != APP_TAG) {
            throw IllegalArgumentException("文件不是团影视备份（app=$app）")
        }
        val version = root.optInt("version", 1)
        if (version < 1 || version > MAX_SUPPORTED_VERSION) {
            throw IllegalArgumentException("不支持的备份版本 v$version")
        }
        val prefsObj = root.optJSONObject("prefs")
            ?: throw IllegalArgumentException("备份文件缺少 prefs 数据")
        val declared: Set<BackupScope> = if (root.has("scopes")) {
            val arr = root.getJSONArray("scopes")
            (0 until arr.length()).mapNotNull { i ->
                val id = arr.getString(i)
                BackupScope.entries.firstOrNull { it.id == id }
            }.toSet()
        } else {
            // 旧版 v1 无 scopes 字段，视为全量
            BackupScope.entries.toSet()
        }
        // v3 之前「通知与提醒」的键归属于个性化分类，为让确认弹窗如实反映内容，补上该分类
        val scopes: Set<BackupScope> =
            if (version < 3 && BackupScope.PERSONALIZATION in declared) {
                declared + BackupScope.NOTIFICATION
            } else {
                declared
            }
        return BackupMeta(
            app = app,
            version = version,
            exportedAt = root.optLong("exportedAt", 0L),
            scopes = scopes,
            keyCount = prefsObj.length(),
        )
    }

    /**
     * 从 [text] 读取导出内容，校验后写回 [prefs] 并提交（apply）。
     * 仅写回文件里存在的键（合并语义），不清除未包含的分类。
     * @return 写入的键数量与元信息。
     * @throws Exception 文件格式错误、校验失败等。
     */
    fun import(prefs: SharedPreferences, text: String): ImportResult {
        val meta = parseMeta(text) // 先校验
        val root = JSONObject(text)
        val prefsObj = root.getJSONObject("prefs")
        val editor = prefs.edit()
        val keys = prefsObj.keys()
        var count = 0
        while (keys.hasNext()) {
            val key = keys.next()
            val item = prefsObj.getJSONObject(key)
            val type = item.getString("t")
            when (type) {
                "b" -> editor.putBoolean(key, item.getBoolean("v"))
                "i" -> editor.putInt(key, item.getInt("v"))
                "l" -> editor.putLong(key, item.getLong("v"))
                "f" -> editor.putFloat(key, item.getDouble("v").toFloat())
                "s" -> editor.putString(key, item.optString("v", ""))
                "ss" -> {
                    val arr = item.getJSONArray("v")
                    val set = LinkedHashSet<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    editor.putStringSet(key, set)
                }
                else -> continue // 未知类型跳过，避免破坏现有配置
            }
            count++
        }
        editor.apply()
        return ImportResult(importedCount = count, meta = meta)
    }

    /**
     * 重启应用，使导入后的配置在所有模块生效。
     * 通过启动包名对应的 LAUNCHER Intent 并清空原有任务栈实现。
     */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
        if (context is Activity) {
            context.finishAffinity()
        } else {
            // 非 Activity 上下文（如 Application）无法直接 finish，退而求其次杀进程
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /** 将 SharedPreferences 中的原生值编码为 {t, v} 结构。 */
    private fun encodeValue(value: Any?): JSONObject {
        val obj = JSONObject()
        when (value) {
            is Boolean -> {
                obj.put("t", "b")
                obj.put("v", value)
            }
            is Int -> {
                obj.put("t", "i")
                obj.put("v", value)
            }
            is Long -> {
                obj.put("t", "l")
                obj.put("v", value)
            }
            is Float -> {
                obj.put("t", "f")
                obj.put("v", value)
            }
            is String -> {
                obj.put("t", "s")
                obj.put("v", value)
            }
            is Set<*> -> {
                obj.put("t", "ss")
                val arr = JSONArray()
                value.forEach { if (it is String) arr.put(it) }
                obj.put("v", arr)
            }
            else -> {
                // 兜底：统一按字符串存（理论上不会走到，prefs 只存上述类型）
                obj.put("t", "s")
                obj.put("v", value?.toString() ?: "")
            }
        }
        return obj
    }
}
