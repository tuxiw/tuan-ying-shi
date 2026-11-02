@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lanlinju.videoplayer.enhancement

import androidx.media3.effect.RgbMatrix

/**
 * 色彩辅助（色觉辅助 / 视觉增强）模式。
 *
 * 面向色觉障碍用户与弱光 / 对比度敏感场景：不改变播放器本身的行为，只在渲染管线末端
 * 叠加一层 RGB 颜色矩阵（[RgbMatrix]），因此对任何片源、任何解码器都生效，
 * 且可以和画质增强（Anime4K 超分）叠加使用。
 */
enum class ColorAssistMode(val label: String) {
    /** 不开启。 */
    OFF("不开启"),

    /** 红绿色弱校正（最常见类型）：把红绿通道差异重新映射到亮度差，提升可辨识度。 */
    RED_GREEN("红绿色弱"),

    /** 蓝黄色弱校正（较少见类型）。 */
    BLUE_YELLOW("蓝黄色弱"),

    /** 灰度：去掉色彩信息，只保留亮度，适合重度色觉障碍或专注看画面结构。 */
    GRAYSCALE("灰度"),

    /** 高对比：压低中间调、拉开明暗差，适合暗场 / 投影环境。 */
    HIGH_CONTRAST("高对比"),
}

/**
 * media3 [RgbMatrix] 的元素个数：**4×4 = 16**。
 *
 * 不是 4×5（20）！`DefaultShaderProgram.updateCompositeRgbMatrixArray` 把每个 RgbMatrix 的结果
 * 直接交给 `android.opengl.Matrix.multiplyMM`（只认 4×4），并在 `updateMatrixCache` 里断言
 * `length == 16`，不满足就抛
 * `IllegalStateException: A 4x4 transformation matrix must have 16 elements`，
 * 随后被包装成 `ExoPlaybackException` 冒泡成「播放出错」——只要色彩辅助不是「不开启」就必崩。
 */
private const val RGB_MATRIX_SIZE = 16

/**
 * 用颜色矩阵实现的色彩辅助效果。
 *
 * 矩阵语义由 media3 的着色器决定（`assets/shaders/fragment_shader_transformation_sdr_*_es2.glsl`）：
 * `vec4 transformed = uRgbMatrix * vec4(rgb, 1)`，且输出 alpha 仍取原帧的 alpha
 * （`gl_FragColor = vec4(convertToOutputColors(transformedColors.rgb), inputColor.a)`）。
 *
 * 因此矩阵是**列主序**：`index = 列 * 4 + 行`
 * - 列 = 输入分量：0=R, 1=G, 2=B, 3=常量 1；
 * - 行 = 输出分量：0=R, 1=G, 2=B, 3=A（A 行不生效）。
 * 常量偏移（如高对比的 +0.5·(1−c)）落在第 4 列：[12] / [13] / [14]。
 */
internal class ColorMatrixEffect(matrix: FloatArray) : RgbMatrix {
    private val matrix: FloatArray = matrix

    init {
        // 早失败：尺寸错了与其让播放器在渲染线程崩，不如在构造时就说明白。
        require(matrix.size == RGB_MATRIX_SIZE) {
            "RgbMatrix 必须是 $RGB_MATRIX_SIZE 元素的 4x4 矩阵，收到 ${matrix.size}"
        }
    }

    // 每帧都会被调用（DefaultShaderProgram 会与缓存比较），返回同一个只读数组，避免每帧分配。
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
}

/** 取 [mode] 对应的效果；[ColorAssistMode.OFF] 返回 null（调用方据此不加特效）。 */
internal fun colorAssistEffectOrNull(mode: ColorAssistMode): RgbMatrix? = when (mode) {
    ColorAssistMode.OFF -> null

    ColorAssistMode.RED_GREEN -> ColorMatrixEffect(
        rgb4x4(
            daltonize3x3(
                // 绿色弱（deuteranopia）模拟矩阵：色觉正常者看到的颜色，经此矩阵即近似患者所见
                simulate = floatArrayOf(
                    0.367322f, 0.860646f, -0.227968f,
                    0.280085f, 0.672501f, 0.047413f,
                    -0.011820f, 0.042940f, 0.968881f,
                ),
                shift = RED_GREEN_SHIFT,
            )
        )
    )

    ColorAssistMode.BLUE_YELLOW -> ColorMatrixEffect(
        rgb4x4(
            daltonize3x3(
                // 蓝黄色弱（tritanopia）模拟矩阵
                simulate = floatArrayOf(
                    1.255528f, -0.076749f, -0.178779f,
                    -0.078411f, 0.930809f, 0.147602f,
                    0.004733f, 0.691367f, 0.303900f,
                ),
                shift = BLUE_YELLOW_SHIFT,
            )
        )
    )

    // 灰度：Rec.709 亮度权重，三行相同（alpha 行不生效）
    ColorAssistMode.GRAYSCALE -> ColorMatrixEffect(rgb4x4(LUMA_3X3))

    ColorAssistMode.HIGH_CONTRAST -> ColorMatrixEffect(contrast4x4(1.55f))
}

