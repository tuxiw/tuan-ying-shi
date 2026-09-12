package com.example.tuanyingshi.util.source_rule

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.seimicrawler.xpath.JXDocument

/**
 * CSS 数据源「测试分析工具」的领域模型与执行引擎（仅调试模式入口调用）。
 *
 * 与 [SourceEditorViewModel.runTest] 的三步骤"能否跑通"验证不同，本工具做**详情检查**：
 * 逐项输出每类配置/抓取/解析的命中数、样本、原始 HTML 片段，便于定位"为什么这条规则不工作"。
 *
 * 对齐 animeko 的 `SelectorMediaSourceTester` 思路——把"网络可达性""选择器命中""字段提取"
 * 拆成独立可检查的维度，而非一次性给个成功/失败。区别在于本工具为每个维度给出可量化指标。
 */
object SourceAnalysisEngine {

    /** 单项检查结论。 */
    enum class CheckStatus {
        /** 通过。 */
        PASS,
        /** 可疑 / 非致命（用了兜底值、匹配率低等），建议优化。 */
        WARN,
        /** 失败 / 致命（选择器零命中、被 WAF 拦截、链接非法）。 */
        FAIL,
        /** 跳过（前置步骤无数据，如没有搜索结果则剧集检查整体跳过）。 */
        SKIP,
    }

    /** 一条检查项。 */
    data class AnalysisItem(
        val label: String,
        val status: CheckStatus,
        val detail: String,
    )

    /** 一组同类检查（如"搜索解析"）。 */
    data class AnalysisSection(
        val title: String,
        val items: List<AnalysisItem>,
    )

    /** 完整分析报告。 */
    data class SourceAnalysisReport(
        val ruleName: String,
        val keyword: String,
        val sections: List<AnalysisSection>,
        val overall: CheckStatus,
        val summary: String,
    )

    /** 运行一次完整分析。 */
    suspend fun analyze(rule: SourceRule, keyword: String): SourceAnalysisReport {
        val sections = mutableListOf<AnalysisSection>()
        sections += configSection(rule)

        val net = networkSection(rule, keyword)
        sections += net

        val search = searchSection(rule, keyword)
        sections += search.first

        val searchEntryUrl = search.second
        val episode = if (searchEntryUrl != null) {
            episodeSection(rule, searchEntryUrl)
        } else {
            AnalysisSection("剧集解析（需先有搜索结果）", listOf(
                AnalysisItem("整体", CheckStatus.SKIP, "搜索未解析出任何条目，剧集解析跳过。请先修复搜索步骤。")
            )) to null
        }
        sections += episode.first
        val episodeUrl = episode.second
        sections += if (episodeUrl != null) {
            videoSection(rule, episodeUrl)
        } else {
            AnalysisSection("视频匹配（需先有剧集）", listOf(
                AnalysisItem("整体", CheckStatus.SKIP, "未解析出剧集，视频匹配跳过。请先修复剧集步骤。")
            ))
        }

        val overall = computeOverall(sections)
        return SourceAnalysisReport(
            ruleName = rule.name.ifBlank { "(未命名规则)" },
            keyword = keyword,
            sections = sections,
            overall = overall,
            summary = buildSummary(overall, sections),
        )
    }

    // ───────────────────────── 0. 配置完整性（离线） ─────────────────────────

