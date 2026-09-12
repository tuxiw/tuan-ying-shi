package com.example.tuanyingshi.util.source_rule

import com.example.tuanyingshi.util.DownloadManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * 数据源订阅导入器。
 *
 * 解析「弹弹 play / 影视订阅」导出的 JSON（[SubscriptionPayload]）：
 * ```
 * { "exportedMediaSourceDataList": { "mediaSources": [ { factoryId, version, arguments:{ name, iconUrl, searchConfig, ... } } ] } }
 * ```
 * 把每个 mediaSource 的 web-selector 配置映射为 [SourceRule]，使其可被 [RuleBasedAnimeSource] 直接使用。
 *
 * 订阅规则以 `sub_<订阅id>_<序号>` 作为稳定 id（序号来自 JSON 数组顺序），便于后续增量更新时去重。
 */
object SubscriptionImporter {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * 拉取并解析订阅 JSON，返回该订阅名下的全部规则（不含 subscriptionId，由调用方回填）。
     * @throws Exception 网络或解析失败时抛出（由上层决定成功/失败展示）。
     */
    suspend fun fetchSubscription(url: String): List<SourceRule> {
        val host = hostOf(url)
        val raw = DownloadManager.getHtml(url, host)
        return parse(raw)
    }

    /** 解析订阅 JSON 文本（与 [fetchSubscription] 同映射逻辑，便于测试 / 本地导入）。 */
    fun parse(text: String): List<SourceRule> {
        val payload = runCatching { json.decodeFromString<SubscriptionPayload>(text) }
            .getOrElse { throw IllegalArgumentException("订阅 JSON 解析失败：${it.message}") }
        val sources = payload.exportedMediaSourceDataList?.mediaSources ?: emptyList()
        return sources.mapIndexed { index, raw -> raw.toSourceRule(index) }
    }

    // ───────────────────────── 映射 ─────────────────────────

