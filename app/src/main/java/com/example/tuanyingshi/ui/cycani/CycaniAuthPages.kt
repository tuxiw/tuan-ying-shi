package com.example.tuanyingshi.ui.cycani

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.navigation.Screen

/**
 * 次元城源站登录页（全屏页面，替代旧登录弹窗）。
 *
 * @param initialUsername 回填账号（登录失效重新登录时沿用上次账号）
 * @param pendingSourceId 非空表示因点击该次元城源而进入，登录成功后自动切到该源
 */
@Composable
fun CycaniLoginPage(
    navController: NavController,
    initialUsername: String = "",
    pendingSourceId: String = "",
    viewModel: CycaniAuthViewModel = hiltViewModel(),
) {
    val loggingIn by viewModel.loggingIn.collectAsStateWithLifecycle()
    val error by viewModel.loginError.collectAsStateWithLifecycle()

    var username by remember { mutableStateOf(initialUsername.ifBlank { viewModel.lastUsername.orEmpty() }) }
    var password by remember { mutableStateOf("") }

    AuthScaffold(
        title = "欢迎您的到来",
        subtitle = "登录次元城账号，同步源站登录状态",
        onBack = { navController.popBackStack() },
    ) {
        AuthField(
            icon = Icons.Filled.Person,
            value = username,
            onValueChange = { username = it },
            placeholder = "账号 / 用户名",
            enabled = !loggingIn,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(14.dp))
        AuthField(
            icon = Icons.Filled.Lock,
            value = password,
            onValueChange = { password = it },
            placeholder = "密码",
            enabled = !loggingIn,
            isPassword = true,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(
                onDone = {
                    viewModel.login(username, password, pendingSourceId) { navController.popBackStack() }
                },
            ),
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))
        AuthActionButton(
            text = "登录",
            isLoading = loggingIn,
            enabled = !loggingIn,
        ) {
            viewModel.login(username, password, pendingSourceId) { navController.popBackStack() }
        }

        Spacer(Modifier.height(20.dp))
        AuthSwitchLink(
            prefix = "还没有账号？",
            linkText = "注册",
        ) {
            viewModel.clearLoginError()
            navController.navigate(Screen.CycaniRegister.create(email = username.takeIf { it.contains("@") }.orEmpty())) {
                popUpTo(Screen.CycaniLogin.route) { inclusive = true }
            }
        }
    }
}

/**
 * 次元城源站注册页（邮箱验证码，全屏页面）。
 *
 * 站方流程：
 * 1. POST /api/auth/send-code  { email, type: "register" } → 邮箱收到验证码；
 * 2. POST /api/v2/auth/register { username, password, email, email_code }（成功后自动登录）。
 */
@Composable
fun CycaniRegisterPage(
    navController: NavController,
    initialEmail: String = "",
    viewModel: CycaniAuthViewModel = hiltViewModel(),
) {
    val registering by viewModel.registering.collectAsStateWithLifecycle()
    val sendingCode by viewModel.sendingCode.collectAsStateWithLifecycle()
    val countdown by viewModel.codeCountdown.collectAsStateWithLifecycle()
    val error by viewModel.registerError.collectAsStateWithLifecycle()
    val info by viewModel.registerInfo.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf(initialEmail) }
    var code by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    // 本地校验错误（两次密码不一致等），与服务端返回的 error 分开
    var localError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        localError = when {
            email.isBlank() || code.isBlank() || username.isBlank() || password.isBlank() -> "请填写完整的注册信息"
            password != confirmPassword -> "两次输入的密码不一致"
            else -> null
        }
        if (localError == null) {
            viewModel.register(username, password, email, code, pendingSourceId = "") {
                navController.popBackStack()
            }
        }
    }

    AuthScaffold(
        title = "创建您的账号",
        subtitle = "注册次元城账号，注册后自动登录",
        onBack = { navController.popBackStack() },
    ) {
        AuthField(
            icon = Icons.Filled.Email,
            value = email,
            onValueChange = { email = it },
            placeholder = "您的邮箱",
            enabled = !registering,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuthField(
                icon = Icons.Filled.Lock,
                value = code,
                onValueChange = { if (it.length <= 8) code = it },
                placeholder = "邮箱验证码",
                enabled = !registering,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            TextButton(
                onClick = { viewModel.sendCode(email) },
                enabled = !registering && !sendingCode && countdown <= 0 && email.isNotBlank(),
            ) {
                if (sendingCode) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (countdown > 0) "${countdown}s" else "获取验证码")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        AuthField(
            icon = Icons.Filled.Person,
            value = username,
            onValueChange = { username = it },
            placeholder = "用户名",
            enabled = !registering,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(14.dp))
        AuthField(
            icon = Icons.Filled.Lock,
            value = password,
            onValueChange = { password = it },
            placeholder = "密码",
            enabled = !registering,
            isPassword = true,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(14.dp))
        AuthField(
            icon = Icons.Filled.Lock,
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            placeholder = "确认密码",
            enabled = !registering,
            isPassword = true,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { if (!registering) submit() }),
        )

        if (info != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = info.orEmpty(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val shownError = localError ?: error
        if (shownError != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = shownError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))
        AuthActionButton(
            text = "注册",
            isLoading = registering,
            enabled = !registering,
        ) { submit() }

        Spacer(Modifier.height(20.dp))
        AuthSwitchLink(
            prefix = "已有账号？",
            linkText = "登录",
        ) {
            viewModel.clearRegisterState()
            navController.navigate(Screen.CycaniLogin.create(username = username)) {
                popUpTo(Screen.CycaniRegister.route) { inclusive = true }
            }
        }
    }
}

// ───────────────── 两种页面共用的骨架与控件 ─────────────────

/**
 * 认证页骨架：顶部返回行 + 居中标题区 + 内容列（可滚动，避让输入法）。
 */
@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(28.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            AuthLogo()
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(36.dp))
        content()
        Spacer(Modifier.height(32.dp))
    }
}

/** 顶部 Logo：主色渐变圆形 + 「次」字。 */
@Composable
private fun AuthLogo() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "次",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 带前导图标的输入框（支持密码可见性切换）。 */
@Composable
private fun AuthField(
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "隐藏密码" else "显示密码",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.medium,
    )
}

/** 全宽主操作按钮（登录 / 注册），加载中显示转圈。 */
@Composable
private fun AuthActionButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 底部「已有账号？登录」式切换链接。 */
@Composable
private fun AuthSwitchLink(
    prefix: String,
    linkText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClick) {
            Text(text = linkText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}
