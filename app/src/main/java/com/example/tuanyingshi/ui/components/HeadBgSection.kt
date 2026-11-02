package com.example.tuanyingshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File

/**
 * 主题套装顶头图区容器（首页 / 排期表 / 排行榜共用，保证三个主 Tab 顶部观感一致）。
 *
 * 把 [content]（页面顶部的标题行 + 标签行）垫在 head_bg 头图之上：
 * - 头图 edge-to-edge 铺到状态栏之下（配合 AppNavigation 对这三个路由放行 0dp top padding）；
 * - [content] 必须自己 `statusBarsPadding()` 避让状态栏，否则内容会被状态栏压住；
 *
 * @param headBg 主题套装的 head_bg 图片文件；为 null（未应用套装）时整层不渲染，
 *   容器退化为一个普通 Box，页面顶部保持常规实色外观，无任何回归。
 *
 * ⚠️ 背景图必须用 `matchParentSize()`：**不参与测量**，尺寸完全由 [content] 决定。
 * 用 `fillMaxSize()` 会在松约束（Scaffold topBar / Column）下把头图撑满全屏、挤掉内容。
 */
@Composable
fun HeadBgSection(
    headBg: File?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (headBg != null) {
            AsyncImage(
                model = headBg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // 整体暗化（顶重底轻）：头图深浅随主题而变，压一层暗化保证
            // 状态栏图标、白色标题与白色标签文字在任何底图上都读得清。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0xFF000000).copy(alpha = 0.42f),
                            1.0f to Color(0xFF000000).copy(alpha = 0.28f),
                        ),
                    ),
            )
            // 最底部渐隐回页面底色：让头图与下方内容自然衔接，不出现生硬直边
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.86f to Color.Transparent,
                            1.0f to MaterialTheme.colorScheme.background,
                        ),
                    ),
            )
        }
        content()
    }
}
