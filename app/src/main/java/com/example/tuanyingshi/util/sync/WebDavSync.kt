package com.example.tuanyingshi.util.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.util.ConfigBackup
import com.example.tuanyingshi.util.preferences
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.StringWriter
import java.util.concurrent.TimeUnit

/**
 * 跨设备同步（WebDAV）。
 *
 * 通过 WebDAV 协议（PROPFIND / GET / PUT）把配置文件上传到远端固定路径、或从远端拉取，
 * 实现手机端与平板端之间的配置互通。无需自建后端，可对接 Nextcloud / ownCloud / 各类 NAS /
 * 支持 WebDAV 的网盘。
 *
 * 同步模型为「最后写入者胜（newer-wins）」的双向同步：
 * - [syncNow] 先比较远端文件导出时间与本机上次同步时间：
 *   - 远端更新 → 拉取并合并导入（注入本机缺失的键）；
 *   - 随后把本机全量配置上传覆盖远端。
 * - 因此任一台改了设置并触发同步，另一端下次同步即可拿到；同机短时间内重复同步是幂等的。
 *
 * 敏感信息注意：账号会话（含登录令牌 / 代理密码）属于「个性化」分类，随全量导出进入同步文件，
 * 且 WebDAV 凭据本身也会随配置同步到另一台设备（同一账号），这是预期行为。
 *
 * 认证：使用 HTTP Basic（仅建议在 HTTPS 下使用）；若未填账号则不加 Authorization 头。
 */
object WebDavSync {

    private const val KEY_URL = "webdav_url"
    private const val KEY_USER = "webdav_user"
    private const val KEY_PASS = "webdav_pass"
    private const val KEY_REMOTE_FILE = "webdav_remote_file"
    private const val KEY_ENABLED = "webdav_enabled"
    private const val KEY_LAST_SYNC = "webdav_last_sync_ms"
    private const val KEY_AUTO_LAUNCH = "webdav_auto_launch"
    private const val DEFAULT_REMOTE_FILE = "tuanyingshi-sync.json"

    private val prefs: SharedPreferences
        get() = TuanyingApp.getInstance().preferences

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ===== 配置存取 =====
    fun getUrl(): String = prefs.getString(KEY_URL, "")?.trim() ?: ""
    fun setUrl(v: String) = prefs.edit().putString(KEY_URL, v.trim()).apply()

    fun getUser(): String = prefs.getString(KEY_USER, "")?.trim() ?: ""
    fun setUser(v: String) = prefs.edit().putString(KEY_USER, v.trim()).apply()

    fun getPass(): String = prefs.getString(KEY_PASS, "") ?: ""
    fun setPass(v: String) = prefs.edit().putString(KEY_PASS, v).apply()

    fun getRemoteFile(): String {
        val v = prefs.getString(KEY_REMOTE_FILE, "")?.trim()
        return if (v.isNullOrBlank()) DEFAULT_REMOTE_FILE else v
    }
    fun setRemoteFile(v: String) {
        val t = v.trim()
        prefs.edit().putString(KEY_REMOTE_FILE, if (t.isBlank()) DEFAULT_REMOTE_FILE else t).apply()
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    fun isAutoLaunch(): Boolean = prefs.getBoolean(KEY_AUTO_LAUNCH, true)
    fun setAutoLaunch(v: Boolean) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH, v).apply()

    fun getLastSyncMs(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)
    private fun setLastSyncMs(v: Long) = prefs.edit().putLong(KEY_LAST_SYNC, v).apply()

    /** 是否已填写足以连接的最小信息（地址 + 账号）。 */
    fun isConfigured(): Boolean = getUrl().isNotBlank() && getUser().isNotBlank()

    // ===== 内部工具 =====
    private fun fullUrl(): String {
        val base = getUrl().trimEnd('/') + "/"
        return base + getRemoteFile().trimStart('/')
    }

    /** 远端文件的父集合 URL（用于 PROPFIND 测连通性）。 */
    private fun parentCollectionUrl(): String {
        val full = fullUrl()
        val idx = full.lastIndexOf('/')
        return if (idx < 0) full else full.substring(0, idx + 1)
    }

    private fun Request.Builder.auth(): Request.Builder {
        val u = getUser()
        val p = getPass()
        if (u.isBlank() && p.isBlank()) return this
        header("Authorization", Credentials.basic(u, p))
        return this
    }

    // ===== 对外操作 =====

    /** 测试连通性：对父集合发 PROPFIND( Depth:0 )。207/200 视为可达，404 视为集合可达但文件未建（也算成功）。 */
    fun testConnection(): Result<Unit> = runCatching {
        val body = "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/></d:prop></d:propfind>"
            .toRequestBody("application/xml; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url(parentCollectionUrl())
            .method("PROPFIND", body)
            .header("Depth", "0")
            .auth()
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code !in listOf(207, 200, 404)) {
                throw IllegalStateException("连接失败：HTTP ${resp.code} ${resp.message}")
            }
        }
    }

    /** 上传本机全量配置到远端（PUT 覆盖）。成功后更新上次同步时间。 */
    fun upload(context: Context): Result<Unit> = runCatching {
        val sw = StringWriter()
        ConfigBackup.exportAll(context.preferences, sw)
        val body = sw.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(fullUrl())
            .put(body)
            .header("Content-Type", "application/json")
            .auth()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("上传失败：HTTP ${resp.code} ${resp.message}")
            }
        }
        setLastSyncMs(System.currentTimeMillis())
    }

    /** 下载远端文件内容；远端 404 时返回 null。 */
    fun download(): Result<String?> = runCatching {
        val req = Request.Builder()
            .url(fullUrl())
            .get()
            .auth()
            .build()
        client.newCall(req).execute().use { resp ->
            when {
                resp.code == 404 -> null
                resp.isSuccessful -> resp.body?.string()
                else -> throw IllegalStateException("下载失败：HTTP ${resp.code} ${resp.message}")
            }
        }
    }

    /** 下载并导入（合并）远端配置，返回导入结果；远端无文件时抛友好异常。 */
    fun downloadAndImport(context: Context): Result<ConfigBackup.ImportResult> = runCatching {
        val text = download().getOrThrow()
            ?: throw IllegalStateException("云端暂无同步文件，请先在任一设备上传")
        val res = ConfigBackup.import(context.preferences, text)
        setLastSyncMs(System.currentTimeMillis())
        res
    }

    /** 双向同步（newer-wins）：远端更新则先拉取合并，再把本机全量上传。 */
    fun syncNow(context: Context): Result<SyncOutcome> = runCatching {
        val remoteText = download().getOrThrow()
        val remoteMeta = remoteText?.let { runCatching { ConfigBackup.parseMeta(it) }.getOrNull() }
        val localLast = getLastSyncMs()
        val pulled = remoteMeta != null && remoteMeta.exportedAt > localLast
        if (pulled) {
            ConfigBackup.import(context.preferences, remoteText!!)
        }
        upload(context).getOrThrow()
        setLastSyncMs(System.currentTimeMillis())
        SyncOutcome(pulled = pulled, importedCount = if (pulled) remoteMeta!!.keyCount else 0)
    }

    /** [syncNow] 的结果摘要。 */
    data class SyncOutcome(val pulled: Boolean, val importedCount: Int)

    /** 启动后调用：开启 + 已配置 + 启动自动同步时执行一次双向同步；失败静默忽略。 */
    fun runDue(context: Context) {
        if (!isEnabled() || !isConfigured() || !isAutoLaunch()) return
        runCatching { syncNow(context) }
    }
}
