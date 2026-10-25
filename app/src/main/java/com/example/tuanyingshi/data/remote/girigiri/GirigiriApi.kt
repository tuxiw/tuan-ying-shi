package com.example.tuanyingshi.data.remote.girigiri

import com.example.tuanyingshi.util.ChromeMobileUserAgent
import com.example.tuanyingshi.util.applyProxy
import com.example.tuanyingshi.util.log
import com.example.tuanyingshi.util.source_rule.CfVerificationCoordinator
import com.example.tuanyingshi.util.source_rule.CloudflareDetector
import com.example.tuanyingshi.util.source_rule.SourceCookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Girigiri（girigirilove.com）API 资源客户端。
 *
 * 与通用 CSS 规则引擎（[com.example.tuanyingshi.util.source_rule.RuleExecutor]）完全解耦：
 * 站点为 macCMS 架构，全部数据走 m3u8 子域下的 `/api.php/...` JSON 接口，
 * 由本类直连抓取、[GirigiriParser] 解析；域名可在「数据源」里修改，解析逻辑写死在本类。
 *
 * 已核对接口（2026-10）：
 * - 时间表：`App2/weekList`
 * - 排行榜：`App2/vodSortSearch?id={7=TV番组|11=剧场番组}&page&pageSize`
 * - 筛选：`Vod/get_list?type_id&offset&limit&vod_name&vod_class&vod_year&vod_area&orderby&...`
 * - 详情（带播放地址）：`Vod/get_detail?vod_id`
 * - 筛选项：`Vod/getAllTagOption` / `Vod/get_year` / `Vod/get_area` / `Vod/get_class`
 * - 猜你喜欢：`App2/likeList`（POST，user_id + pageSize）
 *
 * 请求头按站点要求带 `Origin` / `Referer`（Web 站域，如 https://ani.girigirilove.com）。
 * Cloudflare：接口域一般不触发，但仍按过盾链路兜底（命中挑战 → 全局过盾 → 重发）。
 */
object GirigiriApi {

    private const val TAG = "GirigiriApi"

    /** 站点默认 Web 域（Referer / Origin 用）。 */
    const val DEFAULT_WEB_URL = "https://ani.girigirilove.com"

    /** 接口默认域（m3u8 子域）。 */
    const val DEFAULT_API_URL = "https://m3u8.girigirilove.com"

    /** 排行榜 id：TV番组 / 剧场番组（与 RankingCategory label 对应由源层映射）。 */
    const val RANK_ID_TV = 7
    const val RANK_ID_MOVIE = 11

    /** 分类 type_id：TV番剧 / 剧场版。 */
    const val TYPE_ID_TV = 2
    const val TYPE_ID_MOVIE = 21

    /** 接口路径前缀：macCMS collect 入口。主站 /api.php 是 SPA HTML，仅 m3u8 子域此路径是 JSON 接口。 */
    private const val API_PATH = "api.php"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .applyProxy()
        .build()

    /**
     * 当前选中的 Girigiri Web 域（含 scheme，无尾斜杠）。
     * 由 [com.example.tuanyingshi.data.remote.parse.GirigiriSource] 在 onEnter / 切源时写入，
     * 与「数据源」里的域名设置保持同步；默认回退到官方域。
     */
    var BASE_URL: String = DEFAULT_WEB_URL

    /** Web 域（无尾斜杠）。 */
    private val webUrl: String
        get() = BASE_URL.trim().trimEnd('/')

    /** 接口域：girigirilove.com 族域名把最左子域换成 m3u8；其它（镜像）域原样使用。 */
    val apiUrl: String
        get() = deriveSubdomainUrl(webUrl, "m3u8")

    /** 接口 host（Cookie / 过盾按此 host 隔离）。 */
    val apiHost: String
        get() = runCatching { java.net.URI(apiUrl).host }.getOrDefault("m3u8.girigirilove.com")

    /** 图片 CDN 域：相对 upload/vod 路径补全用。 */
    private val picUrl: String
        get() = deriveSubdomainUrl(webUrl, "jpg")

    /** 接口请求的 Origin（按站点要求）。 */
    private val origin: String
        get() = webUrl

    /** 接口请求的 Referer（按站点要求）。 */
    private val referer: String
        get() = "$webUrl/"

