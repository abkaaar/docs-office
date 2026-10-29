package com.docsoffice.app.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedFormatsMimeTest {
    @Test
    fun extensionFromMime_mapsCommonTypes() {
        assertEquals("pdf", SupportedFormats.extensionFromMime("application/pdf"))
        assertEquals(
            "docx",
            SupportedFormats.extensionFromMime(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ),
        )
        assertEquals("jpg", SupportedFormats.extensionFromMime("image/jpeg"))
        assertEquals("txt", SupportedFormats.extensionFromMime("text/plain; charset=utf-8"))
        assertEquals(
            "pptm",
            SupportedFormats.extensionFromMime(
                "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
            ),
        )
    }

    @Test
    fun extensionFromMime_unknownIsBlank() {
        assertTrue(SupportedFormats.extensionFromMime("application/octet-stream").isEmpty())
        assertTrue(SupportedFormats.extensionFromMime(null).isEmpty())
    }
}
