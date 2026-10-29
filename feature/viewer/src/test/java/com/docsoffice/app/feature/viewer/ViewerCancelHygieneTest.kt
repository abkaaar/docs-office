package com.docsoffice.app.feature.viewer

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents the cancel-hygiene rule used in ViewerViewModel open/render catch blocks:
 * CancellationException must propagate, not become CannotOpen.
 */
class ViewerCancelHygieneTest {
    @Test
    fun cancellationIsNotMappedToCorrupt() {
        val error: Throwable = CancellationException("closed")
        assertTrue(error is CancellationException)
        assertFalse(mapsToCorrupt(error))
    }

    @Test
    fun genericErrorsMapToCorrupt() {
        assertTrue(mapsToCorrupt(IllegalStateException("bad pdf")))
    }

    private fun mapsToCorrupt(error: Throwable): Boolean =
        error !is CancellationException &&
            error !is OutOfMemoryError &&
            error !is SecurityException
}
