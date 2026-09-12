package com.example.tuanyingshi.util

import android.os.Build
import com.example.tuanyingshi.BuildConfig
import com.example.tuanyingshi.TuanyingApp
import java.util.UUID

/**
 * 设备标识与设备信息，供后台「用户分析 - 登录设备」使用（POST /user/devices）。
 *
 * 后台要展示「设备型号 / 平台 / App 版本 / 最近活跃」，这些数据只能由客户端上报：
 * - [deviceId]：稳定的安装级标识，首次读取时生成 UUID 并持久化，卸载重装会变（符合"一台设备"的直觉）；
 * - [deviceName]：厂商 + 机型，如 `Xiaomi M2012K11AC`；
 * - [appVersion]：`版本名 (版本号)`，便于后台定位是哪个版本的数据。
 */
object DeviceInfo {

    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_REPORT = "device_last_report_at"

    /** 上报节流间隔：12 小时内不重复上报（启动心跳用，登录成功时强制上报）。 */
    private const val REPORT_INTERVAL_MS = 12 * 60 * 60 * 1000L

    private val prefs get() = TuanyingApp.getInstance().preferences

    /** 稳定的设备标识（首次调用生成 UUID 并写入 preferences，之后保持不变）。 */
    fun deviceId(): String {
        val saved = prefs.getString(KEY_DEVICE_ID, null)
        if (!saved.isNullOrBlank()) return saved
        val fresh = "a${UUID.randomUUID().toString().replace("-", "").take(30)}"
        prefs.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }

    /**
     * 设备型号：厂商 + 机型。机型本身已带厂商前缀时不再重复拼接，
     * 避免后台出现「Xiaomi Xiaomi M2012K11AC」这种重复。
     */
    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        if (model.isEmpty()) return manufacturer.ifEmpty { "Android 设备" }
        if (manufacturer.isEmpty() || model.contains(manufacturer, ignoreCase = true)) return model
        return "$manufacturer $model"
    }

    /** 系统版本，如 `Android 13`。 */
    fun systemVersion(): String = "Android ${Build.VERSION.RELEASE.orEmpty()}"

    /** 平台标识，固定 android。 */
    fun platform(): String = "android"

    /** App 版本：`0.5.2 (52)`，后端会截断到 32 字符。 */
    fun appVersion(): String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    /** 距上次成功上报是否已超过节流间隔。 */
    fun isReportDue(): Boolean {
        val last = prefs.getLong(KEY_LAST_REPORT, 0L)
        return System.currentTimeMillis() - last >= REPORT_INTERVAL_MS
    }

    /** 记录一次成功上报时间。 */
    fun markReported() {
        prefs.edit().putLong(KEY_LAST_REPORT, System.currentTimeMillis()).apply()
    }
}
