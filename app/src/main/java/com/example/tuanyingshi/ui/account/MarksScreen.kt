package com.example.tuanyingshi.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.tuanyingshi.data.remote.backend.BackendClient
import com.example.tuanyingshi.util.BackendPrefs

/**
 * 我的追番：展示后端「在看 / 看过 / 抛弃」三种标记下的番剧（后端 marks 为追番状态，
 * 并非「点赞」，此处作为「我的」中该入口的后端对应功能）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarksScreen(navController: NavController) {
    val groups = listOf(
        "WATCHING" to "在看",
        "WATCHED" to "看过",
        "ABANDONED" to "抛弃",
    )
    var data by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!BackendPrefs.isLoggedIn) {
            error = "请先在「我的」中登录后端账号"
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching {
            groups.associate { (mark, _) ->
                mark to (BackendClient.api.marks(mark).data.orEmpty())
            }
        }.onSuccess { data = it; loading = false }
            .onFailure { error = it.message ?: "加载失败"; loading = false }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("我的追番", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(error ?: "", modifier = Modifier.align(Alignment.Center).padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                data.none { it.value.isNotEmpty() } -> Text("暂无追番记录", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groups.forEach { (mark, label) ->
                        val list = data[mark].orEmpty()
                        if (list.isNotEmpty()) {
                            item(key = "header_$mark") {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            items(list, key = { "$mark-$it" }) { detailUrl ->
                                BackendAnimeRow(
                                    title = detailUrl.substringAfterLast("/").ifBlank { detailUrl },
                                    img = null,
                                    onClick = { openAnime(navController, null, detailUrl) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