    private fun configSection(rule: SourceRule): AnalysisSection {
        val items = mutableListOf<AnalysisItem>()

        // 名称
        items += if (rule.name.isBlank()) {
            AnalysisItem("规则名称", CheckStatus.WARN, "未填写名称，保存后不影响运行但难以在列表里辨识。")
        } else {
            AnalysisItem("规则名称", CheckStatus.PASS, rule.name)
        }

        // 播放器兼容
        items += when {
            rule.onlySupportsPlayers.isEmpty() ->
                AnalysisItem("播放器兼容", CheckStatus.PASS, "未限制播放器（不限）。")
            rule.onlySupportsPlayers.any { it.equals("exoplayer", ignoreCase = true) } ->
                AnalysisItem("播放器兼容", CheckStatus.PASS, "包含 exoplayer，本 App 可用。")
            else ->
                AnalysisItem("播放器兼容", CheckStatus.FAIL,
                    "onlySupportsPlayers 不含 exoplayer（=${rule.onlySupportsPlayers}），本 App 会直接跳过该源。")
        }

        // 搜索链接模板（按引擎区分占位符：CSS 用 {keyword}，XPath 用 @keyword）
        val (searchUrlTpl, placeholder) = when (rule.engine) {
            RuleEngineKind.Xpath -> rule.xpath.searchUrl to "@keyword"
            else -> rule.search.searchUrl to "{keyword}"
        }
        items += when {
            searchUrlTpl.isBlank() ->
                AnalysisItem("搜索链接模板", CheckStatus.FAIL, "搜索链接为空，无法发起搜索。")
            !searchUrlTpl.contains(placeholder) ->
                AnalysisItem("搜索链接模板", CheckStatus.FAIL,
                    "搜索链接不含 $placeholder 占位符，关键词无法代入：\n$searchUrlTpl")
            else ->
                AnalysisItem("搜索链接模板", CheckStatus.PASS, searchUrlTpl)
        }

        // 搜索解析格式（按引擎区分校验项）
        if (rule.engine == RuleEngineKind.Xpath) {
            val ok = rule.xpath.searchList.isNotBlank() && rule.xpath.searchName.isNotBlank() && rule.xpath.searchResult.isNotBlank()
            items += if (ok)
                AnalysisItem("搜索解析格式", CheckStatus.PASS,
                    "XPath：列表(${rule.xpath.searchList}) + 名称(${rule.xpath.searchName}) + 链接(${rule.xpath.searchResult})")
            else
                AnalysisItem("搜索解析格式", CheckStatus.FAIL, "XPath 需要 searchList / searchName / searchResult 三个 XPath 同时配置。")
        } else {
            val fmt = rule.search.format()
            items += when (fmt) {
                SubjectFormat.A ->
                    if (rule.search.titleSelector.isBlank())
                        AnalysisItem("搜索解析格式", CheckStatus.FAIL, "A 格式需要 titleSelector 选出含名称与链接的 <a> 元素。")
                    else AnalysisItem("搜索解析格式", CheckStatus.PASS, "A：单选择器（${rule.search.titleSelector}）")
                SubjectFormat.Indexed ->
                    if (rule.search.titleSelector.isBlank() || rule.search.linkSelector.isBlank())
                        AnalysisItem("搜索解析格式", CheckStatus.FAIL, "indexed 格式需要 titleSelector 与 linkSelector 同时配置。")
                    else AnalysisItem("搜索解析格式", CheckStatus.PASS, "indexed：名称(${rule.search.titleSelector}) + 链接(${rule.search.linkSelector})")
                SubjectFormat.JsonPathIndexed ->
                    if (rule.search.titleSelector.isBlank() || rule.search.linkSelector.isBlank())
                        AnalysisItem("搜索解析格式", CheckStatus.WARN, "json-path 模式建议同时配置 titleSelector/linkSelector 为 JsonPath 表达式。")
                    else AnalysisItem("搜索解析格式", CheckStatus.PASS, "json-path-indexed：${rule.search.titleSelector} / ${rule.search.linkSelector}")
            }
        }

        // 剧集线路格式（按引擎区分校验项）
        if (rule.engine == RuleEngineKind.Xpath) {
            items += if (rule.xpath.chapterResult.isNotBlank())
                AnalysisItem("剧集解析格式", CheckStatus.PASS, "XPath：剧集 <a> XPath（${rule.xpath.chapterResult}）")
            else
                AnalysisItem("剧集解析格式", CheckStatus.FAIL, "XPath 需要 chapterResult（剧集 <a> XPath）。")
        } else {
            val chFmt = rule.episodes.format()
            when (chFmt) {
                ChannelFormat.IndexGrouped -> {
                    val ok = rule.episodes.lineNameSelector.isNotBlank() &&
                            rule.episodes.episodePanelSelector.isNotBlank() &&
                            rule.episodes.episodeListSelector.isNotBlank()
                    items += if (ok) {
                        AnalysisItem("剧集线路格式", CheckStatus.PASS,
                            "index-grouped：线路名(${rule.episodes.lineNameSelector}) + 面板(${rule.episodes.episodePanelSelector}) + 列表(${rule.episodes.episodeListSelector})")
                    } else {
                        AnalysisItem("剧集线路格式", CheckStatus.FAIL, "index-grouped 需要 线路名/面板/列表 三个选择器都配置。")
                    }
                }
                ChannelFormat.NoChannel -> {
                    items += if (rule.episodes.episodeListSelector.isNotBlank()) {
                        AnalysisItem("剧集线路格式", CheckStatus.PASS, "no-channel：列表(${rule.episodes.episodeListSelector})")
                    } else {
                        AnalysisItem("剧集线路格式", CheckStatus.FAIL, "no-channel 需要 episodeListSelector。")
                    }
                }
            }
        }
        if (rule.episodes.episodeNumberRegex.isBlank()) {
            items += AnalysisItem("EP 序号正则", CheckStatus.WARN, "未配置，将用默认「第X话」规则解析集数。")
        } else {
            items += AnalysisItem("EP 序号正则", CheckStatus.PASS, rule.episodes.episodeNumberRegex)
        }

        // 视频策略
        items += when (rule.video.videoMode) {
            VideoMode.INTERCEPT ->
                if (rule.video.videoUrlRegex.isBlank())
                    AnalysisItem("视频匹配策略", CheckStatus.WARN, "INTERCEPT 未配置 videoUrlRegex，将用默认 .m3u8|.mp4|.mkv 匹配。")
                else AnalysisItem("视频匹配策略", CheckStatus.PASS, "INTERCEPT：${rule.video.videoUrlRegex}")
            VideoMode.SILISILI_POST ->
                if (rule.video.silisiliFormField.isBlank())
                    AnalysisItem("视频匹配策略", CheckStatus.WARN, "SILISILI_POST 未配置表单字段，将用默认 player。")
                else AnalysisItem("视频匹配策略", CheckStatus.PASS, "SILISILI_POST：${rule.video.silisiliFormField}")
            VideoMode.GIRIGIRI_B64 ->
                AnalysisItem("视频匹配策略", CheckStatus.PASS, "GIRIGIRI_B64：脚本内 Base64 解码，无需额外配置。")
        }

        // 请求头 / 缓存
        items += AnalysisItem("User-Agent", CheckStatus.PASS,
            if (rule.headers.userAgent.isBlank()) "（空，将使用内置默认 UA）" else "已配置。")
        items += if (rule.search.cacheMinutes <= 0) {
            AnalysisItem("搜索缓存", CheckStatus.WARN, "cacheMinutes=0，重复搜索会重新抓取（可能触发限流）。")
        } else {
            AnalysisItem("搜索缓存", CheckStatus.PASS, "TTL=${rule.search.cacheMinutes} 分钟。")
        }

        return AnalysisSection("① 配置完整性（离线）", items)
    }

