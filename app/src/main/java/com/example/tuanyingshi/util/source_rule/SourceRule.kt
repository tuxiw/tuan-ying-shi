package com.example.tuanyingshi.util.source_rule

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 视频匹配策略。
 * - INTERCEPT：WebView 拦截 / 正则从页面资源中匹配视频直链（通用站点）；
 * - SILISILI_POST：嘶哩嘶哩式「POST 表单 + MD5 + AES 解密」获取 m3u8；
 * - GIRIGIRI_B64：Girigiri 式「脚本内 Base64 编码直链」解码。
 */
@Serializable
enum class VideoMode {
    INTERCEPT,
    SILISILI_POST,
    GIRIGIRI_B64,
}

/**
 * 规则执行引擎种类。
 * - [Css]：animeko 风格 CSS 选择器（本项目原生，[SearchStep]/[EpisodesStep] 的 CSS 字段）；
 * - [Xpath]：Kazumi 风格 XPath（[XpathStep]）；
 * - [Api]：Kazumi 风格 HTTP+JSON（[ApiSearchStep]/[ApiChapterStep]）。
 *
 * 搜索与剧集可分别指定引擎（Kazumi 允许 `searchMode` 与 `chapterMode` 不同），
 * 故除 [engine]（搜索用）外另设 [SourceRule.chapterEngine]（剧集用）。
 * 默认 [Css] 以兼容既有 animeko 订阅源。
 */
@Serializable
enum class RuleEngineKind {
    Css,
    Xpath,
    Api,
}

/**
 * 条目（搜索结果）解析格式。对应 animeko `SelectorSubjectFormat`。
 *
 * 由 [SourceRule.SearchStep.subjectFormatId] 选择，与订阅 JSON 的 `subjectFormatId` 完全一致。
 */
@Serializable
enum class SubjectFormat(val id: String) {
    /** 单个 `<a>` 同时给出名称（title 属性或文本）与链接。 */
    A("a"),

    /** 两个选择器分别选出名称列表与链接列表，按索引一一对应。 */
    Indexed("indexed"),

    /** 搜索接口返回 JSON，用 JsonPath 分别取名称与链接。 */
    JsonPathIndexed("json-path-indexed"),
    ;

    companion object {
        /**
         * 解析格式 id。为兼容旧规则，[raw] 为空或无法识别时回退到
         * [fallback]（旧 [SourceRule.SearchStep.ParserType]）推导。
         */
        fun from(raw: String, fallback: SourceRule.SearchStep.ParserType): SubjectFormat =
            entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: when (fallback) {
                SourceRule.SearchStep.ParserType.SingleTag -> A
                SourceRule.SearchStep.ParserType.MultiTag -> Indexed
                SourceRule.SearchStep.ParserType.JsonPath -> JsonPathIndexed
            }
    }
}

/**
 * 线路 / 剧集解析格式。对应 animeko `SelectorChannelFormat`。
 *
 * 由 [SourceRule.EpisodesStep.channelFormatId] 选择，与订阅 JSON 的 `channelFormatId` 完全一致。
 * 注意：订阅 JSON 里 `index-grouped` 与 `no-channel` 两套配置**总是同时存在**，
 * 必须按 id 选择，不能靠「哪套配置存在」来判断。
 */
@Serializable
enum class ChannelFormat(val id: String) {
    /** tab + 面板形式：线路名称列表与剧集面板列表按索引对应。 */
    IndexGrouped("index-grouped"),

    /** 无线路（或视作只有一个线路）：直接在整页上选剧集列表。 */
    NoChannel("no-channel"),
    ;

    companion object {
        fun from(raw: String, groupByLine: Boolean): ChannelFormat =
            entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
                ?: if (groupByLine) IndexGrouped else NoChannel
    }
}

/**
 * 资源源规则配置。
 *
 * 参考弹弹播放器等应用的「数据源」配置，把整个抓取流程拆为三步：
 * 1. 搜索条目：用关键词搜出候选番剧；
 * 2. 搜索剧集：进入条目详情页，提取剧集（支持多线路分组）；
 * 3. 匹配视频：进入剧集播放页，按 [VideoMode] 获取真实视频 URL。
 *
 * 规则以 JSON 持久化，用户可自由新增 / 编辑 / 删除；内置规则可编辑但不可删除。
 */
