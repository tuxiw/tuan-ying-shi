package com.example.tuanyingshi.util.sync

import android.content.Context
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.data.local.AppDatabase
import com.example.tuanyingshi.data.local.entity.DownloadEntity
import com.example.tuanyingshi.data.local.entity.EpisodeEntity
import com.example.tuanyingshi.data.local.entity.FavoriteEntity
import com.example.tuanyingshi.data.local.entity.HistoryEntity
import com.example.tuanyingshi.data.local.entity.UserAnimeStatusEntity
import com.example.tuanyingshi.util.ConfigBackup
import com.example.tuanyingshi.util.preferences
import com.example.tuanyingshi.util.resolveDownloadDir
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.text.Charsets
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * 跨设备直连同步（局域网点对点）。
 *
 * 模型：一方作为「接收方」开启本地 HTTP 服务并展示 本机IP:端口 + 6 位配对码；
 * 另一方作为「连接方」输入地址与配对码后连接，选择要同步的分类与方向
 * （拉取=从对方获取 / 推送=发送给对方）。
 *
 * 配对码是每个连接请求必须携带的校验项（?code=），不匹配直接 403，避免同网段
 * 陌生设备误连窃取数据。
 *
 * 可同步三类数据（用户可自由勾选）：
 * - 设置：SharedPreferences 全量（复用 [ConfigBackup]）。
 * - 观看历史：Room 的 history / episode / favourite / user_anime_status 四表。
 * - 下载文件：下载索引（download_table）+ 实际 .ts 文件。
 */
object DirectSync {

    /** 可同步的分类。 */
    enum class SyncCategory(val id: String, val label: String) {
        SETTINGS("settings", "设置"),
        HISTORY("history", "观看历史"),
        DOWNLOADS("downloads", "下载文件"),
    }

    data class DeviceInfo(val name: String, val categories: List<SyncCategory>)

    /** 同步结果。 */
    data class SyncOutcome(val success: Boolean, val detail: String)

    private const val PORT = 8787
    private val gson = Gson()

    /** 生成 6 位随机配对码。 */
    fun generateCode(): String = (0..5).joinToString("") { Random.nextInt(0, 10).toString() }

    // ───────────────────────── 通用工具 ─────────────────────────

    /** 取本机局域网 IPv4（不依赖 ACCESS_WIFI_STATE 权限）。 */
    fun localIpAddress(): String {
        return runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp || ni.isLoopback) return@forEach
                ni.inetAddresses.toList().forEach { addr ->
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    fun deviceName(): String =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // ───────────────────────── 历史 / 下载 序列化 ─────────────────────────

    private data class HistoryBundle(
        val history: HistoryEntity,
        val episodes: List<EpisodeEntity>,
    )

    private data class HistoryDump(
        val histories: List<HistoryBundle>,
        val favorites: List<FavoriteEntity>,
        val statuses: List<UserAnimeStatusEntity>,
    )

    private data class DownloadManifestItem(
        val episodeUrl: String,
        val saveName: String,
        val detailUrl: String,
        val animeTitle: String,
        val animeImg: String,
        val episodeName: String,
        val size: Long,
    )

    private fun exportHistory(context: Context): String = runBlocking(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val histories = db.historyDao().getAll()
        val bundles = histories.map { h ->
            HistoryBundle(h, db.historyDao().getEpisodes(h.historyId))
        }
        val favorites = db.favoriteDao().getAll()
        val statuses = db.userStatusDao().getAll()
        gson.toJson(HistoryDump(bundles, favorites, statuses))
    }

    private fun importHistory(context: Context, text: String) = runBlocking(Dispatchers.IO) {
        val dump = gson.fromJson(text, HistoryDump::class.java)
        val db = AppDatabase.getInstance(context)
        for (b in dump.histories) {
            val newId = db.historyDao().upsert(b.history.copy(historyId = 0L))
            for (ep in b.episodes) {
                db.historyDao().insertEpisode(ep.copy(episodeId = 0L, historyId = newId))
            }
        }
        for (f in dump.favorites) db.favoriteDao().insert(f.copy(favouriteId = 0L))
        for (s in dump.statuses) db.userStatusDao().insert(s.copy(statusId = 0L))
    }

    private fun exportDownloadsManifest(context: Context): List<DownloadManifestItem> =
        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            db.downloadDao().getCompleted().map { ent ->
                val f = File(ent.savePath, ent.saveName)
                DownloadManifestItem(
                    episodeUrl = ent.episodeUrl,
                    saveName = ent.saveName,
                    detailUrl = ent.detailUrl,
                    animeTitle = ent.animeTitle,
                    animeImg = ent.animeImg,
                    episodeName = ent.episodeName,
                    size = if (f.exists()) f.length() else 0L,
                )
            }
        }

