package com.example.tuanyingshi.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 骨架闪烁块：用渐变扫光模拟内容加载中的占位，用于「页面先进入、部分 UI 显示加载动画」的场景。
 * 颜色随当前主题自适应（浅色/深色都可读）。
 */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "shimmerX",
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x.value - 0.4f, x.value - 0.4f),
        end = Offset(x.value + 0.4f, x.value + 0.4f),
    )
    Box(modifier = modifier.clip(shape).background(brush))
}

/** 居中圆形进度 + 文案，作为某块区域（如视频区）的局部加载动画。 */
@Composable
fun LoadingIndicator(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 选集区局部加载：圆形进度 + 文案 + 底部一排骨架占位，表达「选集正在加载」。 */
@Composable
fun EpisodesLoading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(4) {
                ShimmerBlock(
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(4) {
                ShimmerBlock(
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }
    }
}

/** 详情/播放页信息面板骨架：封面 + 标题 + 元信息 + 选集占位。用于元数据尚未到达时的「先进页面」占位。 */
@Composable
fun DetailInfoSkeleton(
    modifier: Modifier = Modifier,
    tablet: Boolean = false,
) {
    if (tablet) {
        Row(modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShimmerBlock(modifier = Modifier.fillMaxWidth().height(280.dp), shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(20.dp))
                ShimmerBlock(modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(8.dp))
            }
            Column(modifier = Modifier.weight(0.62f).padding(20.dp)) {
                ShimmerBlock(modifier = Modifier.fillMaxWidth(0.7f).height(28.dp))
                Spacer(Modifier.height(12.dp))
                ShimmerBlock(modifier = Modifier.fillMaxWidth(0.4f).height(18.dp))
                Spacer(Modifier.height(16.dp))
                ShimmerBlock(modifier = Modifier.fillMaxWidth().height(120.dp))
                Spacer(Modifier.height(20.dp))
                ShimmerBlock(modifier = Modifier.fillMaxWidth(0.3f).height(20.dp))
                Spacer(Modifier.height(10.dp))
                repeat(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(4) {
                            ShimmerBlock(modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(8.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            ShimmerBlock(modifier = Modifier.fillMaxWidth().height(220.dp))
            Spacer(Modifier.height(16.dp))
            ShimmerBlock(modifier = Modifier.fillMaxWidth(0.8f).height(26.dp).padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))
            ShimmerBlock(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
            ShimmerBlock(modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 16.dp))
            Spacer(Modifier.height(20.dp))
            ShimmerBlock(modifier = Modifier.fillMaxWidth(0.3f).height(20.dp).padding(horizontal = 16.dp))
            Spacer(Modifier.height(10.dp))
            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(4) {
                        ShimmerBlock(modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** 播放页信息面板骨架：标题/元信息/简介 + 一排选集占位。元数据未到时「先进页面」显示。 */
@Composable
fun PlayerInfoSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp)) {
        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.7f).height(24.dp))
        Spacer(Modifier.height(10.dp))
        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp))
        Spacer(Modifier.height(14.dp))
        ShimmerBlock(modifier = Modifier.fillMaxWidth().height(80.dp))
        Spacer(Modifier.height(20.dp))
        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.3f).height(18.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(8) {
                ShimmerBlock(modifier = Modifier.width(72.dp).height(40.dp), shape = RoundedCornerShape(8.dp))
            }
        }
    }
}
