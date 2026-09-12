package com.example.tuanyingshi.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tuanyingshi.ui.home.HomeCategory

/**
 * 首页分类横滑标签：仅 RECOMMEND 选中时左侧带粉色心形 + 下方粉色下划线指示器。
 * 与外层 HorizontalPager 联动：点击标签通过 [onSelect] 触发 pager 滑动，选中态由 [selectedIndex] 决定。
 */
@Composable
fun CategoryTabs(
    categories: List<HomeCategory>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        // 与 TopHeader / 正文统一为 background，避免出现明暗拼接带。
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            categories.forEachIndexed { index, category ->
                CategoryTab(
                    category = category,
                    isSelected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun CategoryTab(
    category: HomeCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val textColor = if (isSelected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // "推荐" 在选中态左侧展示粉心（与参考图一致）
            if (category == HomeCategory.RECOMMEND) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = category.label,
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
        // 选中态下方粉红下划线（圆角小条），未选中态保留同等高度占位避免抖动
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(3.dp)
                .then(
                    if (isSelected) Modifier.background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(1.5.dp),
                    ) else Modifier
                ),
        )
    }
}
