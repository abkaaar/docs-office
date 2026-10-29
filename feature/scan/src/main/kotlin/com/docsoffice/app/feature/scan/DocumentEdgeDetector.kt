package com.docsoffice.app.feature.scan

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class EdgeDetectResult(
    val corners: QuadCorners,
    val confidence: Float,
    val autoDetected: Boolean,
)

/**
 * On-device document boundary detection via OpenCV (Canny + largest convex quad).
 * Falls back to inset defaults when OpenCV is unavailable or confidence is low.
 */
object DocumentEdgeDetector {
    private const val TAG = "DocumentEdgeDetector"

    @Volatile
    private var opencvReady: Boolean? = null

    fun ensureOpenCv(): Boolean {
        opencvReady?.let { return it }
        return synchronized(this) {
            opencvReady?.let { return it }
            val ok = runCatching { OpenCVLoader.initLocal() }
                .recoverCatching { OpenCVLoader.initDebug() }
                .getOrDefault(false)
            opencvReady = ok
            if (!ok) Log.w(TAG, "OpenCV unavailable; using manual crop defaults")
            ok
        }
    }

    fun detect(source: Bitmap): EdgeDetectResult {
        if (!ensureOpenCv()) {
            return EdgeDetectResult(QuadCorners.insetDefaults(), 0f, autoDetected = false)
        }
        return runCatching { detectOpenCv(source) }
            .onFailure { Log.w(TAG, "Document edge detection failed", it) }
            .getOrElse {
                EdgeDetectResult(QuadCorners.insetDefaults(), 0f, autoDetected = false)
            }
    }

    private fun detectOpenCv(source: Bitmap): EdgeDetectResult {
        val maxSide = 960
        val scale = min(1f, maxSide.toFloat() / max(source.width, source.height))
        val w = (source.width * scale).toInt().coerceAtLeast(32)
        val h = (source.height * scale).toInt().coerceAtLeast(32)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, w, h, true)
        } else {
            source
        }
        val rgba = Mat()
        Utils.bitmapToMat(scaled, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        Imgproc.dilate(edges, edges, Mat())

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        val imgArea = (w * h).toDouble()
        var best: Array<Point>? = null
        var bestArea = 0.0
        for (contour in contours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)
            if (approx.total() != 4L) continue
            if (!Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) continue
            val area = Imgproc.contourArea(approx)
            if (area < imgArea * 0.18 || area > imgArea * 0.98) continue
            if (area > bestArea) {
                bestArea = area
                best = approx.toArray()
            }
        }

        rgba.release()
        gray.release()
        edges.release()
        if (scaled !== source) scaled.recycle()

        val pts = best ?: return EdgeDetectResult(QuadCorners.insetDefaults(), 0f, false)
        val ordered = orderCorners(pts)
        val corners = QuadCorners(
            topLeft = (ordered[0].x.toFloat() / w) to (ordered[0].y.toFloat() / h),
            topRight = (ordered[1].x.toFloat() / w) to (ordered[1].y.toFloat() / h),
            bottomRight = (ordered[2].x.toFloat() / w) to (ordered[2].y.toFloat() / h),
            bottomLeft = (ordered[3].x.toFloat() / w) to (ordered[3].y.toFloat() / h),
        )
        val confidence = (bestArea / imgArea).toFloat().coerceIn(0f, 1f)
        val valid = confidence >= 0.2f &&
            corners.topLeft.first < corners.topRight.first &&
            corners.bottomLeft.first < corners.bottomRight.first &&
            corners.topLeft.second < corners.bottomLeft.second
        return if (valid) {
            EdgeDetectResult(corners, confidence, autoDetected = true)
        } else {
            EdgeDetectResult(QuadCorners.insetDefaults(), confidence, autoDetected = false)
        }
    }

    private fun orderCorners(pts: Array<Point>): List<Point> {
        val sorted = pts.sortedBy { it.x + it.y }
        val tl = sorted.first()
        val br = sorted.last()
        val remaining = pts.filter { it !== tl && it !== br }
        val tr = remaining.maxBy { it.x - it.y }
        val bl = remaining.minBy { it.x - it.y }
        return listOf(tl, tr, br, bl)
    }
}
