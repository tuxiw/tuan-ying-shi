package com.example.tuanyingshi.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicReference

/**
 * 验证码输入宿主：把 [KazumiWebViewVerifier] 的「suspend 取用户输入」桥接到 Compose 弹窗。
 *
 * 用法（在任意 Composable 作用域）：
 * ```
 * val host = rememberCaptchaInputHost()
 * LaunchedEffect(Unit) { KazumiWebViewVerifier.captchaInputProvider = { host.request(it) } }
 * // ...搜索触发...
 * host.Render()   // 在可组合树里渲染弹窗
 * ```
 */
class CaptchaInputHost {
    private val _pending = MutableStateFlow<CaptchaRequest?>(null)
    internal val pending: StateFlow<CaptchaRequest?> = _pending.asStateFlow()

    private val _current = AtomicReference<CompletableDeferred<String?>?>(null)

    /** 供验证器在协程中调用：弹出对话框并挂起，直到用户提交或取消。返回用户输入文本或 null。 */
    suspend fun request(imageBase64: String): String? {
        val deferred = CompletableDeferred<String?>()
        _current.set(deferred)
        _pending.update { CaptchaRequest(imageBase64, deferred) }
        return try {
            deferred.await()
        } finally {
            _pending.compareAndSet(CaptchaRequest(imageBase64, deferred), null)
            if (_current.get() === deferred) _current.set(null)
        }
    }

    fun submit(code: String) {
        _current.getAndSet(null)?.complete(code)
        _pending.value = null
    }

    fun cancel() {
        _current.getAndSet(null)?.complete(null)
        _pending.value = null
    }

    @Composable
    fun Render() {
        val req by pending.collectAsState()
        if (req != null) {
            CaptchaDialog(
                imageBase64 = req!!.imageBase64,
                onSubmit = { submit(it) },
                onCancel = { cancel() },
            )
        }
    }
}

internal data class CaptchaRequest(
    val imageBase64: String,
    val deferred: CompletableDeferred<String?>,
)

@Composable
fun rememberCaptchaInputHost(): CaptchaInputHost = remember { CaptchaInputHost() }

@Composable
fun CaptchaDialog(
    imageBase64: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = { onSubmit(code) }, enabled = code.isNotBlank()) { Text("提交") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
        title = { Text("验证码验证") },
        text = {
            Column {
                val bitmap = remember(imageBase64) { base64ToBitmap(imageBase64) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "验证码图片",
                        modifier = Modifier.height(80.dp),
                    )
                } else {
                    Text("正在加载验证码图片…")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("请输入验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrect = false),
                )
            }
        },
    )
}

private fun base64ToBitmap(dataUrl: String): android.graphics.Bitmap? {
    val base64 = dataUrl.substringAfter("base64,", "").takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
