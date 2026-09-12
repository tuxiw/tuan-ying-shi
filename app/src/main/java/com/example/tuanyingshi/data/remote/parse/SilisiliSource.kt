package com.example.tuanyingshi.data.remote.parse

import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.EpisodeBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.util.DownloadManager
import com.example.tuanyingshi.util.decryptData
import com.example.tuanyingshi.util.applyProxy
import com.example.tuanyingshi.util.getDefaultDomain
import com.example.tuanyingshi.util.log
import com.example.tuanyingshi.util.source_rule.BuiltInSourceRules
import com.example.tuanyingshi.util.source_rule.RuleExecutor
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.security.MessageDigest

/**
 * 嘶哩嘶哩（silisili.link）数据源——从 LaQoo 移植。
 * Cookie + POST 解密获取视频流 m3u8。
 */
object SilisiliSource : AnimeSource {

    private const val LOG_TAG = "SilisiliSource"
    override var WEB_URL: String  = "www.silisili.link"
    override val DEFAULT_DOMAIN: String = "https://"+WEB_URL
    override var baseUrl = getDefaultDomain()

    override suspend fun getHomeData(): List<HomeBean> {
        val headers = mapOf(
            Pair("Cookie", "silisili=on;path=/;max-age=86400;appdw=on"),
            Pair("User-Agent", "Mozilla/5.0 (Linux; Android 6.0) Mobile"),
        )
        val source = DownloadManager.getHtml(baseUrl,WEB_URL,headers)
        val document = Jsoup.parse(source)
        val homeList = mutableListOf<HomeBean>()

        val elements = document.select("div.conch-content").select("div.container")
        for ((i, el) in elements.withIndex()) {
            if (i == 0 || i == 2 || i == 3 || i == 8) continue
            val title = el.select("h2").text()
            val moreUrl = el.select("div.hl-rb-head > a").attr("href")
            val animeList = mutableListOf<AnimeBean>()
el.select("ul.hl-vod-list > li").forEach {
            it.select("a").apply {
                val animeTitle = attr("title")
                val url = attr("href")
                val imgUrl = attr("data-original")
                // 注意：必须用循环变量 `it`（当前 <li>），不能用外层 `el`（整个 container）
                // 否则会把板块内所有 li 的 hl-pic-text 都拼到一起，导致徽标文本超长
                val episodeName = it.select("div.hl-pic-text").text()
                animeList.add(AnimeBean(animeTitle, imgUrl, url, episodeName))
            }
        }
            homeList.add(HomeBean(title = title, moreUrl = moreUrl, animes = animeList))
        }
        return homeList
    }

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        // 优先走规则引擎（详情-剧集三步解析），解析失败回退原 Jsoup 实现
        return RuleExecutor.getAnimeDetail(BuiltInSourceRules.silisili(baseUrl), detailUrl)
            ?: legacyGetAnimeDetail(detailUrl)
    }

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val rule = BuiltInSourceRules.silisili(baseUrl)
        val url = RuleExecutor.matchVideo(rule, episodeUrl)
        return if (url.isNotBlank()) VideoBean(url) else legacyGetVideoData(episodeUrl)
    }

    private suspend fun legacyGetAnimeDetail(detailUrl: String): AnimeDetailBean {
        val source = getHtml("$baseUrl/$detailUrl")
        val document = Jsoup.parse(source)

        val title = document.select("h1.entry-title").text().split(" ").first()
        val img = document.select("div.v_sd_l > img").attr("src")
        val tags = document.select("p.data").select("a")

        val tagTitles = mutableListOf<String>()
        for (tag in tags) {
            if (tag.text().isEmpty()) continue
            tagTitles.add(tag.text().uppercase())
        }

        val desc = document.select("div.v_cont")
        desc.select("div.v_sd").remove()
        desc.select("span").remove()
        val description = desc.text()

        val channels = getAnimeEpisodes(document)
        val relatedAnimes = getAnimeList(document)

        return AnimeDetailBean(
            title, img, description, tagTitles, relatedAnimes, channels = channels
        )
    }

    private suspend fun legacyGetVideoData(episodeUrl: String): VideoBean {
        val videoUrl = getVideoUrl("$baseUrl/$episodeUrl")
        return VideoBean(videoUrl)
    }

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        val source = getHtml("$baseUrl/vodsearch$query/page/$page/")
        val document = Jsoup.parse(source)
        val animeList = mutableListOf<AnimeBean>()
        document.select("article.post-list").forEach { el ->
            val title = el.select("div.search-image").select("a").attr("title")
            val url = el.select("div.search-image").select("a").attr("href")
            if (url.isBlank() || url.startsWith("javascript:", ignoreCase = true)) return@forEach
            val imgUrl = el.select("div.search-image").select("img").attr("srcset")
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url))
        }
        return animeList
    }

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        val source = getHtml(baseUrl)
        val document = Jsoup.parse(source)

        val weekMap = mutableMapOf<Int, MutableList<AnimeBean>>()
        val dayElements = document.select("div.week_item").select("ul.tab-content")
        dayElements.size.log(LOG_TAG)

        dayElements.movePosition(0, 6)
        dayElements.movePosition(7, 13)

        for (i in 0 until 14) {
            val dayList = mutableListOf<AnimeBean>()
            dayElements[i].select("li").forEach { li ->
                val title = li.select("a.item-cover").attr("title")
                val url = li.select("a.item-cover").attr("href")
                if (url.isBlank() || url.startsWith("javascript:", ignoreCase = true)) return@forEach
                val spanStyle = li.select("span[style]").attr("style")
                val img = getImgUrl(spanStyle)
                val episodeName = li.select("p.num").text()
                dayList.add(AnimeBean(title, img, url, episodeName))
            }
            weekMap[i % 7]?.addAll(dayList) ?: weekMap.set(i, dayList)
        }
        return weekMap
    }

    private fun MutableList<Element>.movePosition(current: Int, destination: Int) {
        val tmp = this[current]
        this.removeAt(current)
        this.add(destination, tmp)
    }

    private fun getAnimeEpisodes(document: Document): Map<Int, List<EpisodeBean>> {
        val channels = mutableMapOf<Int, List<EpisodeBean>>()
        document.select("div.play-pannel-list").forEachIndexed { i, e ->
            val episodes = mutableListOf<EpisodeBean>()
            e.select("li > a").forEach { el ->
                val name = el.text()
                val url = el.attr("href")
                episodes.add(EpisodeBean(name, url))
            }
            channels[i] = episodes
        }
        return channels
    }

    private fun getAnimeList(document: Document): List<AnimeBean> {
        val animeList = mutableListOf<AnimeBean>()
        document.select("div.vod_hl_list").select("a").forEach { el ->
            val title = el.select("div.list-body").text()
            val img: String = getImgUrl(el.select("i.thumb").attr("style"))
            val url = el.attr("href")
            // 过滤占位链接（javascript:），避免脏数据流入列表/详情/历史
            if (url.isBlank() || url.startsWith("javascript:", ignoreCase = true)) return@forEach
            animeList.add(AnimeBean(title, img, url, ""))
        }
        return animeList
    }

    private fun getVideoUrl(url: String): String {
        val encryptData = postRequest(url)
        val params1 = encryptData.substring(0, 9)
        val params2 = encryptData.substring(9)

        val ivAndKey = md5DigestAsHex(params1)
        val iv = ivAndKey.substring(0, 16)
        val key = ivAndKey.substring(16)

        val result = decryptData(params2, key = key, iv = iv)
        val urlRegex = """"url":"(.*?)"""".toRegex()
        return urlRegex.find(result)!!.groupValues[1].replace("\\", "")
    }

    private fun getImgUrl(urlTarget: String): String {
        val urlRegex = """url\((.*?)\)""".toRegex()
        return urlRegex.find(urlTarget)?.groupValues?.get(1) ?: ""
    }

    private suspend fun getHtml(url: String): String {
        val headerMap = mapOf(Pair("Cookie", "silisili=on;path=/;max-age=86400"))
        return DownloadManager.getHtml(url,WEB_URL,headerMap)
    }

    private fun postRequest(url: String): String {
        val client = OkHttpClient.Builder().applyProxy().build()
        val body = FormBody.Builder().add("player", "sili").build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        return response.body!!.charStream().readText()
    }

    private fun md5DigestAsHex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).toHex()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun ByteArray.toHex() = asUByteArray().joinToString(separator = "") { byte ->
        byte.toString(16).padStart(2, '0')
    }
}
