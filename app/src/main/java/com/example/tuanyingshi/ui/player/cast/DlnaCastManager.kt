package com.example.tuanyingshi.ui.player.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * 局域网投屏设备。
 *
 * [controlUrl] 是该设备 AVTransport 服务的 SOAP 控制地址——投屏动作（设置片源 / 播放 / 暂停）
 * 都是往这个地址 POST 一段 SOAP 报文。
 */
data class CastDevice(
    val id: String,
    val name: String,
    val model: String?,
    val controlUrl: String,
)

/**
 * DLNA（UPnP AVTransport）投屏。
 *
 * 为什么是 DLNA 而不是 Google Cast：Cast 需要电视端内置 Chromecast 且依赖 Google Play 服务，
 * 国内电视 / 盒子几乎都不满足；DLNA 是国标电视普遍支持的协议，且完全用标准 Socket + HTTP
 * 实现，不需要引入任何第三方 SDK。
 *
 * 局限：投屏时电视自己去拉视频流，**无法携带 App 内设置的请求头 / 代理**，
 * 因此需要登录态或防盗链的源可能播不出来——UI 上要给出这个提示。
 */
object DlnaCastManager {

    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val XML_TYPE = "text/xml; charset=\"utf-8\""

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 搜索局域网内的媒体渲染设备（电视 / 盒子 / 音箱）。
     *
     * @param timeoutMs 搜索窗口；UDP 会丢包，所以窗口内会重复发几次 M-SEARCH。
     */
    suspend fun discover(context: Context, timeoutMs: Long = 4_500L): List<CastDevice> =
        withContext(Dispatchers.IO) {
            val locations = searchLocations(context, timeoutMs)
            if (locations.isEmpty()) return@withContext emptyList()
            // 并行拉取每台设备的描述文档，避免 N 台设备串行等成十几秒
            locations.map { location ->
                async { runCatching { fetchDevice(location) }.getOrNull() }
            }.awaitAll().filterNotNull().distinctBy { it.controlUrl }
        }

    /** 发送 SSDP M-SEARCH 并收集响应中的 LOCATION。 */
    private suspend fun searchLocations(context: Context, timeoutMs: Long): Set<String> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        // 部分 ROM 默认丢弃多播包，必须显式持有 MulticastLock 才能收到 SSDP 回应
        val lock = runCatching {
            wifi?.createMulticastLock("tuanyingshi-dlna")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()

        val locations = linkedSetOf<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 900
                socket.broadcast = true
                val message = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 2\r\n")
                    // 只找 MediaRenderer（能播视频的设备），避免把路由器 / NAS 也列出来
                    append("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n")
                    append("\r\n")
                }
                val payload = message.toByteArray()
                val target = InetAddress.getByName(SSDP_ADDRESS)
                val endAt = SystemClock.elapsedRealtime() + timeoutMs
                var lastSend = 0L
                val buffer = ByteArray(4096)

