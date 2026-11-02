package com.lanlinju.videoplayer

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanlinju.videoplayer.enhancement.ColorAssistMode
import kotlinx.coroutines.delay

/** 面板背景：接近纯黑的深灰，和视频画面区分开但不过亮（夜间观看不刺眼）。 */
private val PanelBackground = Color(0xF21C1C1E)
private val PanelDivider = Color(0x14FFFFFF)

/** 定时关闭的预设分钟数；0 表示「不开启」。 */
private val SLEEP_PRESETS = listOf(0 to "不开启", 15 to "15:00", 30 to "30:00", 60 to "60:00")

/** 倍速档位：与原先「倍速」居中面板保持一致。 */
private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 画面尺寸档位：与原先「画面适应」居中面板保持一致。 */
private val RESIZE_MODES = listOf(
    ResizeMode.Fit,
    ResizeMode.Fill,
    ResizeMode.Full,
    ResizeMode.FixedRatio_16_9,
    ResizeMode.FixedRatio_4_3,
)

/**
 * 播放器「更多」面板：稍后再看 / 小窗播放 / 投屏 + 定时关闭 + 播放设置列表。
 *
 * 刻意做成底部抽屉式面板（与参考设计的交互一致），而不是把四个入口都塞进顶部栏——
 * 顶部栏在手机竖屏下空间有限，再多的图标只会互相挤压缩小点击热区。
 *
 * 面板整体绘制在控制层内部，因此 `state.showMoreUi()` 会把控制层的自动隐藏计时暂停，
 * 用户慢慢挑选项时面板不会自己消失。
 */