@Serializable
data class SourceRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val iconUrl: String = "",
    val isBuiltIn: Boolean = false,
    /** 来自哪个订阅（[com.example.tuanyingshi.util.source_rule.SourceSubscription.id]）；空字符串表示用户独立创建的规则。 */
    val subscriptionId: String = "",

    /**
     * 数据源优先级（animeko `MediaSourceTier`）。数字越小越优先，用于聚合搜索结果的排序。
     * 订阅里的 `tier` 字段即此值。
     */
    val tier: Int = 4,

    /**
     * 仅在这些播放器上可用（`mpv` / `vlc` / `exoplayer` / `avkit`，animeko `onlySupportsPlayers`）。
     * 为空表示不限。本项目运行在 Android（ExoPlayer），若列表非空且不含 `exoplayer` 则该源会被跳过。
     */
    val onlySupportsPlayers: List<String> = emptyList(),

    /** 步骤 1：搜索条目 */
    val search: SearchStep = SearchStep(),

    /** 步骤 2：搜索剧集 */
    val episodes: EpisodesStep = EpisodesStep(),

    /** 步骤 3：匹配视频 */
    val video: VideoStep = VideoStep(),

    /** 详情页解析（标题 / 封面 / 简介 / 标签），规则化替代硬编码解析器 */
    val detail: DetailStep = DetailStep(),

    /** 过滤设置 */
    val filters: FilterStep = FilterStep(),

    /** 标记与播放器去重选项 */
    val marks: MarkStep = MarkStep(),

    /** 播放时附加的请求头 */
    val headers: HeaderStep = HeaderStep(),

    /**
     * 解析引擎种类（搜索用）。默认 [RuleEngineKind.Css] 兼容既有 animeko 订阅源；
     * 接入 KazumiRules 时为 [RuleEngineKind.Xpath] 或 [RuleEngineKind.Api]。
     */
    val engine: RuleEngineKind = RuleEngineKind.Css,

    /**
     * 解析引擎种类（剧集用）。Kazumi 允许 `searchMode` 与 `chapterMode` 不同，
     * 故剧集单独指定；缺省回退到 [engine]。
     */
    val chapterEngine: RuleEngineKind = RuleEngineKind.Css,

    /** Kazumi 风格 XPath 步骤（[engine]/[chapterEngine] 为 [RuleEngineKind.Xpath] 时生效）。 */
    val xpath: XpathStep = XpathStep(),

    /** Kazumi 风格 API 搜索步骤（[engine] 为 [RuleEngineKind.Api] 时生效）。 */
    val apiSearch: ApiSearchStep = ApiSearchStep(),

    /** Kazumi 风格 API 剧集步骤（[chapterEngine] 为 [RuleEngineKind.Api] 时生效）。 */
    val apiChapter: ApiChapterStep = ApiChapterStep(),

    /**
     * 反反爬虫验证配置（对齐 Kazumi `antiCrawlerConfig`）。
     * 默认空（无需验证）；接入 KazumiRules 时按规则里的 `antiCrawlerConfig` 映射而来。
     */
    val antiCrawler: AntiCrawlerConfig = AntiCrawlerConfig(),
) {
    /**
     * 步骤 1：搜索条目
     *
     * @param searchUrl 搜索链接模板，需包含 `{keyword}` 占位符
     * @param baseUrl 可选，用于拼接详情页 URL；留空则默认从搜索链接根路径生成
     * @param useFirstWordOnly 以空格分割，仅用第一个词搜索（兼容性差的站点）
     * @param removeSpecialChars 去除特殊字符以及「电影」等字样
     * @param alternateNameCount 每次播放用多少个条目名称进行查询；1=只用主名称，2=额外用日文原名，>2=再用其他别名
     * @param requestIntervalMs 搜索请求间隔毫秒
     * @param cacheMinutes 搜索缓存有效期分钟，0=不缓存
     * @param subjectFormatId 条目解析格式 id（[SubjectFormat]），留空时回退到 [parserType]
     * @param preferShorterName 搜索结果按名称长度升序，优先更短（更精确）的标题
     * @param parserType 名称/链接列表解析方式（旧字段，仅作 [subjectFormatId] 缺失时的回退）
     * @param titleSelector CSS Selector 或 JsonPath，选取条目名称列表
     * @param linkSelector CSS Selector 或 JsonPath，选取条目链接列表；与 titleSelector 按顺序一一对应
     */
    @Serializable
    data class SearchStep(
        val searchUrl: String = "",
        val baseUrl: String = "",
        val useFirstWordOnly: Boolean = false,
        val removeSpecialChars: Boolean = false,
        val alternateNameCount: Int = 1,
        val requestIntervalMs: Long = 3000L,
        val cacheMinutes: Int = 120,
        val subjectFormatId: String = "",
        val preferShorterName: Boolean = true,
        val parserType: ParserType = ParserType.MultiTag,
        val titleSelector: String = "",
        val linkSelector: String = "",
    ) {
        @Serializable
        enum class ParserType {
            SingleTag,   // 单标签：名称和链接在同一元素
            MultiTag,    // 多标签：分别选取名称列表和链接列表
            JsonPath,    // JsonPath：从 JSON 接口解析
        }

        /** 实际生效的条目解析格式。 */
        fun format(): SubjectFormat = SubjectFormat.from(subjectFormatId, parserType)
    }

    /**
     * 步骤 2：搜索剧集
     *
     * 字段与 animeko `SelectorChannelFormat` 的映射：
     * - `index-grouped`：[lineNameSelector]=`selectChannelNames`，[lineNameRegex]=`matchChannelName`，
     *   [episodePanelSelector]=`selectEpisodeLists`，[episodeListSelector]=`selectEpisodesFromList`，
     *   [episodeLinkSelector]=`selectEpisodeLinksFromList`；
     * - `no-channel`：[episodeListSelector]=`selectEpisodes`，[episodeLinkSelector]=`selectEpisodeLinks`，
     *   剧集直接从整页选取（[episodePanelSelector] 不参与）。
     *
     * @param preferShortestTitle 优先选择满足匹配的最短标题条目，避免第一季匹配到第二季
     * @param channelFormatId 线路解析格式 id（[ChannelFormat]），留空时回退到 [groupByLine]
     * @param groupByLine 是否按线路分组（旧字段，仅作 [channelFormatId] 缺失时的回退）
     * @param lineNameSelector 线路名称列表 CSS Selector
     * @param lineNameRegex 从线路元素 text 中提取线路名称的正则，留空用整个 text；期望命名分组 `ch`。
     *   注意：配置了该正则但匹配不到时，该线路会被整体丢弃（与 animeko 一致）。
     * @param episodePanelSelector 剧集面板列表 CSS Selector，每个面板对应一个线路
     * @param episodeListSelector 从每个剧场面板中提取剧集列表的 CSS Selector
     * @param episodeLinkSelector 可选，当剧集元素不是 `<a>` 时，用此项提取链接
     * @param episodeNumberRegex 从剧集名称中提取序号的正则，期望命名分组 `ep`
     */
    @Serializable
    data class EpisodesStep(
        val preferShortestTitle: Boolean = false,
        val channelFormatId: String = "",
        val groupByLine: Boolean = true,
        val lineNameSelector: String = "",
        val lineNameRegex: String = "",
        val episodePanelSelector: String = "",
        val episodeListSelector: String = "",
        val episodeLinkSelector: String = "",
        val episodeNumberRegex: String = "",
    ) {
        /** 实际生效的线路解析格式。 */
        fun format(): ChannelFormat = ChannelFormat.from(channelFormatId, groupByLine)
    }

    /**
     * 步骤 3：匹配视频
     *
     * @param enableNestedLinks 启用嵌套链接：遇到匹配链接时终止父页面加载并跳转，在嵌套页面继续查找
     * @param nestedLinkRegex 需要跳转进入的嵌套链接正则；若包含命名分组 `v`，使用该分组作为 URL，否则用整个匹配
     * @param videoUrlRegex 匹配真实视频 URL 的正则；若包含命名分组 `v`，使用该分组，否则用整个匹配
     * @param cookies 附加的 Cookie，key=value 一行一个
     */
    @Serializable
    data class VideoStep(
        /** 视频匹配策略（见 [VideoMode]）。默认 INTERCEPT 通用正则拦截。 */
        val videoMode: VideoMode = VideoMode.INTERCEPT,
        /** SILISILI_POST 用：POST 表单字段名（值固定为 "sili"）。 */
        val silisiliFormField: String = "player",
        val enableNestedLinks: Boolean = false,
        val nestedLinkRegex: String = "",
        val videoUrlRegex: String = "",
        val cookies: String = "",
    )

    /** 详情页解析（标题 / 封面 / 简介 / 标签），用于规则化替代硬编码解析器。 */
    @Serializable
    data class DetailStep(
        val titleSelector: String = "",
        val imgSelector: String = "",
        /** 封面图属性名：默认 src；Girigiri 等用 data-src。 */
        val imgAttr: String = "src",
        val descSelector: String = "",
        val tagSelector: String = "",
    )

    /** 过滤设置 */
    @Serializable
    data class FilterStep(
        val filterByEntryName: Boolean = false,
        val filterByEpisodeNumber: Boolean = false,
    )

    /**
     * 标记与播放器内去重
     *
     * @param resolution 资源分辨率标记（仅做偏好/过滤，不影响抓取）
     * @param subtitleLanguage 字幕语言标记
     * @param distinguishEntryNames 播放器选源时是否区分条目名称（false=相同标题剧集去重）
     * @param distinguishLineNames 播放器选源时是否区分线路名称
     */
    @Serializable
    data class MarkStep(
        val resolution: String = "1080P",
        val subtitleLanguage: String = "简中",
        val distinguishEntryNames: Boolean = false,
        val distinguishLineNames: Boolean = false,
    )

    /** 播放时附加的 HTTP 头 */
    @Serializable
    data class HeaderStep(
        val referer: String = "",
        val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    )

    /**
     * Kazumi 风格 XPath 解析步骤（对应 Kazumi `Plugin` 的 `search*` / `chapter*` 字段）。
     * 搜索 URL 用 `@keyword` 占位符（非 `{keyword}`）；剧集相对 URL 以 [baseUrl] 补成绝对地址。
     */
    @Serializable
    data class XpathStep(
        /** 搜索链接模板，含 `@keyword` 占位符（Kazumi 约定）。 */
        val searchUrl: String = "",
        /** 搜索请求是否用 POST（表单）。GET 时 [searchUrl] 直接带 query。 */
        val usePost: Boolean = false,
        /** 搜索结果节点列表 XPath。 */
        val searchList: String = "",
        /** 相对每个结果节点取名称的 XPath（取 text）。 */
        val searchName: String = "",
        /** 相对每个结果节点取详情页链接的 XPath（取 `href` 属性）。 */
        val searchResult: String = "",
        /** 站点根，用于把相对链接补成绝对地址、以及剧集 URL 归一化。 */
        val baseUrl: String = "",
        /** 附加 Referer。 */
        val referer: String = "",
        /** 线路（road）节点列表 XPath；为空表示无线路，剧集直接取自整页。 */
        val chapterRoads: String = "",
        /** 相对线路节点（或无线路时相对整页）取剧集 `<a>` 的 XPath（取 `href` + text）。 */
        val chapterResult: String = "",
    )

    /**
     * Kazumi 风格 API 搜索步骤（对应 `searchApiConfig`）。
     * 请求 URL / query / body 中的 `@keyword` 会在运行时被搜索词替换。
     */
    @Serializable
    data class ApiSearchStep(
        val method: String = "GET",
        val url: String = "",
        val headers: Map<String, String> = emptyMap(),
        val query: Map<String, String> = emptyMap(),
        /** `none` / `json` / `form`。 */
        val bodyType: String = "none",
        /** 请求体原文（json 时为 JSON 字符串；form 时为键值对象 JSON 字符串）。 */
        val body: String = "",
        /** JSONPath：命中列表节点。 */
        val listPath: String = "",
        /** JSONPath：从列表项取名称。 */
        val namePath: String = "",
        /** JSONPath：从列表项取来源标识（用于拼装剧集请求 `@source`）。 */
        val sourcePath: String = "",
    )

    /**
     * Kazumi 风格 API 剧集步骤（对应 `chapterApiConfig`）。
     * 请求 URL / query / body 中的 `@source` 会在运行时被搜索来源标识替换；
     * [episodePageUrl] 模板还支持 `@episodeUrl` / `@roadIndex` / `@episodeIndex` / `@slug`。
     */
    @Serializable
    data class ApiChapterStep(
        val method: String = "GET",
        val url: String = "",
        val headers: Map<String, String> = emptyMap(),
        val query: Map<String, String> = emptyMap(),
        val bodyType: String = "none",
        val body: String = "",
        /** `nested` / `delimited`。 */
        val format: String = "nested",
        /** nested：线路列表 JSONPath。 */
        val roadsPath: String = "",
        /** 线路名称 JSONPath（相对线路节点）。 */
        val roadNamePath: String = "",
        /** 剧集列表 JSONPath（相对线路节点）。 */
        val episodesPath: String = "",
        /** 剧集名称 JSONPath（相对剧集节点）。 */
        val episodeNamePath: String = "",
        /** 剧集播放地址 JSONPath（相对剧集节点）；为空时用 [episodePageUrl] 拼装。 */
        val episodeUrlPath: String = "",
        /** 最终播放页模板（含 `@source`/`@episodeUrl`/`@roadIndex`/`@episodeIndex`/`@slug`）。 */
        val episodePageUrl: String = "",
        val episodePageQuery: Map<String, String> = emptyMap(),
        /** 命名 JSONPath 捕获，作为模板变量（如 `slug: $.data.slug`）。 */
        val variables: Map<String, String> = emptyMap(),
        // ── delimited 分隔符格式（少数站点用） ──
        val roadNamesPath: String = "",
        val roadEpisodesPath: String = "",
        val roadSeparator: String = "",
        val episodeSeparator: String = "",
        val fieldSeparator: String = "",
    )
}
