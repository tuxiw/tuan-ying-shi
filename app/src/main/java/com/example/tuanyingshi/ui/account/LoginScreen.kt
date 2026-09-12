package com.example.tuanyingshi.ui.account

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.data.remote.backend.BackendAccount
import com.example.tuanyingshi.util.BackendPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 后端账号页：登录（用户名/邮箱 + 密码）与注册（邮箱验证码）两档切换。
 * 成功后写入会话并回退。仅在后端模式下可达。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 登录表单
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // 注册表单
    var regEmail by remember { mutableStateOf("") }
    var regCode by remember { mutableStateOf("") }
    var regUser by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    var regNick by remember { mutableStateOf("") }

    // 顶部模式：登录 / 注册 / 扫码登录
    var mode by remember { mutableStateOf("login") }

    // 扫码登录（出码端）状态
    var qrTicket by remember { mutableStateOf("") }
    var qrImgUrl by remember { mutableStateOf("") }
    var qrStateText by remember { mutableStateOf("等待扫描…") }
    var qrPolling by remember { mutableStateOf(false) }
    var qrPollInterval by remember { mutableStateOf(2000L) }

    fun guardAddress(): Boolean {
        if (!BackendPrefs.isBackendMode() || BackendPrefs.apiBaseUrl.isBlank()) {
            errorMsg = "请先在「设置 → 数据 → 后端模式」中填写后端地址并开启"
            return false
        }
        errorMsg = null
        return true
    }

    fun qrImageUrl(t: String) = BackendPrefs.apiBaseUrl + "auth/qrcode/image?ticket=" + t

    fun startQr() {
        if (!guardAddress()) return
        loading = true
        scope.launch {
            BackendAccount.qrCreate()
                .onSuccess { vo ->
                    val t = vo.ticket ?: run {
                        errorMsg = "获取二维码失败"
                        return@onSuccess
                    }
                    qrTicket = t
                    qrImgUrl = qrImageUrl(t)
                    qrStateText = "等待扫描…"
                    qrPollInterval = (vo.pollIntervalSeconds ?: 2) * 1000L
                    qrPolling = true
                }
                .onFailure { errorMsg = it.message ?: "获取二维码失败" }
            loading = false
        }
    }

    fun refreshQr() {
        qrPolling = false
        qrTicket = ""
        qrImgUrl = ""
        startQr()
    }

    fun doLogin() {
        if (account.isBlank() || password.isBlank()) {
            errorMsg = "账号和密码不能为空"
            return
        }
        if (!guardAddress()) return
        loading = true
        scope.launch {
            BackendAccount.login(account.trim(), password).onSuccess {
                Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }.onFailure {
                errorMsg = it.message ?: "登录失败"
            }
            loading = false
        }
    }

    fun doSendCode() {
        if (regEmail.isBlank()) {
            errorMsg = "请输入邮箱"
            return
        }
        if (!guardAddress()) return
        loading = true
        scope.launch {
            BackendAccount.sendCode(regEmail.trim()).onSuccess {
                Toast.makeText(context, "验证码已发送", Toast.LENGTH_SHORT).show()
            }.onFailure {
                errorMsg = it.message ?: "发送失败"
            }
            loading = false
        }
    }

    fun doRegister() {
        if (regEmail.isBlank() || regCode.isBlank() || regUser.isBlank() || regPass.isBlank()) {
            errorMsg = "邮箱、验证码、用户名、密码均不能为空"
            return
        }
        if (!guardAddress()) return
        loading = true
        scope.launch {
            BackendAccount.register(
                email = regEmail.trim(),
                code = regCode.trim(),
                username = regUser.trim(),
                password = regPass,
                nickname = regNick.trim().ifBlank { null },
            ).onSuccess {
                Toast.makeText(context, "注册并登录成功", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }.onFailure {
                errorMsg = it.message ?: "注册失败"
            }
            loading = false
        }
    }

    // 扫码登录轮询：二维码创建后持续轮询，CONFIRMED 时写入会话并返回
    LaunchedEffect(qrTicket) {
        if (qrTicket.isBlank()) return@LaunchedEffect
        while (qrPolling) {
            delay(qrPollInterval.coerceAtLeast(1500L))
            val vo = BackendAccount.qrPoll(qrTicket).getOrNull() ?: continue
            when (vo.status) {
                "CONFIRMED" -> {
                    vo.login?.let { BackendAccount.applyQrLogin(it) }
                    Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                    return@LaunchedEffect
                }
                "SCANNED" -> qrStateText = "已扫描，请在手机上确认"
                "EXPIRED", "CANCELLED" -> {
                    qrStateText = "二维码已过期"
                    qrPolling = false
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(if (mode == "register") "注册账号" else if (mode == "qr") "扫码登录" else "登录", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = { mode = "login"; qrPolling = false; qrTicket = ""; qrImgUrl = "" },
                    enabled = mode != "login",
                ) { Text("登录") }
                TextButton(
                    onClick = { mode = "register"; qrPolling = false; qrTicket = ""; qrImgUrl = "" },
                    enabled = mode != "register",
                ) { Text("注册") }
                TextButton(
                    onClick = { mode = "qr"; startQr() },
                    enabled = mode != "qr",
                ) { Text("扫码登录") }
            }

            if (mode == "register") {
                OutlinedTextField(
                    value = regEmail,
                    onValueChange = { regEmail = it },
                    label = { Text("邮箱") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = regCode,
                        onValueChange = { regCode = it },
                        label = { Text("验证码") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = ::doSendCode, enabled = !loading) {
                        Text("获取验证码")
                    }
                }
                OutlinedTextField(
                    value = regUser,
                    onValueChange = { regUser = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = regPass,
                    onValueChange = { regPass = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = regNick,
                    onValueChange = { regNick = it },
                    label = { Text("昵称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = ::doRegister, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("注册并登录")
                }
            } else if (mode == "qr") {
                // 扫码登录（出码端）：展示后端直出的二维码 PNG，轮询状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrImgUrl.isNotBlank() && qrStateText != "二维码已过期") {
                        AsyncImage(
                            model = qrImgUrl,
                            contentDescription = "登录二维码",
                            modifier = Modifier
                                .size(200.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Text(
                            text = if (qrStateText == "二维码已过期") "二维码已过期" else "二维码生成中…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(
                    text = qrStateText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (qrStateText == "二维码已过期") {
                    Button(onClick = ::refreshQr, modifier = Modifier.fillMaxWidth()) {
                        Text("刷新二维码")
                    }
                }
                Text(
                    text = "用已登录的「团影视」App「扫一扫」扫描此码完成登录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("用户名或邮箱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = ::doLogin, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("登录")
                }
            }

            errorMsg?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
