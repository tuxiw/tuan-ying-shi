package com.example.tuanyingshi.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.util.BackendPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 后端模式设置页：开启后 App 全部内容数据（首页 / 排期 / 排行 / 搜索 / 分类 / 详情 / 剧集 / 弹幕）
 * 统一走自建后端 API，并禁用本地的数据源 / 弹幕源切换（见 [com.example.tuanyingshi.util.SourceHolder] /
 * [com.example.tuanyingshi.util.BackendPrefs]）。
 *
 * 入口位于「设置 → 数据 → 后端模式」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendSettingsScreen(
    navController: NavController,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("后端模式", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            BackendSettingsContent(navController)
        }
    }
}

/**
 * 后端模式设置内容区（供手机页与平板左右布局右侧共用）。
 */
@Composable
fun BackendSettingsContent(
    navController: NavController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()

    SwitchRow(
        icon = Icons.Filled.Cloud,
        title = "启用后端模式",
        desc = "开启后，首页 / 排期 / 排行 / 搜索 / 分类 / 详情 / 剧集 / 弹幕 全部走自建后端 API；本地数据源与弹幕源切换将被禁用",
        checked = backendMode,
        onCheckedChange = { BackendPrefs.setEnabled(it) },
    )

    if (backendMode) {
        var backendUrl by remember { mutableStateOf(BackendPrefs.baseUrl) }
        var backendToken by remember { mutableStateOf(BackendPrefs.token) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = backendUrl,
                onValueChange = {
                    backendUrl = it
                    BackendPrefs.setBaseUrl(it)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("后端地址") },
                placeholder = { Text("如 http://192.168.1.10:8080") },
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                BackendPrefs.setBaseUrl(backendUrl)
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { BackendClient.testConnection() }.getOrDefault(false)
                    }
                    Toast.makeText(
                        context,
                        if (ok) "连接成功" else "连接失败，请检查后端地址",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }) { Text("测试") }
        }

        OutlinedTextField(
            value = backendToken,
            onValueChange = {
                backendToken = it
                BackendPrefs.setToken(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            singleLine = true,
            label = { Text("访问令牌（可选）") },
            placeholder = { Text("留空则匿名访问公开内容接口") },
        )

        Text(
            text = "提示：后端地址只需填写根地址（可含 /api/v1），应用会自动补全。匿名访问可读取公开内容接口；如需评论 / 收藏等登录态功能，请填入后端下发的 JWT。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }

    Spacer(Modifier.height(24.dp))
}
