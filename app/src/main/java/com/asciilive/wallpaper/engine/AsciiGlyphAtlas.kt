package com.asciilive.wallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils

/**
 * Generates a crisp 1D horizontal strip texture atlas of monospace ASCII glyphs
 * for fast single-lookup sampling inside the fragment shader.
 */
class AsciiGlyphAtlas {

    var textureId: Int = 0
        private set
    var charCount: Int = 1
        private set

    private var lastCharset: String = ""

    fun ensureUploaded(charset: String) {
        val safeCharset = if (charset.length >= 2) charset else " .:-=+*#%@"
        if (textureId != 0 && safeCharset == lastCharset) return

        val glyphs = splitCodePoints(safeCharset)
        charCount = glyphs.size.coerceAtLeast(1)
        lastCharset = safeCharset

        val cellWidth = 48
        val cellHeight = 64
        val bitmapWidth = (cellWidth * charCount).coerceAtMost(4096)
        val effectiveCellWidth = (bitmapWidth / charCount).coerceAtLeast(16)

        val bitmap = Bitmap.createBitmap(
            effectiveCellWidth * charCount,
            cellHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = cellHeight * 0.78f
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }
        val fontMetrics = paint.fontMetrics
        val baselineOffset = (cellHeight - (fontMetrics.descent + fontMetrics.ascent)) * 0.5f

        glyphs.forEachIndexed { index, glyph ->
            val cx = index * effectiveCellWidth + effectiveCellWidth * 0.5f
            canvas.drawText(glyph, cx, baselineOffset, paint)
        }

        if (textureId == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            textureId = ids[0]
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        bitmap.recycle()
    }

    fun release() {
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        lastCharset = ""
    }

    private fun splitCodePoints(input: String): List<String> {
        val result = ArrayList<String>(input.length)
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            result.add(String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        return result
    }
}
