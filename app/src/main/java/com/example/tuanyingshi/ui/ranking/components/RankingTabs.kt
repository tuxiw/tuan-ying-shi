package com.example.tuanyingshi.ui.ranking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuanyingshi.ui.ranking.RankingCategory

/**
 * 排行榜分类 chip 行（4 项）：剧集 / 动漫 / 美剧 / 电影。
 * 与外层 HorizontalPager 联动：点击 chip 通过 [onSelect] 触发 pager 滑动，选中态由 [selectedIndex] 决定。
 * - 选中态：白色文字 + 底部白色短下划线
 * - 非选中：onSurfaceVariant 灰色文字
 */
@Composable
fun RankingTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        RankingCategory.values().forEachIndexed { index, cat ->
            val isSelected = index == selectedIndex

            Column(
                modifier = Modifier
                    .padding(end = 18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = cat.label,
                    fontSize = 15.sp,
                    color = if (isSelected) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .height(3.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                            .padding(horizontal = 12.dp),
                    )
                } else {
                    // 占位让选中/非选中行高一致，避免切换时整行高度跳动
                    Box(modifier = Modifier.padding(top = 4.dp).height(3.dp))
                }
            }
        }
    }
}
