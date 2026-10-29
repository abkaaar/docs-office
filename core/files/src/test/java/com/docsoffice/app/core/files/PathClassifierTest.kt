package com.docsoffice.app.core.files

import com.docsoffice.app.core.model.DocSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathClassifierTest {
    @Test
    fun classifiesWhatsAppAndDownloads() {
        assertEquals(
            DocSource.WHATSAPP,
            PathClassifier.classifySource("/storage/emulated/0/WhatsApp/Media/WhatsApp Documents/a.pdf"),
        )
        assertEquals(
            DocSource.DOWNLOADS,
            PathClassifier.classifySource("/storage/emulated/0/Download/a.pdf"),
        )
        assertEquals(
            DocSource.TELEGRAM,
            PathClassifier.classifySource("/storage/emulated/0/Telegram/Telegram Documents/a.pdf"),
        )
    }

    @Test
    fun excludesHiddenAndCacheDirs() {
        assertTrue(PathClassifier.isExcluded("/sdcard/.hidden/file.pdf", emptySet()))
        assertTrue(PathClassifier.isExcluded("/sdcard/cache/file.pdf", emptySet()))
        assertFalse(PathClassifier.isExcluded("/sdcard/Download/file.pdf", emptySet()))
        assertTrue(
            PathClassifier.isExcluded(
                "/sdcard/Download/skip/file.pdf",
                setOf("/sdcard/Download/skip"),
            ),
        )
    }

    @Test
    fun stableIdIsDeterministic() {
        val a = PathClassifier.stableId("file:///a.pdf", 10, 100)
        val b = PathClassifier.stableId("file:///a.pdf", 10, 100)
        val c = PathClassifier.stableId("file:///a.pdf", 11, 100)
        assertEquals(a, b)
        assertTrue(a != c)
    }
}
