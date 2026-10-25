package com.example.tuanyingshi.data.remote.hanime1

import com.example.tuanyingshi.util.ChromeMobileUserAgent
import com.example.tuanyingshi.util.applyProxy
import com.example.tuanyingshi.util.log
import com.example.tuanyingshi.util.source_rule.CfVerificationCoordinator
import com.example.tuanyingshi.util.source_rule.CloudflareDetector
import com.example.tuanyingshi.util.source_rule.SourceCookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Hanime1（hanime1.me）API 资源客户端。
 *
 * 形态：站点没有公开 JSON 接口，这里以「专用 HTTP 客户端 + 专用解析」方式直连站点取数据，
 * 与通用 CSS 规则引擎（[com.example.tuanyingshi.util.source_rule.RuleExecutor]）完全解耦——
 * 域名可在「数据源」里修改，但解析逻辑写死在本类与 [Hanime1Parser]，不会被当成 CSS 源。
 *
 * 关键站点路径（对照 HaRu 的 `docs/hanime1_api_endpoints.md`）：
 * - 首页：`/`（HTML）
 * - 搜索：`/search?query={q}&page={p}&genre=&sort=&broad=&date=&duration=&tags[]=`
 * - 详情/播放：`/watch?v={code}`（元数据与 MP4 直链同页内联）
 * - 评论：`/loadComment?type=video&id={code}&page={p}`（返回 JSON）
 * - 新番导览：`/previews/{date}`（如 202206）
 *
 * Cloudflare：站点启用 CF 防护。请求自动注入 SourceCookieManager 为该 host 保存的 Cookie
 * （含 cf_clearance）与登录态 UA；若用户未过盾，拉到的会是挑战页，解析层会据此报错提示。
 */
object Hanime1Api {

    private const val TAG = "Hanime1Api"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .readTimeout(30, TimeUnit.SECONDS)
        .applyProxy()
        .build()

    /**
     * 当前选中的 Hanime1 域名（含 scheme，去尾斜杠）。
     * 由 [com.example.tuanyingshi.data.remote.parse.Hanime1Source] 在 onEnter/切源时写入，
     * 与「数据源」里的域名设置保持同步；默认回退到官方域。
     */
    var BASE_URL: String = "https://hanime1.me"

    private val baseUrl: String
        get() = BASE_URL.trimEnd('/')

    private val host: String
        get() = runCatching { java.net.URI(baseUrl).host }.getOrDefault("hanime1.me")

    // ───────────────────────── 内部请求 ─────────────────────────

