package com.example.tuanyingshi.util.source_rule

import com.example.tuanyingshi.util.DownloadManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * KazumiRules 订阅导入器。
 *
 * 与「弹弹 play 导出格式」不同，KazumiRules 仓库结构是：
 * - `index.json`：官方站点目录 `[{ name, version, antiCrawlerEnabled, deprecated, ... }]`（仅收录活跃规则）；
 * - 每个站点一个 `<name>.json`，即一条 Kazumi 规则；
 * - 仓库内实际还有更多**未进目录**的社区 / 旧版规则文件。
 *
 * 本导入器：
 * 1. 读 `index.json` 取得 deprecated 集合（跳过已停用规则）；
 * 2. 用 jsDelivr 数据 API 枚举仓库内**全部**规则 `.json` 文件，最大化源数量
 *    （官方目录通常只列活跃项，仓库实际有 50+ 条规则文件）；
 * 3. 逐个下载并经 [KazumiRuleConverter] 转成 [SourceRule]，转换失败静默丢弃。
 *
 * 推荐订阅地址（官方仓库 `Predidit/KazumiRules@main`，经 jsDelivr 镜像直连）：
 * `https://cdn.jsdelivr.net/gh/Predidit/KazumiRules@main/index.json`
 */
object KazumiRulesImporter {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    /** 拉取并解析一个 KazumiRules 订阅（index.json 地址），返回该订阅名下的全部规则。 */
    suspend fun fetchSubscription(indexUrl: String): List<SourceRule> {
        val baseDir = indexUrl.substringBeforeLast("index.json")

        // 1) index.json：官方目录 + deprecated 集合
        val deprecated = mutableSetOf<String>()
        runCatching {
            val indexText = DownloadManager.getHtml(indexUrl, hostOf(indexUrl))
            json.decodeFromString<List<KazumiIndexEntry>>(indexText)
                .forEach { if (it.deprecated) deprecated.add(it.name) }
        }

        // 2) 枚举仓库内全部规则文件（优先）；失败则回退到 index 里列出的 name
        val names = enumerateRuleNames(baseDir)
            ?: runCatching {
                json.decodeFromString<List<KazumiIndexEntry>>(
                    DownloadManager.getHtml(indexUrl, hostOf(indexUrl))
                ).map { it.name }
            }.getOrDefault(emptyList())

        return names
            .filter { it.isNotBlank() && !deprecated.contains(it) }
            .distinct()
            .mapNotNull { name ->
                runCatching {
                    val text = DownloadManager.getHtml("$baseDir$name.json", hostOf("$baseDir$name.json"))
                    KazumiRuleConverter.convertOrNull(text)
                }.getOrNull()
            }
    }

    /**
     * 通过 jsDelivr 数据 API 枚举仓库内全部规则文件（扁平结构）。
     * @return 规则名列表（不含 `.json` 扩展名）；失败返回 null。
     */
    private suspend fun enumerateRuleNames(baseDir: String): List<String>? {
        // baseDir: https://cdn.jsdelivr.net/gh/OWNER/REPO@ref/
        val dataUrl = baseDir
            .replace("https://cdn.jsdelivr.net/gh/", "https://data.jsdelivr.com/v1/packages/gh/")
            .removeSuffix("/") + "?structure=flat"
        return runCatching {
            val text = DownloadManager.getHtml(dataUrl, "data.jsdelivr.com")
            val tree = json.decodeFromString<JsdelivrFlatTree>(text)
            tree.files
                .map { it.name }
                .filter { it.endsWith(".json") }
                .map { it.removePrefix("/").removeSuffix(".json") }
                .filter { it != "index" }
        }.getOrNull()
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host ?: "" }.getOrDefault("")

    @Serializable
    private data class KazumiIndexEntry(
        val name: String = "",
        val deprecated: Boolean = false,
    )

    @Serializable
    private data class JsdelivrFile(val name: String = "")

    @Serializable
    private data class JsdelivrFlatTree(val files: List<JsdelivrFile> = emptyList())
}
