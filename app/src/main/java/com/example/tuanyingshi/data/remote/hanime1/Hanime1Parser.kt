package com.example.tuanyingshi.data.remote.hanime1

import com.example.tuanyingshi.util.log
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.json.JSONObject

/**
 * Hanime1 专属 HTML/JSON 解析器（**非 CSS 规则引擎**）。
 *
 * 选择器取自 Han1meViewer（YenalyLiew/Han1meViewer，Apache-2.0），按本站需求精简。
 * 本类只服务于 [Hanime1Api]，与 [com.example.tuanyingshi.util.source_rule.RuleExecutor] 无关。
 */
object Hanime1Parser {

    private const val TAG = "Hanime1Parser"

    /** 从链接提取 video code：`watch?v=abc` / `/watch/abc` → abc。 */
    private fun String.toVideoCode(): String? {
        val q = this.substringAfter("watch?v=", "")
        if (q.isNotBlank()) return q.substringBefore("&").substringBefore("#").takeIf { it.isNotBlank() }
        val slash = this.substringAfterLast("/watch/", "")
        if (slash.isNotBlank()) return slash.substringBefore("?").substringBefore("#").takeIf { it.isNotBlank() }
        return null
    }

    // ───────────────────────── 首页 ─────────────────────────

    fun parseHome(body: String): Hanime1Home {
        val doc = Jsoup.parse(body).body()

        // 头图
        val bannerCSS = doc.selectFirst("div[id=home-banner-wrapper]")
        val bannerImg = bannerCSS?.previousElementSibling()
        val bannerTitle = bannerImg?.selectFirst("img")?.attr("alt")
        val bannerPic = bannerImg?.select("img")?.let { it.getOrNull(1)?.absUrl("src") ?: it.getOrNull(0)?.absUrl("src") }
        val bannerDesc = bannerCSS?.selectFirst("h4")?.ownText()
        val bannerCode = doc.select("script").firstOrNull { it.data().contains("watch?v=") }
            ?.data()?.let { Regex("""watch\?v=([^"&]+)""").find(it)?.groupValues?.getOrNull(1) }
        val banner = if (bannerTitle != null && bannerPic != null) {
            Hanime1Banner(
                title = bannerTitle,
                description = bannerDesc,
                picUrl = bannerPic,
                videoCode = bannerCode,
            )
        } else null

        // 分类行
        val rows = doc.select("div[id=home-rows-wrapper] > div")
        val titles = listOf(
            "最新上市", "最新上传", "里番", "泡面番", "Motion Anime",
            "3DCG", "2.5D", "2D", "AI 生成", "MMD", "Cosplay", "他们在看",
        )
        val categories = mutableListOf<Hanime1Category>()
        rows.forEachIndexed { index, row ->
            val videos = extractVideos(row)
            if (videos.isNotEmpty()) {
                val title = titles.getOrElse(index) { "分区${index + 1}" }
                categories.add(Hanime1Category(title = title, videos = videos))
            }
        }

        return Hanime1Home(banner = banner, categories = categories)
    }

    // ───────────────────────── 搜索 ─────────────────────────

    fun parseSearch(body: String): List<Hanime1Video> {
        val doc = Jsoup.parse(body).body()
        val normal = doc.getElementsByClass("content-padding-new").firstOrNull()
        val simplified = doc.getElementsByClass("home-rows-videos-wrapper").firstOrNull()
        val container = normal ?: simplified
        val result = mutableListOf<Hanime1Video>()
        container?.let { result.addAll(extractVideos(it, simplified = normal == null)) }
        return result.distinctBy { it.videoCode }
    }

    // ───────────────────────── 详情 / 播放 ─────────────────────────

