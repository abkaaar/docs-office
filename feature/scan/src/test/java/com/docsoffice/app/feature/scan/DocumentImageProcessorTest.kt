package com.docsoffice.app.feature.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentImageProcessorTest {
    @Test
    fun insetDefaultsAreWithinUnitSquare() {
        val corners = QuadCorners.insetDefaults()
        listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft).forEach { (x, y) ->
            assertTrue(x in 0f..1f)
            assertTrue(y in 0f..1f)
        }
        assertEquals(0.05f, corners.topLeft.first)
        assertEquals(0.95f, corners.topRight.first)
    }

    @Test
    fun edgeDetectResultDefaultsMarkManualFallback() {
        val fallback = EdgeDetectResult(QuadCorners.insetDefaults(), 0f, autoDetected = false)
        assertFalse(fallback.autoDetected)
        assertEquals(3, EnhanceMode.entries.size)
    }
}
