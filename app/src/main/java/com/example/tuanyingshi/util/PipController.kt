package com.example.tuanyingshi.util

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import com.example.tuanyingshi.TuanyingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 小窗播放（画中画）控制器。
 *
 * 为什么单独抽一个单例：进入画中画是 [Activity] 的能力，而播放页是纯 Compose 的函数，
 * 拿不到「当前 Activity 是否已进入小窗」这个系统回调。这里由 [MainActivity] 在
 * `onPictureInPictureModeChanged` 里回报状态，Compose 侧订阅 [inPip] 即可在
 * 小窗里隐藏整套控制层（系统自带的悬浮按钮已经够用）。
 *
 * 另外把视频宽高比同步给系统（Android 12+ 的 [PictureInPictureParams.Builder.setAutoEnterEnabled]
 * 也依赖它），小窗窗口才会贴合画面，不出现大片黑边。
 */
object PipController {
    private const val KEY_LARGE_WINDOW = "player_pip_large_window"

    /**
     * 「大窗」相对「贴合画面」的放大系数。
     *
     * 小窗的窗口大小只能靠宽高比间接控制：系统按 `屏幕宽 × 固定百分比` 定下短边，
     * 长边 = 短边 × 宽高比。所以放大窗口就是把这个比例拉长（横屏视频更宽、竖屏视频更高），
     * 代价是画面里会多出一点黑边 —— 这是应用侧唯一能改变小窗尺寸的手段。
     */
    private const val LARGE_SCALE = 1.35f

    private val _inPip = MutableStateFlow(false)

    /** 当前是否处于小窗（画中画）模式。 */
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()

    private val prefs get() = TuanyingApp.getInstance().preferences

    /** 上次小窗用的是不是「大窗」（持久化：下次进小窗沿用同一档位）。 */
    fun isLargeWindow(): Boolean = prefs.getBoolean(KEY_LARGE_WINDOW, false)

    /** 按当前档位算出应传给系统的宽高比。 */
    fun effectiveRatio(videoRatio: Float): Float {
        val ratio = videoRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        if (!isLargeWindow()) return ratio
        // 横屏视频：把窗口拉宽；竖屏视频：把窗口拉高（短边固定，只能动长边）
        return if (ratio >= 1f) {
            (ratio * LARGE_SCALE).coerceAtMost(2.39f)
        } else {
            (ratio / LARGE_SCALE).coerceAtLeast(1f / 2.39f)
        }
    }

    /**
     * 切换小窗尺寸（贴合画面 ↔ 大窗），并持久化——下次进入小窗沿用这次的选择。
     *
     * @return 切换后的状态：true = 大窗。
     */
    fun toggleSize(activity: Activity, videoRatio: Float): Boolean {
        val next = !isLargeWindow()
        prefs.edit().putBoolean(KEY_LARGE_WINDOW, next).apply()
        // 小窗里改参数是即时生效的（setSeamlessResizeEnabled(true) 会让过渡是平滑的）
        syncParams(activity, effectiveRatio(videoRatio))
        return next
    }

    /** 系统是否支持画中画（Android 8.0+ 且设备声明了该特性）。 */
    fun isSupported(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * 进入小窗播放。
     *
     * @param aspectRatio 视频宽高比（宽/高）；非法值回退 16:9。
     * @return 是否成功进入（系统在免打扰 / 锁屏等场景可能拒绝）。
     */
    fun enter(activity: Activity, aspectRatio: Float): Boolean {
        if (!isSupported(activity)) return false
        // 沿用上次的小窗档位（标准 / 大窗）
        val ratio = effectiveRatio(aspectRatio)
        val params = buildParams(activity, ratio, autoEnter = false)
        val ok = runCatching { activity.enterPictureInPictureMode(params) }.getOrDefault(false)
        if (ok) lastAppliedRatio = ratio
        return ok
    }

    /**
     * 同步小窗参数（宽高比 + 是否允许「按 Home 键自动进入小窗」）。
     * 播放中画面比例变化（换集）时调用，避免小窗里出现黑边。
     */
    fun syncParams(activity: Activity, aspectRatio: Float, autoEnter: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isSupported(activity)) return
        // 已经在小窗里且比例没变就别重复下发：系统会把「当前短边」当作基准重新算一遍窗口，
        // 白白打断用户上一次双击放大 / 双指缩放后系统记住的尺寸。
        if (lastAppliedRatio == aspectRatio && _inPip.value) return
        lastAppliedRatio = aspectRatio
        runCatching {
            activity.setPictureInPictureParams(buildParams(activity, aspectRatio, autoEnter))
        }
    }

    /** 最近一次下发给系统的宽高比；见 [syncParams]。 */
    private var lastAppliedRatio: Float? = null

    /** 由 Activity 的系统回调转发。 */
    fun onPipModeChanged(inPip: Boolean) {
        _inPip.value = inPip
    }

    private fun buildParams(
        activity: Activity,
        aspectRatio: Float,
        autoEnter: Boolean,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        // 系统要求比例落在 [1/2.39, 2.39] 之间，越界会抛 IllegalArgumentException
        val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        val clamped = ratio.coerceIn(1f / 2.39f, 2.39f)
        val rational = Rational((clamped * 1000).toInt(), 1000)
        builder.setAspectRatio(rational)
        // Android 12+ 起，双击小窗由**系统**在「当前尺寸 ↔ 最大/最小尺寸」之间切换
        // （小窗里的触摸事件不投递给应用，应用自己做不了双击）。
        // setExpandedAspectRatio（API 33+）告诉系统「放大态」该用什么比例：
        // 这里沿用当前比例，保证放大后画面不被拉变形、也不突然多出黑边。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_EXPANDED_PICTURE_IN_PICTURE)
        ) {
            builder.setExpandedAspectRatio(rational)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 仅 Android 12+ 支持：允许用户按 Home 键后自动留在小窗继续播放
            builder.setAutoEnterEnabled(autoEnter && isSupported(activity))
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }
}
