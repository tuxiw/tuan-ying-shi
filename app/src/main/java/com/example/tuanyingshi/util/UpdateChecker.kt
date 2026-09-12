package com.example.tuanyingshi.util

import android.content.Context
import android.os.Build
import com.example.tuanyingshi.BuildConfig
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.data.remote.backend.VersionCheckVO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 更新检查器（单例）。
 *
 * 负责：
 * - 拉取远端 [UPDATE_URL] 的更新信息 JSON；
 * - 与本地 [BuildConfig.VERSION_CODE] 比较，判断是否有新版本；
 * - 维护对话框状态 [dialogState]（有更新 / 已是最新 / 失败）与检查中状态 [checking]；
 * - 根据设备 ABI 选择最合适的下载地址。
 *
 * 手动检查（[checkAsync] manual=true）会在「无更新 / 失败」时也给出反馈；
 * 启动自动检查（manual=false）仅在有新版本时才弹窗，静默失败。
 */
object UpdateChecker {
    /** 更新检查地址（团影视官方发布通道）。 */
    const val UPDATE_URL: String = "https://tuanyingshi.pages.dev/v1/update.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** 独立 OkHttpClient（不带代理），直连发布 CDN。 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _dialogState = MutableStateFlow<UpdateDialogState?>(null)
    val dialogState: StateFlow<UpdateDialogState?> = _dialogState.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** 最近一次检查结果（供「软件更新」页持久化展示状态徽标，对话框关闭后不丢失）。 */
    private val _lastResult = MutableStateFlow<UpdateResult>(UpdateResult.Idle)
    val lastResult: StateFlow<UpdateResult> = _lastResult.asStateFlow()

    /** 最近一次检查完成的时间戳（毫秒），用于「上次检查」展示。 */
    private val _lastCheckedAt = MutableStateFlow<Long?>(null)
    val lastCheckedAt: StateFlow<Long?> = _lastCheckedAt.asStateFlow()

    /** 当前 App 版本名（如 0.3.4）。 */
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /**
     * 统一版本号展示：保证恰好一个前导 v。
     * 例如 0.3.6 → v0.3.6；v0.3.6 / V0.3.6 → v0.3.6。
     * 用于避免远端 version 已带 v 时显示成 vv0.3.6。
     */
    fun formatVersion(raw: String): String = "v" + raw.removePrefix("v").removePrefix("V")

    /** 当前 App 版本号（整数，用于比较）。 */
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE

    /**
     * 执行一次更新检查（挂起函数）。
     * @param manual true=用户手动点击（无更新/失败也反馈）；false=启动自动检查（仅发现更新才弹窗）。
     */
    suspend fun check(manual: Boolean) {
        if (_checking.value) return
        _checking.value = true
        try {
            // 后端模式：版本信息由自建后端下发（后台「版本管理」维护），不再读官方 CDN 的 update.json
            if (BackendPrefs.isBackendMode()) {
                checkFromBackend(manual)
                return
            }
            val resp = withContext(Dispatchers.IO) { fetchAndParse() }
            val remoteCode = resp.data?.versionCode ?: 0
            if (remoteCode > currentVersionCode) {
                val data = resp.data ?: throw IllegalStateException("更新数据为空")
                _lastResult.value = UpdateResult.Available
                _dialogState.value = UpdateDialogState.Available(data.toUpdateInfo(resp.website, resp.github))
            } else if (manual) {
                _lastResult.value = UpdateResult.UpToDate
                _dialogState.value = UpdateDialogState.NoUpdate(currentVersionName)
            }
        } catch (e: Throwable) {
            if (manual) {
                _lastResult.value = UpdateResult.Failed(e.message ?: "未知错误")
                _dialogState.value = UpdateDialogState.Failed(e.message ?: "未知错误")
            }
        } finally {
            _checking.value = false
            _lastCheckedAt.value = System.currentTimeMillis()
        }
    }

    /** 非挂起版本：在独立作用域发起检查，供 Compose onClick / Application 启动直接调用。 */
    fun checkAsync(manual: Boolean) {
        scope.launch { check(manual) }
    }

    /** 关闭更新对话框。 */
    fun dismiss() {
        _dialogState.value = null
    }

    /**
     * 调试用：弹出指定更新信息的「有更新」对话框。
     * 忽略版本号比较，用于预览更新 UI、测试应用内更新与下载流程。
     */
    fun showAvailable(info: UpdateInfo) {
        _dialogState.value = UpdateDialogState.Available(info)
    }