    fun parseVideo(body: String): Hanime1Detail {
        val doc = Jsoup.parse(body).body()

        val title = doc.getElementById("shareBtn-title")?.text() ?: ""

        val videoTag = doc.selectFirst("video[id=player]")
        val coverUrl = videoTag?.absUrl("poster").orEmpty()

        val caption = doc.selectFirst("div[class^=video-caption-text]")
        val chineseTitle = caption?.previousElementSibling()?.ownText()
        val introduction = caption?.ownText()

        val uploadViews = doc.selectFirst("div.video-details-wrapper > div > div > div")?.text()
        val m = Regex("""(觀看次數|观看次数)[:：]?\s*(.+次)?\s*(\d{4}-\d{2}-\d{2})""").find(uploadViews.orEmpty())
        val uploadTime = m?.groupValues?.getOrNull(3)
        val views = m?.groupValues?.getOrNull(2)?.trim()

        val tagEls = doc.getElementsByClass("single-video-tag")
        val tags = mutableListOf<String>()
        tagEls.forEach { tag ->
            val child = tag.children().getOrNull(0)
            if (child != null && child.hasAttr("href")) {
                tags.add(child.text().substringBefore(" (").removePrefix("#").trim())
            }
        }

        // 播放直链：优先 <video><source size src>，否则 script 里的 const source = '...'
        val videoUrls = linkedMapOf<String, String>()
        val sources = videoTag?.children()
        if (!sources.isNullOrEmpty()) {
            sources.forEach { src ->
                val size = src.attr("size").takeIf { it.isNotBlank() } ?: "0"
                val url = src.absUrl("src")
                if (url.isNotBlank()) videoUrls["${size}P"] = url
            }
        } else {
            doc.selectFirst("div[id=player-div-wrapper]")?.select("script")?.forEach { script ->
                val data = script.data()
                if (data.isBlank()) return@forEach
                val url = Regex("""const source = '([^']+)'""").find(data)?.groupValues?.getOrNull(1)
                    ?: Regex("""source\s*=\s*'([^']+)'""").find(data)?.groupValues?.getOrNull(1)
                if (!url.isNullOrBlank()) {
                    videoUrls["默认"] = url
                    return@forEach
                }
            }
        }

        // 系列视频
        val playlist = mutableListOf<Hanime1Video>()
        val playlistWrapper = doc.selectFirst("div.video-playlist-wrapper")
            ?: doc.selectFirst("div[id=video-playlist-wrapper]")
        playlistWrapper?.getElementById("playlist-scroll")?.children()?.forEach { child ->
            val code = child.attr("data-href").toVideoCode() ?: return@forEach
            val thumb = child.selectFirst(".thumb-container")
            val c = thumb?.selectFirst("img.main-thumb")?.absUrl("src") ?: return@forEach
            val t = child.selectFirst("h4.video-title a")?.text() ?: return@forEach
            val dur = thumb.selectFirst(".duration")?.text().orEmpty()
            val v = thumb.select(".stat-item").getOrNull(1)?.text().orEmpty()
            val artist = child.selectFirst(".meta-author a")?.text().orEmpty()
            playlist.add(Hanime1Video(title = t, coverUrl = c, videoCode = code, duration = dur, views = v, artistName = artist))
        }

        // 相关推荐
        val related = mutableListOf<Hanime1Video>()
        doc.getElementById("related-tabcontent")?.let { rel ->
            related.addAll(extractVideos(rel, simplified = true))
        }

        // 作者
        val artistName = doc.getElementById("video-artist-name")?.text()?.trim()
        val artistGenre = doc.getElementById("video-artist-name")?.nextElementSibling()?.text()?.trim()
        val artistAvatar = doc.selectFirst(
            "div.video-details-wrapper > div > a > div > img[style*=border-radius: 50%]"
        )?.absUrl("src")

        return Hanime1Detail(
            title = title,
            coverUrl = coverUrl,
            chineseTitle = chineseTitle,
            introduction = introduction,
            uploadTime = uploadTime,
            views = views,
            videoUrls = if (videoUrls.isEmpty()) mapOf("默认" to "") else videoUrls,
            tags = tags.filter { it.isNotBlank() },
            artistName = artistName,
            artistGenre = artistGenre,
            artistAvatar = artistAvatar,
            playlist = playlist,
            related = related.distinctBy { it.videoCode },
        )
    }

    // ───────────────────────── 评论 ─────────────────────────

    fun parseComments(json: String): Hanime1CommentPage {
        // loadComment 返回 {"comments": "<html>..."} 或 {"comments": [...]}
        return runCatching {
            val root = JSONObject(json)
            val commentsNode = root.opt("comments")
            when {
                commentsNode is org.json.JSONArray -> {
                    val list = (0 until commentsNode.length()).mapNotNull { i ->
                        val o = commentsNode.optJSONObject(i) ?: return@mapNotNull null
                        Hanime1Comment(
                            id = o.optString("id", ""),
                            username = o.optString("user_name").takeIf { it.isNotBlank() },
                            avatar = o.optString("avatar").takeIf { it.isNotBlank() },
                            text = o.optString("comment", o.optString("text", "")),
                            time = o.optString("created_at").takeIf { it.isNotBlank() },
                            likes = o.optInt("likes", -1).takeIf { it >= 0 },
                        )
                    }
                    Hanime1CommentPage(list, hasMore = root.optBoolean("has_more", false))
                }
                commentsNode is String -> parseCommentsHtml(commentsNode)
                else -> Hanime1CommentPage(emptyList(), false)
            }
        }.onFailure { it.log(TAG, "parseComments failed") }.getOrDefault(Hanime1CommentPage(emptyList(), false))
    }

