package com.docsoffice.app.core.pdftools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class CompressQuality { LOW, MEDIUM, HIGH }

data class PdfMetadata(
    val pageCount: Int,
    val fileName: String,
    val sizeBytes: Long,
)

/**
 * Facade for PDF tools. Structural ops use [PdfDocumentEngine] (PdfBox) to preserve content;
 * images→PDF, compress (raster), and PDF→images use platform APIs.
 */
@Singleton
class PdfTools @Inject constructor(
    private val engine: PdfDocumentEngine,
    private val pdfBoxInit: PdfBoxInit,
) {

    private fun ensurePdfBox() = pdfBoxInit.ensureInitialized()

    fun pageCount(input: File): Int {
        ensurePdfBox()
        return engine.pageCount(input)
    }

    fun metadata(input: File): PdfMetadata {
        ensurePdfBox()
        return engine.metadata(input)
    }

    fun imagesToPdf(images: List<File>, output: File): File {
        require(images.isNotEmpty()) { "Select at least one image" }
        output.parentFile?.mkdirs()
        val doc = PdfDocument()
        try {
            images.forEachIndexed { index, imageFile ->
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    ?: error("Could not decode ${imageFile.name}")
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = doc.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    doc.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            FileOutputStream(output).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return output
    }

    fun merge(inputs: List<File>, output: File): File {
        ensurePdfBox()
        return engine.merge(inputs, output)
    }

    fun split(input: File, pageIndices: List<Int>, output: File): File {
        ensurePdfBox()
        return engine.split(input, pageIndices, output)
    }

    fun deletePages(input: File, deleteIndices: List<Int>, output: File): File {
        ensurePdfBox()
        return engine.deletePages(input, deleteIndices, output)
    }

    fun rotate(input: File, degrees: Float, output: File): File {
        ensurePdfBox()
        return engine.rotate(input, degrees.toInt(), output)
    }

    /**
     * Raster recompress for size reduction. Flattens pages to images — not vector-preserving.
     * Returns bytes saved (may be negative if output grew).
     */
    fun compress(input: File, quality: CompressQuality, output: File): Pair<File, Long> {
        val scale = when (quality) {
            CompressQuality.LOW -> 0.5f
            CompressQuality.MEDIUM -> 0.75f
            CompressQuality.HIGH -> 0.9f
        }
        val originalSize = input.length()
        output.parentFile?.mkdirs()
        val doc = PdfDocument()
        var pageNumber = 1
        try {
            openRenderer(input) { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
                        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                        val pageInfo = PdfDocument.PageInfo.Builder(w, h, pageNumber++).create()
                        val pdfPage = doc.startPage(pageInfo)
                        pdfPage.canvas.drawBitmap(scaled, 0f, 0f, null)
                        doc.finishPage(pdfPage)
                        if (scaled !== bitmap) scaled.recycle()
                        bitmap.recycle()
                    }
                }
            }
            FileOutputStream(output).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return output to (originalSize - output.length())
    }

    fun reorder(input: File, newOrder: List<Int>, output: File): File {
        ensurePdfBox()
        return engine.reorder(input, newOrder, output)
    }

    fun pdfPagesToImages(
        input: File,
        outputDir: File,
        pageIndices: List<Int>? = null,
        prefix: String = "page",
    ): List<File> {
        outputDir.mkdirs()
        val results = mutableListOf<File>()
        openRenderer(input) { renderer ->
            val indices = pageIndices ?: (0 until renderer.pageCount).toList()
            require(indices.isNotEmpty()) { "Select at least one page" }
            indices.forEach { pageIndex ->
                require(pageIndex in 0 until renderer.pageCount) {
                    "Page ${pageIndex + 1} is out of range (1–${renderer.pageCount})"
                }
                renderer.openPage(pageIndex).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = File(outputDir, "${prefix}_${pageIndex + 1}.jpg")
                    FileOutputStream(out).use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    }
                    bitmap.recycle()
                    results += out
                }
            }
        }
        return results
    }

    fun extractTextByPage(input: File): List<String> {
        ensurePdfBox()
        return engine.extractTextByPage(input)
    }

    /** Parses 1-based ranges like "1-3,5" into 0-based indices. */
    fun parsePageRange(spec: String, pageCount: Int): List<Int> {
        val trimmed = spec.trim()
        require(trimmed.isNotEmpty()) { "Enter a page range (e.g. 1-3,5)" }
        val indices = linkedSetOf<Int>()
        trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            if ('-' in part) {
                val ends = part.split('-', limit = 2)
                require(ends.size == 2) { "Invalid range: $part" }
                val start = ends[0].trim().toIntOrNull() ?: error("Invalid page: ${ends[0]}")
                val end = ends[1].trim().toIntOrNull() ?: error("Invalid page: ${ends[1]}")
                require(start in 1..pageCount && end in 1..pageCount && start <= end) {
                    "Range $part is outside 1–$pageCount"
                }
                for (p in start..end) indices += (p - 1)
            } else {
                val p = part.toIntOrNull() ?: error("Invalid page: $part")
                require(p in 1..pageCount) { "Page $p is outside 1–$pageCount" }
                indices += (p - 1)
            }
        }
        require(indices.isNotEmpty()) { "Select at least one page" }
        return indices.toList()
    }

    private fun <T> openRenderer(input: File, block: (PdfRenderer) -> T): T {
        try {
            return ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use(block)
            }
        } catch (se: SecurityException) {
            throw IllegalStateException("This PDF is password-protected and cannot be processed.", se)
        } catch (iae: IllegalArgumentException) {
            val msg = iae.message.orEmpty()
            if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                throw IllegalStateException("This PDF is password-protected and cannot be processed.", iae)
            }
            throw iae
        }
    }
}
