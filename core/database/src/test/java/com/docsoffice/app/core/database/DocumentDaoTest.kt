package com.docsoffice.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentDaoTest {
    private lateinit var db: DocsOfficeDatabase
    private lateinit var dao: DocumentDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DocsOfficeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.documentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun favoriteTrashRestoreAndPurge() = runTest {
        val doc = DocumentEntity(
            id = "1",
            uri = "file:///a.pdf",
            displayName = "a.pdf",
            extension = "pdf",
            mimeType = "application/pdf",
            sizeBytes = 10,
            modifiedAt = 1,
            addedAt = 1,
        )
        dao.upsertAll(listOf(doc))
        dao.setFavorite("1", true)
        assertEquals(1, dao.favorites().first().size)

        dao.markTrashed("1", 1000L, "file:///trash/a.pdf", "file:///a.pdf")
        assertEquals(0, dao.observeAll().first().size)
        assertEquals(1, dao.observeTrash().first().size)

        dao.markRestored("1", "file:///a.pdf")
        assertEquals(1, dao.observeAll().first().size)
        assertNull(dao.getById("1")?.trashedAt)

        dao.markTrashed("1", 1000L, "file:///trash/a.pdf", "file:///a.pdf")
        val expired = dao.trashedBefore(2000L)
        assertEquals(1, expired.size)
        dao.deleteByIds(listOf("1"))
        assertEquals(0, dao.observeTrash().first().size)
    }

    @Test
    fun searchByNameFindsSubstring() = runTest {
        dao.upsertAll(
            listOf(
                DocumentEntity(
                    id = "1",
                    uri = "file:///invoice.pdf",
                    displayName = "invoice.pdf",
                    extension = "pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 1,
                    modifiedAt = 1,
                    addedAt = 1,
                ),
                DocumentEntity(
                    id = "2",
                    uri = "file:///notes.txt",
                    displayName = "notes.txt",
                    extension = "txt",
                    mimeType = "text/plain",
                    sizeBytes = 1,
                    modifiedAt = 2,
                    addedAt = 2,
                ),
            ),
        )
        val results = dao.searchByName("inv").first()
        assertEquals(1, results.size)
        assertEquals("invoice.pdf", results.first().displayName)
    }
}