    private fun MediaSourceRaw.toSourceRule(index: Int): SourceRule {
        val args = arguments
        val sc = args?.searchConfig

        // 订阅 JSON 里三套 subject 配置与两套 channel 配置通常**同时存在**，
        // 必须按 formatId 选择（与 animeko SelectorSearchConfig.getFormatConfig 一致）。
        val subjectFormatId = sc?.subjectFormatId?.takeIf { it.isNotBlank() } ?: "a"
        val channelFormatId = sc?.channelFormatId?.takeIf { it.isNotBlank() } ?: "no-channel"

        val subjectCfg = when (subjectFormatId) {
            "indexed" -> sc?.selectorSubjectFormatIndexed
            "json-path-indexed" -> sc?.selectorSubjectFormatJsonPathIndexed
            else -> sc?.selectorSubjectFormatA
        }
        // 单标签格式（a）只有一个选择器；其余格式名称与链接各一个
        val titleSelector = when (subjectFormatId) {
            "a" -> subjectCfg?.selectLists ?: ""
            else -> subjectCfg?.selectNames ?: ""
        }
        val linkSelector = when (subjectFormatId) {
            "a" -> subjectCfg?.selectLists ?: ""
            else -> subjectCfg?.selectLinks ?: ""
        }

        val isGrouped = channelFormatId == "index-grouped"
        val chFlat = sc?.selectorChannelFormatFlattened
        val chNo = sc?.selectorChannelFormatNoChannel
        val mv = sc?.matchVideo
        val selectMedia = sc?.selectMedia

        // rawBaseUrl 为空时按 animeko guessBaseUrl 从 searchUrl 猜根（保留 scheme）
        val base = sc?.rawBaseUrl?.takeIf { it.isNotBlank() } ?: guessBaseUrl(sc?.searchUrl.orEmpty())

        return SourceRule(
            id = "sub_${index}",
            name = args?.name?.takeIf { it.isNotBlank() } ?: "订阅源${index + 1}",
            iconUrl = args?.iconUrl ?: "",
            isBuiltIn = false,
            // animeko MediaSourceTier：数字越小越优先，用于聚合结果排序
            tier = args?.tier ?: 4,
            onlySupportsPlayers = sc?.onlySupportsPlayers.orEmpty(),
            search = SourceRule.SearchStep(
                searchUrl = sc?.searchUrl ?: "",
                baseUrl = base,
                useFirstWordOnly = sc?.searchUseOnlyFirstWord ?: false,
                removeSpecialChars = sc?.searchRemoveSpecial ?: false,
                alternateNameCount = (sc?.searchUseSubjectNamesCount ?: 1).coerceAtLeast(1),
                requestIntervalMs = sc?.requestInterval ?: 3000L,
                cacheMinutes = ((sc?.searchCacheTtl ?: 7_200_000L) / 60_000L).toInt(),
                subjectFormatId = subjectFormatId,
                preferShorterName = subjectCfg?.preferShorterName ?: true,
                titleSelector = titleSelector,
                linkSelector = linkSelector,
            ),
            episodes = SourceRule.EpisodesStep(
                channelFormatId = channelFormatId,
                groupByLine = isGrouped,
                lineNameSelector = if (isGrouped) chFlat?.selectChannelNames.orEmpty() else "",
                lineNameRegex = if (isGrouped) chFlat?.matchChannelName.orEmpty() else "",
                episodePanelSelector = if (isGrouped) chFlat?.selectEpisodeLists.orEmpty() else "",
                // no-channel 时剧集列表直接从整页选取，落到 episodeListSelector
                episodeListSelector = if (isGrouped) {
                    chFlat?.selectEpisodesFromList.orEmpty()
                } else {
                    chNo?.selectEpisodes.orEmpty()
                },
                episodeLinkSelector = if (isGrouped) {
                    chFlat?.selectEpisodeLinksFromList.orEmpty()
                } else {
                    chNo?.selectEpisodeLinks.orEmpty()
                },
                episodeNumberRegex = if (isGrouped) {
                    chFlat?.matchEpisodeSortFromName.orEmpty()
                } else {
                    chNo?.matchEpisodeSortFromName.orEmpty()
                },
            ),
            video = SourceRule.VideoStep(
                enableNestedLinks = mv?.enableNestedUrl ?: false,
                nestedLinkRegex = mv?.matchNestedUrl ?: "",
                videoUrlRegex = mv?.matchVideoUrl ?: "",
                cookies = mv?.cookies ?: "",
            ),
            filters = SourceRule.FilterStep(
                // animeko 默认值：filterBySubjectName=true, filterByEpisodeSort=true
                filterByEntryName = sc?.filterBySubjectName ?: true,
                filterByEpisodeNumber = sc?.filterByEpisodeSort ?: true,
            ),
            marks = SourceRule.MarkStep(
                resolution = sc?.defaultResolution?.takeIf { it.isNotBlank() } ?: "1080P",
                subtitleLanguage = when (sc?.defaultSubtitleLanguage) {
                    "CHS" -> "简中"
                    "CHT" -> "繁中"
                    "JPN" -> "日语"
                    "ENG" -> "英语"
                    else -> sc?.defaultSubtitleLanguage?.takeIf { it.isNotBlank() } ?: "简中"
                },
                distinguishEntryNames = selectMedia?.distinguishSubjectName ?: false,
                distinguishLineNames = selectMedia?.distinguishChannelName ?: false,
            ),
            headers = SourceRule.HeaderStep(
                referer = mv?.addHeadersToVideo?.referer ?: "",
                userAgent = mv?.addHeadersToVideo?.userAgent
                    ?.takeIf { it.isNotBlank() }
                    ?: SourceRule.HeaderStep().userAgent,
            ),
        )
    }

    private fun hostOf(url: String): String = runCatching {
        URI(url).host ?: ""
    }.getOrDefault("")

    /** 由站点任意 URL 推根域名（animeko `SelectorSearchConfig.guessBaseUrl`）。 */
    private fun guessBaseUrl(url: String): String {
        if (url.isBlank()) return ""
        return runCatching { URI(url.replace("{keyword}", "")) }
            .getOrNull()
            ?.let { uri ->
                val host = uri.host
                if (uri.scheme.isNullOrBlank() || host.isNullOrBlank()) null else "${uri.scheme}://$host/"
            }
            ?: hostOf(url).let { if (it.isBlank()) "" else "https://$it/" }
    }

