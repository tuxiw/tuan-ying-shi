package com.example.tuanyingshi.util

import android.net.Uri
import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 开屏封面（启动图）个性化偏好，沿用 [DebugPrefs] 的 SharedPreferences + StateFlow 模式。
 *
 * 字段：
 * - [enabled]：是否展示开屏封面（关闭则启动直接进主界面）；
 * - [durationMs]：展示时长（毫秒），范围 [DURATION_MIN_MS, DURATION_MAX_MS]（3–10 秒）；
 * - [source]：封面来源，"picked"（手机相册选择）/ "builtin"（系统内置）/ "url"（自定义链接）；
 * - [pickedUri]：从手机选择的图片 URI（source=picked 时生效，已申请持久读取权限）；
 * - [builtinId]：选中的内置封面 id（见 [BuiltInCovers]）；
 * - [url]：自定义封面链接（source=url 且非空时生效）。
 */
object SplashPrefs {
    const val SOURCE_PICKED = "picked"
    const val SOURCE_BUILTIN = "builtin"
    const val SOURCE_URL = "url"

    /** 展示时长范围（毫秒）：3–10 秒。 */
    const val DURATION_MIN_MS = 3000L
    const val DURATION_MAX_MS = 10000L

    private const val KEY_ENABLED = "splash_cover_enabled"
    private const val KEY_DURATION = "splash_cover_duration_ms"
    private const val KEY_SOURCE = "splash_cover_source"
    private const val KEY_PICKED_URI = "splash_cover_picked_uri"
    private const val KEY_BUILTIN_ID = "splash_cover_builtin_id"
    private const val KEY_URL = "splash_cover_url"

    private val prefs = TuanyingApp.getInstance().preferences

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _durationMs = MutableStateFlow(
        prefs.getLong(KEY_DURATION, DURATION_MIN_MS).let {
            it.coerceIn(DURATION_MIN_MS, DURATION_MAX_MS)
        },
    )
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _source = MutableStateFlow(
        prefs.getString(KEY_SOURCE, SOURCE_BUILTIN).orEmpty().ifBlank { SOURCE_BUILTIN },
    )
    val source: StateFlow<String> = _source.asStateFlow()

    private val _pickedUri = MutableStateFlow(prefs.getString(KEY_PICKED_URI, "").orEmpty())
    val pickedUri: StateFlow<String> = _pickedUri.asStateFlow()

    private val _builtinId = MutableStateFlow(
        prefs.getString(KEY_BUILTIN_ID, BuiltInCovers.DEFAULT_ID).orEmpty()
            .ifBlank { BuiltInCovers.DEFAULT_ID },
    )
    val builtinId: StateFlow<String> = _builtinId.asStateFlow()

    private val _url = MutableStateFlow(prefs.getString(KEY_URL, "").orEmpty())
    val url: StateFlow<String> = _url.asStateFlow()

    // ---- 同步读取（供 SplashActivity 启动时使用） ----
    fun isEnabled(): Boolean = _enabled.value
    fun getDurationMs(): Long = _durationMs.value
    fun getSource(): String = _source.value
    fun getPickedUri(): String = _pickedUri.value
    fun getBuiltinId(): String = _builtinId.value
    fun getUrl(): String = _url.value

    /**
     * 把自定义图片地址解析为 Coil 可直接加载的 model：
     * - 本 App 私有存储的绝对路径（选图时拷贝进 filesDir）→ File，任何进程均可读；
     * - 旧版 content:// URI（未持久化的临时授权）→ Uri，仅会话内有效。
     */
    fun resolvePickedModel(uri: String): Any {
        return if (uri.startsWith("content://")) Uri.parse(uri) else File(uri)
    }

    // ---- 写入（供设置页调用，同时更新内存 StateFlow） ----
    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    fun setDurationMs(value: Long) {
        val clamped = value.coerceIn(DURATION_MIN_MS, DURATION_MAX_MS)
        prefs.edit().putLong(KEY_DURATION, clamped).apply()
        _durationMs.value = clamped
    }

    fun setSource(value: String) {
        prefs.edit().putString(KEY_SOURCE, value).apply()
        _source.value = value
    }

    fun setPickedUri(value: String) {
        prefs.edit().putString(KEY_PICKED_URI, value).apply()
        _pickedUri.value = value
    }

    fun setBuiltinId(value: String) {
        prefs.edit().putString(KEY_BUILTIN_ID, value).apply()
        _builtinId.value = value
    }

    fun setUrl(value: String) {
        prefs.edit().putString(KEY_URL, value).apply()
        _url.value = value
    }
}