    // ───────────────────────── 1. 网络可达性 ─────────────────────────

    private suspend fun networkSection(rule: SourceRule, keyword: String): AnalysisSection {
        val items = mutableListOf<AnalysisItem>()
        val root = RuleExecutor.deriveRootUrl(rule)
        if (root.isBlank()) {
            items += AnalysisItem("站点根", CheckStatus.WARN, "无法从 baseUrl/searchUrl 推导站点根域名（可能搜索链接不是标准 URL）。")
        } else {
            val html = RuleExecutor.fetchPageHtml(rule, root)
            val block = RuleExecutor.blockReasonOf(html)
            items += when {
                html == null -> AnalysisItem("站点根可达", CheckStatus.WARN,
                    "根域名 $root 抓取失败（超时/网络）。不影响搜索页单独测试，但请确认站点在线。")
                block != null -> AnalysisItem("站点根可达", CheckStatus.FAIL,
                    "根域名被拦截（${blockReasonText(block)}）：\n$root")
                else -> AnalysisItem("站点根可达", CheckStatus.PASS, "根域名可访问：$root")
            }
        }
        items += AnalysisItem("本次搜索链接", CheckStatus.PASS,
            "将请求：\n${RuleExecutor.buildSearchUrl(rule, keyword)}")
        return AnalysisSection("② 网络可达性", items)
    }

