package com.lanlinju.videoplayer

import androidx.compose.runtime.Immutable

/**
 * 循环播放模式。
 *
 * - [SINGLE]：单集循环（Media3 `REPEAT_MODE_ONE`，播放器自己循环，无感）；
 * - [LIST]：列表循环（播完自动下一集，最后一集回到第一集）——需要选集列表，由宿主实现；
 * - [NONE]：不循环。
 */
enum class LoopMode(val label: String) {
    SINGLE("单集循环"),
    LIST("列表循环"),
    NONE("不循环"),
}

/**
 * 「更多」面板中可被「编辑播放器」隐藏的条目。
 * 隐藏只是不显示，功能与偏好都还在（再次勾选即恢复）。
 */
enum class MoreItemKey(val label: String) {
    SLEEP_TIMER("定时关闭"),
    SPEED("播放速度"),
    PICTURE_SIZE("画面尺寸"),
    EPISODE("选集"),
    AUTO_PLAY_NEXT("自动连播"),
    LOOP("循环播放"),
    MIRROR("镜像翻转"),
    BACKGROUND_AUDIO("后台听视频"),
    COLOR_ASSIST("色觉辅助"),
    FEEDBACK("播放反馈"),
}

/**
 * 打开「更多」面板时要聚焦的分区。
 *
 * 底栏的「选集 / 倍速 / 画面」快捷入口不再弹居中面板，改为直接打开「更多」并定位到对应分区
 * （选集会默认展开），既保留了快捷入口，又只有一处选项 UI。
 */
enum class MoreFocus {
    NONE,
    SPEED,
    PICTURE_SIZE,
    EPISODE,
}

/**
 * 「更多」面板的宿主配置。
 *
 * 约定：**回调为 null 表示该能力在当前播放页不可用**（例如电视拿不到本机文件所以不能投屏），
 * 对应行 / 格子直接不渲染，而不是显示成灰色不可点。
 *
 * @param onPip 小窗播放（系统画中画）。
 * @param onCast 投屏。
 * @param autoPlayNext / [onAutoPlayNextChange] 自动连播开关（宿主持久化）。
 * @param loopMode / [onLoopModeChange] 循环播放模式（宿主持久化并同步给 [VideoPlayerState]）。
 * @param backgroundAudio / [onBackgroundAudioChange] 后台听视频（宿主持久化）。
 * @param onFeedback 播放反馈（宿主导航到反馈页）。
 * @param onEditPlayer 编辑播放器（宿主弹自定义面板）。
 * @param hiddenItems 「编辑播放器」里被隐藏的条目。
 *
 * 倍速与画面尺寸不需要宿主回调：面板直接操作 [VideoPlayerState]（`setPlaybackSpeed` /
 * `setVideoResize`），当前值也读自 state，所以任何时候都与播放器实际状态一致。
 *
 * @param episodes 选集名列表；为空或不传 [onEpisodeClick] 时不显示「选集」区。
 * @param currentEpisodeIndex 当前集索引，仅用于在选集里高亮（-1 表示未知）。
 * @param onEpisodeClick 点击选集（回调索引）。
 */
@Immutable
data class PlayerMoreConfig(
    val onPip: (() -> Unit)? = null,
    val onCast: (() -> Unit)? = null,
    val episodes: List<String> = emptyList(),
    val currentEpisodeIndex: Int = -1,
    val onEpisodeClick: ((Int) -> Unit)? = null,
    val autoPlayNext: Boolean = true,
    val onAutoPlayNextChange: ((Boolean) -> Unit)? = null,
    val loopMode: LoopMode = LoopMode.NONE,
    val onLoopModeChange: ((LoopMode) -> Unit)? = null,
    val backgroundAudio: Boolean = false,
    val onBackgroundAudioChange: ((Boolean) -> Unit)? = null,
    val onFeedback: (() -> Unit)? = null,
    val onEditPlayer: (() -> Unit)? = null,
    val hiddenItems: Set<MoreItemKey> = emptySet(),
)
