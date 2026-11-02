/*
 * 移植自 animeko (OpenAni)，原许可证为 GNU AGPLv3。
 * 原文件：utils/video-enhancement-shader-provider/.../VideoEnhancementShaderProvider.android.kt
 *
 * 改动：去掉 Compose Resources 依赖，直接从 assets/shaders 读取着色器资源。
 */

package com.lanlinju.videoplayer.enhancement

import android.content.Context

/**
 * assets 中 exo-effects 子目录路径，供各 GlEffect 拼装着色器文件名。
 *
 * 注意：这里**不能**带 "shaders/" 前缀——[VideoEnhancementShaderProvider.getShaderSource] 已用
 * ROOT="shaders" 拼过一次，再带前缀会变成 "shaders/shaders/exo-effects/..."（FileNotFoundException
 * → VideoFrameProcessingException → 一开超分就播放失败）。
 */
internal const val EXO_EFFECT_SHADER_DIRECTORY = "exo-effects"

/**
 * 读取经 assets 打包的 Anime4K / Lanczos 着色器资源。
 *
 * 资源目录布局（相对于 assets 根）：
 * - shaders/Anime4K_*.glsl
 * - shaders/exo-effects：扩展名 .frag / .vert / .glsl
 */
object VideoEnhancementShaderProvider {
    private val shaderTemplatePlaceholder = Regex("""\{\{[A-Z_]+\}\}""")
    private const val ROOT = "shaders"

    fun getShaderSource(context: Context, shaderName: String): String =
        context.assets.open("$ROOT/$shaderName").bufferedReader().use { it.readText() }

    fun renderShaderTemplate(template: String, vararg replacements: Pair<String, String>): String {
        var result = template
        replacements.forEach { (name, value) ->
            val placeholder = "{{$name}}"
            require(placeholder in result) { "Shader template does not contain $placeholder" }
            result = result.replace(placeholder, value)
        }
        require(!shaderTemplatePlaceholder.containsMatchIn(result)) {
            "Shader template contains an unresolved placeholder"
        }
        return result
    }
}
