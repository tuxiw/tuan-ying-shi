package com.example.tuanyingshi.data.remote.parse

import android.net.Uri
import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.EpisodeBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.util.DownloadManager
import com.example.tuanyingshi.util.getDefaultDomain
import com.example.tuanyingshi.data.remote.parse.util.WebViewUtil
import com.example.tuanyingshi.util.source_rule.BuiltInSourceRules
import com.example.tuanyingshi.util.source_rule.RuleExecutor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

/**
 * Girigiri（ani.girigirilove.com）数据源——从 LaQoo 移植。
 * 页面内嵌 Base64 编码的视频地址，正则提取后解码即得直链。
 * 注意：站点走 Cloudflare，本机网络可能需要代理（App 内置代理设置支持）。
 */
object GirigiriSource : AnimeSource {

    override var WEB_URL: String = "ani.girigirilove.com"
    override val DEFAULT_DOMAIN: String = "https://"+WEB_URL
    override var baseUrl = getDefaultDomain()

    /** 浏览器标准头（必须显式传入，避免复用次元城 WAF 的伪造 Host）。 */
    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "zh-CN,zh;q=0.9",
    )

    private suspend fun getHtml(url: String): String =
        DownloadManager.getHtml(url,WEB_URL,defaultHeaders)

    private val webViewUtil = WebViewUtil()

    private const val GIRIGIRI_DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * 抓取渲染后 HTML：优先用 WebView 渲染（过 Cloudflare JS 挑战 / 验证码），
     * 失败（无 WebView 环境 / 超时）再退回 OkHttp。
     */
    private suspend fun fetchDocument(url: String): Document {
        val html = webViewUtil.getPageHtml(
            url = url,
            userAgent = GIRIGIRI_DESKTOP_UA,
            waitAfterLoadMs = 3000L,
        ) ?: DownloadManager.getHtml(url, WEB_URL, defaultHeaders)
        return Jsoup.parse(html)
    }

    override fun onExit() {
        webViewUtil.clearWeb()
    }

    /** 诊断页根域名抓取必须带浏览器头（无 headers 的默认请求会注入次元城假 Host）。 */
    override suspend fun getRootHtml(): String = getHtml(baseUrl)

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        val document = fetchDocument("${baseUrl}/search/${query}----------${page}---/")
        val animeList = mutableListOf<AnimeBean>()
        document.select("div.public-list-box").forEach { el ->
            val title = el.select("div.thumb-txt").text()
            val url = el.select("a").attr("href")
            // 过滤占位链接（javascript:），避免脏数据流入列表/详情/历史
            if (url.isBlank() || url.startsWith("javascript:", ignoreCase = true)) return@forEach
            val imgUrl = el.select("img").attr("data-src").padDomain()
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url))
        }
        return animeList
    }

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        val document = fetchDocument(baseUrl)
        val elements = document.select("div.wow")[0].select("div#week-module-box")
        val weekMap = mutableMapOf<Int, List<AnimeBean>>()
        elements.select("div.public-r").forEachIndexed { index, element ->
            val dayList = getAnimeList(element.select("div.public-list-box"))
            weekMap[index] = dayList
        }
        return weekMap
    }

    override suspend fun getHomeData(): List<HomeBean> {
        val document = fetchDocument(baseUrl)
        val elements = document.select("div.wow").apply { removeAt(0) }
        val homeBeanList = mutableListOf<HomeBean>()
        for ((i, el) in elements.withIndex()) {
            if (i == 1) continue
            val title = el.select("div.title-left > h4").text()
            val moreUrl = el.select("div.title-right > a").attr("href")
            val homeItemBeanList = getAnimeList(el.select("div.public-list-box"))
            homeBeanList.add(HomeBean(title = title, moreUrl = moreUrl, animes = homeItemBeanList))
        }
        return homeBeanList
    }

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        // 优先走规则引擎（详情-剧集三步解析），解析失败回退原 Jsoup 实现
        return RuleExecutor.getAnimeDetail(BuiltInSourceRules.girigiri(baseUrl), detailUrl)
            ?: legacyGetAnimeDetail(detailUrl)
    }

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val rule = BuiltInSourceRules.girigiri(baseUrl)
        val url = RuleExecutor.matchVideo(rule, "${baseUrl}/$episodeUrl")
        return if (url.isNotBlank()) VideoBean(url) else legacyGetVideoData(episodeUrl)
    }

    private suspend fun legacyGetAnimeDetail(detailUrl: String): AnimeDetailBean {
        val document = fetchDocument("${baseUrl}/$detailUrl")
        val main = document.select("div.vod-detail")
        val title = main.select("h3").text()
        val desc = main.select("div#height_limit").text()
        val imgUrl = main.select("img").attr("data-src").padDomain()
        val tags =
            main.select("div.slide-info").last()?.select("a")?.map { it.text() }?.toMutableList()
                ?.also { it.removeAt(it.lastIndex) } ?: emptyList()
        val channels = getAnimeEpisodes(document.select("div.anthology-list").select("ul"))
        val relatedAnimes =
            getAnimeList(document.select("div.box-width.wow").select("div.public-list-box"))
        return AnimeDetailBean(title, imgUrl, desc, tags, relatedAnimes, channels = channels)
    }

    private suspend fun legacyGetVideoData(episodeUrl: String): VideoBean {
        val url = "${baseUrl}/$episodeUrl"
        val document = fetchDocument(url)
        val videoUrl = getVideoUrl(document)
        return VideoBean(videoUrl)
    }

    /** 提取页内脚本中 Base64 编码的视频直链并解码。 */
    private fun getVideoUrl(document: Document): String {
        val videoUrlTarget = document.select("div.player-box > div.player-left > script")[0].data()
        val videoUrlRegex = """"url":"(.*?)","url_next"""".toRegex()
        val rawVideoUrl = videoUrlRegex.find(videoUrlTarget)?.groupValues?.get(1)
            ?: throw IllegalStateException("video url is empty")

        val encodedVideoUrl = String(java.util.Base64.getDecoder().decode(rawVideoUrl), Charsets.UTF_8)
        return Uri.decode(encodedVideoUrl)
    }

    private fun getAnimeList(elements: Elements): List<AnimeBean> {
        val animeList = mutableListOf<AnimeBean>()
        elements.forEach { el ->
            el.select("div.public-list-div > a").forEach { a ->
                val title = a.attr("title")
                val url = a.attr("href")
                // 过滤占位链接（javascript:），避免脏数据流入列表/详情/历史
                if (url.isBlank() || url.startsWith("javascript:", ignoreCase = true)) return@forEach
                val imgUrl = a.select("img").attr("data-src").padDomain()
                val episodeName = a.select("span.public-list-prb").text()
                animeList.add(
                    AnimeBean(
                        title = title,
                        img = imgUrl,
                        url = url,
                        episodeName = episodeName
                    )
                )
            }
        }
        return animeList
    }

    private fun getAnimeEpisodes(elements: Elements): Map<Int, List<EpisodeBean>> {
        val channels = mutableMapOf<Int, List<EpisodeBean>>()
        elements.forEachIndexed { i, e ->
            val dramaElements = e.select("li").select("a")//剧集列表
            val episodes = mutableListOf<EpisodeBean>()
            dramaElements.forEach { el ->
                val name = el.text()
                val url = el.attr("href")
                episodes.add(EpisodeBean(name, url))
            }
            channels[i] = episodes
        }
        return channels
    }

    private fun String.padDomain(): String {
        return "$baseUrl$this"
    }
}
