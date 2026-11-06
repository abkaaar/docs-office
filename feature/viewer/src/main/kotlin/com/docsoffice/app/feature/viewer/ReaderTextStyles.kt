package com.docsoffice.app.feature.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Shared typography tokens for reflow document viewers (text, DOCX, CSV, XLSX, PPTX).
 * PDF bitmaps are out of scope.
 */
object ReaderTextStyles {
    val findMatch: Color = Color(0xFFB45309)

    val bodyFamily: FontFamily = FontFamily.Default
    val gridFamily: FontFamily = FontFamily.Default

    fun bodySize(fontScale: Float): TextUnit = (14f * fontScale).sp

    fun headingSize(level: Int, fontScale: Float): TextUnit {
        val base = when (level) {
            1 -> 22f
            2 -> 18f
            3 -> 16f
            else -> 14f
        }
        return (base * fontScale).sp
    }

    fun cellSize(fontScale: Float): TextUnit = (12f * fontScale).sp

    fun metaSize(fontScale: Float = 1f): TextUnit = (12f * fontScale).sp

    fun headerSize(fontScale: Float): TextUnit = (11f * fontScale).sp

    /**
     * Contrast-safe foreground for text drawn on a slide card background.
     * Custom `#rrggbb` slide fills use luminance; otherwise [themeFg] (reader theme).
     */
    fun slideContentForeground(backgroundHint: String, themeFg: Color): Color {
        val hex = backgroundHint.trim().removePrefix("#")
        if (hex.length != 6) return themeFg
        val rgb = hex.toIntOrNull(16) ?: return themeFg
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance < 0.45) Color.White else Color.Black
    }
}
