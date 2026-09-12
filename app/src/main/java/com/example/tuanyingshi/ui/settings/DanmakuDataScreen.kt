package com.example.tuanyingshi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.data.remote.dandanplay.DandanplayApi
import com.example.tuanyingshi.util.dandanplay.DandanplayConfig
import com.example.tuanyingshi.util.dandanplay.DanmakuPrefs
import kotlinx.coroutines.launch

/**
 * 弹幕源管理页（独立全屏，手机端由设置列表跳入）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanmakuDataScreen(navController: NavController) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("弹幕源管理", color = MaterialTheme.colorScheme.onBackground) },
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
    ) { inner ->
        DanmakuDataScreenContent(modifier = Modifier.fillMaxSize().padding(inner))
    }
}

/**
 * 弹幕源管理内容区（供手机独立页与平板设置左右布局右侧共用，不含 TopAppBar）。
 */
@Composable
fun DanmakuDataScreenContent(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val enabled by DanmakuPrefs.enabled.collectAsStateWithLifecycle()

    // API 地址镜像：编辑后写回 DandanplayConfig 并刷新此处状态
    var baseUrl by remember { mutableStateOf(DandanplayConfig.baseUrl) }

    var showEditor by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // 当前弹幕资源列表（目前仅有弹弹play 一条，结构上保留为列表以便后续扩展）
    val resources = remember(baseUrl, enabled) {
        listOf(
            DanmakuResource(
                id = "dandanplay",
                name = "弹弹play（DandanPlay）",
                baseUrl = baseUrl,
                configured = DandanplayConfig.isConfigured,
                enabled = enabled,
            )
        )
    }

    Box(modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ───────── 弹幕拉取总开关 ─────────
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启用弹幕拉取",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "为所有视频加载弹弹play 弹幕（按番名+集名匹配）",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { DanmakuPrefs.setEnabled(it) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(6.dp)) }

            // ───────── 弹幕资源列表 ─────────
            item {
                SectionHeader(
                    title = "弹幕资源",
                    subtitle = "为所有视频提供弹幕的数据源（按番名+集名匹配），可在下方测试连接或编辑 API 地址",
                    actions = {},
                )
            }

            items(resources, key = { it.id }) { res ->
                DanmakuResourceCard(
                    res = res,
                    onEdit = { showEditor = true },
                    onTest = {
                        testing = true
                        scope.launch {
                            val r = DandanplayApi.testConnection()
                            testing = false
                            testResult = r
                        }
                    },
                )
            }
        }

        if (showEditor) {
            DanmakuBaseUrlDialog(
                initialBaseUrl = baseUrl,
                onDismiss = { showEditor = false },
                onSave = { u ->
                    DandanplayConfig.baseUrl = u
                    baseUrl = DandanplayConfig.baseUrl
                    showEditor = false
                },
            )
        }

        if (testing) {
            AlertDialog(
                onDismissRequest = { /* 测试中不可取消 */ },
                confirmButton = {},
                title = { Text("测试连接") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("正在连接弹弹play 弹幕 API…")
                    }
                },
            )
        }

        if (testResult != null) {
            val (ok, msg) = testResult!!
            AlertDialog(
                onDismissRequest = { testResult = null },
                confirmButton = {
                    TextButton(onClick = { testResult = null }) { Text("知道了") }
                },
                title = { Text(if (ok) "连接成功" else "连接失败") },
                text = { Text(msg) },
            )
        }
    }
}

/** 单个弹幕资源（数据源）的 UI 模型。 */
private data class DanmakuResource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val configured: Boolean,
    val enabled: Boolean,
)

/** 单个弹幕资源卡片：图标 + 名称 + 类型/配置状态角标 + 地址 + 启用状态 + 更多菜单。 */
@Composable
private fun DanmakuResourceCard(
    res: DanmakuResource,
    onEdit: () -> Unit,
    onTest: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Subtitles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = res.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = res.baseUrl,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (res.enabled) "弹幕拉取：已启用" else "弹幕拉取：已关闭",
                    color = if (res.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp,
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑 API 地址") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("测试连接") },
                        onClick = {
                            menuExpanded = false
                            onTest()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** API 地址编辑弹窗：仅允许用户修改 baseUrl，AppId / AppSecret 内置不暴露。 */
@Composable
private fun DanmakuBaseUrlDialog(
    initialBaseUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(baseUrl.trim())
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("编辑 API 地址") },
        text = {
            Column {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.dandanplay.net") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "如弹幕连接异常，可在此切换 API 地址。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

/** 区块标题（含副标题与右侧操作区）。 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** 小标签（角标）：用于展示类型 / 配置状态等。 */
@Composable
private fun TagBadge(
    text: String,
    color: Color,
    contentColor: Color,
) {
    Surface(shape = RoundedCornerShape(6.dp), color = color) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
