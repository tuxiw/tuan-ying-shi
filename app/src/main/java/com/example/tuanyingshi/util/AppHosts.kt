package com.example.tuanyingshi.util

import android.util.Log
import okhttp3.Dns
import java.net.InetAddress

/**
 * 全局 DNS 覆盖（Hosts 优化），移植并泛化自 Han1meViewer 的 `HDns` 内置 / 自定义 Hosts 机制。
 *
 * 在 OkHttp 网络层把指定域名解析到写死的可靠 IP 或用户自定义 IP，用于绕开 DNS 污染 / 直连超时。
 * 通过 [com.example.tuanyingshi.util.ProxyHolder.applyProxy] 统一挂载到所有 OkHttp 客户端，
 * 因此是“网络设置”级别的全局能力，而非仅针对 Hanime1。
 *
 * 解析优先级（命中其一即返回，不再走系统 DNS）：
 *   1. 自定义 Hosts（用户填写，实时生效，**独立于**内置开关）；
 *   2. 内置 Hosts（[AppHostsPrefs.useBuiltInHosts] 开启时，按 Han1meViewer 的内置 IP 表；默认值关闭）；
 *   3. 否则回落系统 DNS。
 *
 * 内置 Hosts 表（参考 Han1meViewer 的 cloudFlareIps / getchuIps / GitHubDns）：
 *   - hanime1.me / hanime1.com / hanimeone.me / javchu.com → Cloudflare 边缘 IP
 *   - www.getchu.com → getchu 固定 IP
 *   - github.com / api.github.com → GitHub 固定 IP
 *
 * 注意：CDN 与 Cloudflare 验证域（challenges.cloudflare.com 等）不在内置表内，仍走系统 DNS，
 * 避免把 CF 验证逻辑绑死到固定 IP（那会导致 cf_clearance 拿不到、过盾失败）。
 */
object AppHosts : Dns {

    private const val TAG = "AppHosts"

    /** 内置 Cloudflare 边缘 IP（对齐 Han1meViewer `cloudFlareIps`）。 */
    private val CLOUDFLARE_IPS = listOf(
        "172.64.229.154", "162.159.0.1", "108.162.192.1", "172.64.33.1", "104.19.0.1",
        "2606:4700:3035::ac43:bb8d", "2606:4700:3030::6815:746", "2606:4700:3030::6815:714"
    )

    /** 内置 getchu 固定 IP（对齐 Han1meViewer `getchuIps`）。 */
    private val GETCHU_IPS = listOf("210.155.150.166", "210.155.150.145")

    /** 内置 GitHub 固定 IP（对齐 Han1meViewer `GitHubDns`）。 */
    private val GITHUB_IPS = listOf(
        "20.205.243.166", "140.82.121.3", "140.82.116.4", "140.82.121.4"
    )
    private val GITHUB_API_IPS = listOf(
        "140.82.116.6", "20.205.243.168", "140.82.121.6"
    )

    /**
     * 内置 Hosts 表：域名（小写）→ 固定 IP 列表。
     * 仅当 [AppHostsPrefs.useBuiltInHosts] 开启时生效，且会被自定义 Hosts 覆盖。
     */
    private val BUILT_IN_MAP: Map<String, List<String>> = mapOf(
        "hanime1.me" to CLOUDFLARE_IPS,
        "hanime1.com" to CLOUDFLARE_IPS,
        "hanimeone.me" to CLOUDFLARE_IPS,
        "javchu.com" to CLOUDFLARE_IPS,
        "www.getchu.com" to GETCHU_IPS,
        "github.com" to GITHUB_IPS,
        "api.github.com" to GITHUB_API_IPS,
    )

    // ───────────────────────── 自定义 Hosts（通用 hosts 文件格式） ─────────────────────────

