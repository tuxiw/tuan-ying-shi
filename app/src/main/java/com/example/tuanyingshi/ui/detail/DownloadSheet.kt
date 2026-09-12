package com.example.tuanyingshi.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import com.example.tuanyingshi.domain.model.Episode

/**
 * 选集下载底部弹窗：用户勾选要下载的集数后点击「开始下载」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(
    episodes: List<Episode>,
    onDismiss: () -> Unit,
    onConfirm: (List<Episode>) -> Unit,
) {
    val selected = remember { mutableStateListOf<Episode>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "选择下载集数",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    selected.clear()
                    selected.addAll(episodes)
                }) { Text("全选") }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                itemsIndexed(episodes, key = { index, ep -> "${index}_${ep.url}" }) { _, ep ->
                    val isSel = selected.contains(ep)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (isSel) selected.remove(ep) else selected.add(ep) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isSel,
                            onCheckedChange = { if (it) selected.add(ep) else selected.remove(ep) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(ep.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开始下载（${selected.size}）") }
        }
    }
}