/** Rec.709 亮度权重（灰度 / 去色用）。 */
private val LUMA_3X3 = floatArrayOf(
    0.2126f, 0.7152f, 0.0722f,
    0.2126f, 0.7152f, 0.0722f,
    0.2126f, 0.7152f, 0.0722f,
)

/** 红绿色弱的「误差位移」矩阵：把丢失的红绿信息折算到可见通道（标准 daltonize 系数）。 */
private val RED_GREEN_SHIFT = floatArrayOf(
    0f, 0f, 0f,
    0.7f, 1f, 0f,
    0.7f, 0f, 1f,
)

/** 蓝黄色弱的误差位移矩阵。 */
private val BLUE_YELLOW_SHIFT = floatArrayOf(
    1f, 0f, 0.7f,
    0f, 1f, 0.7f,
    0f, 0f, 0f,
)

/**
 * 把 3×3 颜色矩阵 + 三通道常量偏移 组装成 media3 需要的 4×4 列主序矩阵（16 元素）。
 *
 * @param linear 行主序 3×3（`linear[row * 3 + col]`），作用于 R/G/B 的线性组合。
 * @param offsetR/G/B 常量偏移，写入第 4 列（与 `vec4(rgb, 1)` 的 w=1 相乘）。
 */
private fun rgb4x4(
    linear: FloatArray,
    offsetR: Float = 0f,
    offsetG: Float = 0f,
    offsetB: Float = 0f,
): FloatArray {
    require(linear.size == 9) { "3x3 颜色矩阵需要 9 个元素，收到 ${linear.size}" }
    val out = FloatArray(RGB_MATRIX_SIZE)
    // 前三列：输入 R / G / B 对输出 R / G / B 的系数（列主序：列 * 4 + 行）
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            out[col * 4 + row] = linear[row * 3 + col]
        }
    }
    // 第 4 列：常量偏移
    out[12] = offsetR
    out[13] = offsetG
    out[14] = offsetB
    // alpha 行不参与计算（输出 alpha 取原帧），仅保持单位阵语义
    out[15] = 1f
    return out
}

/**
 * daltonize 校正矩阵（3×3）：`M = I + S · (I − Sim)`。
 *
 * 直觉：先算出「患者会把颜色看成什么」（Sim），再求出与真实颜色的误差 (I − Sim)，
 * 最后按位移矩阵 S 把这份误差重新分配到患者仍能分辨的通道上——校正后患者看到的画面
 * 才会重新拉开红/绿（或蓝/黄）差异，而不是简单地把整幅画染色。
 */
private fun daltonize3x3(simulate: FloatArray, shift: FloatArray): FloatArray {
    require(simulate.size == 9 && shift.size == 9) { "daltonize 需要两个 3x3 矩阵" }
    // 3×3 单位阵减去模拟矩阵 → 误差矩阵
    val error = FloatArray(9) { i ->
        val row = i / 3
        val col = i % 3
        (if (row == col) 1f else 0f) - simulate[i]
    }
    val out = FloatArray(9)
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            var sum = 0f
            for (k in 0 until 3) {
                sum += shift[row * 3 + k] * error[k * 3 + col]
            }
            out[row * 3 + col] = sum + if (row == col) 1f else 0f
        }
    }
    return out
}

/**
 * 对比度矩阵：`out = (in − 0.5) * contrast + 0.5`，展开即「对角线缩放 + 常量偏移」。
 * 偏移量经 [rgb4x4] 落到第 4 列（着色器里与 w=1 相乘）。
 */
private fun contrast4x4(contrast: Float): FloatArray {
    val offset = 0.5f - 0.5f * contrast
    return rgb4x4(
        linear = floatArrayOf(
            contrast, 0f, 0f,
            0f, contrast, 0f,
            0f, 0f, contrast,
        ),
        offsetR = offset,
        offsetG = offset,
        offsetB = offset,
    )
}
