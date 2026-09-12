package com.example.tuanyingshi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.tuanyingshi.util.ProxyHolder
import com.example.tuanyingshi.util.ProxyMode
import com.example.tuanyingshi.util.ProxyType
import android.widget.Toast

/**
 * 网络代理设置页（独立页面，手机端使用）。
 * 支持三种模式：不使用 / 系统代理 / 自定义；
 * 自定义模式下支持 HTTP 与 SOCKS5 两种协议，并可开启账号密码鉴权。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(navController: NavController) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("网络代理", color = MaterialTheme.colorScheme.onBackground) },
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
    ) { inner ->
        ProxySettingsContent(
            navController = navController,
            inline = false,
            modifier = Modifier.fillMaxSize().padding(inner),
        )
    }
}

/**
 * 网络代理内容区（供手机独立页与平板设置左右布局右侧共用）。
 * - inline=false：保存后返回上一页（手机独立页）。
 * - inline=true：保存后仅提示，不返回（平板内嵌于右侧面板）。
 */
@Composable
fun ProxySettingsContent(
    navController: NavController,
    inline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var mode by remember { mutableStateOf(ProxyHolder.mode) }
    var type by remember { mutableStateOf(ProxyHolder.type) }
    var host by remember { mutableStateOf(ProxyHolder.host) }
    var portStr by remember { mutableStateOf(ProxyHolder.port.takeIf { it != 0 }?.toString() ?: "") }
    var useAuth by remember { mutableStateOf(ProxyHolder.username.isNotBlank()) }
    var username by remember { mutableStateOf(ProxyHolder.username) }
    var password by remember { mutableStateOf(ProxyHolder.password) }

    var saving by remember { mutableStateOf(false) }

    fun onSave() {
        val port = portStr.toIntOrNull() ?: 0
        if (mode == ProxyMode.CUSTOM) {
            if (host.isBlank()) {
                Toast.makeText(context, "请输入代理主机地址", Toast.LENGTH_SHORT).show()
                return
            }
            if (port !in 1..65535) {
                Toast.makeText(context, "请输入有效端口（1-65535）", Toast.LENGTH_SHORT).show()
                return
            }
            if (useAuth && username.isBlank()) {
                Toast.makeText(context, "已开启鉴权，请输入账号", Toast.LENGTH_SHORT).show()
                return
            }
        }
        saving = true
        ProxyHolder.save(
            mode = mode,
            type = type,
            host = host,
            port = port,
            username = if (useAuth) username else "",
            password = if (useAuth) password else "",
        )
        saving = false
        if (inline) {
            Toast.makeText(context, "代理设置已保存", Toast.LENGTH_SHORT).show()
        } else {
            navController.popBackStack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle("代理模式")

        // 模式单选卡片
        ProxyModeCard(
            selected = mode == ProxyMode.NONE,
            title = "不使用代理",
            desc = "直接连接，不走任何代理",
            onClick = { mode = ProxyMode.NONE },
        )
        ProxyModeCard(
            selected = mode == ProxyMode.SYSTEM,
            title = "系统代理",
            desc = "使用 Android 系统当前设置的代理",
            onClick = { mode = ProxyMode.SYSTEM },
        )
        ProxyModeCard(
            selected = mode == ProxyMode.CUSTOM,
            title = "自定义代理",
            desc = "使用自定义的 HTTP / SOCKS5 代理服务器",
            onClick = { mode = ProxyMode.CUSTOM },
        )

        if (mode == ProxyMode.CUSTOM) {
            SectionTitle("自定义配置")

            // 协议类型选择：HTTP / SOCKS5
            Text(
                text = "协议类型",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProtocolChip(
                    selected = type == ProxyType.HTTP,
                    label = "HTTP",
                    modifier = Modifier.weight(1f),
                    onClick = { type = ProxyType.HTTP },
                )
                ProtocolChip(
                    selected = type == ProxyType.SOCKS,
                    label = "SOCKS5",
                    modifier = Modifier.weight(1f),
                    onClick = { type = ProxyType.SOCKS },
                )
            }

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("代理主机") },
                placeholder = { Text("例如 127.0.0.1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = portStr,
                onValueChange = { portStr = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("代理端口") },
                placeholder = { Text("例如 7890 / 1080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // 鉴权开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { useAuth = !useAuth }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "身份认证",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "代理服务器需要账号密码时使用",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Switch(checked = useAuth, onCheckedChange = { useAuth = it })
            }

            if (useAuth) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = if (type == ProxyType.SOCKS) {
                    "提示：SOCKS5 鉴权对下载与视频直链（OkHttp）生效；系统 WebView 解析页仅支持 HTTP 代理规则，将回退为系统默认。"
                } else {
                    "提示：填写的账号密码会以 HTTP Basic 方式在代理 407 时自动发送。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSave() },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("保存")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** 代理模式单选卡片。 */
@Composable
private fun ProxyModeCard(
    selected: Boolean,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
            )
            Text(
                text = desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

/** 协议类型药丸（HTTP / SOCKS5）。 */
@Composable
private fun ProtocolChip(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = textColor, fontSize = 14.sp)
    }
}
