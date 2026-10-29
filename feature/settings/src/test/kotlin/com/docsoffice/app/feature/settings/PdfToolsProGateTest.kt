package com.docsoffice.app.feature.settings

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PDF tools no longer require Pro entitlement.
 */
class PdfToolsProGateTest {
    @Test
    fun toolsRunWithoutEntitlement() {
        assertTrue(PdfToolEntitlement.canExecute(isPro = false))
        assertTrue(PdfToolEntitlement.canExecute(isPro = true))
    }
}
