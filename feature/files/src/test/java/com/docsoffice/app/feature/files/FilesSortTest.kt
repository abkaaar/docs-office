package com.docsoffice.app.feature.files

import com.docsoffice.app.core.datastore.SortMode
import com.docsoffice.app.core.model.DocCategory
import com.docsoffice.app.core.model.DocSource
import com.docsoffice.app.core.model.Document
import org.junit.Assert.assertEquals
import org.junit.Test

class FilesSortTest {
    @Test
    fun sortsByNameSizeDate() {
        val docs = listOf(
            doc("b.pdf", size = 20, modified = 1),
            doc("a.pdf", size = 10, modified = 3),
            doc("c.pdf", size = 30, modified = 2),
        )
        val byName = docs.sortedWith(compareBy { it.displayName.lowercase() })
        assertEquals("a.pdf", byName.first().displayName)
        val bySize = docs.sortedWith(compareByDescending { it.sizeBytes })
        assertEquals("c.pdf", bySize.first().displayName)
        val byDate = docs.sortedWith(compareByDescending { it.modifiedAt })
        assertEquals("a.pdf", byDate.first().displayName)
        assertEquals(SortMode.DATE.name, SortMode.DATE.name)
    }

    private fun doc(name: String, size: Long, modified: Long) = Document(
        id = name,
        uri = "file:///$name",
        displayName = name,
        extension = "pdf",
        mimeType = "application/pdf",
        sizeBytes = size,
        modifiedAt = modified,
        addedAt = modified,
        source = DocSource.DOWNLOADS,
        category = DocCategory.PDF,
    )
}