    // ───────────────────────── 接收方（Host / ServerSocket） ─────────────────────────

    object Server {
        private var serverSocket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()
        @Volatile
        var isRunning = false
            private set
        var listenAddress: String = ""
            private set
        var listenPort: Int = 0
            private set

        @Synchronized
        fun start(context: Context, code: String, categories: Set<SyncCategory>) {
            if (isRunning) return
            serverSocket = try {
                ServerSocket(PORT)
            } catch (_: Exception) {
                ServerSocket(0)
            }
            isRunning = true
            listenPort = serverSocket!!.localPort
            listenAddress = localIpAddress()
            Thread {
                try {
                    while (isRunning) {
                        val sock = serverSocket!!.accept()
                        executor.execute { handle(sock, context.applicationContext, code, categories) }
                    }
                } catch (_: Exception) {
                    // socket closed
                }
            }.start()
        }

        @Synchronized
        fun stop() {
            isRunning = false
            runCatching { serverSocket?.close() }
            serverSocket = null
        }

        private fun handle(
            sock: Socket,
            context: Context,
            code: String,
            categories: Set<SyncCategory>,
        ) {
            try {
                val rawIn = sock.getInputStream()
                val requestLine = readLine(rawIn)
                if (requestLine.isBlank()) {
                    runCatching { sock.close() }
                    return
                }
                val tokens = requestLine.split(" ")
                if (tokens.size < 2) {
                    runCatching { sock.close() }
                    return
                }
                val method = tokens[0]
                val (path, query) = splitPath(tokens[1])

                val headers = mutableMapOf<String, String>()
                var line = readLine(rawIn)
                while (line.isNotEmpty()) {
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] =
                        line.substring(idx + 1).trim()
                    line = readLine(rawIn)
                }
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (contentLength > 0) readExact(rawIn, contentLength) else ByteArray(0)

                if (query["code"] != code) {
                    sendText(sock, 403, "text/plain", "forbidden")
                    return
                }

                when {
                    method == "GET" && path == "/hello" -> {
                        val root = JSONObject()
                        root.put("name", deviceName())
                        val arr = JSONArray()
                        categories.forEach { arr.put(it.id) }
                        root.put("categories", arr)
                        sendText(sock, 200, "application/json", root.toString())
                    }
                    method == "GET" && path == "/cat/settings" -> {
                        val sw = StringWriter()
                        ConfigBackup.exportAll(context.preferences, sw)
                        sendText(sock, 200, "application/json", sw.toString())
                    }
                    method == "POST" && path == "/cat/settings" -> {
                        val res = ConfigBackup.import(
                            context.preferences,
                            String(body, Charsets.UTF_8),
                        )
                        sendText(sock, 200, "application/json", "{\"ok\":true,\"count\":${res.importedCount}}")
                    }
                    method == "GET" && path == "/cat/history" -> {
                        sendText(sock, 200, "application/json", exportHistory(context))
                    }
                    method == "POST" && path == "/cat/history" -> {
                        importHistory(context, String(body, Charsets.UTF_8))
                        sendText(sock, 200, "application/json", "{\"ok\":true}")
                    }
                    method == "GET" && path == "/cat/downloads" -> {
                        sendText(sock, 200, "application/json", gson.toJson(exportDownloadsManifest(context)))
                    }
                    method == "GET" && path == "/file" -> {
                        serveDownloadFile(sock, context, query["ep"] ?: "")
                    }
                    method == "POST" && path == "/file" -> {
                        receiveDownloadFile(sock, context, query, body)
                    }
                    else -> sendText(sock, 404, "text/plain", "not found")
                }
            } catch (_: Exception) {
                runCatching { sock.close() }
            }
        }

