package com.example.tuanyingshi.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 网络状态监测（单例）：基于 ConnectivityManager 的 NetworkCallback 实时跟踪联网状态，
 * 通过 [isOnline] 暴露给 Compose。App 启动时在 [init] 注册监听。
 *
 * 无网络时即「离线模式」：在线数据源（次元城等）不可用，仅已下载内容可观看。
 */
/**
 * 网络类型：用于「根据当前网络智能切换数据源」判断应使用的资源源。
 * - [OFFLINE]：无任何联网能力；
 * - [WIFI]：WiFi 网络；
 * - [CELLULAR]：蜂窝移动数据（流量）；
 * - [ETHERNET]：以太网（有线 / 部分车机等）。
 */
enum class NetworkType { OFFLINE, WIFI, CELLULAR, ETHERNET }

object NetworkMonitor {
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** 当前网络传输类型（WiFi / 移动数据 / 以太网 / 离线），供智能切源使用。 */
    private val _networkType = MutableStateFlow(NetworkType.OFFLINE)
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _isOnline.value = isCurrentlyOnline(cm)
        _networkType.value = detectType(cm)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOnline.value = true
                        _networkType.value = detectType(cm)
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = isCurrentlyOnline(cm)
                        _networkType.value = detectType(cm)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        caps: NetworkCapabilities,
                    ) {
                        _isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        _networkType.value = detectType(cm)
                    }
                },
            )
        }
    }

    /** 当前是否真正具备互联网能力（不只是“有网卡”）。 */
    private fun isCurrentlyOnline(cm: ConnectivityManager): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** 依据当前激活网络的传输类型推断网络类型。 */
    private fun detectType(cm: ConnectivityManager): NetworkType {
        val net = cm.activeNetwork ?: return NetworkType.OFFLINE
        val caps = cm.getNetworkCapabilities(net) ?: return NetworkType.OFFLINE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            // 有互联网能力但无法识别具体传输（如 VPN）：当作 WiFi 处理，走「高质量」源
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkType.WIFI
            else -> NetworkType.OFFLINE
        }
    }
}
