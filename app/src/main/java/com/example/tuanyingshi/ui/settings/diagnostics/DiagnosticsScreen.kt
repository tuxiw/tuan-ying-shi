package com.example.tuanyingshi.ui.settings.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

private val COLOR_OK = Color(0xFF4CAF50)
private val COLOR_EMPTY = Color(0xFFFFB300)
private val COLOR_FAIL = Color(0xFFE53935)
private val COLOR_RUNNING = Color(0xFF42A5F5)
private val COLOR_IDLE = Color(0xFF9E9E9E)

/**
 * 数据源诊断页：逐源（次元城 / 嘶哩嘶哩）跑通真实抓取链路，把每一步状态、耗时、异常栈都展示出来，
 * 没有数据时用来快速定位问题（WAF 拦截 / 超时 / 选择器失效 / 空结果）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    navController: NavController,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        // 外层 AppNavigation 已按 innerPadding 预留状态栏/导航栏，内层不再重复扣除 systemBars
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("数据源诊断", color = MaterialTheme.colorScheme.onBackground) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ===== 数据源选择 =====
            SourceSelector(
                sources = viewModel.sources,
                selectedId = state.selectedSourceId,
                enabled = state.phase != DiagStatus.RUNNING,
                onSelect = { viewModel.selectSource(it) },
            )

            // ===== 概览 =====
            SummaryCard(state, viewModel.sources)

            // ===== 操作按钮 =====
            Button(
                onClick = { viewModel.run() },
                enabled = state.phase != DiagStatus.RUNNING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.phase == DiagStatus.RUNNING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when (state.phase) {
                        DiagStatus.RUNNING -> "诊断中…"
                        DiagStatus.IDLE -> "开始诊断"
                        else -> "重新运行诊断"
                    },
                )
            }

            // ===== 各步骤 =====
            state.steps.forEach { step ->
                StepCard(step)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SummaryCard(state: DiagnosticsUiState, sources: List<DiagSource>) {
    val source = sources.firstOrNull { it.id.name == state.selectedSourceId }
    val (text, color) = when (state.phase) {
        DiagStatus.RUNNING -> "诊断进行中…" to COLOR_RUNNING
        DiagStatus.FINISHED -> {
            val ok = state.passedCount
            val total = state.steps.size
            val problems = state.problemCount
            val txt = if (problems == 0) "全部通过 ✓  $ok/$total · 耗时 ${state.totalMs}ms"
            else "通过 $ok/$total · 异常/空结果 $problems · 耗时 ${state.totalMs}ms"
            txt to if (problems == 0) COLOR_OK else COLOR_FAIL
        }
        else -> "点击下方按钮开始诊断" to COLOR_IDLE
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        if (state.phase == DiagStatus.RUNNING) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = "探测目标：${source?.name ?: "?"} · ${source?.baseUrl ?: ""}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SourceSelector(
    sources: List<DiagSource>,
    selectedId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.forEach { src ->
            FilterChip(
                selected = src.id.name == selectedId,
                onClick = { onSelect(src.id.name) },
                enabled = enabled,
                label = { Text(src.name) },
            )
        }
    }
}

@Composable
private fun StepCard(step: DiagStep) {
    val dotColor = when (step.status) {
        DiagStatus.OK -> COLOR_OK
        DiagStatus.EMPTY -> COLOR_EMPTY
        DiagStatus.FAIL -> COLOR_FAIL
        DiagStatus.RUNNING -> COLOR_RUNNING
        else -> COLOR_IDLE
    }
    val statusText = when (step.status) {
        DiagStatus.OK -> "通过"
        DiagStatus.EMPTY -> "空结果"
        DiagStatus.FAIL -> "失败"
        DiagStatus.RUNNING -> "运行中"
        else -> "待运行"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, RoundedCornerShape(50)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = step.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (step.status == DiagStatus.RUNNING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = "${statusText}${if (step.durationMs > 0) " · ${step.durationMs}ms" else ""}",
                color = dotColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (step.detail.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = step.detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
            )
        }

        if (step.error != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .background(
                        color = COLOR_FAIL.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(10.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = step.error,
                        color = COLOR_FAIL,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
