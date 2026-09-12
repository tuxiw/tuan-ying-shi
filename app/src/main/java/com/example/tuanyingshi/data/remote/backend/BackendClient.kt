package com.example.tuanyingshi.data.remote.backend

import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.applyProxy
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 后端 Retrofit 客户端。
 *
 * - baseUrl 来自 [BackendPrefs.apiBaseUrl]，地址变更时自动重建；
 * - 复用全局代理配置（[applyProxy]，实时跟随系统/自定义代理）；
 * - 附加 JWT 拦截器：[BackendPrefs.token] 非空时自动带上 `Authorization: Bearer <token>`。
 */
object BackendClient {

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var lastBase: String = ""

    private val authInterceptor = Interceptor { chain ->
        val token = BackendPrefs.token
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
        val base = BackendPrefs.apiBaseUrl
        if (base.isBlank()) error("后端地址未配置，请先在「通用设置 → 后端模式」中填写")
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

    /** 测试后端连通性：拉一次首页，成功返回 true。 */
    suspend fun testConnection(): Boolean = runCatching {
        val base = BackendPrefs.apiBaseUrl
        if (base.isBlank()) return false
        api.home().ok
    }.getOrDefault(false)
}
