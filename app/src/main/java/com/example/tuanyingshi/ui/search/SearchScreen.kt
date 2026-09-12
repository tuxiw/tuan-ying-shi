package com.example.tuanyingshi.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.ui.components.adaptiveMinCardWidth
import com.example.tuanyingshi.ui.home.components.HotAnimeCard
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.ui.components.rememberCaptchaInputHost
import com.example.tuanyingshi.util.source_rule.KazumiWebViewVerifier

/**
 * 搜索页：顶部搜索输入框（输入即搜，debounce 在 ViewModel 内处理）+ 自适应结果网格。
 *
 * 采用流式逐源显示（对齐 animeko）：[SearchViewModel.uiState] 随各数据源返回即时更新，
 * 快源先显、慢源后补，并展示「正在搜索」的源清单。API 类源支持「加载更多」翻页。
 * 状态栏处理与其它进入式页面一致：内层 Scaffold 不再重复扣除 systemBars（WindowInsets(0)），
 * 只依赖 AppNavigation 外层那一层预留。
 */
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 反爬验证码输入桥：把验证器的 suspend 取输入接到 Compose 弹窗
    val captchaHost = rememberCaptchaInputHost()
    LaunchedEffect(Unit) {
        KazumiWebViewVerifier.captchaInputProvider = { base64 -> captchaHost.request(base64) }
    }
    DisposableEffect(Unit) {
        onDispose { KazumiWebViewVerifier.captchaInputProvider = null }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = {
                            Text("搜索番剧名称", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.onSearch() }),
                        shape = MaterialTheme.shapes.small,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 44.dp, end = 4.dp)
                            .align(Alignment.Center),
                    )
                }
            }
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            when {
                query.isBlank() -> CenteredMessage("输入关键词，搜索你想看的番剧")
                state.isSearching && state.results.isEmpty() ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (state.pending.isNotEmpty()) {
                            Text(
                                text = "正在搜索：${state.pending.joinToString("、")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CenteredMessage("搜索中…")
                    }
                state.results.isEmpty() -> {
                    if (SourceHolder.currentSourceMode == SourceMode.Girigiri && query.isNotBlank()) {
                        GirigiriVerifyFallback(
                            query = query,
                            onRetry = { viewModel.onSearch() },
                        )
                    } else {
                        CenteredMessage("没有找到相关番剧")
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = adaptiveMinCardWidth),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.pending.isNotEmpty()) {
                            items(count = 1, span = { GridItemSpan(maxLineSpan) }) {
                                PendingInfoRow(pending = state.pending)
                            }
                        }
                        items(
                            items = state.results,
                            key = { "${it.detailUrl}#${it.sourceName}" },
                        ) { anime ->
                            HotAnimeCard(
                                anime = anime,
                                onClick = {
                                    navController.navigate(Screen.Detail.create(anime.detailUrl, anime.sourceId))
                                },
                            )
                        }
                        if (state.canLoadMore) {
                            items(count = 1, span = { GridItemSpan(maxLineSpan) }) {
                                LoadMoreButton(
                                    isLoadingMore = state.isLoadingMore,
                                    onLoadMore = viewModel::loadMore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    captchaHost.Render()
}

/** 仍有数据源在加载时的提示行（快源已显示，慢源还在爬）。 */
@Composable
private fun PendingInfoRow(pending: List<String>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "正在加载更多源：${pending.joinToString("、")}…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 「加载更多」按钮（API 类源翻页）。 */
@Composable
private fun LoadMoreButton(isLoadingMore: Boolean, onLoadMore: () -> Unit) {
    Button(
        onClick = onLoadMore,
        enabled = !isLoadingMore,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(if (isLoadingMore) "加载中…" else "加载更多")
    }
}

/**
 * Girigiri 搜索空结果兜底：直接内嵌展示 Girigiri 搜索页，让用户在网页里完成
 * Cloudflare / 验证码校验。校验通过后 CookieManager 会保存 cf_clearance，
 * 点「重试」重新拉取时，GirigiriSource 的 WebView 抓取会自动带上该 Cookie，
 * 从而绕过验证、返回真实结果。
 *
 * 仅在当前数据源为 Girigiri 且已输入关键词时生效（见调用处）。
 */
@Composable
private fun GirigiriVerifyFallback(query: String, onRetry: () -> Unit) {
    val url = "${SourceHolder.currentSource.baseUrl}/search/${Uri.encode(query)}----------1---/"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Girigiri 搜索需要网页验证，请在下方页面完成验证后点「重试」",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
        )
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("重试")
        }
    }
}