    // ───────────────────────── 2. 搜索解析 ─────────────────────────

    private suspend fun searchSection(rule: SourceRule, keyword: String): Pair<AnalysisSection, String?> {
        val items = mutableListOf<AnalysisItem>()
        val searchUrl = RuleExecutor.buildSearchUrl(rule, keyword)
        val html = RuleExecutor.fetchPageHtml(rule, searchUrl)
        val block = RuleExecutor.blockReasonOf(html)

        if (html == null) {
            items += AnalysisItem("搜索页加载", CheckStatus.FAIL, "搜索页抓取失败（超时 / 网络错误）。")
            return AnalysisSection("③ 搜索解析", items) to null
        }
        if (block != null) {
            items += AnalysisItem("搜索页加载", CheckStatus.FAIL,
                "搜索页被拦截（${blockReasonText(block)}）。请检查 Base URL / 请求头 / 限流间隔。")
            return AnalysisSection("③ 搜索解析", items) to null
        }
        items += AnalysisItem("搜索页加载", CheckStatus.PASS, "搜索页正常加载（${html.length} 字符）。")

        val parsed = if (rule.engine == RuleEngineKind.Xpath) {
            RuleExecutor.selectSubjectsXpath(html, rule)
        } else {
            RuleExecutor.selectSubjects(html, rule)
        }
        val titleCount: Int
        val linkCount: Int
        if (rule.engine == RuleEngineKind.Xpath) {
            titleCount = countXpath(html, rule.xpath.searchName)
            linkCount = countXpath(html, rule.xpath.searchResult)
        } else {
            val doc = Jsoup.parse(html)
            titleCount = countSelect(doc, rule.search.titleSelector)
            linkCount = countSelect(doc, rule.search.linkSelector)
        }

        items += if (parsed.isEmpty()) {
            AnalysisItem("条目解析", CheckStatus.FAIL,
                "选择器零命中（raw：title=$titleCount, link=$linkCount）。请核对搜索结果 DOM 与选择器。")
        } else {
            AnalysisItem("条目解析", CheckStatus.PASS,
                "解析出 ${parsed.size} 条；raw 选择器命中 title=$titleCount, link=$linkCount。")
        }

        if (parsed.isNotEmpty()) {
            val first = parsed.first()
            items += if (first.url.startsWith("http")) {
                AnalysisItem("相对链接解析", CheckStatus.PASS, first.url)
            } else {
                AnalysisItem("相对链接解析", CheckStatus.WARN,
                    "首条链接非绝对地址（可能拼接基准错误）：\n${first.url}")
            }

            val best = parsed.maxOf { RuleExecutor.matchRate(it.title, keyword) }
            items += if (best >= 50) {
                AnalysisItem("最佳匹配率", CheckStatus.PASS, "最佳匹配率 $best%（阈值 50），标题命中良好。")
            } else {
                AnalysisItem("最佳匹配率", CheckStatus.WARN, "最佳匹配率仅 $best%，可能搜到了不相关条目。")
            }

            items += AnalysisItem("样本（前 5）", CheckStatus.PASS,
                parsed.take(5).joinToString("\n") { "· ${it.title}  →  ${it.url}" })

            items += AnalysisItem("原始 HTML 片段", CheckStatus.PASS,
                "（前 600 字符）\n${html.take(600)}")
        }

        return AnalysisSection("③ 搜索解析", items) to parsed.firstOrNull()?.url
    }

    // ───────────────────────── 3. 剧集解析 ─────────────────────────

