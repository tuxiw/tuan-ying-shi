package com.example.tuanyingshi.util

import android.widget.Toast
import com.example.tuanyingshi.TuanyingApp
import com.example.tuanyingshi.data.remote.api.cycani.CycaniAuthManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 智能切换数据源中枢：根据当前网络类型，自动把资源源切到用户为该网络配置（或自动挑选）的源；
 * 当数据源加载失败时 [reportFetchFailed] 会提示用户并自动切到下一个可用源。
 *
 * 设计要点：
 * 1. 总开关 [SmartSourcePrefs.enabled]（默认开启）。后端模式下完全禁用（数据源由后端接管）。
 * 2. 仅在「网络类型 / 映射配置变化」或「开关由关到开」时应用，**不覆盖用户在同网络下的手动选择**
 *    —— 通过记录上次应用的源 id（[lastAppliedTargetId]）实现：只要目标源与上次应用的一致就不动手。
 * 3. 离线（[NetworkType.OFFLINE]）且未显式配置离线源时，保持用户当前源（离线无源可用）。
 * 4. 与 [PlaybackPrefs] 的「播放失败时自动换线路/源」是不同维度：本类作用于**资源源级别**，
 *    且由网络变化 / 加载失败驱动，不干预播放器内的线路切换。
 */
object SmartSourceSwitcher {
    @Volatile
    private var started = false

    /** 上次应用（或失败切换）的源 id；用于避免覆盖手动选择、避免回切到刚失败的源。 */
    @Volatile
    private var lastAppliedTargetId: String? = null

    /** 失败自动切换的防抖时间戳，避免并发失败请求反复横跳。 */
    @Volatile
    private var lastFailureSwitchMs = 0L

    private const val FAILURE_COOLDOWN_MS = 8000L

    /** 在 [TuanyingApp.onCreate] 中调用一次，启动对网络类型与开关的监听。 */
    fun init() {
        if (started) return
        started = true
        combine(
            NetworkMonitor.networkType,
            SmartSourcePrefs.enabled,
            SmartSourcePrefs.wifiSourceId,
            SmartSourcePrefs.cellularSourceId,
            SmartSourcePrefs.ethernetSourceId,
        ) { type, enabled, wifi, cellular, ethernet ->
            NetworkCfg(type, enabled, wifi, cellular, ethernet)
        }.combine(BackendPrefs.enabled) { cfg, backend ->
            cfg to backend
        }.onEach { (cfg, backend) ->
            if (!cfg.enabled || backend || BackendPrefs.isBackendMode()) {
                lastAppliedTargetId = null
                return@onEach
            }
            val target = resolveTarget(cfg) ?: return@onEach
            // 目标与上次应用一致（含用户手动选回默认源的情况）→ 不动手
            if (target == lastAppliedTargetId) return@onEach
            lastAppliedTargetId = target
            if (target == SourceHolder.currentSourceId) return@onEach
            // 离线且未显式配置离线源：保持当前源
            if (cfg.type == NetworkType.OFFLINE && SmartSourcePrefs.offlineSourceId.value == null) return@onEach
            SourceHolder.switchSource(target)
            showToast("已根据当前网络切换数据源")
        }.launchIn(GlobalScope)
    }

    /** 计算某网络配置下应使用的源 id（null = 自动）。 */
    private fun resolveTarget(cfg: NetworkCfg): String? {
        val configured = when (cfg.type) {
            NetworkType.WIFI -> cfg.wifi
            NetworkType.ETHERNET -> cfg.ethernet ?: cfg.wifi
            NetworkType.CELLULAR -> cfg.cellular
            NetworkType.OFFLINE -> SmartSourcePrefs.offlineSourceId.value
        }
        return configured ?: autoPick(cfg.type)
    }

    /**
     * 自动挑选：未显式配置时按网络类型给出可用源。
     * 次元城（Cycanime）需登录，未登录跳过；蜂窝网络下优先轻量源（嘶哩嘶哩），WiFi/以太网优先全量源（次元城）。
     */
    private fun autoPick(type: NetworkType): String? {
        val all = SourceHolder.getResourceSources()
        val cycaniLoggedIn = CycaniAuthManager.isLoggedIn
        val usable = all.filter { it.type != SourceMode.Cycanime || cycaniLoggedIn }
        if (usable.isEmpty()) return null
        val rank: (ResourceSource) -> Int = { rs ->
            when (rs.id) {
                "builtin_cycani" -> if (type == NetworkType.CELLULAR) 5 else 0
                "builtin_silisili" -> if (type == NetworkType.CELLULAR) 0 else 1
                "builtin_girigiri" -> 2
                SourceHolder.EXTERNAL_CSS_SOURCE_ID -> 3
                else -> 4
            }
        }
        return usable.minByOrNull(rank)?.id
    }

    /**
     * 数据源加载失败时调用：提示用户并自动切到下一个可用源。
     * - 仅当 [failedSourceId] 正是当前正在使用的源时才处理（避免误切）；
     * - 内置 [FAILURE_COOLDOWN_MS] 防抖，避免多请求并发失败时反复横跳；
     * - 切换后首页 / 详情等已订阅源变化的页面会自动重新加载。
     */
    fun reportFetchFailed(failedSourceId: String?) {
        if (!SmartSourcePrefs.isEnabled()) return
        if (BackendPrefs.isBackendMode()) return
        if (failedSourceId != SourceHolder.currentSourceId) return
        val now = System.currentTimeMillis()
        if (now - lastFailureSwitchMs < FAILURE_COOLDOWN_MS) return
        val next = nextCandidate(failedSourceId) ?: return
        lastFailureSwitchMs = now
        lastAppliedTargetId = next
        val failedName = SourceHolder.getResourceSource(failedSourceId.orEmpty())?.name ?: "当前数据源"
        val nextName = SourceHolder.getResourceSource(next)?.name ?: "其他数据源"
        SourceHolder.switchSource(next)
        showToast("「$failedName」加载失败，已自动切换到「$nextName」")
    }

    /** 选下一个可用源：优先用当前网络配置源（非失败源），否则按优先级取首个非失败源。 */
    private fun nextCandidate(failedSourceId: String?): String? {
        val all = SourceHolder.getResourceSources()
        val cycaniLoggedIn = CycaniAuthManager.isLoggedIn
        val candidates = all.filter { it.id != failedSourceId && (it.type != SourceMode.Cycanime || cycaniLoggedIn) }
        if (candidates.isEmpty()) return null
        val type = NetworkMonitor.networkType.value
        val configured = resolveTarget(NetworkCfg(type, true, SmartSourcePrefs.wifiSourceId.value, SmartSourcePrefs.cellularSourceId.value, SmartSourcePrefs.ethernetSourceId.value))
        if (configured != null && configured != failedSourceId) return configured
        val rank: (ResourceSource) -> Int = { rs ->
            when (rs.id) {
                "builtin_silisili" -> 0
                "builtin_girigiri" -> 1
                SourceHolder.EXTERNAL_CSS_SOURCE_ID -> 2
                "builtin_cycani" -> 3
                else -> 4
            }
        }
        return candidates.minByOrNull(rank)?.id
    }

    private fun showToast(msg: String) {
        runCatching {
            Toast.makeText(TuanyingApp.getInstance(), msg, Toast.LENGTH_LONG).show()
        }
    }

    /** 网络配置快照（combine 用，避免 5+ 流落到 vararg 重载）。 */
    private data class NetworkCfg(
        val type: NetworkType,
        val enabled: Boolean,
        val wifi: String?,
        val cellular: String?,
        val ethernet: String?,
    )
}
