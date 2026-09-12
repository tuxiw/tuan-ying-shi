package com.example.tuanyingshi.ui.settings.source_editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.example.tuanyingshi.util.source_rule.MatchTag
import com.example.tuanyingshi.util.source_rule.TestIssue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.ui.settings.SwitchRow
import com.example.tuanyingshi.util.source_rule.ChannelFormat
import com.example.tuanyingshi.util.source_rule.RuleEngineKind
import com.example.tuanyingshi.util.source_rule.RuleExecutor
import com.example.tuanyingshi.util.source_rule.SourceRule
import com.example.tuanyingshi.util.source_rule.SubjectFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceEditorScreen(
    navController: NavController,
    ruleId: String?,
    engine: RuleEngineKind? = null,
    viewModel: SourceEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(ruleId, engine) { viewModel.loadRule(ruleId, engine) }

    val rule by viewModel.rule.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId == null) "新建数据源" else "编辑数据源") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.save()
                        if (ruleId == null) navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.Done, contentDescription = "保存")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (saved) {
                Text(
                    text = "已保存",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionTitle("基础信息")
            OutlinedTextField(
                value = rule.name,
                onValueChange = viewModel::updateName,
                label = { Text("名称 *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = rule.iconUrl,
                onValueChange = viewModel::updateIconUrl,
                label = { Text("图标链接") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SectionTitle("解析引擎")
            Text(
                "选择该数据源使用的解析引擎：CSS（选择器+正则）/ XPath（Kazumi 风格）。剧集引擎将一并跟随设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val engines = listOf(RuleEngineKind.Css, RuleEngineKind.Xpath)
                engines.forEachIndexed { index, kind ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = engines.size),
                        onClick = { viewModel.updateEngine(kind) },
                        selected = rule.engine == kind,
                    ) {
                        Text(if (kind == RuleEngineKind.Css) "CSS 选择器" else "XPath")
                    }
                }
            }

            val isXpath = rule.engine == RuleEngineKind.Xpath
            if (isXpath) {
                XpathStepFields(viewModel = viewModel, rule = rule)
            } else {

            OutlinedTextField(
                value = rule.search.searchUrl,
                onValueChange = { value -> viewModel.updateSearch { copy(searchUrl = value) } },
                label = { Text("搜索链接 *") },
                placeholder = { Text("https://example.com/search/{keyword}") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = rule.search.baseUrl,
                onValueChange = { value -> viewModel.updateSearch { copy(baseUrl = value) } },
                label = { Text("Base URL（可选）") },
                placeholder = { Text(RuleExecutor.deriveRootUrl(rule).ifBlank { "留空自动用搜索链接域名拼接详情页" }) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = rule.search.requestIntervalMs.toString(),
                    onValueChange = { viewModel.updateSearch { copy(requestIntervalMs = it.toLongOrNull() ?: 0L) } },
                    label = { Text("请求间隔(ms)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = rule.search.cacheMinutes.toString(),
                    onValueChange = { viewModel.updateSearch { copy(cacheMinutes = it.toIntOrNull() ?: 0) } },
                    label = { Text("缓存(分钟)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchItem(
                    title = "仅使用第一个词",
                    checked = rule.search.useFirstWordOnly,
                    onCheckedChange = { value -> viewModel.updateSearch { copy(useFirstWordOnly = value) } },
                    modifier = Modifier.weight(1f),
                )
                SwitchItem(
                    title = "去除特殊字符",
                    checked = rule.search.removeSpecialChars,
                    onCheckedChange = { value -> viewModel.updateSearch { copy(removeSpecialChars = value) } },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = rule.search.alternateNameCount.toString(),
                onValueChange = { viewModel.updateSearch { copy(alternateNameCount = it.toIntOrNull() ?: 1) } },
                label = { Text("尝试条目名称数量") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("条目解析格式（subjectFormatId）", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SubjectFormat.entries.forEachIndexed { index, format ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = SubjectFormat.entries.size),
                        onClick = { viewModel.updateSearch { copy(subjectFormatId = format.id) } },
                        selected = rule.search.format() == format,
                    ) {
                        Text(
                            when (format) {
                                SubjectFormat.A -> "单标签"
                                SubjectFormat.Indexed -> "多标签"
                                SubjectFormat.JsonPathIndexed -> "JsonPath"
                            }
                        )
                    }
                }
            }

            SwitchItem(
                title = "优先更短标题",
                desc = "搜索结果按标题长度升序，避免第一季匹配到第二季",
                checked = rule.search.preferShorterName,
                onCheckedChange = { value -> viewModel.updateSearch { copy(preferShorterName = value) } },
            )

            OutlinedTextField(
                value = rule.search.titleSelector,
                onValueChange = { value -> viewModel.updateSearch { copy(titleSelector = value) } },
                label = {
                    Text(
                        if (rule.search.format() == SubjectFormat.A) {
                            "提取条目 <a> 列表 CSS Selector"
                        } else {
                            "提取条目名称列表（CSS Selector / JsonPath）"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (rule.search.format() != SubjectFormat.A) {
                OutlinedTextField(
                    value = rule.search.linkSelector,
                    onValueChange = { value -> viewModel.updateSearch { copy(linkSelector = value) } },
                    label = { Text("提取条目链接列表（CSS Selector / JsonPath）") },
                    placeholder = { Text("与名称列表按顺序一一对应") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            SectionTitle("步骤 2：搜索剧集")
            SwitchItem(
                title = "优先最短标题",
                checked = rule.episodes.preferShortestTitle,
                onCheckedChange = { value -> viewModel.updateEpisodes { copy(preferShortestTitle = value) } },
            )

            Text("线路解析格式（channelFormatId）", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ChannelFormat.entries.forEachIndexed { index, format ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ChannelFormat.entries.size),
                        onClick = { viewModel.updateEpisodes { copy(channelFormatId = format.id, groupByLine = format == ChannelFormat.IndexGrouped) } },
                        selected = rule.episodes.format() == format,
                    ) {
                        Text(
                            when (format) {
                                ChannelFormat.IndexGrouped -> "线路分组"
                                ChannelFormat.NoChannel -> "无线路"
                            }
                        )
                    }
                }
            }

            if (rule.episodes.format() == ChannelFormat.IndexGrouped) {
                OutlinedTextField(
                    value = rule.episodes.lineNameSelector,
                    onValueChange = { value -> viewModel.updateEpisodes { copy(lineNameSelector = value) } },
                    label = { Text("线路名称列表 Selector") },
                    placeholder = { Text("留空则线路命名为「线路 N」") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = rule.episodes.lineNameRegex,
                    onValueChange = { value -> viewModel.updateEpisodes { copy(lineNameRegex = value) } },
                    label = { Text("匹配线路名称正则（可选）") },
                    placeholder = { Text("命名分组 ch；留空用整个文本；匹配不上则丢弃该线路") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = rule.episodes.episodePanelSelector,
                    onValueChange = { value -> viewModel.updateEpisodes { copy(episodePanelSelector = value) } },
                    label = { Text("剧集面板列表 Selector") },
                    placeholder = { Text("每个面板对应一个线路，与线路名称按索引对应") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = rule.episodes.episodeListSelector,
                onValueChange = { value -> viewModel.updateEpisodes { copy(episodeListSelector = value) } },
                label = {
                    Text(
                        if (rule.episodes.format() == ChannelFormat.IndexGrouped) {
                            "从面板提取剧集列表 Selector"
                        } else {
                            "剧集列表 Selector（整页选取）"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = rule.episodes.episodeLinkSelector,
                onValueChange = { value -> viewModel.updateEpisodes { copy(episodeLinkSelector = value) } },
                label = { Text("剧集链接 Selector（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = rule.episodes.episodeNumberRegex,
                onValueChange = { value -> viewModel.updateEpisodes { copy(episodeNumberRegex = value) } },
                label = { Text("剧集序号正则（可选）") },
                placeholder = { Text("命名分组 ep，例如：第\\s*(?<ep>.+)\\s*['话集]") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            } // end else（CSS 步骤 1/2）

            SectionTitle("步骤 3：匹配视频")
            SwitchRow(
                icon = Icons.Default.PlayArrow,
                title = "启用嵌套链接",
                desc = "匹配到嵌套链接时跳转后继续查找视频",
                checked = rule.video.enableNestedLinks,
                onCheckedChange = { value -> viewModel.updateVideo { copy(enableNestedLinks = value) } },
            )
            OutlinedTextField(
                value = rule.video.nestedLinkRegex,
                onValueChange = { value -> viewModel.updateVideo { copy(nestedLinkRegex = value) } },
                label = { Text("匹配嵌套链接正则") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = rule.video.videoUrlRegex,
                onValueChange = { value -> viewModel.updateVideo { copy(videoUrlRegex = value) } },
                label = { Text("匹配视频链接正则") },
                placeholder = { Text("留空默认匹配 .m3u8 / .mp4 / .mkv") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = rule.video.cookies,
                onValueChange = { value -> viewModel.updateVideo { copy(cookies = value) } },
                label = { Text("Cookies（可选）") },
                placeholder = { Text("key=value，一行一个") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            SectionTitle("过滤设置")
            SwitchRow(
                icon = Icons.Default.PlayArrow,
                title = "使用条目名称过滤",
                desc = "要求资源标题包含条目名称",
                checked = rule.filters.filterByEntryName,
                onCheckedChange = { value -> viewModel.updateFilters { copy(filterByEntryName = value) } },
            )
            SwitchRow(
                icon = Icons.Default.PlayArrow,
                title = "使用剧集序号过滤",
                desc = "要求资源标题包含剧集序号",
                checked = rule.filters.filterByEpisodeNumber,
                onCheckedChange = { value -> viewModel.updateFilters { copy(filterByEpisodeNumber = value) } },
            )

            SectionTitle("标记与选源")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = rule.marks.resolution,
                    onValueChange = { value -> viewModel.updateMarks { copy(resolution = value) } },
                    label = { Text("标记分辨率") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = rule.marks.subtitleLanguage,
                    onValueChange = { value -> viewModel.updateMarks { copy(subtitleLanguage = value) } },
                    label = { Text("标记字幕语言") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            SwitchRow(
                icon = Icons.Default.PlayArrow,
                title = "区分条目名称",
                desc = "播放器选源时不去重相同标题的条目",
                checked = rule.marks.distinguishEntryNames,
                onCheckedChange = { value -> viewModel.updateMarks { copy(distinguishEntryNames = value) } },
            )
            SwitchRow(
                icon = Icons.Default.PlayArrow,
                title = "区分线路名称",
                desc = "播放器选源时不去重相同标题不同线路的剧集",
                checked = rule.marks.distinguishLineNames,
                onCheckedChange = { value -> viewModel.updateMarks { copy(distinguishLineNames = value) } },
            )

            SectionTitle("播放时 HTTP 头")
            OutlinedTextField(
                value = rule.headers.referer,
                onValueChange = { value -> viewModel.updateHeaders { copy(referer = value) } },
                label = { Text("Referer") },
                placeholder = { Text(RuleExecutor.deriveRootUrl(rule).ifBlank { "留空自动用站点域名" }) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = rule.headers.userAgent,
                onValueChange = { value -> viewModel.updateHeaders { copy(userAgent = value) } },
                label = { Text("User-Agent") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            TestPanel(viewModel = viewModel)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    desc: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (!desc.isNullOrBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun XpathStepFields(
    viewModel: SourceEditorViewModel,
    rule: SourceRule,
) {
    SectionTitle("步骤 1：XPath 搜索")
    OutlinedTextField(
        value = rule.xpath.searchUrl,
        onValueChange = { viewModel.updateXpath { copy(searchUrl = it) } },
        label = { Text("搜索链接 *") },
        placeholder = { Text("https://example.com/search?q=@keyword") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    SwitchItem(
        title = "使用 POST 提交",
        desc = "勾选后把搜索链接的 query 作为表单 POST（Kazumi 部分站点）",
        checked = rule.xpath.usePost,
        onCheckedChange = { viewModel.updateXpath { copy(usePost = it) } },
    )
    OutlinedTextField(
        value = rule.xpath.searchList,
        onValueChange = { viewModel.updateXpath { copy(searchList = it) } },
        label = { Text("结果节点列表 XPath *") },
        placeholder = { Text("//div[@class='search-item']") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = rule.xpath.searchName,
        onValueChange = { viewModel.updateXpath { copy(searchName = it) } },
        label = { Text("取名称 XPath *") },
        placeholder = { Text("相对每个结果节点取标题文本") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = rule.xpath.searchResult,
        onValueChange = { viewModel.updateXpath { copy(searchResult = it) } },
        label = { Text("取详情链接 XPath *") },
        placeholder = { Text("相对每个结果节点取 href") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = rule.xpath.baseUrl,
        onValueChange = { viewModel.updateXpath { copy(baseUrl = it) } },
        label = { Text("站点根 URL（可选）") },
        placeholder = { Text("留空自动用搜索链接域名拼接相对链接") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = rule.xpath.referer,
        onValueChange = { viewModel.updateXpath { copy(referer = it) } },
        label = { Text("Referer（可选）") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    SectionTitle("步骤 2：XPath 剧集")
    OutlinedTextField(
        value = rule.xpath.chapterRoads,
        onValueChange = { viewModel.updateXpath { copy(chapterRoads = it) } },
        label = { Text("线路节点 XPath（可选）") },
        placeholder = { Text("留空表示无线路，剧集直接取自整页") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = rule.xpath.chapterResult,
        onValueChange = { viewModel.updateXpath { copy(chapterResult = it) } },
        label = { Text("剧集 <a> XPath *") },
        placeholder = { Text("相对线路节点（或无线路时整页）取剧集链接与文本") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun TestPanel(viewModel: SourceEditorViewModel) {
    val keyword by viewModel.testKeyword.collectAsStateWithLifecycle()
    val episode by viewModel.testEpisode.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val results by viewModel.testResults.collectAsStateWithLifecycle()

    SectionTitle("测试数据源")
    OutlinedTextField(
        value = keyword,
        onValueChange = viewModel::updateTestKeyword,
        label = { Text("关键词") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = episode,
        onValueChange = viewModel::updateTestEpisode,
        label = { Text("剧集序号") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Button(
        onClick = viewModel::runTest,
        enabled = !isTesting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(if (isTesting) "测试中..." else "运行测试")
    }

    results?.let { r ->
        if (r.error != null) {
            Text(
                text = "错误：${r.error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            return
        }

        // 步骤 1：搜索条目
        Text("步骤 1：搜索条目", style = MaterialTheme.typography.bodyLarge)
        r.entryIssue?.let { IssueCard(it) } ?: run {
            r.entries.take(5).forEach { pres ->
                Text("• ${pres.result.title}", style = MaterialTheme.typography.bodySmall)
                Text("  ${pres.result.url}", style = MaterialTheme.typography.bodySmall)
                TagRow(pres.tags)
            }
            if (r.entries.isEmpty()) {
                Text("无条目（站点无此番，或关键词需调整）", color = MaterialTheme.colorScheme.outline)
            }
        }

        // 步骤 2：搜索剧集
        Text("步骤 2：搜索剧集", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        r.episodeIssue?.let { IssueCard(it) } ?: run {
            r.groups.take(3).forEach { group ->
                Text("线路：${group.lineName}", style = MaterialTheme.typography.bodyMedium)
                group.episodes.take(5).forEach { ep ->
                    Text("  EP${ep.number ?: "?"}: ${ep.name} -> ${ep.url}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (r.groups.isEmpty()) Text("无剧集", color = MaterialTheme.colorScheme.outline)
            else r.episodeTags.takeIf { it.isNotEmpty() }?.let { TagRow(it) }
        }

        // 步骤 3：匹配视频
        Text("步骤 3：匹配视频", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        r.videoIssue?.let { IssueCard(it) } ?: run {
            Text(
                text = r.videoUrl.ifBlank { "未匹配到视频" },
                color = if (r.videoUrl.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 单条验证问题的卡片：Blocked 偏红（网络 / WAF 侧），InvalidConfig 偏黄（配置侧）。 */
@Composable
private fun IssueCard(issue: TestIssue) {
    val color = when (issue) {
        is TestIssue.Blocked -> MaterialTheme.colorScheme.error
        is TestIssue.InvalidConfig -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = when (issue) {
                    is TestIssue.Blocked -> "⛔ 被拦截：${issue.reason.name}"
                    is TestIssue.InvalidConfig -> "⚠️ 配置未匹配"
                },
                color = color,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = issue.message,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 字段级校验标签行（对齐 animeko MatchTag：✓ / ✗ / 缺失）。 */
@Composable
private fun TagRow(tags: List<MatchTag>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
    ) {
        tags.forEach { tag ->
            val color = when {
                tag.isMissing -> MaterialTheme.colorScheme.error
                tag.isMatch -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            }
            Surface(
                color = color.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text = tag.display,
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
