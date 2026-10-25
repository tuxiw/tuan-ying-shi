package com.example.tuanyingshi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tuanyingshi.util.COLUMNS_AUTO_MAX
import com.example.tuanyingshi.util.COLUMNS_MIN
import com.example.tuanyingshi.util.UiPrefs
import kotlin.math.floor

/**
 * 平板 / 折叠屏 UI 适配工具。
 *
 * 判定依据统一用 [LocalConfiguration.smallestScreenWidthDp]（最小屏幕宽度 dp，不随横竖屏旋转变化），
 * 对应 Android 官方 sw600dp 平板断点。不引入 androidx.compose.material3.windowsizeclass 依赖——
 * Material3 已内置 NavigationRail，我们的需求只需一个「是否够宽」的布尔即可。
 *
 * 为什么用「最小宽度」而非「当前朝向宽度」：
 * 若用当前朝向宽度，[isTablet] 在手机竖屏(<600)→横屏(>=600) 时会被误判为平板，
 * 导致播放页根布局在 Column↔Row 间跳变、VideoPlayer 被重挂、从头重新播放（旋转即重播）。
 * 用最小宽度则手机无论横竖屏都恒判为手机，平板横竖屏都恒判为平板，永不跳变。
 *
 * 阈值沿用 Material 3 约定：
 * - 手机（compact）：  < 600dp
 * - 平板（medium）：  600dp ~ 839dp
 * - 展开 / 大平板（expanded）： >= 840dp
 */

/** 当前屏幕宽度（dp），随横竖屏 / 折叠状态变化而自动重组。 */
@Composable
fun currentWidthDp(): Int = LocalConfiguration.current.screenWidthDp

/** 最小屏幕宽度（dp）：取横竖屏中较小者，不随旋转变化。用于「是否够宽」的平板判定。 */
@Composable
fun smallestWidthDp(): Int = LocalConfiguration.current.smallestScreenWidthDp

/** 是否平板及以上（最小宽度 >= 600dp）。 */
@Composable
fun isTablet(): Boolean = smallestWidthDp() >= 600

/** 是否大平板 / 展开态（最小宽度 >= 840dp）。 */
@Composable
fun isExpanded(): Boolean = smallestWidthDp() >= 840

/**
 * 根据屏幕宽度计算网格列数。
 * @param phoneBase   手机基准列数
 * @param tabletBase  平板(600-839)列数，默认 phoneBase + 2
 * @param expandedBase 大平板(>=840)列数，默认 tabletBase + 2
 */
@Composable
fun adaptiveColumns(
    phoneBase: Int,
    tabletBase: Int = phoneBase + 2,
    expandedBase: Int = tabletBase + 2,
): Int = when {
    isExpanded() -> expandedBase
    isTablet() -> tabletBase
    else -> phoneBase
}

/**
 * 自适应网格的最小卡片宽度：系统按此值自动算列数（[androidx.compose.foundation.lazy.grid.GridCells.Adaptive]）。
 * 竖图卡片取 ~120dp，平板自动铺更多列。
 */
val adaptiveMinCardWidth: Dp = 120.dp

/** 列表 / 详情类页面在超宽屏上的最大内容宽度，避免横屏平板被拉得过宽。 */
val maxContentWidth: Dp = 1080.dp

// ───────── 列表列数：屏幕自适应 ─────────

/** 海报卡（2:3 竖图）在自动档下的目标最小宽度：手机竖屏约 3 列，平板 5-6 列。 */
val autoPosterMinWidth: Dp = 104.dp

/** 选集按钮胶囊在自动档下的目标最小宽度。 */
val autoEpisodeMinWidth: Dp = 88.dp

/** 详情页平板双栏时，右侧内容区占屏宽的比例（左海报栏 weight=0.38 / 右内容栏 0.62）。 */
private const val TABLET_DETAIL_CONTENT_FRACTION = 0.62f

/**
 * 已知可用宽度时的列数反推（自动档核心算法，纯函数便于复用）。
 *
 * 逻辑：`(可用宽度 + 间距) / (目标最小宽度 + 间距)` 向下取整，再收敛到 [COLUMNS_MIN, maxColumns]。
 * [availableWidth] 指**去掉左右 padding 后**真正用于排卡的宽度。
 */
fun columnsForWidth(
    availableWidth: Dp,
    minItemWidth: Dp,
    spacing: Dp = 10.dp,
    maxColumns: Int = COLUMNS_AUTO_MAX,
): Int {
    val unit = minItemWidth.value + spacing.value
    val usable = (availableWidth.value + spacing.value).coerceAtLeast(unit)
    return floor(usable / unit).toInt().coerceIn(COLUMNS_MIN, maxColumns)
}

/**
 * 按**当前屏幕宽度**反推列数：横竖屏旋转、折叠展开都会自动加列，并按 [maxContentWidth] 截断，
 * 避免在被限宽的超宽屏上算出过多列。
 */
@Composable
fun screenAdaptiveColumns(
    minItemWidth: Dp,
    horizontalPadding: Dp = 12.dp,
    spacing: Dp = 10.dp,
    maxColumns: Int = COLUMNS_AUTO_MAX,
): Int {
    val width = LocalConfiguration.current.screenWidthDp.toFloat()
        .coerceAtMost(maxContentWidth.value)
    val available = (width - horizontalPadding.value * 2f).coerceAtLeast(0f)
    return columnsForWidth(available.dp, minItemWidth, spacing, maxColumns)
}

/**
 * 详情页「选集」区的可用宽度：平板双栏时内容只占 62% 屏宽，再减去左右 20dp 内边距。
 */
@Composable
private fun detailContentWidth(): Dp {
    val width = LocalConfiguration.current.screenWidthDp.toFloat()
        .coerceAtMost(maxContentWidth.value)
    val usable = if (isTablet()) width * TABLET_DETAIL_CONTENT_FRACTION else width
    return (usable - 40f).coerceAtLeast(0f).dp
}

/**
 * 首页 / 收藏等**海报网格**的最终列数：自动档按屏幕算，手动档用用户设定的值。
 */
@Composable
fun effectiveHomeColumns(): Int {
    val auto by UiPrefs.homeColumnsAuto.collectAsStateWithLifecycle()
    val manual by UiPrefs.homeColumns.collectAsStateWithLifecycle()
    val adaptive = screenAdaptiveColumns(
        minItemWidth = autoPosterMinWidth,
        horizontalPadding = 12.dp,
        spacing = 10.dp,
    )
    return if (auto) adaptive else manual
}

/**
 * 详情页**选集网格**的最终列数：自动档按屏幕算，手动档用用户设定的值。
 */
@Composable
fun effectiveDetailColumns(): Int = effectiveDetailColumnsFor(detailContentWidth())

/**
 * 详情页选集网格最终列数——**已知实际可用宽度**时的版本。
 * 播放页右栏 / 详情页右栏等「实际宽度 ≠ 屏幕宽度」的场景必须用这个，
 * 否则会按全屏宽算出过多列，选集胶囊被挤压截断。
 */
@Composable
fun effectiveDetailColumnsFor(availableWidth: Dp): Int {
    val auto by UiPrefs.detailColumnsAuto.collectAsStateWithLifecycle()
    val manual by UiPrefs.detailColumns.collectAsStateWithLifecycle()
    val adaptive = columnsForWidth(availableWidth, autoEpisodeMinWidth, spacing = 10.dp)
    return if (auto) adaptive else manual
}
