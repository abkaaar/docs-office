package com.docsoffice.app.renderer.ooxml

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OoxmlZipSafetyTest {
    @Test
    fun isEntrySafe_rejectsHugeDeclaredSizeAndBadRatio() {
        val huge = ZipEntry("huge.xml")
        huge.size = OoxmlZip.MAX_ENTRY_BYTES + 1
        huge.compressedSize = 100
        assertFalse(OoxmlZip.isEntrySafe(huge))

        val bomb = ZipEntry("bomb.xml")
        bomb.size = 10_000_000
        bomb.compressedSize = 10
        assertFalse(OoxmlZip.isEntrySafe(bomb))

        val ok = ZipEntry("ok.xml")
        ok.size = 100
        ok.compressedSize = 40
        assertTrue(OoxmlZip.isEntrySafe(ok))
    }

    @Test
    fun readEntryText_smallEntryWorks() {
        val file = File.createTempFile("small", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("hello.txt"))
                zip.write("hi".toByteArray())
                zip.closeEntry()
            }
            OoxmlZip.resetBudget()
            ZipFile(file).use { z ->
                assertNull(OoxmlZip.readEntryText(z, "missing.txt"))
                assertTrue(OoxmlZip.readEntryText(z, "hello.txt")!!.contains("hi"))
            }
        } finally {
            file.delete()
        }
    }
}
