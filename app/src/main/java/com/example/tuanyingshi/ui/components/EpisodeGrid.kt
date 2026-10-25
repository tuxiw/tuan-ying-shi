package com.example.tuanyingshi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tuanyingshi.domain.model.Episode
import com.example.tuanyingshi.util.COLUMNS_AUTO_MAX
import com.example.tuanyingshi.util.sanitizeColumns

/**
 * 选集网格：详情页 / 播放页共用，保证两页选集样式一致。
 *
 * 列数自适应按**本组件实际测得的宽度**（BoxWithConstraints）计算，而非屏幕宽度——
 * 播放页右栏 / 详情页平板右栏等「网格宽度 ≠ 屏幕宽度」的场景下，
 * 按屏幕算会得到过多列，胶囊被挤压成「第…」。
 *
 * @param columns 手动档列数；不传时按实际宽度自适应。
 * @param currentEpisodeUrl 当前正在播放/观看的集地址，匹配的集高亮显示（实心主色按钮）。
 */
@Composable
fun EpisodeGrid(
    episodes: List<Episode>,
    onPlay: (Episode) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int? = null,
    currentEpisodeUrl: String? = null,
) {
    // 紧凑内容边距：默认 Button 左右各 24dp 会吃掉近半宽度，短名（第01集）直接截断
    val compactPadding = PaddingValues(horizontal = 8.dp)
    BoxWithConstraints(modifier = modifier) {
        // 自适应档可能算出超过手动上限（6）的列数，收敛时用更大的上限
        val cols = sanitizeColumns(columns ?: effectiveDetailColumnsFor(maxWidth), COLUMNS_AUTO_MAX)
        Column {
            episodes.chunked(cols).forEach { rowEps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowEps.forEach { ep ->
                        val isCurrent = currentEpisodeUrl != null && ep.url == currentEpisodeUrl
                        if (isCurrent) {
                            Button(
                                onClick = { onPlay(ep) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                contentPadding = compactPadding,
                            ) {
                                Text(
                                    ep.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onPlay(ep) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                contentPadding = compactPadding,
                            ) {
                                Text(ep.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    repeat(cols - rowEps.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