    /**
     * 把 Web 域的最左子域换成 [label]（girigirilove.com 族：ani/anime → m3u8 / jpg）。
     * 非该族域名（用户自建镜像）原样返回，由其自行托管 api.php。
     */
    private fun deriveSubdomainUrl(web: String, label: String): String {
        val uri = runCatching { java.net.URI(web) }.getOrNull() ?: return DEFAULT_API_URL
        val host = uri.host ?: return DEFAULT_API_URL
        val scheme = uri.scheme ?: "https"
        if (!host.endsWith("girigirilove.com")) return "$scheme://$host"
        val newHost = if (host == "girigirilove.com") "$label.girigirilove.com" else "$label.${host.substringAfter('.')}"
        return "$scheme://$newHost"
    }

    /** 封面地址补全：已是绝对地址原样返回；相对 upload/vod 路径补图片 CDN 域。 */
    fun pic(path: String): String {
        val p = path.trim()
        if (p.isEmpty()) return ""
        if (p.startsWith("http://") || p.startsWith("https://")) return p
        if (p.startsWith("//")) return "https:$p"
        return "${picUrl.trimEnd('/')}/${p.trimStart('/')}"
    }

    // ───────────────────────── 内部请求 ─────────────────────────

    /**
     * 统一请求入口：注入 Origin / Referer / UA / Cookie；命中 Cloudflare 挑战页且本地尚无
     * clearance 时，触发全局过盾（[CfVerificationCoordinator] → WebView 过盾 + cookie 回填），
     * 再用新 Cookie 重发一次（cf_clearance 与 UA 绑定，回填后必须重发）。
     *
     * @param form POST 表单（null 表示 GET）。
     * @param isJson 是否为 XHR JSON 请求（决定 Accept / X-Requested-With 头）。
     * @return 响应正文；硬失败或重发后仍是挑战页时返回 null。
     */
    private suspend fun request(
        path: String,
        form: Map<String, String>? = null,
        isJson: Boolean = true,
    ): String? {
        val url = if (path.startsWith("http")) path else "${apiUrl.trimEnd('/')}/$API_PATH/$path"

        suspend fun tryOnce(): Pair<String?, Boolean> = withContext(Dispatchers.IO) {
            val cookie = SourceCookieManager.cookieHeaderForHost(apiHost).orEmpty()
            val ua = SourceCookieManager.userAgentForHost(apiHost) ?: ChromeMobileUserAgent
            val builder = Request.Builder().url(url).apply {
                header("User-Agent", ua)
                header("Origin", origin)
                header("Referer", referer)
                header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                if (isJson) {
                    header("Accept", "application/json, text/javascript, */*; q=0.01")
                    header("X-Requested-With", "XMLHttpRequest")
                } else {
                    header("Accept", "*/*")
                }
                if (cookie.isNotBlank()) header("Cookie", cookie)
                if (form != null) {
                    val body = FormBody.Builder().also { b -> form.forEach { (k, v) -> b.add(k, v) } }.build()
                    post(body)
                }
            }
            runCatching {
                client.newCall(builder.build()).execute().use { resp ->
                    val body = resp.body?.string()
                    val code = if (resp.isSuccessful) 200 else resp.code
                    val challenge = CloudflareDetector.isChallenge(code, body)
                    if (!resp.isSuccessful) {
                        "$TAG request $path -> HTTP $code${if (challenge) " (CF 挑战页)" else ""}".log(TAG)
                    }
                    body to challenge
                }
            }.onFailure { it.log(TAG, "request $path failed") }.getOrElse { null to false }
        }

        val (body1, challenge1) = tryOnce()
        if (!challenge1) return body1

        // 命中挑战页但已持有 clearance → 被站点自有规则拦（非 CF），按硬失败处理。
        if (SourceCookieManager.hasClearance(apiHost)) return body1

        // 尚未过盾 → 走全局 Cloudflare 验证（顶起 CfVerificationCard 让用户过盾），再重发一次。
        val ua = SourceCookieManager.userAgentForHost(apiHost) ?: ChromeMobileUserAgent
        "$TAG request: 命中 Cloudflare 挑战，触发过盾 url=$url".log(TAG)
        val result = CfVerificationCoordinator.verify(url, ua)
        // 过盾器只在真正拿到 cf_clearance 时才算成功；其余（冷却/超时/伪成功）一律判失败，
        // 返回 null 交由 UI 重试，避免拿无 clearance 的垃圾 Cookie 死循环。
        if (result == null || !SourceCookieManager.hasClearance(apiHost)) {
            "$TAG request: 过盾${if (result == null) "失败/冷却中" else "伪成功(无 clearance)"}，返回 null 交由 UI 重试".log(TAG)
            return null
        }
        val (body2, challenge2) = tryOnce()
        // 过盾后重发仍被拦 → 返回 null，交由 UI 重试。
        return if (challenge2) null else body2
    }

