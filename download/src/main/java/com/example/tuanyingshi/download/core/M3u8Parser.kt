package com.example.tuanyingshi.download.core

import com.example.tuanyingshi.download.utils.closeQuietly
import com.example.tuanyingshi.download.utils.hexToBytes
import java.net.URI

class M3u8Parser(param: DownloadParam) {

    private var uri: URI
    private var originalUrl: String
    private var redirectUrl: String = ""
    private val tsUrlList = ArrayList<String>()
    private var key: ByteArray = ByteArray(0)
    private var iv: ByteArray = ByteArray(16) { 0 }

    init {
        originalUrl = param.url
        uri = URI.create(originalUrl)
    }

    private fun isRedirectUrl(lines: List<String>): Boolean {
        val list = lines.filter { !it.startsWith("#") && it.isNotBlank() }
        return list.isNotEmpty() && list[0].contains(".m3u8", ignoreCase = true)
    }

    private fun parseRedirectUrl(lines: List<String>): String {
        val urls = ArrayList<Pair<Int, String>>()

        for (i in lines.indices) {
            if (lines[i].contains("BANDWIDTH")) {
                val bandwidth = parseBandwidth(lines[i])
                val urlLine = lines.getOrNull(i + 1) ?: continue
                urls.add(Pair(bandwidth.toInt(), urlLine))
            }
        }
        // 选择最高码率下载；若主播放列表没有 BANDWIDTH 行则兜底取第一个非注释行
        val maxBandwidth = urls.maxByOrNull { it.first }
        val maxUrl = maxBandwidth?.second
            ?: lines.first { !it.startsWith("#") && it.isNotBlank() }

        redirectUrl = maxUrl.getFullUrl()
        return redirectUrl
    }

    private fun parseBandwidth(s: String): String {
        val bandwidthRegex = "BANDWIDTH=(\\d+)".toRegex()
        return bandwidthRegex.find(s)?.groupValues?.get(1)?.trim() ?: "0"
    }

    /**
     * 从 #EXT-X-KEY 行解析 IV。
     * m3u8 里的 IV 是 32 位十六进制字符串（16 字节），必须按 hex 解码。
     */
    private fun parseIv(s: String): ByteArray {
        val ivRegex = "IV=0x([0-9A-Fa-f]+)".toRegex()
        ivRegex.find(s)?.groupValues?.get(1)?.let { hex ->
            if (hex.length == 32) {
                iv = hex.hexToBytes()
            }
        }
        return iv
    }

    private fun parseKeyUrl(s: String): String {
        val keyRegex = "URI=\"(.*?)\"".toRegex()
        val keyUrl = keyRegex.find(s)!!.groupValues[1]

        return keyUrl.getFullUrl()
    }

    private fun parseTsUrl(tsUrl: String) {
        tsUrlList.add(tsUrl.getFullUrl())
    }

    private fun String.getFullUrl(): String {
        if (this.contains("http")) return this

        return if (!this.startsWith("/")) {
            val url = redirectUrl.ifEmpty { originalUrl }
            url.substring(0, url.lastIndexOf("/") + 1) + this
        } else {
            "${uri.scheme}://${uri.host}$this"
        }
    }

    /**
     * 拉取 key 文件并作为原始字节返回。
     * AES key 是 16/24/32 字节的二进制数据，不能当作文本读取，否则非 ASCII 字节会被破坏。
     */
    private suspend fun getKeyContent(keyUrl: String, config: DownloadConfig): ByteArray {
        val response = config.request(keyUrl, emptyMap())
        if (!response.isSuccessful || response.body() == null) {
            throw RuntimeException("key request failed: ${response.code()}")
        }
        key = response.body()!!.bytes()
        if (key.size !in listOf(16, 24, 32)) {
            throw RuntimeException("invalid key length: ${key.size}")
        }
        return key
    }

    private suspend fun parseKey(s: String, config: DownloadConfig): ByteArray {
        val keyUrl = parseKeyUrl(s)
        return getKeyContent(keyUrl, config)
    }

    fun getTsUrlList(): List<String> {
        return tsUrlList
    }

    fun getKey(): ByteArray {
        return key
    }

    fun getIv(): ByteArray {
        return iv
    }

    suspend fun parseM3u8(m3u8Url: String, config: DownloadConfig) {
        val response = config.request(m3u8Url, emptyMap())
        try {
            if (!response.isSuccessful || response.body() == null) {
                throw RuntimeException("request failed: ${response.code()}")
            }
            val lines = response.body()!!.byteStream()
                .bufferedReader()
                .readLines()

            // 校验：合法 m3u8 第一行必须是 #EXTM3U；否则可能是站点封锁页/404页/HTML。
            val firstLine = lines.firstOrNull()?.trim() ?: ""
            if (!firstLine.startsWith("#EXTM3U")) {
                throw RuntimeException("not a valid m3u8, first line='$firstLine'")
            }

            if (isRedirectUrl(lines)) {
                parseM3u8(parseRedirectUrl(lines), config)
                return
            }

            lines.forEach { line ->
                if (!line.startsWith("#")) {
                    parseTsUrl(line)
                } else {
                    if (line.contains("#EXT-X-KEY")) {
                        parseKey(line, config)
                        parseIv(line)
                    }
                }
            }

            if (tsUrlList.isEmpty()) {
                throw RuntimeException("m3u8 has no ts segments")
            }
        } finally {
            response.closeQuietly()
        }
    }
}
