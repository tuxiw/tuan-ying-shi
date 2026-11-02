package com.lanlinju.videoplayer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.LinkedHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HLS（m3u8）源的**进度帧预览取帧器**。
 *
 * 背景：[MediaMetadataRetriever] 是直连源站的原生 API，既不走 App 内 OkHttp 代理、
 * 也无法带上播放所需的鉴权请求头，因此对在线 m3u8 / 防盗链源**几乎必然取不到帧**
 * （表现为拖动进度条时气泡只有时间、没有缩略图）。
 *
 * 本取帧器改走「与播放器同一条网络链路」：
 * 1. 用带 [PlayerProxySelector] + 播放请求头的 OkHttp 拉 m3u8 清单；
 * 2. 主清单（#EXT-X-STREAM-INF）取**第一个变体**——通常码率最低，取帧流量最小；
 * 3. 按 #EXTINF 累加时间轴，定位目标时间落在哪个分片及片内偏移；
 * 4. 下载该分片（带 #EXT-X-MAP 时前置 init 分片，fMP4 才能解码）到缓存目录；
 * 5. 用 [MediaMetadataRetriever] 对本地分片按片内偏移取帧——本地解码一定能拿到鉴权后的数据。
 *
 * 流量保护（硬性）：
 * - 每个片源累计下载超过 [MAX_TOTAL_BYTES_PER_SOURCE] 就不再取帧，避免拖进度条刷爆流量；
 * - 分片文件 LRU 保留 [MAX_CACHED_SEGMENTS] 个，换源时全部清理；
 * - HTTP 请求走可取消封装，跨秒拖动时上一个下载会被真正 cancel，而不是空跑。
 *
 * 不支持（直接放弃，不影响播放）：AES 加密分片（#EXT-X-KEY）、#EXT-X-BYTERANGE 分片。
 */
internal class HlsFramePreviewFetcher(context: Context) {

    private val lock = Any()
    private val cacheDir = File(context.cacheDir, "hls_preview").apply { mkdirs() }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(PlayerProxySelector)
        .apply { playerProxyAuthenticator?.invoke()?.let { proxyAuthenticator(it) } }
        .build()

    private var playlistKey: String? = null
    private var segments: List<Segment>? = null
    private var initFile: File? = null
    private val segmentFiles = LinkedHashMap<String, File>(MAX_CACHED_SEGMENTS, 0.75f, true)
    private var usedBytes = 0L
    /** 流量预算耗尽或源不支持 → 置位后不再发起任何下载。 */
    private var exhausted = false

    private data class Segment(val uri: String, val durationSec: Double)

    /** 换片源时调用：清掉清单、已缓存分片与流量计数。 */
    fun reset() {
        synchronized(lock) {
            playlistKey = null
            segments = null
            initFile = null
            segmentFiles.clear()
            usedBytes = 0L
            exhausted = false
        }
        runCatching { cacheDir.deleteRecursively() }
        runCatching { cacheDir.mkdirs() }
    }

    /**
     * 取 [positionMs] 处的预览帧；取不到返回 null（调用方会降级为只显示时间）。
     */
    suspend fun fetch(
        url: String,
        headers: Map<String, String>,
        positionMs: Long,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val segs = ensurePlaylist(url, headers) ?: return@withContext null
        val targetSec = positionMs / 1000.0

        var acc = 0.0
        var target: Segment? = null
        var offsetSec = 0.0
        for (i in segs.indices) {
            val s = segs[i]
            if (targetSec < acc + s.durationSec || i == segs.lastIndex) {
                target = s
                offsetSec = (targetSec - acc).coerceAtLeast(0.0)
                break
            }
            acc += s.durationSec
        }
        val seg = target ?: return@withContext null
        val media = downloadSegment(seg, headers) ?: return@withContext null
        // fMP4（.m4s）分片必须前置 init 分片才能被解码器识别
        val file = mergeWithInit(media) ?: media
        grabFrame(file, (offsetSec * 1000.0).toLong())
    }

