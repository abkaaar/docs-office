package com.docsoffice.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Debug
import android.util.LruCache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.docsoffice.app.renderer.api.RenderSession
import com.docsoffice.app.renderer.api.UriDocumentSource
import com.docsoffice.app.renderer.pdf.PdfDocumentRenderer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device PDF memory stress against [PdfDocumentRenderer] + an LRU budget matching
 * ViewerViewModel (~24 MiB). Records RSS-related Debug counters to logcat.
 *
 * Device: whatever is attached when `connectedDebugAndroidTest` runs.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PdfMemoryStressTest {

    private lateinit var fixtureDir: File

    @Before
    fun setup() {
        fixtureDir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "pdf_stress",
        ).also {
            it.mkdirs()
            it.listFiles()?.forEach { f -> f.delete() }
        }
    }

    @Test
    fun longTextPdf_rapidPageWalk_staysWithinCacheBudget() {
        runBlocking {
        val file = createTextHeavyPdf(pageCount = 40, name = "text_heavy.pdf")
        val beforeNative = Debug.getNativeHeapAllocatedSize()
        val beforeJava = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val t0 = System.nanoTime()

        val renderer = PdfDocumentRenderer()
        val session = renderer.open(UriDocumentSource(file.toURI().toString()), null) as RenderSession.Paged
        val pageCount = session.pageCount
        val firstPageMs: Long
        val cache = memoryBoundedCache()
        try {
            val rendering = ConcurrentHashMap.newKeySet<Int>()
            val width = 1080

            val tFirst = System.nanoTime()
            val first = session.renderPage(0, width) as Bitmap
            firstPageMs = (System.nanoTime() - tFirst) / 1_000_000
            cache.put(0, first)

            // Rapid forward, back, distant jumps (mirrors continuous-scroll stress).
            val sequence = buildList {
                addAll(0 until pageCount)
                addAll((pageCount - 1) downTo 0)
                add(0)
                add(pageCount / 2)
                add(pageCount - 1)
            }
            for (index in sequence) {
                if (!rendering.add(index)) continue
                try {
                    val existing = cache.get(index)
                    if (existing != null) continue
                    val bmp = session.renderPage(index, width) as Bitmap
                    cache.put(index, bmp)
                } finally {
                    rendering.remove(index)
                }
            }

            session.close()
            val cacheKbDuring = cache.size()
            cache.evictAll()

            val afterNative = Debug.getNativeHeapAllocatedSize()
            val afterJava = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000

            val report = buildString {
                appendLine("PdfMemoryStress text_heavy pages=$pageCount sizeBytes=${file.length()}")
                appendLine("firstPageMs=$firstPageMs totalMs=$elapsedMs")
                appendLine("javaHeapBefore=$beforeJava javaHeapAfter=$afterJava")
                appendLine("nativeHeapBefore=$beforeNative nativeHeapAfter=$afterNative")
                appendLine("cacheMaxKb=${cache.maxSize()} cachePeakKb=$cacheKbDuring")
            }
            android.util.Log.i("PdfMemoryStress", report)

            assertTrue("first page should appear under 5s", firstPageMs < 5_000)
            assertTrue("cache stayed within budget", cacheKbDuring <= cache.maxSize())
        } finally {
            runCatching { session.close() }
            cache.evictAll()
        }
        }
    }

    @Test
    fun imageHeavyPdf_openClose_releasesWithoutCrash() {
        runBlocking {
        val file = createImageHeavyPdf(pageCount = 12, name = "image_heavy.pdf")
        val renderer = PdfDocumentRenderer()
        val session = renderer.open(UriDocumentSource(file.toURI().toString()), null) as RenderSession.Paged
        val pageCount = session.pageCount
        val cache = memoryBoundedCache()
        try {
            for (i in 0 until pageCount) {
                val bmp = session.renderPage(i, 720) as Bitmap
                cache.put(i, bmp)
            }
            // Switch "document": close and reopen
            session.close()
            cache.evictAll()
            val session2 = renderer.open(UriDocumentSource(file.toURI().toString()), null) as RenderSession.Paged
            val bmp = session2.renderPage(0, 720) as Bitmap
            assertTrue(bmp.width > 0)
            bmp.recycle()
            session2.close()
            android.util.Log.i(
                "PdfMemoryStress",
                "image_heavy pages=$pageCount sizeBytes=${file.length()} reopen=ok",
            )
            Unit
        } finally {
            runCatching { session.close() }
            cache.evictAll()
        }
        }
    }

    private fun memoryBoundedCache(): LruCache<Int, Bitmap> {
        val maxKb = 24 * 1024
        return object : LruCache<Int, Bitmap>(maxKb) {
            override fun sizeOf(key: Int, value: Bitmap): Int =
                (value.byteCount / 1024).coerceAtLeast(1)

            override fun entryRemoved(
                evicted: Boolean,
                key: Int,
                oldValue: Bitmap,
                newValue: Bitmap?,
            ) {
                if (evicted && oldValue !== newValue && !oldValue.isRecycled) {
                    oldValue.recycle()
                }
            }
        }
    }

    private fun createTextHeavyPdf(pageCount: Int, name: String): File {
        val file = File(fixtureDir, name)
        val doc = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
        }
        repeat(pageCount) { pageIndex ->
            val pageInfo = PdfDocument.PageInfo.Builder(612, 792, pageIndex + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            repeat(40) { line ->
                canvas.drawText(
                    "Page ${pageIndex + 1} line $line — Docs Office stress fixture",
                    40f,
                    40f + line * 16f,
                    paint,
                )
            }
            doc.finishPage(page)
        }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun createImageHeavyPdf(pageCount: Int, name: String): File {
        val file = File(fixtureDir, name)
        val doc = PdfDocument()
        val paint = Paint().apply { style = Paint.Style.FILL }
        repeat(pageCount) { pageIndex ->
            val pageInfo = PdfDocument.PageInfo.Builder(800, 1000, pageIndex + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            // Dense colored bands ≈ image-heavy decode cost for PdfRenderer.
            for (y in 0 until 1000 step 4) {
                paint.color = Color.rgb((y + pageIndex * 17) % 255, 80, (pageIndex * 40) % 255)
                canvas.drawRect(0f, y.toFloat(), 800f, (y + 4).toFloat(), paint)
            }
            doc.finishPage(page)
        }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
