package com.example.tuanyingshi.util.source_rule

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Kazumi 规则 JSON → 本项目 [SourceRule] 转换器。
 *
 * Kazumi 规则（KazumiRules 仓库每个 `<name>.json`）字段与本项目模型不同：
 * - 解析用 **XPath**（`searchList`/`searchName`/`searchResult`/`chapterRoads`/`chapterResult`），
 *   或 **API**（`searchApiConfig`/`chapterApiConfig` 的 HTTP + JsonPath）；
 * - 搜索 URL 用 `@keyword` 占位符（非本项目的 `{keyword}`）；
 * - 反爬配置在 `antiCrawlerConfig`。
 *
 * 转换后产出 `engine = Xpath | Api` 的 [SourceRule]，由 [RuleExecutor] 的 XPath/API 分支直接消费。
 * 因 Kazumi 允许 `searchMode` 与 `chapterMode` 不同，故分别写入 [SourceRule.engine] 与
 * [SourceRule.chapterEngine]。
 */
object KazumiRuleConverter {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    /** 转换单条 Kazumi 规则文本；失败抛异常（由上层决定整体成功/失败）。 */
    fun convert(text: String): SourceRule = runCatching { json.decodeFromString<KazumiRuleRaw>(text) }
        .getOrElse { throw IllegalArgumentException("Kazumi 规则解析失败：${it.message}") }
        .toSourceRule()

    /** 转换单条；失败返回 null（用于批量导入时跳过坏规则）。 */
    fun convertOrNull(text: String): SourceRule? = runCatching { convert(text) }.getOrNull()

    /**
     * 转换「规则数组」文本（顶层 JSON 数组），用于订阅拉取的 XPath 源清单
     * （如 `xpath_v0.1_01.json`）。
     * 每条规则固定 id（`xpath_<序号>`）；**不强制 `isBuiltIn`**，由调用方决定归属
     * （订阅拉取的规则应视为非内置，从而可被订阅更新覆盖、随订阅删除而清理）。
     */
    fun convertList(text: String): List<SourceRule> {
        val raws = runCatching { json.decodeFromString<List<KazumiRuleRaw>>(text) }
            .getOrElse { throw IllegalArgumentException("Kazumi 规则数组解析失败：${it.message}") }
        return raws.mapIndexed { i, r ->
            r.toSourceRule().copy(id = "xpath_$i")
        }
    }

    // ───────────────────────── 映射 ─────────────────────────

    private fun KazumiRuleRaw.toSourceRule(): SourceRule {
        // 引擎判定：优先看显式 mode 字段，其次看是否存在对应的 *ApiConfig（真实 Kazumi 规则常省略 mode 而直接带 ApiConfig）。
        val engine = if (searchMode.equals("api", ignoreCase = true) || searchApiConfig != null) RuleEngineKind.Api else RuleEngineKind.Xpath
        val chapterEngine = if (chapterMode.equals("api", ignoreCase = true) || chapterApiConfig != null) RuleEngineKind.Api else RuleEngineKind.Xpath

        val anti = antiCrawlerConfig?.let { ac ->
            AntiCrawlerConfig(
                enabled = ac.enabled,
                captchaType = ac.captchaType,
                captchaImage = ac.captchaImage,
                captchaInput = ac.captchaInput,
                captchaButton = ac.captchaButton,
                captchaDetectType = when (ac.captchaDetectType) {
                    2 -> CaptchaDetectType.REGEX
                    3 -> CaptchaDetectType.XPATH
                    else -> CaptchaDetectType.TEXT
                },
                captchaDetectValue = ac.captchaDetectValue,
                captchaScript = ac.captchaScript,
            )
        } ?: AntiCrawlerConfig()

        val referer = referer.ifBlank { baseURL }

        return SourceRule(
            name = name.takeIf { it.isNotBlank() } ?: "Kazumi源",
            // Kazumi 规则无 icon；UI 列表里图标留空（用文字首字兜底）
            tier = 4,
            search = SourceRule.SearchStep(
                baseUrl = baseURL,
            ),
            xpath = SourceRule.XpathStep(
                searchUrl = searchURL,
                usePost = usePost,
                searchList = searchList,
                searchName = searchName,
                searchResult = searchResult,
                baseUrl = baseURL,
                referer = referer,
                chapterRoads = chapterRoads,
                chapterResult = chapterResult,
            ),
            apiSearch = searchApiConfig?.toApiSearch() ?: SourceRule.ApiSearchStep(),
            apiChapter = chapterApiConfig?.toApiChapter() ?: SourceRule.ApiChapterStep(),
            engine = engine,
            chapterEngine = chapterEngine,
            antiCrawler = anti,
            headers = SourceRule.HeaderStep(
                referer = referer,
                userAgent = userAgent.ifBlank { SourceRule.HeaderStep().userAgent },
            ),
        )
    }

