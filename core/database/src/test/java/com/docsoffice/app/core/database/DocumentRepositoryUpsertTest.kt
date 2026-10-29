package com.docsoffice.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.docsoffice.app.core.model.Document
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentRepositoryUpsertTest {
    private lateinit var db: DocsOfficeDatabase
    private lateinit var repository: DocumentRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DocsOfficeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DocumentRepository(
            documentDao = db.documentDao(),
            readingProgressDao = db.readingProgressDao(),
            searchHistoryDao = db.searchHistoryDao(),
            documentTextDao = db.documentTextDao(),
            tagDao = db.tagDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun scanUpsertPreservesRecentsAndFavorites() = runTest {
        repository.upsertAll(
            listOf(
                Document(
                    id = "1",
                    uri = "file:///a.pdf",
                    displayName = "a.pdf",
                    extension = "pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 10,
                    modifiedAt = 1,
                    addedAt = 1,
                ),
            ),
        )
        repository.setLastOpenedAt("1", 99L)
        repository.setFavorite("1", true)

        // Fresh scan row (defaults wipe lastOpened/favorite if REPLACE alone).
        repository.upsertAll(
            listOf(
                Document(
                    id = "1",
                    uri = "file:///a.pdf",
                    displayName = "a.pdf",
                    extension = "pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 12,
                    modifiedAt = 3,
                    addedAt = 999,
                ),
            ),
        )

        val recent = repository.recents(10).first()
        assertEquals(1, recent.size)
        assertEquals(99L, recent.first().lastOpenedAt)
        assertTrue(repository.favorites().first().any { it.id == "1" })
        assertEquals(1L, repository.getById("1")!!.addedAt)
        assertEquals(12L, repository.getById("1")!!.sizeBytes)
    }
}
