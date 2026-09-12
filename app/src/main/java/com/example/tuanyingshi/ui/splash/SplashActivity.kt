package com.example.tuanyingshi.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tuanyingshi.MainActivity
import com.example.tuanyingshi.R
import com.example.tuanyingshi.ui.theme.TuanyingshiTheme
import com.example.tuanyingshi.util.BuiltInCovers
import com.example.tuanyingshi.util.SplashPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/**
 * 启动页：纯 Compose 实现，不依赖 AndroidX SplashScreen compat 库。
 *
 * 行为：
 * 1. 冷启动瞬间由 Theme.Splash 的 android:windowBackground（首页暗色）兜底，无白屏。
 * 2. 开屏封面关闭时：直接跳转主界面。
 * 3. 开屏封面开启时：展示启动图（+ 右上角倒计时跳过按钮）→ 倒计时结束/点击跳过 → 主界面。
 */
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 读取偏好（同步，SharedPreferences 在主线程读很快）
        val enabled = SplashPrefs.isEnabled()
        val durationMs = SplashPrefs.getDurationMs().coerceAtLeast(300L)

        // 开屏关闭：直接跳转主界面，不展示 Compose 启动图
        if (!enabled) {
            navigateToMain()
            return
        }

        val cover = resolveCover()

        // 沉浸式：状态栏/导航栏内容覆盖在启动图上
        enableEdgeToEdge()

        setContent {
            TuanyingshiTheme {
                SplashScreen(cover = cover, durationMs = durationMs, onSkip = { navigateToMain() })
            }
        }
    }

    /** 按偏好解析要展示的开屏封面。 */
    private fun resolveCover(): SplashCoverUi {
        return when (SplashPrefs.getSource()) {
            SplashPrefs.SOURCE_URL -> {
                val u = SplashPrefs.getUrl()
                if (u.isNotBlank()) SplashCoverUi.Remote(u) else SplashCoverUi.BuiltIn(
                    BuiltInCovers.getDrawableRes(SplashPrefs.getBuiltinId()),
                )
            }
            SplashPrefs.SOURCE_PICKED -> {
                val uri = SplashPrefs.getPickedUri()
                if (uri.isNotBlank()) {
                    SplashCoverUi.LocalUri(uri)
                } else {
                    SplashCoverUi.BuiltIn(BuiltInCovers.getDrawableRes(SplashPrefs.getBuiltinId()))
                }
            }
            else -> SplashCoverUi.BuiltIn(BuiltInCovers.getDrawableRes(SplashPrefs.getBuiltinId()))
        }
    }

    private fun navigateToMain() {
        if (!isFinishing && !isDestroyed) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

/** 开屏封面的展示形式。 */
private sealed interface SplashCoverUi {
    data class BuiltIn(val resId: Int) : SplashCoverUi
    data class Remote(val url: String) : SplashCoverUi
    data class LocalUri(val uri: String) : SplashCoverUi
}

@Composable
private fun SplashScreen(
    cover: SplashCoverUi,
    durationMs: Long,
    onSkip: () -> Unit,
) {
    var remaining by remember { mutableStateOf(durationMs) }
    var finished by remember { mutableStateOf(false) }

    fun trigger() {
        if (!finished) {
            finished = true
            onSkip()
        }
    }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            val left = (durationMs - (System.currentTimeMillis() - start))
                .coerceAtLeast(0L)
            remaining = left
            if (left <= 0L) {
                trigger()
                break
            }
            delay(50L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (cover) {
            is SplashCoverUi.BuiltIn -> Image(
                painter = painterResource(id = cover.resId),
                contentDescription = "团影视",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            is SplashCoverUi.Remote -> AsyncImage(
                model = cover.url,
                contentDescription = "团影视",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            is SplashCoverUi.LocalUri -> AsyncImage(
                model = SplashPrefs.resolvePickedModel(cover.uri),
                contentDescription = "团影视",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // 右上角倒计时跳过按钮：圆环进度 + 文字，点击立即跳过
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp, end = 16.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .clickable { trigger() }
                    .size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 2.5.dp.toPx()
                    val progress = (remaining / durationMs.toFloat())
                        .coerceIn(0f, 1f)
                    drawArc(
                        color = Color.Black.copy(alpha = 0.35f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke),
                    )
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = stroke),
                    )
                }
                Text(
                    text = "跳过",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
