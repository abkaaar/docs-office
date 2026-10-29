package com.docsoffice.app.ui

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntentDocumentParserTest {
    @Test
    fun extractsViewData() {
        val uri = Uri.parse("content://downloads/a.pdf")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        assertEquals(uri, IntentDocumentParser.extractUri(intent))
    }

    @Test
    fun returnsNullForLauncher() {
        val intent = Intent(Intent.ACTION_MAIN)
        assertNull(IntentDocumentParser.extractUri(intent))
    }
}
