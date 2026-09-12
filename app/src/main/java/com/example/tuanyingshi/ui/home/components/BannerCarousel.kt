package com.example.tuanyingshi.ui.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.tuanyingshi.domain.model.HomeBanner
import kotlinx.coroutines.delay

/**
 * 首页顶部轮播图。
 *
 * - 自动轮播（默认 8 秒一页）
 * - 每张 Banner 按自身图片宽高比展示，容器高度随当前页图片尺寸自适应切换（不再固定 16:9 裁剪）
 * - 底部中央指示器 + 标题浮层
 */
@Composable
fun BannerCarousel(
    banners: List<HomeBanner>,
    onBannerClick: (HomeBanner) -> Unit,
    modifier: Modifier = Modifier,
    autoScrollIntervalMs: Long = 8000L,
) {
    if (banners.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { banners.size },
    )

    // 每张 Banner 测量出的宽高比（width / height），默认 16:9 兜底
    val ratios = remember { mutableStateMapOf<Int, Float>() }
    val currentRatio = (ratios[pagerState.currentPage] ?: (16f / 9f)).coerceIn(1.4f, 2.2f)

    // 首张测量值直接 snap，避免启动瞬间容器高度突变导致首页内容跳动；
    // 之后切换到不同比例的 Banner 才平滑过渡。
    val displayRatio = remember { Animatable(currentRatio) }
    var firstRatioSnapped by remember { mutableStateOf(false) }
    LaunchedEffect(currentRatio) {
        if (!firstRatioSnapped) {
            displayRatio.snapTo(currentRatio)
            if (ratios.isNotEmpty()) firstRatioSnapped = true
        } else {
            displayRatio.animateTo(currentRatio, tween(durationMillis = 300))
        }
    }

    if (banners.size > 1) {
        LaunchedEffect(banners.size) {
            while (true) {
                delay(autoScrollIntervalMs)
                // 用户正在手动滑动时不触发自动翻页，避免冲突导致两页同显
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % banners.size
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .aspectRatio(displayRatio.value),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 0.dp,
                contentPadding = PaddingValues(0.dp),
                pageSize = PageSize.Fill,
            ) { page ->
                val banner = banners[page]
                BannerItem(
                    banner = banner,
                    onClick = { onBannerClick(banner) },
                    modifier = Modifier.fillMaxSize(),
                    onRatioResolved = { ratio -> ratios[page] = ratio },
                )
            }

            // 底部标题遮罩
            banners.getOrNull(pagerState.currentPage)?.let { current ->
                if (current.title.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = current.title,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 指示器
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (banners.getOrNull(pagerState.currentPage)?.title?.isNotBlank() == true) 36.dp else 8.dp),
            ) {
                BannerIndicators(
                    count = banners.size,
                    current = pagerState.currentPage,
                )
            }
        }
    }
}

@Composable
private fun BannerItem(
    banner: HomeBanner,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRatioResolved: (Float) -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = banner.imageUrl,
            contentDescription = banner.title,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Fit,
            onSuccess = { state: AsyncImagePainter.State.Success ->
                val size = state.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) {
                    onRatioResolved(size.width / size.height)
                }
            },
        )
    }
}

@Composable
private fun BannerIndicators(
    count: Int,
    current: Int,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            repeat(count) { index ->
                val selected = index == current
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.6f)
                        ),
                )
            }
        }
    }
}
