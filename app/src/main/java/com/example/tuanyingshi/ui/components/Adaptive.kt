package com.example.tuanyingshi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