    private fun KazumiApiRequestRaw.bodyString(): String =
        body?.toString() ?: ""

    private fun KazumiApiSearchRaw.toApiSearch() = SourceRule.ApiSearchStep(
        method = request.method.uppercase().takeIf { it.isNotBlank() } ?: "GET",
        url = request.url,
        headers = request.headers,
        query = request.query,
        bodyType = request.bodyType.takeIf { it.isNotBlank() } ?: "none",
        body = request.bodyString(),
        listPath = listPath,
        namePath = namePath,
        sourcePath = sourcePath,
    )

    private fun KazumiApiChapterRaw.toApiChapter() = SourceRule.ApiChapterStep(
        method = request.method.uppercase().takeIf { it.isNotBlank() } ?: "GET",
        url = request.url,
        headers = request.headers,
        query = request.query,
        bodyType = request.bodyType.takeIf { it.isNotBlank() } ?: "none",
        body = request.bodyString(),
        format = format.takeIf { it.isNotBlank() } ?: "nested",
        roadsPath = roadsPath,
        roadNamePath = roadNamePath,
        episodesPath = episodesPath,
        episodeNamePath = episodeNamePath,
        episodeUrlPath = episodeUrlPath,
        episodePageUrl = episodePage?.url ?: "",
        episodePageQuery = episodePage?.query ?: emptyMap(),
        variables = variables,
        roadNamesPath = roadNamesPath,
        roadEpisodesPath = roadEpisodesPath,
        roadSeparator = roadSeparator,
        episodeSeparator = episodeSeparator,
        fieldSeparator = fieldSeparator,
    )

    // ───────────────────────── Kazumi 规则原始模型（仅解析用） ─────────────────────────

    @Serializable
    private data class KazumiRuleRaw(
        val api: String = "",
        val type: String = "",
        val name: String = "",
        val version: String = "",
        val muliSources: Boolean = true,
        val useWebview: Boolean = true,
        val useNativePlayer: Boolean = true,
        val usePost: Boolean = false,
        val useLegacyParser: Boolean = false,
        val adBlocker: Boolean = false,
        val userAgent: String = "",
        val baseURL: String = "",
        val searchURL: String = "",
        val searchList: String = "",
        val searchName: String = "",
        val searchResult: String = "",
        val chapterRoads: String = "",
        val chapterResult: String = "",
        val referer: String = "",
        val searchMode: String = "xpath",
        val chapterMode: String = "xpath",
        val searchApiConfig: KazumiApiSearchRaw? = null,
        val chapterApiConfig: KazumiApiChapterRaw? = null,
        val antiCrawlerConfig: KazumiAntiCrawlerRaw? = null,
    )

    @Serializable
    private data class KazumiApiRequestRaw(
        val method: String = "GET",
        val url: String = "",
        val headers: Map<String, String> = emptyMap(),
        val query: Map<String, String> = emptyMap(),
        val bodyType: String = "none",
        val body: JsonElement? = null,
    )

    @Serializable
    private data class KazumiApiSearchRaw(
        val request: KazumiApiRequestRaw = KazumiApiRequestRaw(),
        val listPath: String = "",
        val namePath: String = "",
        val sourcePath: String = "",
    )

    @Serializable
    private data class KazumiApiChapterRaw(
        val request: KazumiApiRequestRaw = KazumiApiRequestRaw(),
        val format: String = "nested",
        val roadsPath: String = "",
        val roadNamePath: String = "",
        val episodesPath: String = "",
        val episodeNamePath: String = "",
        val episodeUrlPath: String = "",
        val episodePage: KazumiEpisodePageRaw? = null,
        val variables: Map<String, String> = emptyMap(),
        val roadNamesPath: String = "",
        val roadEpisodesPath: String = "",
        val roadSeparator: String = "",
        val episodeSeparator: String = "",
        val fieldSeparator: String = "",
    )

    @Serializable
    private data class KazumiEpisodePageRaw(
        val url: String = "",
        val query: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class KazumiAntiCrawlerRaw(
        val enabled: Boolean = false,
        val captchaType: Int = 1,
        val captchaImage: String = "",
        val captchaInput: String = "",
        val captchaButton: String = "",
        val captchaDetectType: Int = 1,
        val captchaDetectValue: String = "",
        val captchaScript: String = "",
    )
}