                while (SystemClock.elapsedRealtime() < endAt) {
                    // 每 1.2 秒重发一次，共 3~4 次，覆盖 UDP 丢包
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSend > 1_200L) {
                        runCatching {
                            socket.send(DatagramPacket(payload, payload.size, target, SSDP_PORT))
                        }
                        lastSend = now
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (_: Throwable) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    headerValue(text, "LOCATION")?.trim()?.takeIf { it.isNotEmpty() }?.let(locations::add)
                    // 已经拿到设备就不用等满整个窗口（常见场景 1 秒内全部回包）
                    if (locations.isNotEmpty() && SystemClock.elapsedRealtime() > endAt - timeoutMs + 1_500L) break
                }
            }
        } finally {
            runCatching { lock?.release() }
        }
        return locations
    }

    /** 拉取设备描述文档，取出 friendlyName 与 AVTransport 的 controlURL。 */
    private suspend fun fetchDevice(location: String): CastDevice? {
        val xml = httpGet(location) ?: return null
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var text = ""
        var friendlyName: String? = null
        var modelName: String? = null
        var serviceType: String? = null
        var controlUrl: String? = null
        var avControlUrl: String? = null

        while (true) {
            val event = try {
                parser.next()
            } catch (_: Throwable) {
                break
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            when (event) {
                XmlPullParser.START_TAG -> {
                    text = ""
                    if (parser.name == "service") {
                        serviceType = null
                        controlUrl = null
                    }
                }

                XmlPullParser.TEXT -> text += parser.text.orEmpty()

                XmlPullParser.END_TAG -> when (parser.name) {
                    // 描述文档里可能有内嵌设备（如电视 + 其上的投屏模块），取第一个非空即可
                    "friendlyName" -> if (friendlyName.isNullOrBlank()) friendlyName = text.trim()
                    "modelName" -> if (modelName.isNullOrBlank()) modelName = text.trim()
                    "serviceType" -> serviceType = text.trim()
                    "controlURL" -> controlUrl = text.trim()
                    "service" -> {
                        if (avControlUrl == null &&
                            serviceType?.contains("AVTransport") == true &&
                            !controlUrl.isNullOrBlank()
                        ) {
                            avControlUrl = runCatching {
                                URI(location).resolve(controlUrl).toString()
                            }.getOrNull()
                        }
                    }
                }
            }
        }
        val control = avControlUrl ?: return null
        return CastDevice(
            id = location,
            name = friendlyName?.takeIf { it.isNotBlank() } ?: "未知设备",
            model = modelName?.takeIf { it.isNotBlank() },
            controlUrl = control,
        )
    }

    /**
     * 投屏：让电视开始播放 [videoUrl]。
     *
     * @param startPositionMs 续播起点（毫秒）。DLNA 的 SetAVTransportURI 没有「起始位置」参数，
     *   只能在 Play 之后再补一次 AVTransport 的 Seek（REL_TIME）。这是**尽力而为**：
     *   部分电视 / 盒子的渲染器不支持 Seek（或对 HLS 直链无效），此时从 0 开始，
     *   不算投屏失败（返回值仍为 null），手机端进度照常保留。
     * @return 失败原因（null 表示成功），便于直接提示用户。
     */
    suspend fun cast(
        device: CastDevice,
        videoUrl: String,
        title: String,
        startPositionMs: Long = 0L,
    ): String? = withContext(Dispatchers.IO) {
        val metadata = buildDidlMetadata(videoUrl, title)
        val setUri = soapEnvelope(
            service = AV_TRANSPORT,
            action = "SetAVTransportURI",
            innerXml = buildString {
                append("<InstanceID>0</InstanceID>")
                append("<CurrentURI>${escapeXml(videoUrl)}</CurrentURI>")
                append("<CurrentURIMetaData>${escapeXml(metadata)}</CurrentURIMetaData>")
            },
        )
        soapPost(device.controlUrl, AV_TRANSPORT, "SetAVTransportURI", setUri)?.let { return@withContext it }

        val play = soapEnvelope(
            service = AV_TRANSPORT,
            action = "Play",
            innerXml = "<InstanceID>0</InstanceID><Speed>1</Speed>",
        )
        soapPost(device.controlUrl, AV_TRANSPORT, "Play", play)?.let { return@withContext it }

        // 续播：太靠前的进度（<3s）没必要 seek，直接从头播更稳
        if (startPositionMs >= 3_000L) {
            val seek = soapEnvelope(
                service = AV_TRANSPORT,
                action = "Seek",
                innerXml = buildString {
                    append("<InstanceID>0</InstanceID>")
                    append("<Unit>REL_TIME</Unit>")
                    append("<Target>${formatRelTime(startPositionMs)}</Target>")
                },
            )
            // 失败不回滚：片源已经在播，只是没跳到续播点而已
            soapPost(device.controlUrl, AV_TRANSPORT, "Seek", seek)
        }
        null
    }

    /** 毫秒 → DLNA REL_TIME（HH:MM:SS，超过 24 小时也照写小时位）。 */
    private fun formatRelTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    /** 停止投屏（电视回到空闲态）。 */
    suspend fun stop(device: CastDevice): String? = withContext(Dispatchers.IO) {
        val body = soapEnvelope(
            service = AV_TRANSPORT,
            action = "Stop",
            innerXml = "<InstanceID>0</InstanceID>",
        )
        soapPost(device.controlUrl, AV_TRANSPORT, "Stop", body)
    }

    private fun soapEnvelope(service: String, action: String, innerXml: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
        append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
        append("<s:Body>")
        append("<u:$action xmlns:u=\"$service\">")
        append(innerXml)
        append("</u:$action>")
        append("</s:Body>")
        append("</s:Envelope>")
    }

    private fun soapPost(
        controlUrl: String,
        service: String,
        action: String,
        body: String,
    ): String? {
        val request = Request.Builder()
            .url(controlUrl)
            .header("SOAPACTION", "\"$service#$action\"")
            .header("Content-Type", XML_TYPE)
            .post(body.toRequestBody(XML_TYPE.toMediaType()))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) null else "设备拒绝请求（HTTP ${response.code}）"
            }
        } catch (e: Throwable) {
            "连接设备失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun httpGet(url: String): String? = try {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (_: Throwable) {
        null
    }

    /** DIDL-Lite 元数据：告诉电视「这是一个视频条目」，部分设备缺了它会拒绝播放。 */
    private fun buildDidlMetadata(videoUrl: String, title: String): String = buildString {
        append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
        append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
        append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
        append("<item id=\"0\" parentID=\"-1\" restricted=\"1\">")
        append("<dc:title>${escapeXml(title)}</dc:title>")
        append("<upnp:class>object.item.videoItem</upnp:class>")
        append("<res protocolInfo=\"http-get:*:video/*:*\">${escapeXml(videoUrl)}</res>")
        append("</item>")
        append("</DIDL-Lite>")
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /** 从 SSDP 响应头里取字段（大小写不敏感）。 */
    private fun headerValue(response: String, key: String): String? =
        response.lineSequence().firstOrNull { line ->
            line.substringBefore(':', "").trim().equals(key, ignoreCase = true)
        }?.substringAfter(':', "")?.trim()
}