    /**
     * 解析自定义 Hosts 文本（类 /etc/hosts）：
     * - 以换行分隔各行，空行与 `#` 注释忽略；
     * - 每行取空白分隔的 token，其中“合法 IP”作为地址，其余作为域名；
     * - 支持 `域名 IP` 或 `IP 域名` 两种写法，也支持 `域名 IP1 IP2` 多地址。
     * 返回 域名（小写）→ IP 列表（去重、保序）。
     */
    fun parseHostsFile(raw: String): Map<String, List<String>> {
        val result = LinkedHashMap<String, MutableList<String>>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val tokens = trimmed.split(Regex("\\s+"))
            val ips = tokens.filter { isValidIpAddress(it) }
            val hosts = tokens.filter { !isValidIpAddress(it) }
            if (ips.isEmpty() || hosts.isEmpty()) return@forEach
            hosts.forEach { h ->
                val key = h.lowercase()
                val list = result.getOrPut(key) { mutableListOf() }
                ips.forEach { if (it !in list) list.add(it) }
            }
        }
        return result
    }

    /**
     * 校验自定义 Hosts 文本，返回逐行错误信息（空列表表示全部有效）。
     * 每行必须至少含一个合法 IP 与一个域名；纯注释 / 空行不算错误。
     */
    fun validateHostsFile(raw: String): List<String> {
        val errors = mutableListOf<String>()
        raw.lineSequence().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed
            val tokens = trimmed.split(Regex("\\s+"))
            val ips = tokens.filter { isValidIpAddress(it) }
            val hosts = tokens.filter { !isValidIpAddress(it) }
            val lineNo = index + 1
            when {
                ips.isEmpty() -> errors.add("第 $lineNo 行：缺少合法 IP 地址")
                hosts.isEmpty() -> errors.add("第 $lineNo 行：缺少域名")
            }
        }
        return errors
    }

    private fun isValidIpAddress(ip: String): Boolean = runCatching {
        val addr = InetAddress.getByName(ip)
        addr.hostAddress == ip || addr.hostAddress == ip.removePrefix("[").removeSuffix("]")
    }.getOrDefault(false)

    /**
     * 自定义 Hosts 解析结果（实时读取偏好，raw 未变则复用缓存）。
     * 返回 域名（小写）→ IP 列表；空表示未配置。
     */
    private fun customHostsMap(): Map<String, List<String>> {
        val raw = AppHostsPrefs.customHostsData
        if (raw.isBlank()) return emptyMap()
        if (raw == cachedRaw && cachedMap != null) return cachedMap!!
        val map = parseHostsFile(raw)
        cachedRaw = raw
        cachedMap = map
        return map
    }

    @Volatile
    private var cachedRaw: String? = null

    @Volatile
    private var cachedMap: Map<String, List<String>>? = null

    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.lowercase()

        // 1. 自定义 Hosts（始终生效，独立于内置开关）
        val custom = customHostsMap()[host]
        if (!custom.isNullOrEmpty()) {
            Log.i(TAG, "lookup $hostname -> 自定义 Hosts: $custom")
            return custom.map { InetAddress.getByAddress(hostname, InetAddress.getByName(it).address) }
        }

        // 2. 内置 Hosts（需开启开关）
        if (AppHostsPrefs.useBuiltInHosts) {
            val builtIn = BUILT_IN_MAP[host]
            if (!builtIn.isNullOrEmpty()) {
                Log.i(TAG, "lookup $hostname -> 内置 Hosts IPs")
                return builtIn.map { InetAddress.getByAddress(hostname, InetAddress.getByName(it).address) }
            }
        }

        // 3. 系统 DNS
        return Dns.SYSTEM.lookup(hostname)
    }

    /** 给诊断 / 设置页用：列出当前会命中自定义 / 内置 Hosts 的 IP（无则空）。 */
    fun getEffectiveIps(host: String): List<String> {
        val h = host.lowercase()
        val custom = customHostsMap()[h]
        if (!custom.isNullOrEmpty()) return custom.distinct()
        if (AppHostsPrefs.useBuiltInHosts) {
            val builtIn = BUILT_IN_MAP[h]
            if (!builtIn.isNullOrEmpty()) return builtIn.distinct()
        }
        return emptyList()
    }

    /** 内置 Hosts 覆盖的域名列表（供设置页展示）。 */
    fun builtInDomains(): List<String> = BUILT_IN_MAP.keys.toList()
}