    // ───────────────────────── 订阅 JSON 模型（仅解析用，字段按需取舍） ─────────────────────────

    @Serializable
    private data class SubscriptionPayload(
        val exportedMediaSourceDataList: MediaListHolder? = null,
    )

    @Serializable
    private data class MediaListHolder(
        val mediaSources: List<MediaSourceRaw> = emptyList(),
    )

    @Serializable
    private data class MediaSourceRaw(
        val factoryId: String = "",
        val version: Int = 0,
        val arguments: ArgumentsRaw? = null,
    )

    @Serializable
    private data class ArgumentsRaw(
        val name: String = "",
        val description: String = "",
        val iconUrl: String = "",
        val searchConfig: SearchConfigRaw? = null,
        /** animeko MediaSourceTier：0=最高优先，4=Fallback（默认） */
        val tier: Int = 4,
    )

    @Serializable
    private data class SearchConfigRaw(
        val searchUrl: String = "",
        val searchUseOnlyFirstWord: Boolean = false,
        val searchRemoveSpecial: Boolean = false,
        val searchUseSubjectNamesCount: Int = 1,
        val requestInterval: Long = 3000L,
        val searchCacheTtl: Long = 7_200_000L,
        val rawBaseUrl: String = "",
        /** `a` / `indexed` / `json-path-indexed` */
        val subjectFormatId: String = "a",
        val selectorSubjectFormatA: SubjectSelectorRaw? = null,
        val selectorSubjectFormatIndexed: SubjectSelectorRaw? = null,
        val selectorSubjectFormatJsonPathIndexed: SubjectSelectorRaw? = null,
        /** `index-grouped` / `no-channel` */
        val channelFormatId: String = "no-channel",
        val selectorChannelFormatFlattened: ChannelFlattened? = null,
        val selectorChannelFormatNoChannel: ChannelNo? = null,
        val defaultResolution: String = "1080P",
        val defaultSubtitleLanguage: String = "CHS",
        /** `mpv` / `vlc` / `exoplayer` / `avkit`；为空表示不限 */
        val onlySupportsPlayers: List<String> = emptyList(),
        val filterBySubjectName: Boolean = true,
        val filterByEpisodeSort: Boolean = true,
        val selectMedia: SelectMediaRaw? = null,
        val matchVideo: MatchVideoRaw? = null,
    )

    /**
     * 三种 subject 格式的配置。订阅 JSON 只会给出当前 [SearchConfigRaw.subjectFormatId]
     * 对应的那一份，缺失字段按默认值留空。
     */
    @Serializable
    private data class SubjectSelectorRaw(
        /** `a` 格式：直接选出 `<a>` */
        val selectLists: String = "",
        /** `indexed` / `json-path-indexed`：名称与链接分开取 */
        val selectNames: String = "",
        val selectLinks: String = "",
        val preferShorterName: Boolean = true,
    )

    @Serializable
    private data class SelectMediaRaw(
        val distinguishSubjectName: Boolean = true,
        val distinguishChannelName: Boolean = true,
    )

    @Serializable
    private data class ChannelFlattened(
        val selectChannelNames: String = "",
        val matchChannelName: String = "",
        val selectEpisodeLists: String = "",
        val selectEpisodesFromList: String = "",
        val selectEpisodeLinksFromList: String = "",
        val matchEpisodeSortFromName: String = "",
    )

    @Serializable
    private data class ChannelNo(
        val selectEpisodes: String = "",
        val selectEpisodeLinks: String = "",
        val matchEpisodeSortFromName: String = "",
    )

    @Serializable
    private data class MatchVideoRaw(
        val enableNestedUrl: Boolean = false,
        val matchNestedUrl: String = "",
        val matchVideoUrl: String = "",
        val cookies: String = "",
        val addHeadersToVideo: VideoHeadersRaw? = null,
    )

    @Serializable
    private data class VideoHeadersRaw(
        val referer: String = "",
        val userAgent: String = "",
    )
}
