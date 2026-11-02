package com.example.tuanyingshi.util.theme

import android.content.Context
import com.example.tuanyingshi.util.ThemePrefs
import com.example.tuanyingshi.util.dynamicProxySelector
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 主题套装管理器：拉取套装列表、下载 zip（含 MD5 校验）、解压到本地、管理就绪 / 应用状态。
 *
 * 核心约束：主题套装的资源（背景图、导航图标等）必须先由 [download] 把
 * [ThemePackProperties.packageUrl] 的 zip 下载、校验、解压到本地后，才能 [apply]（使用）；
 * 未就绪的主题 [apply] 直接返回 false，UI 也据此禁用「使用」按钮。
 */
object ThemePackManager {

    /** 主题套装列表接口。 */
    const val THEME_JSON_URL = "https://tuanyingshi.pages.dev/v1/theme.json"

    private val client = OkHttpClient.Builder()
        .proxySelector(dynamicProxySelector())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** 套装下载状态。 */
    enum class Status { NOT_DOWNLOADED, DOWNLOADING, READY, CURRENT }

    // ── 本地存储：filesDir/theme_packs/<itemId>/ ──

    private fun rootDir(context: Context) =
        File(context.applicationContext.filesDir, "theme_packs").also { it.mkdirs() }

    fun packDir(context: Context, itemId: Int) = File(rootDir(context), itemId.toString())

    // ── 下载进度（itemId -> 0..100；-1 表示总大小未知）──

    private val _progress = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val progress: StateFlow<Map<Int, Int>> = _progress.asStateFlow()

    private val downloading = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val dlMutex = Mutex()

    /** 拉取套装列表（网络）。失败返回空列表。 */
    suspend fun fetchPacks(): List<ThemePackItem> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = client.newCall(Request.Builder().url(THEME_JSON_URL).build()).execute()
            if (!resp.isSuccessful) return@runCatching emptyList<ThemePackItem>()
            val body = resp.body?.string() ?: return@runCatching emptyList()
            gson.fromJson(body, ThemePackListResp::class.java).data ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** 本地是否已就绪：目录存在 + manifest 标记 ready=true。 */
    fun isReady(context: Context, itemId: Int): Boolean {
        val manifest = File(packDir(context, itemId), "manifest.json")
        if (!manifest.exists()) return false
        return runCatching {
            gson.fromJson(manifest.readText(), PackManifest::class.java).ready == true
        }.getOrDefault(false)
    }

    /** 计算某套装的当前状态（即时值，非订阅）。 */
    fun status(context: Context, itemId: Int): Status {
        val current = ThemePrefs.getConfig().themePackId
        return when {
            current == itemId -> Status.CURRENT
            isReady(context, itemId) -> Status.READY
            itemId in downloading -> Status.DOWNLOADING
            else -> Status.NOT_DOWNLOADED
        }
    }

    /** 当前应用的套装 id（null = 内置默认主题）。 */
    fun currentPackId(): Int? = ThemePrefs.getConfig().themePackId

    /**
     * 下载并解压套装。完成写 manifest（ready=true，并缓存 properties 供离线读取配色）后清理 zip。
     * 仅在未就绪且当前未在下载时才真正执行；重复调用会直接返回 true（已就绪）或 false（已在下载）。
     */
    suspend fun download(context: Context, item: ThemePackItem): Boolean {
        val id = item.itemId
        val url = item.properties?.packageUrl
        if (url.isNullOrBlank()) return false
        if (isReady(context, id)) return true
        // 互斥：已在下载则直接结束
        if (!dlMutex.withLock { downloading.add(id) }) return false
        _progress.value = _progress.value + (id to 0)
        try {
            val dir = packDir(context, id)
            dir.mkdirs()
            val zipFile = File(dir, "pack.zip")
            zipFile.delete()
            if (!downloadFile(url, zipFile) { p ->
                    _progress.value = _progress.value + (id to p)
                }
            ) return false

            // MD5 校验（服务端给出时）
            val md5 = item.properties?.packageMd5
            if (!md5.isNullOrBlank()) {
                val actual = computeFileMd5(zipFile)
                if (actual == null || actual.equals(md5, ignoreCase = true).not()) {
                    zipFile.delete()
                    return false
                }
            }

            // 清理旧解压内容（保留 manifest 之外的残留），再解压
            dir.listFiles { f -> f.name != "pack.zip" && f.name != "manifest.json" }
                ?.forEach { it.deleteRecursively() }
            if (!unzipFile(zipFile, dir)) return false
            zipFile.delete()

            val manifest = PackManifest(
                ready = true,
                name = item.name,
                itemId = id,
                ver = item.properties?.ver,
                md5 = md5,
                properties = item.properties,
            )
            File(dir, "manifest.json").writeText(gson.toJson(manifest))
            return true
        } finally {
            dlMutex.withLock { downloading.remove(id) }
            _progress.value = _progress.value - id
        }
    }

    private suspend fun downloadFile(url: String, out: File, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = client.newCall(Request.Builder().url(url).build()).execute()
                if (!resp.isSuccessful) return@runCatching false
                val body = resp.body ?: return@runCatching false
                val total = body.contentLength()
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            onProgress(
                                if (total > 0) (downloaded * 100 / total).toInt() else -1,
                            )
                        }
                    }
                }
                true
            }.getOrDefault(false).also { if (!it && out.exists()) out.delete() }
        }

    /** 应用（使用）某已就绪的套装；未就绪返回 false。 */
    fun apply(context: Context, itemId: Int): Boolean {
        if (!isReady(context, itemId)) return false
        ThemePrefs.setThemePack(itemId)
        return true
    }

    /** 取消当前套装应用，恢复内置默认主题。 */
    fun clearCurrent() {
        ThemePrefs.setThemePack(null)
    }

    /** 删除某套装本地数据（含正在应用的）。删除当前套装则自动恢复默认。 */
    fun remove(context: Context, itemId: Int) {
        packDir(context, itemId).deleteRecursively()
        if (ThemePrefs.getConfig().themePackId == itemId) ThemePrefs.setThemePack(null)
    }

    /**
     * 读取当前应用的套装 properties（供主题配色使用）。未就绪或解析失败返回 null。
     * properties 在 [download] 时已写入 manifest，故无需网络即可读取。
     */
    fun currentProperties(context: Context): ThemePackProperties? {
        val id = ThemePrefs.getConfig().themePackId ?: return null
        if (!isReady(context, id)) return null
        val manifest = File(packDir(context, id), "manifest.json")
        if (!manifest.exists()) return null
        return runCatching {
            gson.fromJson(manifest.readText(), PackManifest::class.java).properties
        }.getOrNull()
    }

    /**
     * 读取某套装解压后的本地资源文件（图片 / 图标）。按文件名（去扩展名、忽略大小写）精确匹配 [assetKey]。
     * 例如 assetKey="head_bg" 会匹配解压目录里的 head_bg.jpg / head_bg.png 等。
     * 找不到返回 null（例如 zip 内采用哈希命名时）。
     */
    fun localAssetFile(context: Context, itemId: Int, assetKey: String): File? {
        val dir = packDir(context, itemId)
        if (!dir.exists()) return null
        val key = assetKey.lowercase()
        return dir.walkTopDown().firstOrNull { f ->
            !f.isDirectory && f.nameWithoutExtension.lowercase() == key
        }
    }
}

/** 套装本地清单：记录就绪状态与 properties（供离线读取配色）。 */
private data class PackManifest(
    val ready: Boolean = false,
    val name: String? = null,
    val itemId: Int = 0,
    val ver: String? = null,
    val md5: String? = null,
    val properties: ThemePackProperties? = null,
)
