package com.example.tuanyingshi.util.theme

import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 主题套装（B 站装扮格式）的数据模型、MD5 校验与 zip 解压工具。
 *
 * 数据来源：https://tuanyingshi.pages.dev/v1/theme.json
 *
 * 每个套装 [ThemePackItem] 含 [ThemePackProperties]，其中：
 * - 颜色类（color / color_mode / color_second_page / tail_color ...）：决定主题配色；
 * - 资源类（head_bg / side_bg / tail_icon_* / image_cover ...）：仅提供云端 URL，
 *   真正的资源在 [ThemePackProperties.packageUrl] 指向的 zip 内，必须先把 zip 下载并
 *   解压到本地后才能「使用」——即「先把主题对应的内容全部下载下来才能使用」的核心约束。
 */

// ───────────────── 数据模型 ─────────────────

data class ThemePackListResp(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<ThemePackItem>? = null,
)

data class ThemePackItem(
    @SerializedName("item_id") val itemId: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("properties") val properties: ThemePackProperties? = null,
)

data class ThemePackProperties(
    // ── 颜色 ──
    @SerializedName("color") val color: String? = null,
    @SerializedName("color_mode") val colorMode: String? = null,
    @SerializedName("color_second_page") val colorSecondPage: String? = null,
    @SerializedName("side_bg_color") val sideBgColor: String? = null,
    @SerializedName("tail_color") val tailColor: String? = null,
    @SerializedName("tail_color_selected") val tailColorSelected: String? = null,
    @SerializedName("pub_btn_shade_color_top") val pubBtnShadeColorTop: String? = null,
    @SerializedName("pub_btn_shade_color_bottom") val pubBtnShadeColorBottom: String? = null,
    @SerializedName("pub_btn_plus_color") val pubBtnPlusColor: String? = null,
    // ── 背景 / 封面（图片或视频）──
    @SerializedName("head_bg") val headBg: String? = null,
    @SerializedName("head_myself_bg") val headMyselfBg: String? = null,
    @SerializedName("head_myself_squared_bg") val headMyselfSquaredBg: String? = null,
    @SerializedName("head_myself_mp4_bg") val headMyselfMp4Bg: String? = null,
    @SerializedName("head_tab_bg") val headTabBg: String? = null,
    @SerializedName("image_cover") val imageCover: String? = null,
    @SerializedName("image_preview") val imagePreview: String? = null,
    @SerializedName("side_bg") val sideBg: String? = null,
    @SerializedName("side_bg_bottom") val sideBgBottom: String? = null,
    @SerializedName("tail_bg") val tailBg: String? = null,
    // ── 导航图标（tail_icon_*）──
    @SerializedName("tail_icon_channel") val tailIconChannel: String? = null,
    @SerializedName("tail_icon_dynamic") val tailIconDynamic: String? = null,
    @SerializedName("tail_icon_main") val tailIconMain: String? = null,
    @SerializedName("tail_icon_myself") val tailIconMyself: String? = null,
    @SerializedName("tail_icon_shop") val tailIconShop: String? = null,
    @SerializedName("tail_icon_pub_btn_bg") val tailIconPubBtnBg: String? = null,
    @SerializedName("tail_icon_selected_channel") val tailIconSelectedChannel: String? = null,
    @SerializedName("tail_icon_selected_dynamic") val tailIconSelectedDynamic: String? = null,
    @SerializedName("tail_icon_selected_main") val tailIconSelectedMain: String? = null,
    @SerializedName("tail_icon_selected_myself") val tailIconSelectedMyself: String? = null,
    @SerializedName("tail_icon_selected_shop") val tailIconSelectedShop: String? = null,
    @SerializedName("tail_icon_selected_pub_btn_bg") val tailIconSelectedPubBtnBg: String? = null,
    // ── 打包信息 ──
    @SerializedName("package_md5") val packageMd5: String? = null,
    @SerializedName("package_url") val packageUrl: String? = null,
    @SerializedName("ver") val ver: String? = null,
)

// ───────────────── 工具 ─────────────────

/** 计算文件 MD5（小写 32 位十六进制），失败返回 null。 */
fun computeFileMd5(file: File): String? = runCatching {
    val md = MessageDigest.getInstance("MD5")
    FileInputStream(file).use { fis ->
        val buf = ByteArray(8192)
        var read: Int
        while (fis.read(buf).also { read = it } != -1) md.update(buf, 0, read)
    }
    BigInteger(1, md.digest()).toString(16).padStart(32, '0')
}.getOrNull()

/**
 * 解压 zip 到目标目录。净化 entry 路径、拒绝跳出目标目录的穿越 entry，避免 zip 路径注入。
 * @return 成功解压返回 true；任意异常返回 false。
 */
fun unzipFile(zipFile: File, destDir: File): Boolean = runCatching {
    destDir.mkdirs()
    val canonicalDest = destDir.canonicalPath
    ZipInputStream(FileInputStream(zipFile).buffered()).use { zis ->
        var entry: ZipEntry?
        while (zis.nextEntry.also { entry = it } != null) {
            val raw = entry?.name ?: continue
            // 规范化：统一斜杠、去掉前导 "/"，目录 entry 以 "/" 结尾
            val safe = raw.replace('\\', '/').trimStart('/')
            val isDir = safe.endsWith('/') || entry?.isDirectory == true
            if (safe.isEmpty()) {
                zis.closeEntry()
                continue
            }
            if (isDir) {
                File(destDir, safe).mkdirs()
                zis.closeEntry()
                continue
            }
            val outFile = File(destDir, safe)
            // 防 zip 穿越：输出文件必须仍在 destDir 内
            if (!outFile.canonicalPath.startsWith(canonicalDest)) {
                zis.closeEntry()
                continue
            }
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
            zis.closeEntry()
        }
    }
    true
}.getOrDefault(false)
