package com.docsoffice.app.core.pdftools

import org.junit.Assert.assertTrue
import org.junit.Test

class CompressQualityTest {
    @Test
    fun qualityLevelsExist() {
        assertTrue(CompressQuality.entries.size == 3)
        assertTrue(CompressQuality.LOW.name == "LOW")
    }
}
