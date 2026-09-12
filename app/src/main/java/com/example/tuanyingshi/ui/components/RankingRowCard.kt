package com.example.tuanyingshi.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.domain.model.episodeBadge
import com.example.tuanyingshi.ui.components.AbandonDialog
import com.example.tuanyingshi.ui.components.LocalUserStatusRepository

/**
 * 排行榜 / 排期表共用的横向大卡（参考图样式）：
 * - 左侧 3:4 封面（封面图占位用封面色 + 标题首字）。
 * - 右侧信息：标题 + 最新一集 + 连载状态。
 * - 卡片底色取 MaterialTheme.colorScheme.surfaceVariant（亮一档），与页面背景区分。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RankingRowCard(
    anime: Anime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var showAbandon by remember { mutableStateOf(false) }
    val canAbandon = LocalUserStatusRepository.current != null
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (canAbandon) showAbandon = true },
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 左侧封面 3:4：背景为封面色，首字占位；前景用 Coil 加载真实封面图。
            Box(
                modifier = Modifier
                    .width(95.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(coverGradient(anime.title)),
                contentAlignment = Alignment.Center,
            ) {
                if (anime.img.isNotBlank()) {
                    AsyncImage(
                        model = anime.img,
                        contentDescription = anime.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = anime.title.take(1),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 右侧信息
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = anime.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val epBadge = anime.episodeBadge
                if (epBadge.isNotBlank()) {
                    Text(
                        text = epBadge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = ongoingStatusText(epBadge),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
        }
    }
    if (showAbandon) {
        AbandonDialog(
            detailUrl = anime.detailUrl,
            title = anime.title,
            imgUrl = anime.img,
            onDismiss = { showAbandon = false },
        )
    }
}

/**
 * 根据集数标签判断连载状态。
 * - 含"更新至/更新" → 连载更新中
 * - 含"全/完结" → 已完结
 * - 其它 (空/不识别) → 连载更新中 (默认)
 */
private fun ongoingStatusText(episodeName: String): String {
    if (episodeName.isBlank()) return "连载更新中"
    val normalized = episodeName.replace(" ", "")
    return when {
        normalized.contains("更新") -> "连载更新中"
        normalized.contains("完结") || normalized.contains("全") -> "已完结"
        else -> "连载更新中"
    }
}

/** 根据种子生成稳定的封面色（与项目其它卡片调色板一致）。 */
private fun coverGradient(seed: String): Color {
    val palette = listOf(
        0xFF5E4B8B, 0xFF8A3B6E, 0xFF3B5B8A, 0xFF2F6B5A, 0xFF8A5C2F,
        0xFF6B2F4E, 0xFF2F8A8A, 0xFF4B6B8A,
    )
    val idx = (seed.hashCode().rem(palette.size).let { if (it < 0) it + palette.size else it })
    return Color(palette[idx])
}