    private suspend fun episodeSection(rule: SourceRule, entryUrl: String): Pair<AnalysisSection, String?> {
        val items = mutableListOf<AnalysisItem>()
        val html = RuleExecutor.fetchPageHtml(rule, entryUrl)
        val block = RuleExecutor.blockReasonOf(html)

        if (html == null) {
            items += AnalysisItem("详情页加载", CheckStatus.FAIL, "详情页抓取失败（超时 / 网络错误）。")
            return AnalysisSection("④ 剧集解析", items) to null
        }
        if (block != null) {
            items += AnalysisItem("详情页加载", CheckStatus.FAIL,
                "详情页被拦截（${blockReasonText(block)}）。请检查 Referer / Cookie / 限流间隔。")
            return AnalysisSection("④ 剧集解析", items) to null
        }
        items += AnalysisItem("详情页加载", CheckStatus.PASS, "详情页正常加载（${html.length} 字符）。")

        val doc = Jsoup.parse(html)
        val groups = if (rule.engine == RuleEngineKind.Xpath) {
            RuleExecutor.selectEpisodesXpath(html, rule, entryUrl)
        } else {
            RuleExecutor.selectEpisodes(doc, rule, entryUrl)
        }
        val totalEp = groups.sumOf { it.episodes.size }

        val rawLines: String
        val rawList: String
        if (rule.engine == RuleEngineKind.Xpath) {
            val roads = if (rule.xpath.chapterRoads.isBlank()) 0 else countXpath(html, rule.xpath.chapterRoads)
            val eps = countXpath(html, rule.xpath.chapterResult)
            rawLines = "线路节点(raw)=$roads"
            rawList = "剧集节点(raw)=$eps"
        } else {
            rawLines = "线路名(raw)=${countSelect(doc, rule.episodes.lineNameSelector)}"
            rawList = "面板(raw)=${countSelect(doc, rule.episodes.episodePanelSelector)}, 列表(raw)=${countSelect(doc, rule.episodes.episodeListSelector)}"
        }

        items += if (groups.isEmpty()) {
            AnalysisItem("线路与剧集", CheckStatus.FAIL,
                "选择器零命中。$rawLines, $rawList。")
        } else {
            AnalysisItem("线路与剧集", CheckStatus.PASS,
                "线路分组 ${groups.size} 个，剧集共 ${totalEp} 条。")
        }

        if (groups.isNotEmpty()) {
            items += AnalysisItem("原始选择器命中", CheckStatus.PASS,
                "$rawLines, $rawList, 链接=${countSelect(doc, rule.episodes.episodeLinkSelector)}。")

            val all = groups.flatMap { it.episodes }
            val numbered = all.count { it.number != null }
            items += if (totalEp > 0 && numbered == 0) {
                AnalysisItem("EP 序号解析", CheckStatus.WARN,
                    "0/${totalEp} 解析出 EP 序号，播放时只能按名称兜底，可能错集。")
            } else {
                AnalysisItem("EP 序号解析", CheckStatus.PASS, "$numbered/${totalEp} 解析出 EP 序号。")
            }

            val allHttp = all.all { it.url.startsWith("http") }
            items += if (allHttp) {
                AnalysisItem("播放地址校验", CheckStatus.PASS, "全部 ${all.size} 条为 http(s) 绝对地址。")
            } else {
                AnalysisItem("播放地址校验", CheckStatus.FAIL,
                    "存在非 http(s) 的播放地址，拼接基准可能错误。")
            }

            val sample = groups.first().episodes.take(3)
                .joinToString("\n") { "· ${it.name}  →  ${it.url}" }
            items += AnalysisItem("样本剧集（首组前 3）", CheckStatus.PASS, sample)

            items += AnalysisItem("详情页 HTML 片段", CheckStatus.PASS,
                "（前 600 字符）\n${html.take(600)}")
        }

        return AnalysisSection("④ 剧集解析", items) to groups.firstOrNull()?.episodes?.firstOrNull()?.url
    }

    // ───────────────────────── 4. 视频匹配 ─────────────────────────

