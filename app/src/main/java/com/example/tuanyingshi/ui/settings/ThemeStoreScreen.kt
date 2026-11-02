package com.example.tuanyingshi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.ui.components.adaptiveColumns
import com.example.tuanyingshi.ui.components.maxContentWidth
import com.example.tuanyingshi.util.ThemePrefs
import com.example.tuanyingshi.util.theme.ThemePackItem
import com.example.tuanyingshi.util.theme.ThemePackManager
import kotlinx.coroutines.launch

/**
 * 主题套装商店页（个性功能 → 主题套装）。
 *
 * 展示 [ThemePackManager.fetchPacks] 拉取到的套装列表，每个套装需先把 zip 资源下载解压到本地
 * 后才能「使用」——未就绪时「使用」按钮被禁用并提示「请先下载主题才能使用」。
 *
 * 布局：主题卡片**多列网格**（手机 2 列 / 平板 3 列 / 大平板 4 列，超宽屏按 [maxContentWidth] 居中限宽）。
 * 顶部另有「套装应用选项」：可分别关闭**跟随套装强调色**与**跟随套装明暗模式**，
 * 即使用套装但保留自己的强调色 / 深浅色设置（存于 [ThemePrefs]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStoreScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var packs by remember { mutableStateOf<List<ThemePackItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }

    val progress by ThemePackManager.progress.collectAsStateWithLifecycle()
    val config by ThemePrefs.config.collectAsStateWithLifecycle()
    val currentId = config.themePackId
    var readyIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // 下载完成 / 删除后自增，触发本地重扫「已就绪」集合
    var refreshToken by remember { mutableStateOf(0) }

    fun rescanReady() {
        readyIds = packs.filter { ThemePackManager.isReady(context, it.itemId) }
            .map { it.itemId }.toSet()
    }

    LaunchedEffect(Unit) {
        runCatching { packs = ThemePackManager.fetchPacks() }
            .onFailure { loadFailed = true }
        rescanReady()
        loading = false
    }
    LaunchedEffect(refreshToken) { rescanReady() }

    fun onDownload(item: ThemePackItem) {
        scope.launch {
            val ok = ThemePackManager.download(context, item)
            if (ok) refreshToken++ else android.widget.Toast.makeText(
                context, "下载失败，请检查网络后重试", android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun onUse(item: ThemePackItem) {
        if (!ThemePackManager.isReady(context, item.itemId)) {
            android.widget.Toast.makeText(
                context, "请先下载主题才能使用", android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        ThemePackManager.apply(context, item.itemId)
    }

    fun onDelete(itemId: Int) {
        ThemePackManager.remove(context, itemId)
        refreshToken++
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("主题套装", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { innerPadding ->
        // 超宽屏（平板 / 展开态）限宽居中，避免卡片被拉得过宽
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(themeStoreColumns()),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = maxContentWidth),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 顶部说明 + 套装应用选项（整行占位）
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "主题资源需先下载到本地后才能使用；下载完成后可一键应用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PackApplyOptionsCard(
                            packApplied = currentId != null,
                            followAccent = config.packFollowAccent,
                            followColorMode = config.packFollowColorMode,
                            onFollowAccentChange = { ThemePrefs.setPackFollowAccent(it) },
                            onFollowColorModeChange = { ThemePrefs.setPackFollowColorMode(it) },
                        )
                        if (currentId != null) {
                            Button(
                                onClick = { ThemePackManager.clearCurrent() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("恢复内置默认主题")
                            }
                        }
                    }
                }

                when {
                    loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    loadFailed || packs.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (loadFailed) "主题列表加载失败，请稍后重试" else "暂无可选主题",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> items(packs, key = { it.itemId }) { item ->
                        val status = when {
                            currentId == item.itemId -> ThemePackManager.Status.CURRENT
                            item.itemId in progress -> ThemePackManager.Status.DOWNLOADING
                            item.itemId in readyIds -> ThemePackManager.Status.READY
                            else -> ThemePackManager.Status.NOT_DOWNLOADED
                        }
                        ThemePackCard(
                            item = item,
                            status = status,
                            progressPercent = progress[item.itemId] ?: 0,
                            onDownload = { onDownload(item) },
                            onUse = { onUse(item) },
                            onDelete = { onDelete(item.itemId) },
                        )
                    }
                }
            }
        }
    }
}

/** 主题套装页网格列数：手机 2 列 / 平板 3 列 / 大平板（>=840dp）4 列。 */
@Composable
private fun themeStoreColumns(): Int = adaptiveColumns(phoneBase = 2, tabletBase = 3, expandedBase = 4)

/**
 * 套装应用选项：允许用户**不跟随**套装的强调色 / 明暗模式。
 * 两个开关独立生效，未应用套装时不产生效果（此时展示提示文案）。
 *
 * 套装页（[ThemeStoreScreen]）与「主题与色彩」页（[ThemeSettingsContent]）共用，
 * 保证两处开关语义一致、只改一处。
 */
@Composable
internal fun PackApplyOptionsCard(
    packApplied: Boolean,
    followAccent: Boolean,
    followColorMode: Boolean,
    onFollowAccentChange: (Boolean) -> Unit,
    onFollowColorModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "套装应用选项",
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            SwitchRow(
                icon = Icons.Filled.Palette,
                title = "跟随套装强调色",
                desc = "关闭后保留你自己设置的强调色",
                checked = followAccent,
                onCheckedChange = onFollowAccentChange,
            )
            SwitchRow(
                icon = Icons.Filled.DarkMode,
                title = "跟随套装明暗模式",
                desc = "关闭后按你的深浅色设置显示",
                checked = followColorMode,
                onCheckedChange = onFollowColorModeChange,
            )
            if (!packApplied) {
                Text(
                    text = "当前未应用套装，以上选项将在应用套装后生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 单个主题套装卡片：封面 + 名称 + 状态操作行。 */
@Composable
private fun ThemePackCard(
    item: ThemePackItem,
    status: ThemePackManager.Status,
    progressPercent: Int,
    onDownload: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        // 网格卡片：列宽有限，操作区一律「整宽按钮 + 下方文字按钮」竖排，
        // 避免窄列（手机 2 列约 160dp）里按钮并排被挤压 / 换行溢出。
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 封面（网络图，走应用代理）
            val cover = item.properties?.imageCover
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
            }

            Text(
                text = item.name ?: "未命名主题",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "套装 ID ${item.itemId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            when (status) {
                ThemePackManager.Status.NOT_DOWNLOADED -> {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("下载")
                    }
                }

                ThemePackManager.Status.DOWNLOADING -> {
                    Text(
                        text = if (progressPercent < 0) "下载中…" else "下载中 $progressPercent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { if (progressPercent < 0) 0f else progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ThemePackManager.Status.READY -> {
                    Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text("使用") }
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("删除")
                    }
                }

                ThemePackManager.Status.CURRENT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "使用中",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDelete) { Text("删除") }
                    }
                }
            }
        }
    }
}