    /**
     * 调试用：拉取远端更新信息（忽略版本号比较），供下载测试 / 弹窗预览使用。
     * 网络或解析失败时返回 null。
     */
    suspend fun fetchUpdateInfo(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val resp = fetchAndParse()
            resp.data?.toUpdateInfo(resp.website, resp.github)
        }.getOrNull()
    }

    /**
     * 后端模式下的检查更新：走自建后端 `GET /ops/version/check`。
     * 结果映射成 [UpdateInfo]，从而完全复用现有的更新弹窗与应用内下载流程。
     */
    private suspend fun checkFromBackend(manual: Boolean) {
        val resp = withContext(Dispatchers.IO) {
            BackendClient.api.checkVersion(platform = "android", versionCode = currentVersionCode)
        }
        if (!resp.ok) throw IllegalStateException(resp.message ?: "检查更新失败")
        val vo = resp.data
        if (vo != null && vo.hasUpdate == true) {
            _lastResult.value = UpdateResult.Available
            _dialogState.value = UpdateDialogState.Available(vo.toUpdateInfo())
        } else if (manual) {
            _lastResult.value = UpdateResult.UpToDate
            _dialogState.value = UpdateDialogState.NoUpdate(currentVersionName)
        }
    }

    private fun fetchAndParse(): UpdateResponse {
        val request = Request.Builder().url(UPDATE_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            return json.decodeFromString(UpdateResponse.serializer(), body)
        }
    }

    /**
     * 选择「应用内更新」要下载的 APK。
     *
     * 规则：
     * 1. 渠道按用户语言选：简体中文→gitee，英语→github，其他语言兜底→github；
     * 2. 架构按当前设备 ABI 选对应包，缺失则回退 universal；
     * 3. 首选渠道无对应包时，回退到另一渠道（gitee↔github）；
     * 4. 两个渠道都没有任何可用包时，返回 null（调用方应回退到网页渠道选择）。
     *
     * @return [InAppUpdateTarget]（下载地址 + 渠道名 + 文件名），无可用地址时返回 null。
     */
    fun pickInAppUpdate(info: UpdateInfo, context: Context): InAppUpdateTarget? {
        val downloads = info.downloads ?: return null
        val abi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase() ?: "armeabi-v7a"
        val lang = currentLang(context)
        // 简体中文走 gitee，英语走 github，其余语言兜底走 github（国际化覆盖更好）
        val preferred = if (lang == "zh") "gitee" else "github"
        val other = if (preferred == "gitee") "github" else "gitee"

        for (ch in listOf(preferred, other)) {
            val channel = (if (ch == "gitee") downloads.gitee else downloads.github) ?: continue
            val ad = channel.android ?: continue
            val item = pickByAbi(ad, abi) ?: ad.universal ?: continue
            if (item.url.isBlank()) continue
            val fileName = "tuanyingshi_${formatVersion(info.version).removePrefix("v")}_$ch.apk"
            return InAppUpdateTarget(url = item.url, channel = ch, fileName = fileName)
        }
        // 两渠道都没有可用包：应用内无法更新，回退到网页
        return null
    }

    /**
     * 应用内更新完全不可用时的兜底：直接打开网页下载页。
     * 优先发布页 [UpdateInfo.website]，其次 GitHub 页面 [UpdateInfo.github]。
     */
    fun pickFallbackWebUrl(info: UpdateInfo): String? =
        info.website?.takeIf { it.isNotBlank() }
            ?: info.github?.takeIf { it.isNotBlank() }

    /** 按设备 ABI 选对应架构包；unknown 架构返回 null（交由调用方回退 universal）。 */
    private fun pickByAbi(ad: AndroidDownloads, abi: String): DownloadItem? = when {
        abi.contains("arm64") -> ad.arm64v8a
        abi.contains("armeabi") || abi.contains("armv7") -> ad.armeabiv7a
        abi.contains("x86_64") -> ad.x86_64
        else -> null
    }

    /** 读取系统首选语言代码（zh / en / …），用于渠道选择兜底。 */
    private fun currentLang(context: Context): String {
        val locale = if (Build.VERSION.SDK_INT >= 24) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return locale.language.lowercase()
    }
}

// ───────── 数据模型 ─────────

