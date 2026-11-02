/*
 * 移植自 animeko (OpenAni)，原许可证为 GNU AGPLv3。
 * 原文件：app/shared/video-player/src/androidMain/kotlin/videoenhancement/DesktopStyleLanczosSharpEffect.kt
 *
 * 改动：包名调整；着色器目录常量改用本工程的 EXO_EFFECT_SHADER_DIRECTORY。
 */

@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lanlinju.videoplayer.enhancement

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.roundToInt

/**
 * A single-pass radial EWA approximation of mpv's `ewa_lanczossharp` presentation chain.
 *
 * It uses mpv's Jinc radius and sharp blur, sigmoid upscaling, and 0.7 anti-ringing while
 * avoiding a second full-size intermediate texture on mobile GPUs.
 *
 * The output is the input scaled to fit [viewportSize], or the input size while it is `null`.
 * [viewportSize] may be updated from any thread while the effect is in use: the shader program
 * resizes its output on the next frame.
 */
internal class DesktopStyleLanczosSharpEffect : GlEffect {
    @Volatile
    var viewportSize: VideoDimensions? = null

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        DesktopStyleLanczosSharpShaderProgram(context) { viewportSize }
}

private class DesktopStyleLanczosSharpShaderProgram(
    context: Context,
    private val viewportSize: () -> VideoDimensions?,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ true,
    /* texturePoolCapacity = */ 1,
) {
    val shaderSources = LanczosSharpShaderSources(context)

    private val program = try {
        GlProgram(shaderSources.vertexShader, shaderSources.fragmentShader).also {
            it.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
        }
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException("Could not compile desktop-style Lanczos sharp effect", e)
    }

    private var inputWidth = 0
    private var inputHeight = 0
    private var configuredViewport: VideoDimensions? = null

    override fun queueInputFrame(
        glObjectsProvider: GlObjectsProvider,
        inputTexture: GlTextureInfo,
        presentationTimeUs: Long,
    ) {
        if (viewportSize() != configuredViewport) {
            try {
                super.release()
            } catch (e: VideoFrameProcessingException) {
                onError(e)
                return
            }
        }
        super.queueInputFrame(glObjectsProvider, inputTexture, presentationTimeUs)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight
        val viewport = viewportSize()
        configuredViewport = viewport
        return lanczosSharpOutputSize(inputWidth, inputHeight, viewport)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex = */ 0)
            program.setFloatsUniform(
                "uInputSize",
                floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat()),
            )
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not release desktop-style Lanczos sharp effect", e)
        }
        super.release()
    }
}


/**
 * The size of [inputWidth] x [inputHeight] scaled to fit [viewport] while keeping its aspect ratio.
 */
internal fun lanczosSharpOutputSize(inputWidth: Int, inputHeight: Int, viewport: VideoDimensions?): Size {
    if (viewport == null) return Size(inputWidth, inputHeight)
    val scale = minOf(
        viewport.width.toDouble() / inputWidth,
        viewport.height.toDouble() / inputHeight,
    )
    return Size(
        (inputWidth * scale).roundToInt().coerceAtLeast(1),
        (inputHeight * scale).roundToInt().coerceAtLeast(1),
    )
}

private class LanczosSharpShaderSources(context: Context) {
    val vertexShader = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$EXO_EFFECT_SHADER_DIRECTORY/ewa_lanczossharp.vert",
    )
    val fragmentShader = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$EXO_EFFECT_SHADER_DIRECTORY/ewa_lanczossharp.frag",
    )
}
