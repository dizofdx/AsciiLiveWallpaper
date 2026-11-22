package com.asciilive.wallpaper.engine

import android.content.Context
import android.graphics.Color
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.asciilive.wallpaper.data.AsciiSettings
import com.asciilive.wallpaper.data.SourceType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Shared OpenGL ES 3.0 ASCII renderer used by both the in-app live preview (GLSurfaceView)
 * and the system AsciiWallpaperService.Engine.
 */
class AsciiGlRenderer(context: Context) : GLSurfaceView.Renderer {

    private val mediaController = MediaSourceController(context.applicationContext)
    private val glyphAtlas = AsciiGlyphAtlas()

    @Volatile
    private var settings: AsciiSettings = AsciiSettings()

    private var programId: Int = 0
    private var vboId: Int = 0
    private var photoTextureId: Int = 0
    private var videoOesTextureId: Int = 0

    private var viewportWidth: Int = 1080
    private var viewportHeight: Int = 1920

    private val videoMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private var startTimeNs: Long = System.nanoTime()
    private var lastFrameNs: Long = 0L

    // Cached uniform locations
    private var uGlyphAtlas = -1
    private var uPhotoTex = -1
    private var uVideoTex = -1
    private var uVideoMatrix = -1
    private var uResolution = -1
    private var uMediaResolution = -1
    private var uTime = -1
    private var uSourceMode = -1
    private var uDemoPreset = -1
    private var uScaleMode = -1
    private var uColumns = -1
    private var uCharCount = -1
    private var uColorMode = -1
    private var uFgColor = -1
    private var uBgColor = -1
    private var uInvert = -1
    private var uBrightness = -1
    private var uContrast = -1
    private var uGlow = -1
    private var uScanlines = -1

    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            // x, y, u, v
            put(
                floatArrayOf(
                    -1f, -1f, 0f, 0f,
                     1f, -1f, 1f, 0f,
                    -1f,  1f, 0f, 1f,
                     1f,  1f, 1f, 1f
                )
            )
            position(0)
        }

    fun updateSettings(newSettings: AsciiSettings) {
        val previous = settings
        settings = newSettings
        if (newSettings.sourceType == SourceType.VIDEO && newSettings.mediaUri != null) {
            mediaController.prepareVideo(
                uriString = newSettings.mediaUri,
                speed = newSettings.playbackSpeed,
                loop = newSettings.loopVideo
            )
        } else if (previous.sourceType == SourceType.VIDEO) {
            mediaController.stopVideo()
        }

        if (newSettings.sourceType == SourceType.IMAGE && newSettings.mediaUri != null) {
            mediaController.loadPhotoIfNeeded(newSettings.mediaUri)
        }
    }

    fun setVisible(visible: Boolean) {
        if (visible) {
            if (settings.sourceType == SourceType.VIDEO && settings.mediaUri != null) {
                mediaController.resumeVideo()
            }
        } else {
            mediaController.pauseVideo()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        onGlContextCreated()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        onGlViewportChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        renderFrame()
    }

    fun onGlContextCreated() {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        programId = createProgram(AsciiShaders.VERTEX_SHADER, AsciiShaders.FRAGMENT_SHADER)
        cacheUniformLocations()

        // Upload static quad VBO
        val buffers = IntArray(1)
        GLES30.glGenBuffers(1, buffers, 0)
        vboId = buffers[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            quadVertices.capacity() * 4,
            quadVertices,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        // Create 2D photo texture
        val textures = IntArray(2)
        GLES30.glGenTextures(2, textures, 0)
        photoTextureId = textures[0]
        videoOesTextureId = textures[1]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, photoTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // Create OES video texture
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoOesTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        mediaController.attachVideoTexture(videoOesTextureId)
        glyphAtlas.ensureUploaded(settings.activeCharset)

        // Ensure current source is ready after context recreation
        val current = settings
        if (current.sourceType == SourceType.VIDEO && current.mediaUri != null) {
            mediaController.prepareVideo(current.mediaUri, current.playbackSpeed, current.loopVideo)
        } else if (current.sourceType == SourceType.IMAGE && current.mediaUri != null) {
            mediaController.loadPhotoIfNeeded(current.mediaUri)
        }
    }

    fun onGlViewportChanged(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    fun renderFrame() {
        val current = settings
        if (programId == 0) return

        // Update glyph atlas if charset changed
        glyphAtlas.ensureUploaded(current.activeCharset)

        // Upload pending photo bitmap on GL thread
        mediaController.consumePendingPhoto()?.let { bmp ->
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, photoTextureId)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            bmp.recycle()
        }

        // Update zero-copy SurfaceTexture frame for video
        if (current.sourceType == SourceType.VIDEO) {
            mediaController.updateVideoTexImage(videoMatrix)
        }

        val nowNs = System.nanoTime()
        val elapsedSec = ((nowNs - startTimeNs) / 1_000_000_000.0).toFloat() * current.playbackSpeed
        lastFrameNs = nowNs

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(programId)

        // Bind textures
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, glyphAtlas.textureId)
        GLES30.glUniform1i(uGlyphAtlas, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, photoTextureId)
        GLES30.glUniform1i(uPhotoTex, 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoOesTextureId)
        GLES30.glUniform1i(uVideoTex, 2)

        GLES30.glUniformMatrix4fv(uVideoMatrix, 1, false, videoMatrix, 0)
        GLES30.glUniform2f(uResolution, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES30.glUniform2f(
            uMediaResolution,
            mediaController.mediaWidth.toFloat().coerceAtLeast(1f),
            mediaController.mediaHeight.toFloat().coerceAtLeast(1f)
        )
        GLES30.glUniform1f(uTime, elapsedSec)

        val effectiveSource = when {
            current.sourceType == SourceType.IMAGE && current.mediaUri != null -> SourceType.IMAGE.shaderValue
            current.sourceType == SourceType.VIDEO && current.mediaUri != null -> SourceType.VIDEO.shaderValue
            else -> SourceType.DEMO.shaderValue
        }
        GLES30.glUniform1i(uSourceMode, effectiveSource)
        GLES30.glUniform1i(uDemoPreset, current.demoPreset.shaderValue)
        GLES30.glUniform1i(uScaleMode, current.videoScaleMode.shaderValue)
        GLES30.glUniform1f(uColumns, current.columns.toFloat())
        GLES30.glUniform1f(uCharCount, glyphAtlas.charCount.toFloat())
        GLES30.glUniform1i(uColorMode, current.colorMode.shaderValue)

        val fgInt = current.effectiveForegroundColor
        val bgInt = current.effectiveBackgroundColor
        GLES30.glUniform3f(uFgColor, Color.red(fgInt) / 255f, Color.green(fgInt) / 255f, Color.blue(fgInt) / 255f)
        GLES30.glUniform3f(uBgColor, Color.red(bgInt) / 255f, Color.green(bgInt) / 255f, Color.blue(bgInt) / 255f)

        GLES30.glUniform1f(uInvert, if (current.invertLuminance) 1f else 0f)
        GLES30.glUniform1f(uBrightness, current.brightness)
        GLES30.glUniform1f(uContrast, current.contrast)
        GLES30.glUniform1f(uGlow, current.glowIntensity)
        GLES30.glUniform1f(uScanlines, current.scanlinesIntensity)

        // Draw fullscreen quad
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun release() {
        mediaController.release()
        glyphAtlas.release()
        if (photoTextureId != 0 || videoOesTextureId != 0) {
            GLES30.glDeleteTextures(2, intArrayOf(photoTextureId, videoOesTextureId), 0)
            photoTextureId = 0
            videoOesTextureId = 0
        }
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }

    private fun cacheUniformLocations() {
        uGlyphAtlas = GLES30.glGetUniformLocation(programId, "uGlyphAtlas")
        uPhotoTex = GLES30.glGetUniformLocation(programId, "uPhotoTex")
        uVideoTex = GLES30.glGetUniformLocation(programId, "uVideoTex")
        uVideoMatrix = GLES30.glGetUniformLocation(programId, "uVideoMatrix")
        uResolution = GLES30.glGetUniformLocation(programId, "uResolution")
        uMediaResolution = GLES30.glGetUniformLocation(programId, "uMediaResolution")
        uTime = GLES30.glGetUniformLocation(programId, "uTime")
        uSourceMode = GLES30.glGetUniformLocation(programId, "uSourceMode")
        uDemoPreset = GLES30.glGetUniformLocation(programId, "uDemoPreset")
        uScaleMode = GLES30.glGetUniformLocation(programId, "uScaleMode")
        uColumns = GLES30.glGetUniformLocation(programId, "uColumns")
        uCharCount = GLES30.glGetUniformLocation(programId, "uCharCount")
        uColorMode = GLES30.glGetUniformLocation(programId, "uColorMode")
        uFgColor = GLES30.glGetUniformLocation(programId, "uFgColor")
        uBgColor = GLES30.glGetUniformLocation(programId, "uBgColor")
        uInvert = GLES30.glGetUniformLocation(programId, "uInvert")
        uBrightness = GLES30.glGetUniformLocation(programId, "uBrightness")
        uContrast = GLES30.glGetUniformLocation(programId, "uContrast")
        uGlow = GLES30.glGetUniformLocation(programId, "uGlow")
        uScanlines = GLES30.glGetUniformLocation(programId, "uScanlines")
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)

        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw IllegalStateException("GLSL program link failed: $log")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("GLSL compile failed: $log")
        }
        return shader
    }
}
