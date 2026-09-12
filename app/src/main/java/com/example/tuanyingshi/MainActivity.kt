package com.example.tuanyingshi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.tuanyingshi.data.repository.UserStatusRepository
import com.example.tuanyingshi.ui.components.LocalUserStatusRepository
import com.example.tuanyingshi.ui.navigation.AppNavigation
import com.example.tuanyingshi.ui.theme.TuanyingshiTheme
import com.example.tuanyingshi.util.ExternalIntentBus
import com.example.tuanyingshi.util.ExternalPlayRequest
import com.example.tuanyingshi.util.NavIntentBus
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureSystemBars()
        handleExternalIntent(intent)
        handleNavRoute(intent)
        setContent {
            // 通过 Hilt 入口点获取用户状态仓储，注入到 CompositionLocal 供全树卡片长按抛弃使用。
            val userStatusRepository = remember {
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    UserStatusEntryPoint::class.java,
                ).userStatusRepository()
            }
            CompositionLocalProvider(LocalUserStatusRepository provides userStatusRepository) {
                TuanyingshiTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation()
                    }
                }
            }
        }
    }

    /**
     * 系统/文件管理器「用本 App 打开」视频时，MainActivity 会带着 ACTION_VIEW 的
     * intent 启动（或已在运行时收到 onNewIntent）。提取视频 uri 投递给导航层。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
        handleNavRoute(intent)
    }

    private fun handleExternalIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = extractExternalUri(intent) ?: return
        val title = intent.getStringExtra(Intent.EXTRA_TITLE) ?: deriveTitle(uri)
        ExternalIntentBus.post(ExternalPlayRequest(uri, title))
        // 消费一次：清空 data/clip，避免配置变更（如旋屏）重建 Activity 时重复触发。
        intent.setData(null)
        intent.clipData = null
        intent.removeExtra(Intent.EXTRA_STREAM)
    }

    /**
     * 从 ACTION_VIEW 意图中提取视频 uri。
     * 不同文件管理器传递方式不一：有的放在 [Intent.getData]，有的放在 [Intent.getClipData]，
     * 有的放在 [Intent.EXTRA_STREAM]，这里全部兼容，避免漏掉导致 App 只进首页。
     */
    private fun extractExternalUri(intent: Intent): Uri? {
        intent.data?.let { return it }
        intent.clipData?.let { if (it.itemCount > 0) return it.getItemAt(0).uri }
        val stream = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        return stream
    }

    /**
     * 来自通知的跳转请求（如「前往查看」下载列表）。把路由字符串投递给导航层，
     * 由 AppNavigation 收集后执行 navigate。投递后清除 extra，避免重建时重复触发。
     */
    private fun handleNavRoute(intent: Intent?) {
        val route = intent?.getStringExtra("nav_route")
        if (!route.isNullOrBlank()) {
            NavIntentBus.requestNavigation(route)
            intent.removeExtra("nav_route")
        }
    }

    private fun deriveTitle(uri: Uri): String? {
        val seg = uri.lastPathSegment ?: return null
        return seg.substringBeforeLast('.').ifBlank { null }
    }

    /**
     * 固定为暗色状态栏/导航栏图标，确保在暗色背景上可见。
     */
    private fun configureSystemBars() {
        val window = window
        val decorView = window.decorView
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}

/** Hilt 入口点：供 Compose 根处取用户状态仓储（抛弃等），注入到 CompositionLocal。 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(SingletonComponent::class)
interface UserStatusEntryPoint {
    fun userStatusRepository(): UserStatusRepository
}
