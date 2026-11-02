package com.example.tuanyingshi.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.ThemePrefs
import com.example.tuanyingshi.util.theme.ThemePackManager
import com.example.tuanyingshi.util.theme.ThemePackProperties
import java.io.File

/** 单个导航图标在主题套装里的「常规 / 选中」两套本地位图资源（无则均为 null，走内置矢量回退）。 */
data class ThemePackIconPair(val normal: File?, val selected: File?)

/**
 * 当前已应用主题套装的图片 / 图标资源。null 表示未应用套装（走内置默认外观）。
 * 所有图片均为本地已下载解压的文件（指向 filesDir/theme_packs/<id>/...），无需联网即可渲染。
 *
 * 资源命名与 B 站装扮 zip 内语义化文件名一一对应（见 [ThemePackManager.localAssetFile]）：
 *   - tail_bg             → 底部导航栏背景（tail_bg.png）
 *   - side_bg / side_bg_bottom → 侧栏 / 抽屉背景；也作为「全局背景图」底图
 *   - head_myself_squared_bg → 我的界面顶头图（优先），回退 head_myself_bg → head_bg
 *   - tail_color / tail_color_selected → 导航标签常规 / 选中色
 *   - icons[route]        → 对应 Tab 的常规 / 选中图标（套装里 main/myself/channel/dynamic 等）
 */
data class ThemePackAssets(
    val bottomNavBg: File?,
    val sideBg: File?,
    val sideBgBottom: File?,
    val headBg: File?,
    val headMyselfBg: File?,
    val globalBg: File?,
    val tailColor: Color?,
    val tailColorSelected: Color?,
    val icons: Map<String, ThemePackIconPair>,
)

/** 主题套装资源，随 [TuanyingshiTheme] 注入，任意 Composable 可读取（未应用套装时为 null）。 */
val LocalThemePackAssets = compositionLocalOf<ThemePackAssets?> { null }

/** 由当前套装属性解析出可组合的图片资源集合（仅在 themePackId 变化时重算，开销极小）。 */
@Composable
fun rememberThemePackAssets(): ThemePackAssets? {
    val context = LocalContext.current
    val config by ThemePrefs.config.collectAsStateWithLifecycle()
    return remember(config.themePackId) {
        val id = config.themePackId ?: return@remember null
        if (!ThemePackManager.isReady(context, id)) return@remember null
        val props = ThemePackManager.currentProperties(context) ?: return@remember null
        buildAssets(context, id, props)
    }
}

private fun buildAssets(context: Context, id: Int, props: ThemePackProperties): ThemePackAssets {
    fun file(key: String): File? = ThemePackManager.localAssetFile(context, id, key)
    fun color(hex: String?): Color? = parseColorHex(hex)
    val icons = buildMap<String, ThemePackIconPair> {
        // 把套装里能对应的图标全部用上（main / dynamic / channel / myself 等），
        // 不再只接首页/我的；排期表、排行榜在 B 站装扮里没有同名图标，这里用 best-effort
        // 映射到 dynamic / channel，某个主题缺该图标时对应文件为 null，UI 自动回退矢量图标。
        put(
            Screen.Home.route,
            ThemePackIconPair(file("tail_icon_main"), file("tail_icon_selected_main")),
        )
        put(
            Screen.Schedule.route,
            ThemePackIconPair(file("tail_icon_dynamic"), file("tail_icon_selected_dynamic")),
        )
        put(
            Screen.Ranking.route,
            ThemePackIconPair(file("tail_icon_channel"), file("tail_icon_selected_channel")),
        )
        put(
            Screen.Mine.route,
            ThemePackIconPair(file("tail_icon_myself"), file("tail_icon_selected_myself")),
        )
    }
    val sideBg = file("side_bg")
    return ThemePackAssets(
        bottomNavBg = file("tail_bg"),
        sideBg = sideBg,
        sideBgBottom = file("side_bg_bottom"),
        headBg = file("head_bg"),
        // 「我的」页顶头图首选 squared 版（head_myself_squared_bg，方形头图，构图最适合做顶图），
        // 依次回退 head_myself_bg → head_bg，保证不同装扮都能有图可用。
        headMyselfBg = file("head_myself_squared_bg")
            ?: file("head_myself_bg")
            ?: file("head_bg"),
        // 全局背景用最宽幅的 side_bg（装扮里没有专门的全局底图字段）
        globalBg = sideBg,
        tailColor = color(props.tailColor),
        tailColorSelected = color(props.tailColorSelected),
        icons = icons,
    )
}
