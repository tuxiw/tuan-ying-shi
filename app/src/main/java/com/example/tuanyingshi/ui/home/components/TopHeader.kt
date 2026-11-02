package com.example.tuanyingshi.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * 首页顶部：左头像 + 中搜索条 + 右历史/下载。
 * 设计要点：紧凑（整体 ~52dp），与参考图一致。
 *
 * @param avatarUrl 当前登录用户的头像绝对地址；为空（未登录 / 未设置头像 / 非后端模式）时显示人形图标。
 * @param onAvatarClick 点击头像：由调用方打开左侧「快捷设置」抽屉。
 * @param overImage 顶部是否有 head_bg 头图做底。为 true 时本行自身不吃底色（透出头图），
 *   图标 / 搜索框按「图上」配色渲染（白色图标 + 白色胶囊搜索框），保证任意底图上都可读。
 */
@Composable
fun TopHeader(
    query: String,
    placeholder: String = "长风渡",
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    avatarUrl: String? = null,
    onAvatarClick: () -> Unit = {},
    overImage: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 头图铺到了状态栏之下（AppNavigation 对首页不再施加状态栏 top padding），
    // 故内容必须自己 statusBarsPadding 避让，否则头像/搜索框会被状态栏压住。
    Surface(
        // overImage 时透出下方头图；否则沿用 background，与正文消除明暗分界线。
        color = if (overImage) Color.Transparent else MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 头像（登录后展示真实头像，加载失败/未设置时回退到人形图标）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (overImage) Color.White.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    // 图上时加一圈白色描边，让头像与底图分离、边界清晰
                    .then(if (overImage) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "头像",
                    tint = if (overImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // 搜索条（视觉上是只读占位符，点击进入搜索）
            // 图上时改白色胶囊（参考设计稿），与深色/彩色头图形成强对比，文字用深灰保证可读。
            Surface(
                color = if (overImage) Color.White.copy(alpha = 0.94f)
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(17.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onSearchClick() }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = if (overImage) Color(0xFF616161)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (query.isEmpty()) placeholder else query,
                        color = if (overImage) Color(0xFF616161)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 历史
            IconButton(onClick = onHistoryClick) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "历史",
                    tint = if (overImage) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            // 下载
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "下载",
                    tint = if (overImage) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
