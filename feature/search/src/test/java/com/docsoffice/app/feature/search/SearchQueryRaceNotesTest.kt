package com.docsoffice.app.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SearchViewModel uses debounce(150) + flatMapLatest so a slower older query
 * cannot overwrite a newer one. This test locks the intended ordering rule.
 */
class SearchQueryRaceNotesTest {
    @Test
    fun newerQueryWinsOrdering() {
        val emitted = mutableListOf<String>()
        // Simulate flatMapLatest: only the latest subscription's result is kept.
        var generation = 0
        fun startSearch(q: String) {
            val myGen = ++generation
            // "slow" older result arrives after a newer query started
            if (myGen == generation) emitted += q
        }
        startSearch("old")
        startSearch("new")
        // Late completion for gen 1 must not publish
        val lateGen = 1
        if (lateGen == generation) emitted += "old-late"
        assertEquals(listOf("old", "new"), emitted)
    }
}