        private fun serveDownloadFile(sock: Socket, context: Context, ep: String) {
            if (ep.isBlank()) {
                sendText(sock, 400, "text/plain", "missing ep")
                return
            }
            try {
                val ent = runBlocking(Dispatchers.IO) {
                    AppDatabase.getInstance(context).downloadDao().getByEpisode(ep)
                }
                if (ent == null) {
                    sendText(sock, 404, "text/plain", "not found")
                    return
                }
                val file = File(resolveDownloadDir(context), ent.saveName)
                if (!file.exists()) {
                    sendText(sock, 404, "text/plain", "file missing")
                    return
                }
                sendFile(sock, file)
            } catch (_: Exception) {
                runCatching { sendText(sock, 500, "text/plain", "error") }
            }
        }

        private fun receiveDownloadFile(
            sock: Socket,
            context: Context,
            query: Map<String, String>,
            body: ByteArray,
        ) {
            val ep = query["ep"]
            val name = query["name"]
            if (ep == null || name == null) {
                sendText(sock, 400, "text/plain", "missing params")
                return
            }
            try {
                val dir = resolveDownloadDir(context)
                dir.mkdirs()
                val file = File(dir, name)
                file.outputStream().use { it.write(body) }
                val ent = DownloadEntity(
                    animeTitle = query["title"] ?: "",
                    animeImg = query["img"] ?: "",
                    episodeName = query["epname"] ?: name,
                    detailUrl = query["detail"] ?: "",
                    episodeUrl = ep,
                    savePath = dir.absolutePath,
                    saveName = name,
                    status = 2,
                    progress = 100,
                )
                runBlocking(Dispatchers.IO) {
                    AppDatabase.getInstance(context).downloadDao().upsert(ent)
                }
                sendText(sock, 200, "application/json", "{\"ok\":true}")
            } catch (e: Exception) {
                sendText(sock, 500, "text/plain", "save failed: ${e.message}")
            }
        }

        private fun readLine(inp: InputStream): String {
            val sb = StringBuilder()
            var prev = -1
            while (true) {
                val b = inp.read()
                if (b == -1) {
                    if (sb.isEmpty()) return ""
                    break
                }
                if (b == '\n'.code) {
                    if (prev == '\r'.code) sb.setLength(sb.length - 1)
                    break
                }
                sb.append(b.toChar())
                prev = b
            }
            return sb.toString()
        }

        private fun readExact(inp: InputStream, len: Int): ByteArray {
            val buf = ByteArray(len)
            var off = 0
            while (off < len) {
                val n = inp.read(buf, off, len - off)
                if (n < 0) break
                off += n
            }
            return buf
        }

        private fun splitPath(full: String): Pair<String, Map<String, String>> {
            val qIdx = full.indexOf('?')
            val path = if (qIdx < 0) full else full.substring(0, qIdx)
            val qs = if (qIdx < 0) "" else full.substring(qIdx + 1)
            val map = qs.split('&').mapNotNull { seg ->
                val eq = seg.indexOf('=')
                if (eq < 0) null else runCatching {
                    URLDecoder.decode(seg.substring(0, eq), "UTF-8") to
                        URLDecoder.decode(seg.substring(eq + 1), "UTF-8")
                }.getOrNull()
            }.toMap()
            return path to map
        }

