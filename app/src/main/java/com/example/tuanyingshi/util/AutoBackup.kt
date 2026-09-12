package com.example.tuanyingshi.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.example.tuanyingshi.TuanyingApp
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动备份管理器。
 *
 * 在应用私有 Download 目录（`Android/data/<pkg>/files/Download/tuanyingshi/backups/`）下
 * 定期生成全量配置备份，无需任何存储权限。备份文件以 `tuanyingshi-auto-<时间戳>.json` 命名，
 * 仅保留最近 [MAX_HISTORY] 份，超出自动清理。
 *
 * 开关 / 间隔 / 上次时间等偏好存放在主 SharedPreferences，本身属于「个性化」分类，会随备份一起被保存。
 */
object AutoBackup {

    private const val KEY_ENABLED = "auto_backup_enabled"
    private const val KEY_INTERVAL_HOURS = "auto_backup_interval_hours"
    private const val KEY_LAST_MS = "last_auto_backup_ms"
    private const val MAX_HISTORY = 7
    private const val PREFIX = "tuanyingshi-auto-"

    private val prefs: SharedPreferences
        get() = TuanyingApp.getInstance().preferences

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    fun getIntervalHours(): Int = prefs.getInt(KEY_INTERVAL_HOURS, 24)
    fun setIntervalHours(h: Int) = prefs.edit().putInt(KEY_INTERVAL_HOURS, h).apply()

    fun getLastBackupMs(): Long = prefs.getLong(KEY_LAST_MS, 0L)

    /** 备份目录：优先应用私有 Download，不可用时回退到 filesDir。 */
    private fun dir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        return File(base, "tuanyingshi/backups").apply { mkdirs() }
    }

    /**
     * 启动后调用：开启且距上次备份已超过间隔（或从未备份）时执行一次。
     * 放在 [TuanyingApp.onCreate] 中，失败静默忽略，不影响主流程。
     */
    fun runDue(context: Context) {
        if (!isEnabled()) return
        val last = getLastBackupMs()
        val due = last == 0L || (System.currentTimeMillis() - last) >= getIntervalHours() * 3_600_000L
        if (due) runCatching { backupNow(context) }
    }

    /** 立即创建一份全量自动备份，并清理超出上限的旧文件。返回生成的文件，失败返回 null。 */
    fun backupNow(context: Context): File? {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir(context), "$PREFIX$ts.json")
        return try {
            FileWriter(file).use { w -> ConfigBackup.exportAll(context.preferences, w) }
            prefs.edit().putLong(KEY_LAST_MS, System.currentTimeMillis()).apply()
            prune(context)
            file
        } catch (e: IOException) {
            null
        }
    }

    /** 清理旧备份，仅保留文件名（含时间戳）升序的最后 [MAX_HISTORY] 份。 */
    private fun prune(context: Context) {
        val files = dir(context).listFiles { f ->
            f.name.startsWith(PREFIX) && f.extension == "json"
        }?.sortedBy { it.name } ?: return
        files.dropLast(MAX_HISTORY).forEach { it.delete() }
    }

    /** 单条历史备份的展示信息。 */
    data class Entry(
        val file: File,
        val exportedAt: Long,
        val scopes: Set<ConfigBackup.BackupScope>,
        val size: Long,
    )

    /** 历史备份列表（按时间倒序）。无法解析的文件会被跳过。 */
    fun listHistory(context: Context): List<Entry> {
        val files = dir(context).listFiles { f ->
            f.name.startsWith(PREFIX) && f.extension == "json"
        } ?: return emptyList()
        return files.sortedByDescending { it.name }.mapNotNull { f ->
            runCatching {
                val meta = ConfigBackup.parseMeta(f.readText())
                Entry(f, meta.exportedAt, meta.scopes, f.length())
            }.getOrNull()
        }
    }

    /** 从历史文件中恢复配置（写回后由调用方负责重启应用）。 */
    fun restore(context: Context, file: File): ConfigBackup.ImportResult {
        return ConfigBackup.import(context.preferences, file.readText())
    }
}
