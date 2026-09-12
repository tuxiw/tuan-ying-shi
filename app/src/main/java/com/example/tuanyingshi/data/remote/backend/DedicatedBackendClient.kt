package com.example.tuanyingshi.data.remote.backend

import com.example.tuanyingshi.util.DedicatedBackendPrefs
import com.example.tuanyingshi.util.applyProxy
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 专属 APP 后端 Retrofit 客户端（实验功能）。
 *
 * 与 [BackendClient] 完全独立：基址来自 [DedicatedBackendPrefs.apiBaseUrl]，
 * 仅用于「内容数据」来源（首页 / 排期 / 排行 / 搜索 / 分类 / 详情 / 剧集），
 * 不承载登录态用户功能（历史 / 收藏 / 评论 / 账号等仍走 [BackendClient]）。
 */
object DedicatedBackendClient {

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var lastBase: String = ""

    private val authInterceptor = Interceptor { chain ->
        val token = DedicatedBackendPrefs.token
        val request = if (token.isNotBlank()) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .applyProxy()
            .addInterceptor(authInterceptor)
            .build()
    }

    private fun build(): BackendApiService {
        val base = DedicatedBackendPrefs.apiBaseUrl
        if (base.isBlank()) error("专属后端地址未配置，请先在「通用设置 → 后端模式 → 专属 APP 后端模式」中填写")
        if (retrofit == null || lastBase != base) {
            retrofit = Retrofit.Builder()
                .baseUrl(base)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
                .build()
                .also { lastBase = base }
        }
        return retrofit!!.create(BackendApiService::class.java)
    }

    val api: BackendApiService
        get() = build()

    /** 测试专属后端连通性：拉一次首页，成功返回 true。 */
    suspend fun testConnection(): Boolean = runCatching {
        val base = DedicatedBackendPrefs.apiBaseUrl
        if (base.isBlank()) return false
        api.home().ok
    }.getOrDefault(false)
}
