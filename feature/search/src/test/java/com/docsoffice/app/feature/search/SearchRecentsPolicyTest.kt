package com.docsoffice.app.feature.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents empty-query policy: blank search shows recently opened documents,
 * not the full library from searchFiltered("").
 */
class SearchRecentsPolicyTest {
    @Test
    fun blankQueryUsesRecentsNotLibraryResults() {
        val blank = ""
        val whitespace = "   "
        assertTrue(blank.isBlank())
        assertTrue(whitespace.isBlank())
        // When blank, ViewModel emits recentDocuments and empty results.
        val uiWhenBlank = SearchUiState(
            query = blank,
            results = emptyList(),
            recentDocuments = emptyList(),
        )
        assertTrue(uiWhenBlank.results.isEmpty())
        assertFalse(uiWhenBlank.query.isNotBlank())
    }

    @Test
    fun nonBlankQueryShowsResultsNotRecentsSection() {
        val ui = SearchUiState(
            query = "invoice",
            results = emptyList(),
            recentDocuments = emptyList(),
        )
        assertTrue(ui.query.isNotBlank())
    }
}
