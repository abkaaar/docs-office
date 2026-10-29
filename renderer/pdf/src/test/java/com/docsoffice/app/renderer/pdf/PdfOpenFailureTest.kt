package com.docsoffice.app.renderer.pdf

import com.docsoffice.app.renderer.api.OpenFailure
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfOpenFailureTest {
    @Test
    fun mapsSecurityExceptionToPasswordRequired() {
        val mapped = PdfDocumentRenderer.mapOpenError(SecurityException("encrypted"))
        assertTrue(mapped is OpenFailure.PasswordRequired)
    }

    @Test
    fun mapsGenericToCorrupt() {
        val mapped = PdfDocumentRenderer.mapOpenError(IllegalStateException("bad"))
        assertTrue(mapped is OpenFailure.Corrupt)
    }
}
