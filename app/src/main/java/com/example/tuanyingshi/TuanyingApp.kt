package com.example.tuanyingshi

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.tuanyingshi.data.remote.backend.BackendAccount
import com.example.tuanyingshi.util.DownloadManager
import com.example.tuanyingshi.util.NetworkMonitor
import com.example.tuanyingshi.util.NotifPrefs
import com.example.tuanyingshi.util.ProxyHolder
import com.example.tuanyingshi.util.AutoBackup
import com.example.tuanyingshi.util.sync.WebDavSync
import com.example.tuanyingshi.util.source_rule.SourceSubscriptionRepository
import com.example.tuanyingshi.util.applyProxy
import com.example.tuanyingshi.util.UpdateChecker
import com.example.tuanyingshi.util.UpdatePrefs
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.BackendPushPrefs
import okhttp3.OkHttpClient
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class TuanyingApp : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        _instance = this

        // 提前加载并应用已保存的代理配置（含下载模块 / 播放器 / WebView）
        ProxyHolder.ensureInitialized()

        // 初始化网络状态监测（离线模式判断）
        NetworkMonitor.init(this)
        // 启动即离线时给一次提示；可在「设置 → 消息通知 → 离线模式提示」中关闭
        if (!NetworkMonitor.isOnline.value && NotifPrefs.isOfflineToastEnabled()) {
            android.widget.Toast.makeText(
                this,
                "无网络连接，已进入离线模式：仅可观看已下载内容",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }

        // 预加载根域名，提前确认 OkHttp + 伪 Host 连通性
        appScope.launch {
            runCatching { DownloadManager.getHtml("https://www.cycani.org","www.cycani.org") }
        }

        // 启动数据源订阅的每 1 小时自动更新（首次会补更从未更新过的订阅）
        SourceSubscriptionRepository.startAutoUpdate()

        // 启动后若开启自动备份且距上次备份已超过间隔，则生成一份全量备份
        AutoBackup.runDue(this)

        // 启动后若开启 WebDAV 同步且勾选「启动时自动同步」，则执行一次双向同步
        WebDavSync.runDue(this)

        // 启动后若开启「自动检查更新」，则静默检查一次（仅在发现新版本时才弹窗）。
        // 后端模式下受「后端软件更新推送」开关约束：关闭后不再自动检查后端下发的版本更新。
        appScope.launch {
            if (UpdatePrefs.isAutoCheckEnabled() && (!BackendPrefs.isBackendMode() || BackendPushPrefs.isUpdatePushEnabled())) {
                UpdateChecker.check(manual = false)
            }
        }

        // 启动心跳：上报设备型号 / 系统版本 / App 版本，供后台「用户分析 - 登录设备」展示。
        // 内部按 12 小时节流，且只在后端模式 + 已登录时真正发请求，失败静默忽略。
        appScope.launch {
            runCatching { BackendAccount.reportDevice() }
        }
    }

    /**
     * Coil 封面图加载也挂动态代理选择器：
     * 每次请求实时读取 ProxyHolder 配置，改代理后无需重启即生效。
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { OkHttpClient.Builder().applyProxy().build() }
            .build()
    }

    companion object {
        private lateinit var _instance: Application

        fun getInstance(): Context {
            return _instance
        }
    }
}
