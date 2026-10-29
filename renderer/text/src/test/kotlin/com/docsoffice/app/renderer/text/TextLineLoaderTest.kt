package com.docsoffice.app.renderer.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TextLineLoaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun loadsLinesAndTruncatesGiantLine() {
        val file = temp.newFile("a.txt")
        val giant = "x".repeat(10_000)
        file.writeText("one\n$giant\nthree")
        val lines = TextLineLoader.loadLines(file)
        assertEquals(3, lines.size)
        assertEquals("one", lines[0])
        assertTrue(lines[1].endsWith("…"))
        assertTrue(lines[1].length <= 8_001)
        assertEquals("three", lines[2])
    }

    @Test
    fun splitCsvLine_handlesQuotes() {
        val cells = TextLineLoader.splitCsvLine("""a,"b,c",d""")
        assertEquals(listOf("a", "b,c", "d"), cells)
    }

    @Test
    fun parseCsvRows_readsTable() {
        val file = temp.newFile("t.csv")
        file.writeText("name,qty\napple,3\n\"b,anana\",2\n")
        val rows = TextLineLoader.parseCsvRows(file)
        assertEquals(3, rows.size)
        assertEquals(listOf("name", "qty"), rows[0])
        assertEquals(listOf("b,anana", "2"), rows[2])
    }

    @Test
    fun loadLinesChunk_streamsWithoutRereadingPrefix() {
        val file = temp.newFile("big.txt")
        val body = (1..50).joinToString("\n") { "line-$it" }
        file.writeText(body)
        val first = TextLineLoader.loadLinesChunk(file, 0L, maxLines = 10, maxBytes = 10_000)
        assertEquals(10, first.lines.size)
        assertEquals("line-1", first.lines.first())
        assertEquals("line-10", first.lines.last())
        assertTrue(!first.exhausted)
        val second = TextLineLoader.loadLinesChunk(
            file,
            first.nextByteOffset,
            maxLines = 10,
            maxBytes = 10_000,
        )
        assertEquals("line-11", second.lines.first())
        assertTrue(second.lines.none { it == "line-1" })
    }

    @Test
    fun loadLinesChunk_handlesMultibyteAcrossBoundary() {
        val file = temp.newFile("utf8.txt")
        // "café" in UTF-8 — é is C3 A9
        val prefix = "aaa\n"
        val mid = "café-line\n"
        val suffix = "zzz\n"
        file.writeBytes((prefix + mid + suffix).toByteArray(Charsets.UTF_8))
        val first = TextLineLoader.loadLinesChunk(
            file,
            0L,
            maxLines = 1,
            maxBytes = prefix.toByteArray(Charsets.UTF_8).size.toLong(),
        )
        assertEquals(listOf("aaa"), first.lines)
        val second = TextLineLoader.loadLinesChunk(file, first.nextByteOffset, maxLines = 5)
        assertTrue(second.lines.any { it.contains("café") })
        assertTrue(second.lines.any { it == "zzz" })
    }
}
