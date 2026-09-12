package com.example.tuanyingshi.util.source_rule

import java.net.URI

/**
 * 内置数据源规则（硬编码解析器的规则化表达）。
 *
 * 把原 SilisiliSource / GirigiriSource 的「详情-剧集-视频」抓取逻辑改写为可配置规则：
 * - 详情 / 剧集：纯 CSS Selector，由 [RuleExecutor] 通用解析；
 * - 视频：以 [VideoMode] 命名策略保留站点特有的解密/解码（站内嵌 Base64、POST+AES 等）。
 *
 * 搜索 / 首页 / 周表 / 排行 / 分类仍由各源的原实现提供（这些不在三步规则覆盖范围内）。
 * 站内 baseUrl 由调用方按当前域名注入，保证镜像源也能复用同一套规则。
 */
object BuiltInSourceRules {

    /** 嘶哩嘶哩内置规则（detail/episodes/video 走规则引擎，video 用 SILISILI_POST）。 */
    fun silisili(baseUrl: String): SourceRule {
        val base = baseUrl.trim().removeSuffix("/")
        return SourceRule(
            id = "builtin_silisili",
            name = "嘶哩嘶哩",
            isBuiltIn = true,
            search = SourceRule.SearchStep(
                searchUrl = "$base/vodsearch{keyword}/page/1/",
                baseUrl = base,
                subjectFormatId = SubjectFormat.A.id,
                preferShorterName = true,
                parserType = SourceRule.SearchStep.ParserType.SingleTag,
                titleSelector = "article.post-list div.search-image a",
            ),
            episodes = SourceRule.EpisodesStep(
                // 站点有线路 tab 但没有可复用的线路名称选择器，走 index-grouped 的「线路 N」兜底命名
                channelFormatId = ChannelFormat.IndexGrouped.id,
                groupByLine = true,
                episodePanelSelector = "div.play-pannel-list",
                episodeListSelector = "li",
                episodeLinkSelector = "a",
                episodeNumberRegex = "第(?<ep>\\d+)集",
            ),
            video = SourceRule.VideoStep(
                videoMode = VideoMode.SILISILI_POST,
                cookies = "silisili=on;path=/;max-age=86400;appdw=on",
            ),
            detail = SourceRule.DetailStep(
                titleSelector = "h1.entry-title",
                imgSelector = "div.v_sd_l > img",
                imgAttr = "src",
                descSelector = "div.v_cont",
                tagSelector = "p.data a",
            ),
        )
    }

    /** Girigiri 内置规则（detail/episodes/video 走规则引擎，video 用 GIRIGIRI_B64）。 */
    fun girigiri(baseUrl: String): SourceRule {
        val base = baseUrl.trim().removeSuffix("/")
        return SourceRule(
            id = "builtin_girigiri",
            name = "Girigiri",
            isBuiltIn = true,
            search = SourceRule.SearchStep(
                searchUrl = "$base/search/{keyword}----------1---/",
                baseUrl = base,
                subjectFormatId = SubjectFormat.Indexed.id,
                preferShorterName = true,
                parserType = SourceRule.SearchStep.ParserType.MultiTag,
                titleSelector = "div.public-list-box div.thumb-txt",
                linkSelector = "div.public-list-box a",
            ),
            episodes = SourceRule.EpisodesStep(
                channelFormatId = ChannelFormat.IndexGrouped.id,
                groupByLine = true,
                episodePanelSelector = "div.anthology-list > ul",
                episodeListSelector = "li",
                episodeLinkSelector = "a",
                episodeNumberRegex = "第(?<ep>\\d+)集",
            ),
            video = SourceRule.VideoStep(
                videoMode = VideoMode.GIRIGIRI_B64,
            ),
            detail = SourceRule.DetailStep(
                titleSelector = "div.vod-detail h3",
                imgSelector = "div.vod-detail img",
                imgAttr = "data-src",
                descSelector = "div.vod-detail div#height_limit",
                tagSelector = "div.vod-detail div.slide-info a",
            ),
        )
    }

    /** 取规则适用的根域名（用于日志/诊断）。 */
    fun hostOf(baseUrl: String): String = runCatching { URI(baseUrl).host ?: baseUrl }.getOrDefault(baseUrl)
}