    /**
     * 统一请求入口：注入 Cookie / UA；命中 Cloudflare 挑战页且本地尚无 clearance 时，
     * 触发全局过盾（[CfVerificationCoordinator] → WebView 过盾 + cookie 回填），再用新 Cookie 重发一次。
     *
     * 对齐规则源 [com.example.tuanyingshi.util.source_rule.RuleExecutor.fetchOkHttpRaw]
     * 的补救链路：过盾 → cookie 回填 → 重发原请求（cf_clearance 与 UA 绑定，回填后必须重发）。
     *
     * @param isJson 是否为 XHR JSON 请求（决定 Accept / X-Requested-With 头）。
     * @return 页面正文；硬失败或重发后仍是挑战页时返回 null。
     */
    private suspend fun requestBody(path: String, accept: String, isJson: Boolean): String? {
        val url = if (path.startsWith("http")) path else "$baseUrl/$path"

        suspend fun tryOnce(): Pair<String?, Boolean> = withContext(Dispatchers.IO) {
            val cookie = SourceCookieManager.cookieHeaderForHost(host).orEmpty()
            val ua = SourceCookieManager.userAgentForHost(host) ?: ChromeMobileUserAgent
            val req = Request.Builder().url(url).apply {
                header("Host", host)
                header("User-Agent", ua)
                header("Accept", accept)
                if (isJson) {
                    header("X-Requested-With", "XMLHttpRequest")
                } else {
                    header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    header("Upgrade-Insecure-Requests", "1")
                }
                header("Referer", "$baseUrl/")
                if (cookie.isNotBlank()) header("Cookie", cookie)
            }.build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.let { if (isJson) it.string() else it.charStream().readText() }
                    val code = if (resp.isSuccessful) 200 else resp.code
                    val challenge = CloudflareDetector.isChallenge(code, body)
                    if (!resp.isSuccessful) {
                        "$TAG requestBody $path -> HTTP $code${if (challenge) " (CF 挑战页)" else ""}".log(TAG)
                    }
                    body to challenge
                }
            }.onFailure { it.log(TAG, "requestBody $path failed") }.getOrElse { null to false }
        }

        val (body1, challenge1) = tryOnce()
        if (!challenge1) return body1

        // 命中挑战页但已持有 clearance → 说明是被站点自有规则拦（非 CF），按硬失败处理。
        if (SourceCookieManager.hasClearance(host)) return body1

        // 尚未过盾 → 走全局 Cloudflare 验证（会顶起 CfVerificationCard 让用户过盾），再重发一次。
        val ua = SourceCookieManager.userAgentForHost(host) ?: ChromeMobileUserAgent
        "$TAG requestBody: 命中 Cloudflare 挑战，触发过盾 url=$url".log(TAG)
        val result = CfVerificationCoordinator.verify(url, ua)
        // 过盾器只在真正拿到 cf_clearance 时才算成功；其余（冷却/超时/伪成功）一律判失败，
        // 返回 null 交由 UI 重试，避免拿无 clearance 的垃圾 Cookie 死循环 403。
        if (result == null || !SourceCookieManager.hasClearance(host)) {
            "$TAG requestBody: 过盾${if (result == null) "失败/冷却中" else "伪成功(无 clearance)"}，返回 null 交由 UI 重试".log(TAG)
            return null
        }
        val (body2, challenge2) = tryOnce()
        // 过盾后重发仍被拦（极少数，例如 Turnstile 需人工点击未完成）→ 返回 null，交由 UI 重试。
        return if (challenge2) null else body2
    }

    private suspend fun fetchHtml(path: String): String? =
        requestBody(path, "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8", false)

    private suspend fun fetchJson(path: String): String? =
        requestBody(path, "application/json, text/javascript, */*; q=0.01", true)

    // ───────────────────────── 公开接口 ─────────────────────────

    /** 首页：头图 + 各分类行。 */
    suspend fun home(): Hanime1Home? = fetchHtml("").let { html ->
        if (html.isNullOrBlank()) null else Hanime1Parser.parseHome(html)
    }

    /**
     * 搜索。
     * @param broad true 时传 broad=on（模糊匹配更广）。
     */
    suspend fun search(
        query: String,
        page: Int = 1,
        genre: String? = null,
        sort: String? = null,
        broad: Boolean = false,
        date: String? = null,
        duration: String? = null,
        tags: Set<String> = emptySet(),
        brands: Set<String> = emptySet(),
    ): List<Hanime1Video> {
        val params = buildList {
            add("query=${query.encodeU()}")
            add("page=$page")
            if (!genre.isNullOrBlank()) add("genre=${genre.encodeU()}")
            if (!sort.isNullOrBlank()) add("sort=${sort.encodeU()}")
            if (broad) add("broad=on")
            if (!date.isNullOrBlank()) add("date=${date.encodeU()}")
            if (!duration.isNullOrBlank()) add("duration=${duration.encodeU()}")
            tags.forEach { add("tags[]=${it.encodeU()}") }
            brands.forEach { add("brands[]=${it.encodeU()}") }
        }.joinToString("&")
        return fetchHtml("search?$params").let { html ->
            if (html.isNullOrBlank()) emptyList() else Hanime1Parser.parseSearch(html)
        }
    }

    /** 视频详情 + 播放直链。 */
    suspend fun video(videoCode: String): Hanime1Detail? =
        fetchHtml("watch?v=$videoCode").let { html ->
            if (html.isNullOrBlank()) null else Hanime1Parser.parseVideo(html)
        }

    /** 评论分页（loadComment 接口，返回 JSON）。 */
    suspend fun comment(videoCode: String, page: Int = 1): Hanime1CommentPage {
        // loadComment 用 id 参数（video code），type=video；page 从 1 开始。
        val json = fetchJson("loadComment?type=video&id=$videoCode&page=$page")
        if (json.isNullOrBlank()) return Hanime1CommentPage(emptyList(), false)
        return Hanime1Parser.parseComments(json)
    }

    /** 新番导览：取首页 + previews 页混排（这里返回首页，preview 页可后续扩展）。 */
    suspend fun preview(date: String): Hanime1Home? = fetchHtml("previews/$date").let { html ->
        if (html.isNullOrBlank()) null else Hanime1Parser.parseHome(html)
    }

    // ───────────────────────── 工具 ─────────────────────────

    private fun String.encodeU(): String =
        java.net.URLEncoder.encode(this, "UTF-8")

    // ───────────────────────── 登录态操作（需 Cloudflare 过盾 + 登录 Cookie） ─────────────────────────

    /**
     * 收藏 / 取消收藏（POST /like）。需要登录态（cf_clearance + 会话 Cookie），未登录会失败。
     * @param isPositive true=收藏，false=取消。
     * @return 是否成功（仅作 UI 反馈，失败原因由调用方提示）。
     */
    suspend fun favorite(videoCode: String, isPositive: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/like"
            val cookie = SourceCookieManager.cookieHeaderForHost(host).orEmpty()
            val ua = SourceCookieManager.userAgentForHost(host) ?: ChromeMobileUserAgent
            val body = okhttp3.FormBody.Builder()
                .add("like-foreign-id", videoCode)
                .add("like-status", if (isPositive) "like" else "unlike")
                .add("like-is-positive", if (isPositive) "1" else "0")
                .build()
            val req = Request.Builder().url(url).post(body).apply {
                header("Host", host)
                header("User-Agent", ua)
                header("Referer", "$baseUrl/watch?v=$videoCode")
                header("X-Requested-With", "XMLHttpRequest")
                if (cookie.isNotBlank()) header("Cookie", cookie)
            }.build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.onFailure { it.log(TAG, "favorite failed") }.getOrDefault(false)
    }
}
