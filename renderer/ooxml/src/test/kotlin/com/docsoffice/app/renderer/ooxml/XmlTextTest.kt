package com.docsoffice.app.renderer.ooxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlTextTest {
    @Test
    fun stripsTagsAndEntities() {
        val xml = "<w:p><w:r><w:t>Hello &amp; world</w:t></w:r></w:p>"
        val text = XmlText.stripTags(xml)
        assertTrue(text.contains("Hello & world"))
    }

    @Test
    fun extractsBetween() {
        val xml = "<a>one</a><a>two</a>"
        assertEquals(listOf("one", "two"), XmlText.extractBetween(xml, "<a>", "</a>"))
    }
}