    private fun String.encodeU(): String = java.net.URLEncoder.encode(this, "UTF-8")

    // ───────────────────────── 公开接口 ─────────────────────────

    /** 时间表：一周七天更新列表。week 1=周一 .. 7=周日。 */
    suspend fun weekList(): List<GirigiriWeekDay> =
        parseWeekList(request("App2/weekList").orEmpty())

    /**
     * 排行榜。
     * @param rankId [RANK_ID_TV]=TV番组 / [RANK_ID_MOVIE]=剧场番组。
     */
    suspend fun ranking(rankId: Int, page: Int = 1, pageSize: Int = 20): GirigiriListPage =
        parseListPage(request("App2/vodSortSearch?id=$rankId&page=$page&pageSize=$pageSize").orEmpty())

    /**
     * 分类筛选列表（offset 分页）。
     *
     * @param typeId 分类：2=TV番剧，21=剧场版（null=全部，搜索时用）。
     * @param vodName 名称关键词（搜索用）。
     * @param vodClass 类型（喜剧 / 奇幻 …）。
     * @param vodYear 年份（含 "2000至90年代" 这类非数字值）。
     * @param vodArea 季度（十月 / 一月 …）。
     * @param orderBy 排序：null=更新时间，hits=热度，score=评分。
     */
    suspend fun list(
        typeId: Int? = null,
        offset: Int = 0,
        limit: Int = 24,
        vodName: String? = null,
        vodClass: String? = null,
        vodYear: String? = null,
        vodArea: String? = null,
        vodLang: String? = null,
        vodVersion: String? = null,
        vodState: String? = null,
        orderBy: String? = null,
    ): GirigiriListPage {
        val params = buildList {
            if (typeId != null) add("type_id=$typeId")
            add("offset=$offset")
            add("limit=$limit")
            if (!vodName.isNullOrBlank()) add("vod_name=${vodName.encodeU()}")
            if (!vodClass.isNullOrBlank()) add("vod_class=${vodClass.encodeU()}")
            if (!vodYear.isNullOrBlank()) add("vod_year=${vodYear.encodeU()}")
            if (!vodArea.isNullOrBlank()) add("vod_area=${vodArea.encodeU()}")
            if (!vodLang.isNullOrBlank()) add("vod_lang=${vodLang.encodeU()}")
            if (!vodVersion.isNullOrBlank()) add("vod_version=${vodVersion.encodeU()}")
            if (!vodState.isNullOrBlank()) add("vod_state=${vodState.encodeU()}")
            if (!orderBy.isNullOrBlank()) add("orderby=${orderBy.encodeU()}")
        }.joinToString("&")
        return parseListPage(request("Vod/get_list?$params").orEmpty())
    }

    /** 番剧详情（含播放线路与 m3u8 直链）。 */
    suspend fun detail(vodId: Long): GirigiriDetail? =
        parseDetail(request("Vod/get_detail?vod_id=$vodId").orEmpty())

    /** 筛选项：四个接口并行拉取后合并。 */
    suspend fun filterOptions(): GirigiriFilterOptions? = coroutineScope {
        val tagDeferred = async { request("Vod/getAllTagOption") }
        val yearDeferred = async { request("Vod/get_year?type_id_1=0") }
        val areaDeferred = async { request("Vod/get_area?type_id_1=0") }
        val classDeferred = async { request("Vod/get_class?type_id_1=0") }
        val options = parseFilterOptions(
            tagBody = tagDeferred.await(),
            yearBody = yearDeferred.await(),
            areaBody = areaDeferred.await(),
            classBody = classDeferred.await(),
        )
        if (options.classes.isEmpty() && options.years.isEmpty()) null else options
    }

    /** 猜你喜欢（POST 表单；未登录 user_id 传空，返回站内热门推荐）。 */
    suspend fun likeList(userId: String = "", pageSize: Int = 20): List<GirigiriVod> =
        parseLikeList(
            request(
                path = "App2/likeList",
                form = mapOf("user_id" to userId, "pageSize" to pageSize.toString()),
            ).orEmpty()
        )

    /**
     * 诊断探针：拉一次时间表接口原文（截断），供「数据源诊断」验证接口可达性。
     * 站点 Web 前端在部分地区不可达，接口域才是有效探针目标。
     */
    suspend fun rootProbe(): String =
        request("App2/weekList").orEmpty().take(400)
}