@Serializable
data class UpdateResponse(
    val code: Int = 200,
    val message: String? = null,
    val data: UpdateData? = null,
    val publishTime: String? = null,
    /** 官方发布页地址（顶层字段，与 data 同级）。 */
    val website: String? = null,
    /** GitHub Release 下载页地址（顶层字段）。 */
    val github: String? = null,
    /** Gitee Release 下载页地址（顶层字段，兜底用）。 */
    val gitee: String? = null,
)

@Serializable
data class UpdateData(
    val version: String = "",
    val versionCode: Int = 0,
    val isForceUpdate: Boolean = false,
    val updateNote: String? = null,
    val changelog: Changelog? = null,
    val downloads: Downloads? = null,
) {
    fun toUpdateInfo(website: String?, github: String?) = UpdateInfo(
        version = version,
        versionCode = versionCode,
        isForceUpdate = isForceUpdate,
        updateNote = updateNote,
        changelog = changelog,
        downloads = downloads,
        website = website,
        github = github,
    )
}

@Serializable
data class Changelog(
    val fixes: List<String> = emptyList(),
    val features: List<String> = emptyList(),
)

@Serializable
data class Downloads(
    /** GitHub 渠道的安装包（含 android 子级）。 */
    val github: ChannelDownloads? = null,
    /** Gitee 渠道的安装包（含 android 子级）。 */
    val gitee: ChannelDownloads? = null,
)

@Serializable
data class ChannelDownloads(
    /** Android 平台安装包（按架构细分）。 */
    val android: AndroidDownloads? = null,
)

@Serializable
data class AndroidDownloads(
    @SerialName("universal") val universal: DownloadItem? = null,
    @SerialName("arm64-v8a") val arm64v8a: DownloadItem? = null,
    @SerialName("armeabi-v7a") val armeabiv7a: DownloadItem? = null,
    @SerialName("x86_64") val x86_64: DownloadItem? = null,
)

@Serializable
data class DownloadItem(
    val url: String = "",
    val size: String? = null,
    val md5: String? = null,
    val description: String? = null,
)

/** 解析后的更新信息（供 UI 层使用）。 */
data class UpdateInfo(
    val version: String,
    val versionCode: Int,
    val isForceUpdate: Boolean,
    val updateNote: String?,
    val changelog: Changelog?,
    val downloads: Downloads?,
    val website: String? = null,
    val github: String? = null,
)

/** 应用内更新要下载的目标（地址 + 渠道 + 文件名）。 */
data class InAppUpdateTarget(
    val url: String,
    val channel: String,
    val fileName: String,
)

/**
 * 后端版本检查结果 → [UpdateInfo]。
 *
 * 后端只下发单一 [VersionCheckVO.downloadUrl]（无 gitee/github 分包结构），
 * 这里把它放进 universal 包，使 [UpdateChecker.pickInAppUpdate] 能按现有规则
 * 选中它并复用应用内下载；同时作为 website 兜底，供无法应用内更新时打开网页下载。
 */
private fun VersionCheckVO.toUpdateInfo(): UpdateInfo {
    val url = downloadUrl.orEmpty()
    val hasUrl = url.isNotBlank()
    val item = DownloadItem(url = url, size = fileSize?.toString(), md5 = md5)
    val android = AndroidDownloads(universal = item.takeIf { hasUrl })
    val channelPkg = ChannelDownloads(android = android)
    return UpdateInfo(
        version = latestVersionName.orEmpty(),
        versionCode = latestVersionCode ?: 0,
        isForceUpdate = forceUpdate == true,
        updateNote = releaseNotes,
        changelog = null,
        downloads = Downloads(github = channelPkg, gitee = channelPkg).takeIf { hasUrl },
        website = url.takeIf { hasUrl },
        github = null,
    )
}

/** 最近一次更新检查的结果（供「软件更新」页展示状态徽标）。 */
sealed interface UpdateResult {
    /** 尚未检查过。 */
    object Idle : UpdateResult

    /** 已是最新版本。 */
    object UpToDate : UpdateResult

    /** 发现新版本。 */
    object Available : UpdateResult

    /** 检查失败。 */
    data class Failed(val message: String) : UpdateResult
}

/** 更新检查对话框状态。 */
sealed interface UpdateDialogState {
    /** 发现新版本。 */
    data class Available(val info: UpdateInfo) : UpdateDialogState

    /** 已是最新版本（手动检查反馈）。 */
    data class NoUpdate(val versionName: String) : UpdateDialogState

    /** 检查失败（手动检查反馈）。 */
    data class Failed(val message: String) : UpdateDialogState
}