        private fun sendText(sock: Socket, code: Int, contentType: String, body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val out = sock.getOutputStream()
            out.write(
                (
                    "HTTP/1.1 $code\r\nContent-Type: $contentType; charset=utf-8\r\n" +
                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    ).toByteArray(Charsets.UTF_8),
            )
            out.write(bytes)
            out.flush()
            runCatching { sock.close() }
        }

        private fun sendFile(sock: Socket, file: File) {
            val out = sock.getOutputStream()
            out.write(
                (
                    "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                        "Content-Length: ${file.length()}\r\nConnection: close\r\n\r\n"
                    ).toByteArray(Charsets.UTF_8),
            )
            file.inputStream().use { it.copyTo(out) }
            out.flush()
            runCatching { sock.close() }
        }
    }

    // ───────────────────────── 连接方（Client / OkHttp） ─────────────────────────

    object Client {
        private val http = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        suspend fun fetchInfo(address: String, code: String): DeviceInfo =
            withContext(Dispatchers.IO) {
                val resp = http.newCall(
                    Request.Builder().url("http://$address/hello?code=${enc(code)}").build(),
                ).execute()
                if (!resp.isSuccessful) throw IllegalStateException("连接失败(${resp.code})")
                val obj = JSONObject(resp.body!!.string())
                val name = obj.optString("name", "未知设备")
                val arr = obj.optJSONArray("categories") ?: JSONArray()
                val cats = (0 until arr.length()).mapNotNull { i ->
                    SyncCategory.entries.firstOrNull { c -> c.id == arr.getString(i) }
                }
                DeviceInfo(name, cats)
            }

        suspend fun pullSettings(address: String, code: String): SyncOutcome =
            withContext(Dispatchers.IO) {
                try {
                    val resp = http.newCall(
                        Request.Builder().url("http://$address/cat/settings?code=${enc(code)}").build(),
                    ).execute()
                    if (!resp.isSuccessful) return@withContext SyncOutcome(false, "拉取设置失败(${resp.code})")
                    val res = ConfigBackup.import(TuanyingApp.getInstance().preferences, resp.body!!.string())
                    SyncOutcome(true, "已同步设置 ${res.importedCount} 项")
                } catch (e: Exception) {
                    SyncOutcome(false, "拉取设置出错：${e.message}")
                }
            }

        suspend fun pushSettings(address: String, code: String): SyncOutcome =
            withContext(Dispatchers.IO) {
                try {
                    val sw = StringWriter()
                    ConfigBackup.exportAll(TuanyingApp.getInstance().preferences, sw)
                    val body = sw.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val resp = http.newCall(
                        Request.Builder().url("http://$address/cat/settings?code=${enc(code)}")
                            .post(body).build(),
                    ).execute()
                    if (!resp.isSuccessful) return@withContext SyncOutcome(false, "推送设置失败(${resp.code})")
                    SyncOutcome(true, "已推送设置到对方")
                } catch (e: Exception) {
                    SyncOutcome(false, "推送设置出错：${e.message}")
                }
            }

        suspend fun pullHistory(address: String, code: String): SyncOutcome =
            withContext(Dispatchers.IO) {
                try {
                    val resp = http.newCall(
                        Request.Builder().url("http://$address/cat/history?code=${enc(code)}").build(),
                    ).execute()
                    if (!resp.isSuccessful) return@withContext SyncOutcome(false, "拉取历史失败(${resp.code})")
                    importHistory(TuanyingApp.getInstance(), resp.body!!.string())
                    SyncOutcome(true, "已同步观看历史")
                } catch (e: Exception) {
                    SyncOutcome(false, "拉取历史出错：${e.message}")
                }
            }

        suspend fun pushHistory(address: String, code: String): SyncOutcome =
            withContext(Dispatchers.IO) {
                try {
                    val json = exportHistory(TuanyingApp.getInstance())
                    val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                    val resp = http.newCall(
                        Request.Builder().url("http://$address/cat/history?code=${enc(code)}")
                            .post(body).build(),
                    ).execute()
                    if (!resp.isSuccessful) return@withContext SyncOutcome(false, "推送历史失败(${resp.code})")
                    SyncOutcome(true, "已推送观看历史到对方")
                } catch (e: Exception) {
                    SyncOutcome(false, "推送历史出错：${e.message}")
                }
            }

        suspend fun pullDownloads(address: String, code: String): SyncOutcome =
            withContext(Dispatchers.IO) {
                try {
                    val ctx = TuanyingApp.getInstance()
                    val resp = http.newCall(
                        Request.Builder().url("http://$address/cat/downloads?code=${enc(code)}").build(),
                    ).execute()
                    if (!resp.isSuccessful) return@withContext SyncOutcome(false, "拉取下载清单失败(${resp.code})")
                    val manifest = gson.fromJson(
                        resp.body!!.string(),
                        Array<DownloadManifestItem>::class.java,
                    ).toList()
                    if (manifest.isEmpty()) {
                        return@withContext SyncOutcome(true, "对方没有可同步的下载文件")
                    }
                    val dir = resolveDownloadDir(ctx)
                    dir.mkdirs()
                    var ok = 0
                    manifest.forEach { item ->
                        try {
                            val fresp = http.newCall(
                                Request.Builder().url(
                                    "http://$address/file?code=${enc(code)}&ep=${enc(item.episodeUrl)}",
                                ).build(),
                            ).execute()
                            if (fresp.isSuccessful) {
                                val file = File(dir, item.saveName)
                                fresp.body!!.byteStream().use { inp ->
                                    FileOutputStream(file).use { out -> inp.copyTo(out) }
                                }
                                val ent = DownloadEntity(
                                    animeTitle = item.animeTitle,
                                    animeImg = item.animeImg,
                                    episodeName = item.episodeName,
                                    detailUrl = item.detailUrl,
                                    episodeUrl = item.episodeUrl,
                                    savePath = dir.absolutePath,
                                    saveName = item.saveName,
                                    status = 2,
                                    progress = 100,
                                )
                                AppDatabase.getInstance(ctx).downloadDao().upsert(ent)
                                ok++
                            }
                        } catch (_: Exception) {
                        }
                    }
                    SyncOutcome(true, "已同步下载文件 $ok/${manifest.size}")
                } catch (e: Exception) {
                    SyncOutcome(false, "拉取下载出错：${e.message}")
                }
            }

        suspend fun pushDownloads(address: String, code: String): SyncOutcome =
            withContext(Dispatchers.IO) {
                try {
                    val ctx = TuanyingApp.getInstance()
                    val completed = AppDatabase.getInstance(ctx).downloadDao().getCompleted()
                    if (completed.isEmpty()) {
                        return@withContext SyncOutcome(true, "本机没有已完成的下载文件")
                    }
                    var ok = 0
                    completed.forEach { ent ->
                        val file = File(ent.savePath, ent.saveName)
                        if (!file.exists()) return@forEach
                        try {
                            val body = file.asRequestBody("application/octet-stream".toMediaType())
                            val url = "http://$address/file?code=${enc(code)}" +
                                "&ep=${enc(ent.episodeUrl)}&name=${enc(ent.saveName)}" +
                                "&detail=${enc(ent.detailUrl)}&title=${enc(ent.animeTitle)}" +
                                "&img=${enc(ent.animeImg)}&epname=${enc(ent.episodeName)}"
                            val resp = http.newCall(
                                Request.Builder().url(url).post(body).build(),
                            ).execute()
                            if (resp.isSuccessful) ok++
                        } catch (_: Exception) {
                        }
                    }
                    SyncOutcome(true, "已推送下载文件 $ok/${completed.size}")
                } catch (e: Exception) {
                    SyncOutcome(false, "推送下载出错：${e.message}")
                }
            }
    }
}
