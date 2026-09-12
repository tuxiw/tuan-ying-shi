package com.example.tuanyingshi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 可展开长文本：默认收起（最多 [collapsedMaxLines] 行，超长省略号），
 * 内容确实超行时显示「展开全文 / 收起」入口，点击切换。
 * 用于番剧简介等长描述，默认收起展示。
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    collapsedMaxLines: Int = 3,
) {
    var expanded by remember { mutableStateOf(false) }
    var isExpandable by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                // 折叠态下实际行数达到上限才认为可展开（短文本不显示入口）
                if (!expanded) isExpandable = result.lineCount >= collapsedMaxLines
            },
        )
        if (isExpandable) {
            Text(
                text = if (expanded) "收起" else "展开全文",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { expanded = !expanded }
                    .padding(top = 2.dp, bottom = 4.dp, start = 8.dp),
            )
        }
    }
}
