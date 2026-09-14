package com.example.tuanyingshi.ui.account

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.data.remote.backend.BackendAccount
import com.example.tuanyingshi.util.BackendPrefs
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.launch

/**
 * 扫码登录的「扫码端」：相机扫描出码端展示的二维码，
 * 解析出 ticket 后先标记已扫描（让出码端显示「已扫描」），再点「确认登录」完成授权。
 *
 * 界面说明：
 * - 相机画面**内嵌在本页**（自绘取景框 + 激光线 + 手电筒），不再跳转第三方扫码页，
 *   避免出现默认页方向跟着传感器旋转、黑底无框的问题。
 * - 相机不可用 / 扫码失败时，可点「手动输入登录码」粘贴登录码兜底。
 *
 * 需后端模式 + 已登录：scan / confirm 都要求当前账号已登录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val backendMode by BackendPrefs.enabled.collectAsStateWithLifecycle()
    val user by BackendAccount.userState.collectAsStateWithLifecycle()

    var scannedTicket by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var manualInput by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    // 一次会话只处理一次扫码结果，避免相机连续回调重复提交
    var handled by remember { mutableStateOf(false) }

    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val barcodeViewRef = remember { mutableStateOf<DecoratedBarcodeView?>(null) }

    // 前置条件：后端模式 + 已登录
    val canScan = backendMode && user != null

    // 权限申请（进入页面即申请一次；被拒后可在权限卡片里重试）
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCamPermission = granted
        if (!granted) scanError = "需要相机权限才能扫码"
    }

    LaunchedEffect(canScan, hasCamPermission) {
        if (canScan && !hasCamPermission) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /** 处理一帧扫码结果：解析 ticket 并向后端登记「已扫描」。 */
    val onContents: (String) -> Unit = { contents ->
        if (!handled) {
            val t = parseTicket(contents)
            if (t == null) {
                scanError = "无法识别的二维码（仅支持团影视登录码）"
            } else {
                handled = true
                scope.launch {
                    BackendAccount.qrScan(t)
                        .onSuccess { scannedTicket = t }
                        .onFailure {
                            handled = false
                            scanError = it.message ?: "扫码失败"
                        }
                }
            }
        }
    }

    // 是否任意一张卡片（权限 / 确认 / 成功 / 失败 / 手动输入）遮挡了扫码区
    val showCard = !canScan || !hasCamPermission || resultMsg != null ||
        scannedTicket != null || scanError != null || showManual
    // 相机是否应处于运行状态：前置满足 + 有权限 + 没有卡片遮挡
    val scanning = canScan && hasCamPermission && !showCard

    // 相机生命周期：随 scanning 与页面生命周期开关预览
    val barcodeView = barcodeViewRef.value
    DisposableEffect(lifecycleOwner, barcodeView, scanning) {
        val observer = LifecycleEventObserver { _, event ->
            val view = barcodeView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (scanning) view.resume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> view.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (scanning && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            barcodeView?.resume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeView?.pause()
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            /* ═══ 相机画面 + 自绘取景框 ═══ */
            if (canScan && hasCamPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        DecoratedBarcodeView(ctx).apply {
                            // 关掉库自带的取景框 + 底部状态文字，改用本页自绘（更贴合主题配色）
                            viewFinder.visibility = View.GONE
                            setStatusText("")
                            setDecoderFactory(DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE)))
                            decodeContinuous(object : BarcodeCallback {
                                override fun barcodeResult(result: BarcodeResult) {
                                    result.text?.let(onContents)
                                }
                            })
                            barcodeViewRef.value = this
                        }
                    },
                )
                if (!showCard) {
                    ScanFrameOverlay(modifier = Modifier.fillMaxSize())

                    Text(
                        text = "将二维码放入框内，即可自动扫描",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp, start = 24.dp, end = 24.dp),
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showManual = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) { Text("手动输入登录码") }

                        TextButton(
                            onClick = {
                                val view = barcodeViewRef.value ?: return@TextButton
                                if (torchOn) view.setTorchOff() else view.setTorchOn()
                                torchOn = !torchOn
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) { Text(if (torchOn) "关闭手电筒" else "手电筒") }
                    }
                }
            }

            /* ═══ 状态卡片层 ═══ */
            if (showCard) {
                Box(Modifier.fillMaxSize().background(Color(0xB3000000)))
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when {
                            !canScan -> {
                                Text(
                                    "请先在「设置 → 后端模式」开启并登录后端账号，再使用扫一扫。",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { navController.popBackStack() }) { Text("返回") }
                            }

                            !hasCamPermission -> {
                                Text("需要相机权限", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "开启相机权限后，才能扫描另一台设备上的登录二维码。",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = { navController.popBackStack() }) { Text("返回") }
                                    Button(onClick = {
                                        scanError = null
                                        permLauncher.launch(Manifest.permission.CAMERA)
                                    }) { Text("去授权") }
                                }
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
                                Text(
                                    scanError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = { navController.popBackStack() }) { Text("返回") }
                                    Button(onClick = {
                                        scanError = null
                                        handled = false
                                    }) { Text("重新扫描") }
                                }
                            }

                            else -> {
                                // 手动输入登录码
                                Text("手动输入登录码", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "在需要登录的设备上复制登录码，粘贴到这里即可完成授权。",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = manualInput,
                                    onValueChange = { manualInput = it },
                                    label = { Text("登录码 / 二维码内容") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = { showManual = false }) { Text("取消") }
                                    Button(
                                        enabled = manualInput.isNotBlank(),
                                        onClick = {
                                            val raw = manualInput.trim()
                                            val t = parseTicket(raw) ?: raw
                                            showManual = false
                                            scope.launch {
                                                BackendAccount.qrScan(t)
                                                    .onSuccess { scannedTicket = t }
                                                    .onFailure { scanError = it.message ?: "登录码无效" }
                                            }
                                        },
                                    ) { Text("使用") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 自绘扫码取景层：暗色遮罩挖出中央方框 + 四角高亮括号 + 上下往复的激光线。
 *
 * @param centerFraction 取景框**框心**在高度上的占比。0.5 = 正中；这里默认 0.40，
 *   让框整体偏上 —— 页面顶部有提示文字、底部有「手动输入 / 手电筒」操作区，
 *   框居中时会显得重心压在下方。
 */
@Composable
private fun ScanFrameOverlay(
    modifier: Modifier = Modifier,
    centerFraction: Float = 0.40f,
) {
    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "scan_laser")
    val laserProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "laser_progress",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val side = minOf(w, h) * 0.68f
        val left = (w - side) / 2f
        val centerY = h * centerFraction
        val top = centerY - side / 2f
        val right = left + side
        val bottom = top + side

        // 暗色遮罩（EvenOdd 挖空中央方框）
        val maskPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, w, h))
            addRect(Rect(left, top, right, bottom))
        }
        drawPath(maskPath, Color(0x99000000))

        // 四角括号
        val corner = side * 0.16f
        val stroke = 3.dp.toPx()
        fun edge(a: Offset, b: Offset) = drawLine(
            color = primary,
            start = a,
            end = b,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        edge(Offset(left, top), Offset(left + corner, top))
        edge(Offset(left, top), Offset(left, top + corner))
        edge(Offset(right, top), Offset(right - corner, top))
        edge(Offset(right, top), Offset(right, top + corner))
        edge(Offset(left, bottom), Offset(left + corner, bottom))
        edge(Offset(left, bottom), Offset(left, bottom - corner))
        edge(Offset(right, bottom), Offset(right - corner, bottom))
        edge(Offset(right, bottom), Offset(right, bottom - corner))

        // 激光线
        val inset = 8.dp.toPx()
        val laserY = top + side * laserProgress
        drawLine(
            color = primary,
            start = Offset(left + inset, laserY),
            end = Offset(right - inset, laserY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** 解析团影视登录二维码：tuanyingshi://qrlogin?t=<ticket> */
private fun parseTicket(content: String): String? {
    val uri = android.net.Uri.parse(content)
    if ("tuanyingshi".equals(uri.scheme, true) && "qrlogin".equals(uri.host, true)) {
        return uri.getQueryParameter("t")
    }
    return null
}
