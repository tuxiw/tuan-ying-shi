package com.example.tuanyingshi.ui.settings.source_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.util.source_rule.SourceAnalysisEngine

/**
 * CSS 数据源「测试分析工具」页面（仅调试模式入口可达）。
 * 对一条规则做六大维度的详情检查，输出带状态色的检查清单与原始 HTML 片段。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceAnalysisScreen(
    navController: NavController,
    ruleId: String?,
    viewModel: SourceAnalysisViewModel = hiltViewModel(),
) {
    val rule by viewModel.rule.collectAsStateWithLifecycle()
    val keyword by viewModel.keyword.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(ruleId) { viewModel.loadRule(ruleId) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                title = {
                    Text(
                        "数据源调试分析 · ${rule.name.ifBlank { "未命名" }}",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // 关键词输入 + 开始分析
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = viewModel::updateKeyword,
                    label = { Text("测试关键词") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        viewModel.runAnalysis()
                    }),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.runAnalysis()
                    },
                    enabled = !isAnalyzing,
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(if (isAnalyzing) "分析中" else "开始分析", modifier = Modifier.padding(start = 6.dp))
                }
            }

            if (isAnalyzing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }

            error?.let {
                Text(
                    "分析出错：$it",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            report?.let { rep ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor(rep.overall).copy(alpha = 0.16f),
                ) {
                    Text(
                        rep.summary,
                        color = statusColor(rep.overall),
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(rep.sections) { section ->
                        SectionCard(section)
                    }
                }
            } ?: run {
                if (!isAnalyzing) {
                    Text(
                        "输入测试关键词后点击「开始分析」。本工具会逐项检查该数据源的配置完整性、网络可达性、" +
                                "搜索/剧集/视频解析，并给出原始 HTML 片段，便于定位规则问题。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(section: SourceAnalysisEngine.AnalysisSection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                section.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
            section.items.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .padding(top = 5.dp)
                            .background(statusColor(item.status), RoundedCornerShape(50)),
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        )
                        if (item.detail.isNotBlank()) {
                            Text(
                                item.detail,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun statusColor(status: SourceAnalysisEngine.CheckStatus): Color = when (status) {
    SourceAnalysisEngine.CheckStatus.PASS -> Color(0xFF4CAF50)
    SourceAnalysisEngine.CheckStatus.WARN -> Color(0xFFFFB300)
    SourceAnalysisEngine.CheckStatus.FAIL -> Color(0xFFE53935)
    SourceAnalysisEngine.CheckStatus.SKIP -> Color(0xFF9E9E9E)
}
