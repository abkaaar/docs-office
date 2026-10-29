package com.docsoffice.app.core.pdftools

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM tests for PdfBox structural ops. Blank pages avoid PDType1Font / PDFTextStripper
 * which need PDFBoxResourceLoader (Android assets) unavailable in plain unit tests.
 * Page MediaBox sizes act as content identity for merge/split/reorder fidelity.
 */
class PdfToolsOperationsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val tools = PdfTools(
        engine = PdfBoxAndroidEngine(),
        pdfBoxInit = PdfBoxInit { },
    )

    @Test
    fun parsePageRange_oneBasedRanges() {
        assertEquals(listOf(0, 1, 2, 4), tools.parsePageRange("1-3,5", 5))
    }

    @Test
    fun parsePageRange_rejectsOutOfBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            tools.parsePageRange("1-9", 3)
        }
    }

    @Test
    fun merge_preservesPageCount() {
        val a = blankPdf("a.pdf", 1)
        val b = blankPdf("b.pdf", 2)
        val out = File(tmp.root, "merged.pdf")
        tools.merge(listOf(a, b), out)
        assertEquals(3, tools.pageCount(out))
    }

    @Test
    fun merge_preservesPageDimensionOrder() {
        val letter = sizedPdf("m-letter.pdf", PDRectangle.LETTER)
        val a4 = sizedPdf("m-a4.pdf", PDRectangle.A4)
        val out = File(tmp.root, "merged-sized.pdf")
        tools.merge(listOf(letter, a4), out)
        assertMediaBoxes(out, listOf(PDRectangle.LETTER, PDRectangle.A4))
    }

    @Test
    fun split_extractsSelectedPages() {
        val input = blankPdf("in.pdf", 3)
        val out = File(tmp.root, "split.pdf")
        tools.split(input, listOf(0, 2), out)
        assertEquals(2, tools.pageCount(out))
    }

    @Test
    fun split_preservesSelectedPageDimensions() {
        val letter = sizedPdf("s-letter.pdf", PDRectangle.LETTER)
        val a4 = sizedPdf("s-a4.pdf", PDRectangle.A4)
        val legal = sizedPdf("s-legal.pdf", PDRectangle.LEGAL)
        val combined = File(tmp.root, "split-sized-src.pdf")
        tools.merge(listOf(letter, a4, legal), combined)
        val out = File(tmp.root, "split-sized.pdf")
        tools.split(combined, listOf(0, 2), out)
        assertMediaBoxes(out, listOf(PDRectangle.LETTER, PDRectangle.LEGAL))
    }

    @Test
    fun deletePages_removesSelection() {
        val input = blankPdf("in.pdf", 3)
        val out = File(tmp.root, "del.pdf")
        tools.deletePages(input, listOf(1), out)
        assertEquals(2, tools.pageCount(out))
    }

    @Test
    fun deletePages_preservesRemainingPageDimensions() {
        val letter = sizedPdf("d-letter.pdf", PDRectangle.LETTER)
        val a4 = sizedPdf("d-a4.pdf", PDRectangle.A4)
        val legal = sizedPdf("d-legal.pdf", PDRectangle.LEGAL)
        val combined = File(tmp.root, "del-sized-src.pdf")
        tools.merge(listOf(letter, a4, legal), combined)
        val out = File(tmp.root, "del-sized.pdf")
        tools.deletePages(combined, listOf(1), out)
        assertMediaBoxes(out, listOf(PDRectangle.LETTER, PDRectangle.LEGAL))
    }

    @Test
    fun reorder_preservesPageDimensionsOrder() {
        val letter = sizedPdf("letter.pdf", PDRectangle.LETTER)
        val a4 = sizedPdf("a4.pdf", PDRectangle.A4)
        val combined = File(tmp.root, "reorder-src.pdf")
        tools.merge(listOf(letter, a4), combined)
        val out = File(tmp.root, "reordered.pdf")
        tools.reorder(combined, listOf(1, 0), out)
        assertMediaBoxes(out, listOf(PDRectangle.A4, PDRectangle.LETTER))
    }

    @Test
    fun rotate_setsPageRotationMetadata() {
        val input = blankPdf("rot.pdf", 1)
        val out = File(tmp.root, "rotated.pdf")
        tools.rotate(input, 90f, out)
        assertEquals(1, tools.pageCount(out))
        PDDocument.load(out).use { doc ->
            assertEquals(90, doc.getPage(0).rotation)
        }
    }

    private fun blankPdf(name: String, pages: Int): File {
        val file = File(tmp.root, name)
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage(PDRectangle.LETTER)) }
            doc.save(file)
        }
        return file
    }

    private fun sizedPdf(name: String, box: PDRectangle): File {
        val file = File(tmp.root, name)
        PDDocument().use { doc ->
            doc.addPage(PDPage(box))
            doc.save(file)
        }
        return file
    }

    private fun assertMediaBoxes(pdf: File, expected: List<PDRectangle>) {
        PDDocument.load(pdf).use { doc ->
            assertEquals(expected.size, doc.numberOfPages)
            expected.forEachIndexed { index, box ->
                val page = doc.getPage(index).mediaBox
                assertEquals("width page $index", box.width, page.width, 0.01f)
                assertEquals("height page $index", box.height, page.height, 0.01f)
            }
        }
    }
}