@Composable
internal fun PlayerMorePanel(
    state: VideoPlayerState,
    config: PlayerMoreConfig,
    onClose: () -> Unit,
) {
    var showCustomTimerDialog by remember { mutableStateOf(false) }
    var colorExpanded by remember { mutableStateOf(false) }
    // 从底栏「选集」进来时直接展开选集；从 ⋮ 进来则默认收起（集数多时不撑爆面板）
    var episodeExpanded by remember { mutableStateOf(state.moreFocus.value == MoreFocus.EPISODE) }
    // 倍速不从 state 读（ExoPlayer 的 playbackParameters 不是可观察状态），
    // 因此本地保存一份并在选择时更新 —— 面板内即时高亮，且面板重建时以实际值为准。
    var currentSpeed by remember { mutableFloatStateOf(state.player.playbackParameters.speed) }
    // 画面尺寸读 state：宿主进页时会按偏好设一次，之后任何改动也都会写回 state。
    val currentResize by state.videoResizeMode
    val videoScale by state.videoScale
    val videoRotation by state.videoRotation

    // 剩余时间每秒刷新一次（只在面板打开且定时器生效时跑）
    val deadline by SleepTimer.deadlineElapsedMs.collectAsState()
    var nowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadline) {
        if (deadline == null) return@LaunchedEffect
        while (SleepTimer.deadlineElapsedMs.value != null) {
            nowMs = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val remainingMs = deadline?.let { (it - nowMs).coerceAtLeast(0L) } ?: 0L
    val currentPresetMinutes = if (deadline == null) 0 else ((remainingMs + 59_999L) / 60_000L).toInt()
    val colorMode by state.colorAssistMode
    val loopMode by state.loopMode
    val mirror by state.mirrorEnabled
    val hidden = config.hiddenItems

    // 朝向按播放器自身可用区域决定，而不是按屏幕方向：平板分栏时播放器只占左侧一块，
    // 此时它仍是「宽 > 高」，应贴播放器右缘竖排；手机竖屏则是宽 < 高，走底部抽屉。
    // 不再压暗画面：面板是不透明的侧栏 / 抽屉，视频在旁边继续可见（和参考设计一致）。
    // 这里仍然铺满整层，只为「点面板外任意位置即关闭」——所以必须关掉水波纹，
    // 否则在透明区域点击会闪一圈白色涟漪。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    ) {
        // 判定「够宽」而不是「是不是平板」：
        // - 播放器区域宽 > 高（横屏 / 平板横屏）→ 右侧竖栏；
        // - 播放器区域本身够宽（≥600dp，平板竖屏分栏时播放器也常在 600dp 以上）→ 同样走右侧竖栏；
        // - 其余（手机竖屏）→ 底部抽屉。
        // 右侧栏限宽 360dp 且不超过播放器宽度的 42%，保证画面不被完全盖住。
        val sideSheet = maxWidth >= maxHeight || maxWidth >= 600.dp
        // 右侧栏保底 240dp：内容区 = 240 − 左右各 16dp padding = 208dp，
        // 刚好容得下两个 92dp 操作格 + 10dp 间距（194dp）。否则窄播放器区域（手机横屏）
        // 会把面板压得比一行操作格还窄，「投屏」被挤出面板边缘裁掉。
        // 同时仍受 360dp 上限与「不超过播放器宽 42%」约束，且不超出播放器可用宽度。
        val panelWidth = if (sideSheet) {
            minOf(maxWidth, maxOf(240.dp, minOf(360.dp, maxWidth * 0.42f)))
        } else {
            maxWidth
        }
        val panelShape = if (sideSheet) {
            RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
        } else {
            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        }
        Column(
            modifier = Modifier
                .align(if (sideSheet) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(if (sideSheet) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                .width(panelWidth)
                // 先按面板形状裁剪再画背景：任何超出面板的内容一律裁掉，
                // 不会再画到视频区域上方（历史上出现过 chip 溢出画到画面上的问题）。
                .clip(panelShape)
                .background(PanelBackground, panelShape)
                // 吃掉点击事件，避免点面板内部时穿透到遮罩（把面板关掉）
                .clickable(enabled = false, onClick = {})
                // 底部抽屉才需要限高（横屏高度有限时靠滚动）；右侧栏直接占满高度
                .then(if (sideSheet) Modifier else Modifier.heightIn(max = 480.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // ── 标题 ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "更多",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                if (deadline != null) {
                    Text(
                        text = "定时关闭剩余 ${formatSleepRemaining(remainingMs)}",
                        color = Color(0xFFFFB74D),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 小窗播放 / 投屏 ────────────────────────────────────
            // 固定宽度（不用 weight(1f)）：入口个数会随宿主能力变化（本地页没有投屏等），
            // 平分宽度会让「只剩两个」时格子被拉得又宽又扁、比例失衡。
            // 但容器必须用 FlowRow 而不是 Row：窄面板下放不下就自动换行，
            // 否则会横向溢出把「投屏」挤出面板边缘裁掉（手机横屏实测）。
            if (config.onPip != null || config.onCast != null) {
                ChipFlowRow(spacing = 10.dp) {
                    if (config.onPip != null) {
                        ActionTile(
                            icon = Icons.Rounded.PictureInPictureAlt,
                            label = "小窗播放",
                            onClick = config.onPip,
                        )
                    }
                    if (config.onCast != null) {
                        ActionTile(
                            icon = Icons.Rounded.Cast,
                            label = "投屏",
                            onClick = config.onCast,
                        )
                    }
                }
            }

            // ── 定时关闭 ───────────────────────────────────────────
            if (MoreItemKey.SLEEP_TIMER !in hidden) {
                SectionDivider()
                SectionTitle(
                    text = "定时关闭",
                    hint = if (deadline != null) formatSleepRemaining(remainingMs) + "后暂停播放" else null,
                )
                ChipFlowRow {
                    SLEEP_PRESETS.forEach { (minutes, label) ->
                        MiniChip(
                            text = label,
                            selected = if (minutes == 0) deadline == null else currentPresetMinutes == minutes,
                            onClick = {
                                if (minutes == 0) SleepTimer.cancel() else SleepTimer.start(minutes)
                            },
                        )
                    }
                    MiniChip(
                        text = "自定义",
                        selected = deadline != null && SLEEP_PRESETS.none { it.first == currentPresetMinutes },
                        onClick = { showCustomTimerDialog = true },
                    )
                }
            }

            // ── 播放速度（原「倍速」居中面板，收进这里）──────────────
            if (MoreItemKey.SPEED !in hidden) {
                SectionDivider()
                SectionTitle(text = "播放速度", hint = "${currentSpeed}x")
                ChipFlowRow {
                    SPEED_OPTIONS.forEach { speed ->
                        MiniChip(
                            text = "${speed}x",
                            selected = speed == currentSpeed,
                            onClick = {
                                state.control.setPlaybackSpeed(speed)
                                state.setSpeedText("${speed}x")
                                currentSpeed = speed
                            },
                        )
                    }
                }
            }

            // ── 画面尺寸（原「画面适应」居中面板，收进这里）───────────
            if (MoreItemKey.PICTURE_SIZE !in hidden) {
                SectionDivider()
                SectionTitle(text = "画面尺寸", hint = resizeLabel(currentResize))
                ChipFlowRow {
                    RESIZE_MODES.forEach { mode ->
                        MiniChip(
                            text = resizeLabel(mode),
                            selected = mode == currentResize,
                            // setVideoResize 内部会同步 resizeText，底栏文字（若有）也跟着更新
                            onClick = { state.control.setVideoResize(mode) },
                        )
                    }
                }
                // 双指缩放 / 旋转的结果与「一键还原」：手势没有刻度，这里给出读数，
                // 让用户知道当前捏到了多少，并能在面板里直接还原（不必等浮层按钮出现）。
                if (videoScale != 1f || videoRotation != 0f) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniChip(
                            text = "一键还原",
                            selected = true,
                            onClick = { state.resetVideoTransform() },
                        )
                        Text(
                            text = "缩放 ${(videoScale * 100).toInt()}% · 旋转 ${videoRotation.toInt()}°",
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── 选集（原居中选集面板，收进这里）──────────────────────
            if (config.onEpisodeClick != null && config.episodes.isNotEmpty() && MoreItemKey.EPISODE !in hidden) {
                SectionDivider()
                // 可折叠：集数可能上百，默认收起不至于把面板撑爆；底栏「选集」进来时才默认展开。
                ExpandableRow(
                    icon = Icons.Rounded.VideoLibrary,
                    title = "选集",
                    value = "共 ${config.episodes.size} 集",
                    expanded = episodeExpanded,
                    onClick = { episodeExpanded = !episodeExpanded },
                )
                AnimatedVisibility(
                    visible = episodeExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    // 限高 + 内部滚动：有界高度既避免嵌套滚动容器抛异常，也不会把面板撑到看不见别的项。
                    val indexed = config.episodes.mapIndexed { index, name -> index to name }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 64.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .heightIn(max = 220.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = indexed,
                            key = { (index, _) -> index },
                        ) { (index, name) ->
                            EpisodeChip(
                                text = name,
                                selected = index == config.currentEpisodeIndex,
                                onClick = {
                                    config.onEpisodeClick.invoke(index)
                                    onClose()
                                },
                            )
                        }
                    }
                }
            }

            SectionDivider()

            // ── 播放设置列表 ───────────────────────────────────────
            if (config.onAutoPlayNextChange != null && MoreItemKey.AUTO_PLAY_NEXT !in hidden) {
                SwitchRow(
                    icon = Icons.Rounded.PlaylistPlay,
                    title = "自动连播",
                    checked = config.autoPlayNext,
                    onCheckedChange = config.onAutoPlayNextChange,
                )
            }

            if (config.onLoopModeChange != null && MoreItemKey.LOOP !in hidden) {
                SectionTitle(text = "循环播放")
                ChipFlowRow {
                    LoopMode.entries.forEach { mode ->
                        MiniChip(
                            text = mode.label,
                            selected = loopMode == mode,
                            onClick = { config.onLoopModeChange.invoke(mode) },
                        )
                    }
                }
            }

            if (MoreItemKey.MIRROR !in hidden) {
                SwitchRow(
                    icon = Icons.Rounded.Flip,
                    title = "镜像翻转",
                    checked = mirror,
                    onCheckedChange = { state.setMirrorEnabled(it) },
                )
            }

            if (config.onBackgroundAudioChange != null && MoreItemKey.BACKGROUND_AUDIO !in hidden) {
                SwitchRow(
                    icon = Icons.Rounded.Headphones,
                    title = "后台听视频",
                    checked = config.backgroundAudio,
                    onCheckedChange = config.onBackgroundAudioChange,
                )
            }

            if (MoreItemKey.COLOR_ASSIST !in hidden) {
                ExpandableRow(
                    icon = Icons.Rounded.Palette,
                    title = "色觉辅助",
                    value = if (colorMode == ColorAssistMode.OFF) "不开启" else colorMode.label,
                    expanded = colorExpanded,
                    onClick = { colorExpanded = !colorExpanded },
                )
                AnimatedVisibility(
                    visible = colorExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        ChipFlowRow(
                            modifier = Modifier.padding(start = 32.dp, top = 6.dp, bottom = 4.dp),
                        ) {
                            ColorAssistMode.entries.forEach { mode ->
                                MiniChip(
                                    text = mode.label,
                                    selected = colorMode == mode,
                                    onClick = { state.setColorAssistMode(mode) },
                                )
                            }
                        }
                        Text(
                            text = "为色觉障碍提供校正；与画质增强可同时开启。",
                            color = Color.White.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 32.dp, bottom = 4.dp),
                        )
                    }
                }
            }

            // ── 反馈 / 编辑播放器 ──────────────────────────────────
            if (config.onFeedback != null || config.onEditPlayer != null) {
                SectionDivider()
            }
            if (config.onFeedback != null && MoreItemKey.FEEDBACK !in hidden) {
                ValueRow(
                    icon = Icons.Rounded.Feedback,
                    title = "播放反馈",
                    value = "",
                    onClick = config.onFeedback,
                )
            }
            if (config.onEditPlayer != null) {
                ValueRow(
                    icon = Icons.Rounded.Widgets,
                    title = "编辑播放器",
                    value = "",
                    onClick = config.onEditPlayer,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showCustomTimerDialog) {
        CustomSleepTimerDialog(
            initialMinutes = if (currentPresetMinutes in 1..600) currentPresetMinutes else 45,
            onDismiss = { showCustomTimerDialog = false },
            onConfirm = { minutes ->
                SleepTimer.start(minutes)
                showCustomTimerDialog = false
            },
        )
    }
}

/** 面板首行的方形操作格：图标 + 文字（定宽，不随同行个数被拉宽）。 */
@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

/** 带开关的设置行：图标 + 标题 + Switch。 */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.95f),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** 带当前值与「>」的设置行：点进去是子面板 / 二级页面。 */
@Composable
private fun ValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.95f),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 可展开的设置行（展开后下方挂子选项）。 */
@Composable
private fun ExpandableRow(
    icon: ImageVector,
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    ValueRow(icon = icon, title = title, value = value, onClick = onClick)
}

@Composable
private fun SectionTitle(text: String, hint: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.95f),
            style = MaterialTheme.typography.titleSmall,
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(10.dp))
        if (hint != null) {
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PanelDivider)
    )
    Spacer(Modifier.height(10.dp))
}

/**
 * 胶囊单选行容器：用 [FlowRow] 自动换行，避免选项过多时在窄面板（手机竖屏 / 右侧窄栏）
 * 被横向截断——之前每行直接 [Row] + spacedBy，chip 总宽超过面板宽度就会溢出面板、
 * 画到视频区域上方，看起来像被截断。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlowRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
    }
}

/** 面板内的胶囊单选项：选中用主题色填充，未选中为半透明白底。 */
@Composable
private fun MiniChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.10f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.82f),
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** 选集格：当前集用主题色描边 + 高亮文字，其余为半透明白底。 */
@Composable
private fun EpisodeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.10f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.88f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 自定义定时关闭：输入分钟数（1–600）。 */
@Composable
private fun CustomSleepTimerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initialMinutes.toString()) }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in 1..600
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义定时关闭") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        // 只保留数字，最多 3 位，避免录入非法值
                        text = input.filter { it.isDigit() }.take(3)
                    },
                    singleLine = true,
                    label = { Text("分钟（1–600）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "到点后自动暂停播放，不会退出播放页。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { minutes?.let(onConfirm) }, enabled = valid) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
