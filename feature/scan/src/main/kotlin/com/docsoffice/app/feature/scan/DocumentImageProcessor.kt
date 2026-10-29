package com.docsoffice.app.feature.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

enum class EnhanceMode { COLOR, GRAYSCALE, DOCUMENT_BW }

data class QuadCorners(
    val topLeft: Pair<Float, Float>,
    val topRight: Pair<Float, Float>,
    val bottomRight: Pair<Float, Float>,
    val bottomLeft: Pair<Float, Float>,
) {
    companion object {
        /** Default inset crop (5% margins) in normalized 0–1 coordinates. */
        fun insetDefaults(): QuadCorners {
            val m = 0.05f
            return QuadCorners(
                topLeft = m to m,
                topRight = (1f - m) to m,
                bottomRight = (1f - m) to (1f - m),
                bottomLeft = m to (1f - m),
            )
        }
    }
}

object DocumentImageProcessor {

    fun applyEnhance(source: Bitmap, mode: EnhanceMode): Bitmap {
        if (mode == EnhanceMode.COLOR) return source.copy(Bitmap.Config.ARGB_8888, false)
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val matrix = ColorMatrix()
        when (mode) {
            EnhanceMode.GRAYSCALE -> matrix.setSaturation(0f)
            EnhanceMode.DOCUMENT_BW -> {
                matrix.setSaturation(0f)
                val contrast = 1.6f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                val scale = floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                )
                matrix.postConcat(ColorMatrix(scale))
            }
            EnhanceMode.COLOR -> Unit
        }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * Perspective-corrects [source] using normalized (0–1) corner points, producing a
     * rectangular bitmap. Uses [Matrix.setPolyToPoly].
     */
    fun perspectiveCorrect(source: Bitmap, corners: QuadCorners): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val src = floatArrayOf(
            corners.topLeft.first * w, corners.topLeft.second * h,
            corners.topRight.first * w, corners.topRight.second * h,
            corners.bottomRight.first * w, corners.bottomRight.second * h,
            corners.bottomLeft.first * w, corners.bottomLeft.second * h,
        )
        val outW = max(
            distance(src[0], src[1], src[2], src[3]),
            distance(src[6], src[7], src[4], src[5]),
        ).toInt().coerceAtLeast(1)
        val outH = max(
            distance(src[0], src[1], src[6], src[7]),
            distance(src[2], src[3], src[4], src[5]),
        ).toInt().coerceAtLeast(1)
        val dst = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat(),
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
            // Fallback: axis-aligned crop of bounding box.
            return axisAlignedCrop(source, corners)
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun process(source: Bitmap, corners: QuadCorners, mode: EnhanceMode): Bitmap {
        val warped = perspectiveCorrect(source, corners)
        val enhanced = applyEnhance(warped, mode)
        if (enhanced !== warped) warped.recycle()
        return enhanced
    }

    private fun axisAlignedCrop(source: Bitmap, corners: QuadCorners): Bitmap {
        val xs = listOf(
            corners.topLeft.first, corners.topRight.first,
            corners.bottomRight.first, corners.bottomLeft.first,
        )
        val ys = listOf(
            corners.topLeft.second, corners.topRight.second,
            corners.bottomRight.second, corners.bottomLeft.second,
        )
        val left = (min(xs.min(), xs.max()) * source.width).toInt().coerceIn(0, source.width - 1)
        val right = (max(xs.min(), xs.max()) * source.width).toInt().coerceIn(left + 1, source.width)
        val top = (min(ys.min(), ys.max()) * source.height).toInt().coerceIn(0, source.height - 1)
        val bottom = (max(ys.min(), ys.max()) * source.height).toInt().coerceIn(top + 1, source.height)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