    private suspend fun videoSection(rule: SourceRule, episodeUrl: String): AnalysisSection {
        val items = mutableListOf<AnalysisItem>()
        val html = RuleExecutor.fetchPageHtml(rule, episodeUrl)
        val block = RuleExecutor.blockReasonOf(html)
        items += when {
            html == null -> AnalysisItem("播放页加载", CheckStatus.WARN, "播放页抓取失败（超时/网络），仅做视频匹配尝试。")
            block != null -> AnalysisItem("播放页加载", CheckStatus.FAIL,
                "播放页被拦截（${blockReasonText(block)}）。请检查播放请求头（Referer / Cookie）。")
            else -> AnalysisItem("播放页加载", CheckStatus.PASS, "播放页正常加载。")
        }

        val video = RuleExecutor.matchVideoDetailed(rule, episodeUrl)
        if (video.url.isBlank()) {
            items += AnalysisItem("视频直链匹配", CheckStatus.FAIL,
                "未能拦截到视频直链。请检查 videoUrlRegex / 嵌套链接配置 / 播放请求头。")
        } else {
            items += AnalysisItem("视频直链匹配", CheckStatus.PASS, "匹配到直链：\n${video.url}")
            items += if (video.url.contains("m3u8", ignoreCase = true)) {
                AnalysisItem("格式", CheckStatus.PASS, "为 .m3u8（HLS），ExoPlayer 可直接播放。")
            } else {
                AnalysisItem("格式", CheckStatus.WARN,
                    "直链非 .m3u8（${video.url.substringAfterLast('.', "")}），ExoPlayer 可能无法直接播放。")
            }
        }
        val referer = video.headers["Referer"].orEmpty()
        val cookie = video.headers["Cookie"].orEmpty()
        items += AnalysisItem("播放请求头", CheckStatus.PASS,
            "Referer=${if (referer.isBlank()) "（无）" else referer}\nCookie=${if (cookie.isBlank()) "（无）" else "已设置"}")
        return AnalysisSection("⑤ 视频匹配", items)
    }

    // ───────────────────────── 辅助 ─────────────────────────

    private fun countSelect(doc: Element, selector: String): Int =
        if (selector.isBlank()) 0 else runCatching { doc.select(selector).size }.getOrDefault(0)

    /** XPath 命中计数（调试分析用）：XPath 源的「raw 命中数」统计。 */
    private fun countXpath(html: String, xpath: String): Int =
        if (xpath.isBlank()) 0 else runCatching { JXDocument.create(html).selN(xpath).size }.getOrDefault(0)

    private fun blockReasonText(reason: BlockReason): String = when (reason) {
        BlockReason.WAF -> "反爬 / WAF 拦截"
        BlockReason.RATE_LIMITED -> "请求过于频繁，触发限流"
        BlockReason.FORBIDDEN -> "403 禁止访问"
        BlockReason.NOT_FOUND -> "404 页面不存在"
        BlockReason.CAPTCHA -> "需要人机验证"
    }

    private fun computeOverall(sections: List<AnalysisSection>): CheckStatus {
        var worst = CheckStatus.PASS
        for (s in sections) for (it in s.items) {
            if (it.status == CheckStatus.SKIP) continue
            if (it.status == CheckStatus.FAIL) return CheckStatus.FAIL
            if (it.status == CheckStatus.WARN) worst = CheckStatus.WARN
        }
        return worst
    }

    private fun buildSummary(overall: CheckStatus, sections: List<AnalysisSection>): String {
        val fail = sections.sumOf { it.items.count { i -> i.status == CheckStatus.FAIL } }
        val warn = sections.sumOf { it.items.count { i -> i.status == CheckStatus.WARN } }
        val skip = sections.sumOf { it.items.count { i -> i.status == CheckStatus.SKIP } }
        val head = when (overall) {
            CheckStatus.PASS -> "✅ 整体通过"
            CheckStatus.WARN -> "⚠️ 基本可用，有 $warn 处建议优化"
            CheckStatus.FAIL -> "❌ 存在 $fail 处致命问题"
            CheckStatus.SKIP -> "⏭️ 大部分步骤被跳过"
        }
        return "$head ｜ 失败 $fail · 警告 $warn · 跳过 $skip"
    }
}
