package com.example.tuanyingshi.ui.account

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.data.remote.backend.BackendAccount
import com.example.tuanyingshi.util.BackendPrefs
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/**
 * 扫码登录的「扫码端」：相机扫描出码端展示的二维码，
 * 解析出 ticket 后先标记已扫描（让出码端显示「已扫描」），再点「确认登录」完成授权。
 *
 * 需后端模式 + 已登录：scan / confirm 都要求当前账号已登录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()
    val user by BackendAccount.userState.collectAsStateWithLifecycle()

    var scannedTicket by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val t = parseTicket(result.contents)
            if (t != null) {
                scope.launch {
                    BackendAccount.qrScan(t)
                        .onSuccess { scannedTicket = t }
                        .onFailure { scanError = it.message ?: "扫码失败" }
                }
            } else {
                scanError = "无法识别的二维码（仅支持团影视登录码）"
            }
        } else {
            // 用户取消扫码
            navController.popBackStack()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanLauncher.launch(scanOptions())
        } else {
            scanError = "需要相机权限才能扫码"
        }
    }

    LaunchedEffect(Unit) {
        if (!backendMode || user == null) return@LaunchedEffect
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> scanLauncher.launch(scanOptions())
            else -> permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫一扫") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !backendMode || user == null -> {
                    Text("请先在「设置 → 后端模式」开启并登录后端账号，再使用扫一扫。")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { navController.popBackStack() }) { Text("返回") }
                }
                resultMsg != null -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "成功",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(resultMsg!!, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { navController.popBackStack() }) { Text("完成") }
                }
                scannedTicket != null -> {
                    Text(
                        "确认为「${user?.username ?: "当前账号"}」登录？",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "确认后，请在需要登录的设备上完成登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            enabled = !confirming,
                            onClick = {
                                val t = scannedTicket ?: return@OutlinedButton
                                scope.launch { BackendAccount.qrCancel(t) }
                                navController.popBackStack()
                            },
                        ) { Text("取消") }
                        Button(
                            enabled = !confirming,
                            onClick = {
                                val t = scannedTicket ?: return@Button
                                confirming = true
                                scope.launch {
                                    BackendAccount.qrConfirm(t)
                                        .onSuccess { resultMsg = "已确认，请在登录设备上完成登录" }
                                        .onFailure { scanError = it.message ?: "确认失败" }
                                    confirming = false
                                }
                            },
                        ) {
                            if (confirming) {
                                CircularProgressIndicator(Modifier.size(18.dp))
                            } else {
                                Text("确认登录")
                            }
                        }
                    }
                }
                scanError != null -> {
                    Text(scanError!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    if (scanError!!.contains("相机")) {
                        Button(onClick = {
                            scanError = null
                            permLauncher.launch(Manifest.permission.CAMERA)
                        }) { Text("重试") }
                    } else {
                        Button(onClick = { navController.popBackStack() }) { Text("返回") }
                    }
                }
                else -> {
                    Text("正在启动相机…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun scanOptions(): ScanOptions =
    ScanOptions().setPrompt("扫描登录二维码").setBeepEnabled(false).setOrientationLocked(false)

/** 解析团影视登录二维码：tuanyingshi://qrlogin?t=<ticket> */
private fun parseTicket(content: String): String? {
    val uri = android.net.Uri.parse(content)
    if ("tuanyingshi".equals(uri.scheme, true) && "qrlogin".equals(uri.host, true)) {
        return uri.getQueryParameter("t")
    }
    return null
}
