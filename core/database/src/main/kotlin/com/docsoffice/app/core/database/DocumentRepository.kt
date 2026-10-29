package com.docsoffice.app.core.database

import com.docsoffice.app.core.model.CategoryCount
import com.docsoffice.app.core.model.DocCategory
import com.docsoffice.app.core.model.DocSource
import com.docsoffice.app.core.model.Document
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val readingProgressDao: ReadingProgressDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val documentTextDao: DocumentTextDao,
    private val tagDao: TagDao,
) {
    fun observeAll(): Flow<List<Document>> =
        documentDao.observeAll().map { list -> list.map { it.toModel() } }

    fun recents(limit: Int = 10): Flow<List<Document>> =
        documentDao.recents(limit).map { list -> list.map { it.toModel() } }

    fun favorites(): Flow<List<Document>> =
        documentDao.favorites().map { list -> list.map { it.toModel() } }

    fun observeTrash(): Flow<List<Document>> =
        documentDao.observeTrash().map { list -> list.map { it.toModel() } }

    fun observeCount(): Flow<Int> = documentDao.observeCount()

    fun categoryCounts(): Flow<List<CategoryCount>> {
        val pdf = documentDao.countByExtensions(listOf("pdf"))
        val word = documentDao.countByExtensions(listOf("doc", "docx"))
        val excel = documentDao.countByExtensions(listOf("xls", "xlsx", "xlsm"))
        val slides = documentDao.countByExtensions(listOf("ppt", "pptx", "pptm"))
        val text = documentDao.countByExtensions(listOf("txt", "md", "csv", "json", "log", "xml"))
        val image = documentDao.countByExtensions(listOf("jpg", "jpeg", "png", "webp", "gif"))
        return combine(pdf, word, excel, slides, text, image) { values: Array<Int> ->
            listOf(
                CategoryCount(DocCategory.PDF, values[0]),
                CategoryCount(DocCategory.WORD, values[1]),
                CategoryCount(DocCategory.EXCEL, values[2]),
                CategoryCount(DocCategory.SLIDES, values[3]),
                CategoryCount(DocCategory.TEXT, values[4]),
                CategoryCount(DocCategory.IMAGE, values[5]),
            )
        }
    }

    fun searchByName(query: String): Flow<List<Document>> =
        documentDao.searchByName(query).map { list -> list.map { it.toModel() } }

    fun searchFiltered(
        query: String,
        source: DocSource? = null,
        extension: String? = null,
        minModified: Long? = null,
        maxModified: Long? = null,
        sort: String = "date",
    ): Flow<List<Document>> =
        documentDao.searchFiltered(
            query = query,
            source = source?.name,
            extension = extension,
            minModified = minModified,
            maxModified = maxModified,
            sort = sort,
        ).map { list -> list.map { it.toModel() } }

    suspend fun getById(id: String): Document? = documentDao.getById(id)?.toModel()

    suspend fun getEntityById(id: String): DocumentEntity? = documentDao.getById(id)

    suspend fun getByUri(uri: String): Document? = documentDao.getByUri(uri)?.toModel()

    /**
     * Insert or update scan/library rows without wiping user state
     * (recents, favorites, trash, pageCount, original addedAt).
     */
    suspend fun upsertAll(documents: List<Document>) {
        if (documents.isEmpty()) return
        documentDao.upsertAll(documents.map { mergeWithExisting(it) })
    }

    suspend fun upsertEntity(entity: DocumentEntity) {
        val existing = documentDao.getById(entity.id)
        documentDao.upsertAll(listOf(mergeEntities(incoming = entity, existing = existing)))
    }

    private suspend fun mergeWithExisting(incoming: Document): DocumentEntity {
        val existing = documentDao.getById(incoming.id)
        return mergeEntities(
            incoming = incoming.toEntity(originalUri = existing?.originalUri),
            existing = existing,
        )
    }

    private fun mergeEntities(
        incoming: DocumentEntity,
        existing: DocumentEntity?,
    ): DocumentEntity {
        if (existing == null) return incoming
        return incoming.copy(
            addedAt = existing.addedAt,
            lastOpenedAt = existing.lastOpenedAt ?: incoming.lastOpenedAt,
            isFavorite = existing.isFavorite || incoming.isFavorite,
            pageCount = incoming.pageCount ?: existing.pageCount,
            trashedAt = existing.trashedAt,
            originalUri = existing.originalUri,
        )
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        documentDao.setFavorite(id, favorite)
    }

    suspend fun toggleFavorite(id: String) {
        val current = documentDao.getById(id) ?: return
        documentDao.setFavorite(id, !current.isFavorite)
    }

    suspend fun setLastOpenedAt(id: String, openedAt: Long = System.currentTimeMillis()) {
        documentDao.setLastOpenedAt(id, openedAt)
    }

    suspend fun markTrashed(id: String, trashUri: String, originalUri: String) {
        documentDao.markTrashed(id, System.currentTimeMillis(), trashUri, originalUri)
    }

    suspend fun markRestored(id: String, uri: String) {
        documentDao.markRestored(id, uri)
    }

    suspend fun renameDocument(oldId: String, newId: String, displayName: String, uri: String) {
        val existing = documentDao.getById(oldId) ?: return
        documentDao.deleteByIds(listOf(oldId))
        documentDao.upsertAll(
            listOf(
                existing.copy(
                    id = newId,
                    displayName = displayName,
                    uri = uri,
                ),
            ),
        )
    }

    suspend fun deleteByIds(ids: List<String>) {
        documentDao.deleteByIds(ids)
    }

    suspend fun trashedBefore(before: Long): List<DocumentEntity> = documentDao.trashedBefore(before)

    suspend fun allIdentityRows(): List<DocumentIdentity> = documentDao.allIdentityRows()

    suspend fun getReadingProgress(docId: String): Int? =
        readingProgressDao.get(docId)?.position

    suspend fun setReadingProgress(docId: String, position: Int) {
        readingProgressDao.upsert(
            ReadingProgressEntity(docId, position, System.currentTimeMillis()),
        )
    }

    fun recentSearches(limit: Int = 20): Flow<List<String>> =
        searchHistoryDao.recent(limit).map { list -> list.map { it.query } }

    suspend fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchHistoryDao.insert(SearchHistoryEntity(query = trimmed, at = System.currentTimeMillis()))
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.clear()
    }

    fun searchContent(query: String): Flow<List<Document>> =
        documentTextDao.searchContent(query.trim())
            .map { list -> list.map { it.toModel() } }

    suspend fun contentSnippet(docId: String, needle: String): String? =
        documentTextDao.snippet(docId, needle)

    fun observeTags() = tagDao.observeTags()

    fun tagsForDocument(docId: String) = tagDao.tagsForDocument(docId)

    suspend fun createTag(name: String, color: String = "#0E7490"): Long =
        tagDao.upsertTag(TagEntity(name = name, color = color))

    suspend fun assignTag(docId: String, tagId: Long) {
        tagDao.link(DocumentTagCrossRef(docId, tagId))
    }

    fun bookmarks(docId: String) = tagDao.bookmarks(docId)

    suspend fun addBookmark(docId: String, page: Int, label: String) {
        tagDao.upsertBookmark(BookmarkEntity(docId, page, label))
    }
}