    /** 把 init 分片拼到媒体分片前面（结果缓存复用），无需拼接时返回 null。 */
    private fun mergeWithInit(media: File): File? {
        val init = synchronized(lock) { initFile } ?: return null
        val merged = File(cacheDir, "merged_${media.name}")
        if (merged.exists() && merged.length() > 0) return merged
        return try {
            merged.outputStream().use { os ->
                init.inputStream().use { it.copyTo(os) }
                media.inputStream().use { it.copyTo(os) }
            }
            merged
        } catch (t: Throwable) {
            runCatching { merged.delete() }
            null
        }
    }

    // ===== 清单 =====

    private suspend fun ensurePlaylist(url: String, headers: Map<String, String>): List<Segment>? {
        synchronized(lock) {
            if (playlistKey == url) return segments
            if (exhausted) return null
        }
        val raw = getString(url, headers) ?: return null
        val lines = raw.lineSequence().map { it.trim() }.toList()

        // 主清单：取第一个变体（通常码率最低 → 分片最小 → 取帧流量最小）
        val variant = firstVariant(lines)
        if (variant != null) {
            val resolved = resolve(url, variant)
            return ensurePlaylist(resolved, headers)
        }

        // 加密分片：没有密钥无法解码，直接放弃（播放不受影响，播放器自己会解密）
        if (lines.any { it.startsWith("#EXT-X-KEY") }) return null
        // 字节区间分片需要按 Range 拼装，风险高，直接放弃
        if (lines.any { it.startsWith("#EXT-X-BYTERANGE") }) return null

        // fMP4 分片（.m4s）必须前置 init 分片才能解码
        val mapUri = lines.firstOrNull { it.startsWith("#EXT-X-MAP") }
            ?.let { Regex("""URI="([^"]+)"""").find(it)?.groupValues?.getOrNull(1) }
        val init = mapUri?.let { downloadInit(resolve(url, it), headers) }

        val out = ArrayList<Segment>()
        var duration = 0.0
        for (line in lines) {
            when {
                line.startsWith("#EXTINF:") -> duration = parseExtinf(line)
                line.isNotEmpty() && !line.startsWith("#") -> {
                    if (duration > 0) out.add(Segment(resolve(url, line), duration))
                    duration = 0.0
                }
            }
        }
        if (out.isEmpty()) return null

        synchronized(lock) {
            playlistKey = url
            segments = out
            initFile = init
        }
        return out
    }

    /** 主清单里 #EXT-X-STREAM-INF 之后的第一行 URI（即第一个变体）。 */
    private fun firstVariant(lines: List<String>): String? {
        for (i in lines.indices) {
            if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue
            val next = lines.getOrNull(i + 1)
            if (!next.isNullOrEmpty() && !next.startsWith("#")) return next
        }
        return null
    }

    private fun parseExtinf(line: String): Double =
        line.substringAfter("#EXTINF:", "")
            .substringBefore(',')
            .trim()
            .toDoubleOrNull() ?: 0.0

    private fun resolve(base: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return runCatching { URI(base).resolve(ref).toString() }.getOrDefault(ref)
    }

    // ===== 下载 =====

    private suspend fun downloadInit(uri: String, headers: Map<String, String>): File? {
        val file = File(cacheDir, "init_${uri.hashCode()}.bin")
        if (file.exists() && file.length() > 0) return file
        val size = download(uri, headers, file, INIT_SEGMENT_LIMIT_BYTES)
        return if (size > 0) file else null
    }

    private suspend fun downloadSegment(seg: Segment, headers: Map<String, String>): File? {
        synchronized(lock) {
            if (exhausted) return null
            segmentFiles[seg.uri]?.let { if (it.exists() && it.length() > 0) return it }
            if (usedBytes >= MAX_TOTAL_BYTES_PER_SOURCE) {
                exhausted = true
                return null
            }
        }
        val file = File(cacheDir, "seg_${seg.uri.hashCode()}.bin")
        if (!file.exists() || file.length() == 0L) {
            val size = download(seg.uri, headers, file, SEGMENT_LIMIT_BYTES)
            if (size <= 0L) {
                runCatching { file.delete() }
                // 分片过大（高码率长分片）→ 判定该源不适合取帧，停止以免刷流量
                if (size == TOO_LARGE) synchronized(lock) { exhausted = true }
                return null
            }
            synchronized(lock) {
                usedBytes += size
                if (usedBytes >= MAX_TOTAL_BYTES_PER_SOURCE) exhausted = true
            }
        }
        synchronized(lock) {
            segmentFiles[seg.uri] = file
            evictOldestLocked()
        }
        return file
    }

    /** LRU 淘汰最久未用的分片文件（调用前必须持有 lock）。 */
    private fun evictOldestLocked() {
        while (segmentFiles.size > MAX_CACHED_SEGMENTS) {
            val it = segmentFiles.entries.iterator()
            if (!it.hasNext()) break
            val oldest = it.next()
            it.remove()
            runCatching { oldest.value.delete() }
        }
    }

    /**
     * 流式下载到文件，返回实际字节数；失败返回 0，**分片超过 [limit] 返回 [TOO_LARGE] 且根本不下载**。
     * 走可取消封装：跨秒拖动时旧请求会被真正 cancel，不会在后台空跑烧流量。
     */
    private suspend fun download(
        url: String,
        headers: Map<String, String>,
        out: File,
        limit: Long,
    ): Long {
        val request = buildRequest(url, headers)
        val call = client.newCall(request)
        val response = runCatching { call.await() }.getOrNull() ?: return 0L
        return response.use { resp ->
            if (!resp.isSuccessful) return 0L
            val body = resp.body ?: return 0L
            // 先按声明长度判断是否过大：避免在「高码率长分片」上白下载十几 MB 才放弃
            if (body.contentLength() > limit) return TOO_LARGE
            try {
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                out.outputStream().use { os ->
                    body.byteStream().use { ins ->
                        while (true) {
                            val read = ins.read(buffer)
                            if (read <= 0) break
                            if (total + read > limit) return 0L
                            os.write(buffer, 0, read)
                            total += read
                        }
                    }
                }
                total
            } catch (t: Throwable) {
                runCatching { out.delete() }
                0L
            }
        }
    }

    private suspend fun getString(url: String, headers: Map<String, String>): String? {
        val request = buildRequest(url, headers)
        return runCatching {
            client.newCall(request).await().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    private fun buildRequest(url: String, headers: Map<String, String>): Request =
        Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

    // ===== 取帧 =====

    /**
     * 对本地分片文件取帧。带 init 分片时 [file] 里是「init + media」拼接后的数据。
     * 注意：分片文件本身可能只是 init，需保证调用方已拼接。
     */
    private fun grabFrame(file: File, offsetMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(
                offsetMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * 把 [Call] 包成可取消的挂起函数：协程被取消（如拖动跨秒、LaunchedEffect 重启）时
     * 真正 [Call.cancel]，而不是让下载在后台继续跑完。
     */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }

    companion object {
        /** [download] 的返回值之一：分片超过大小上限（调用方据此停止取帧）。 */
        private const val TOO_LARGE = -1L

        /** 单个片源累计允许的取帧下载流量：超出即停止取帧（降级为只显示时间）。 */
        private const val MAX_TOTAL_BYTES_PER_SOURCE = 32L * 1024 * 1024

        /** 单个分片的大小上限：超过说明分片过大（高码率长分片），放弃以免刷流量。 */
        const val SEGMENT_LIMIT_BYTES = 12L * 1024 * 1024

        /** init 分片（fMP4）很小，给个宽松但有限的上限。 */
        const val INIT_SEGMENT_LIMIT_BYTES = 2L * 1024 * 1024

        /** 缓存目录里最多保留的分片文件数（LRU 淘汰）。 */
        const val MAX_CACHED_SEGMENTS = 6
    }
}