    private fun parseCommentsHtml(html: String): Hanime1CommentPage {
        val doc = Jsoup.parse(html)
        val items = doc.select("[class*=comment-item], div.comment, .comment-block")
        val list = items.mapNotNull { el ->
            val id = el.attr("data-comment-id").takeIf { it.isNotBlank() } ?: el.id()
            val avatar = el.selectFirst("img")?.absUrl("src")
            val username = el.selectFirst("[class*=user-name], .comment-username, .username")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
            val text = el.selectFirst("[class*=comment-text], .comment-body, .comment-content")?.text()?.trim()
                ?: el.ownText()
            if (text.isBlank()) return@mapNotNull null
            val time = el.selectFirst("[class*=comment-time], .comment-date, time")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
            val likes = el.selectFirst("[class*=comment-like], .like-count")?.text()?.trim()
                ?.toIntOrNull()
            Hanime1Comment(id = id, username = username, avatar = avatar, text = text, time = time, likes = likes)
        }
        return Hanime1CommentPage(list, hasMore = false)
    }

    // ───────────────────────── 卡片解析 ─────────────────────────

    /**
     * 从某个容器抽取视频卡片。
     * @param simplified 是否为简化版（home-rows-videos-div / a[href*=watch]），否则按 horizontal-card 解析。
     */
    private fun extractVideos(container: Element, simplified: Boolean = false): List<Hanime1Video> {
        val result = mutableListOf<Hanime1Video>()
        if (simplified) {
            container.select("a[href*=watch]").forEach { a ->
                val code = a.attr("abs:href").toVideoCode() ?: a.attr("href").toVideoCode() ?: return@forEach
                val cover = a.selectFirst("img")?.absUrl("src")
                    ?: a.selectFirst("img")?.attr("src")?.let { if (it.startsWith("http")) it else null }
                val title = a.selectFirst("div[class\$=title], .home-rows-videos-title")?.text()
                    ?: a.attr("title").takeIf { it.isNotBlank() }
                if (cover != null && title != null) {
                    result.add(Hanime1Video(title = title, coverUrl = cover, videoCode = code))
                }
            }
        } else {
            container.select("div[class^=horizontal-card]").forEach { card ->
                parseNormalCard(card)?.let { result.add(it) }
            }
            // 兜底：简化版容器里也常见 home-rows-videos-div
            if (result.isEmpty()) {
                result.addAll(extractVideos(container, simplified = true))
            }
        }
        return result
    }

    /** 正常卡片解析（对齐 Han1meViewer hanimeNormalItemVer2）。 */
    private fun parseNormalCard(item: Element): Hanime1Video? {
        val title = item.selectFirst("div.title, h4.video-title")?.text()?.trim()
        val cover = item.select("img").getOrNull(0)?.absUrl("src")
        val code = item.select("a").getOrNull(0)?.absUrl("href")?.toVideoCode()
            ?: item.select("a").getOrNull(0)?.attr("href")?.toVideoCode()
        if (title == null || cover == null || code == null) return null

        val thumb = item.selectFirst("div[class^=thumb-container]")
        val duration = thumb?.selectFirst("div[class^=duration]")?.text().orEmpty()
        val views = thumb?.select("div[class^=stat-item]")?.getOrNull(1)?.text().orEmpty()

        val subtitle = item.selectFirst("div.subtitle, div.video-meta-data")?.text()?.trim().orEmpty()
        var artist = ""
        var uploadTime = ""
        if (subtitle.contains("•")) {
            val parts = subtitle.split("•").map { it.trim() }
            artist = parts.getOrNull(0).orEmpty()
            uploadTime = parts.getOrNull(1).orEmpty()
        } else {
            artist = item.selectFirst("div.subtitle a, .video-meta-data a")?.text()?.trim().orEmpty()
            uploadTime = item.selectFirst("span.subtitle-time, .video-meta-data span")?.text()?.trim().orEmpty()
        }
        return Hanime1Video(
            title = title,
            coverUrl = cover,
            videoCode = code,
            duration = duration,
            views = views,
            uploadTime = uploadTime,
            artistName = artist,
        )
    }
}
