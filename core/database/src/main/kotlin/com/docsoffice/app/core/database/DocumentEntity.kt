package com.docsoffice.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.docsoffice.app.core.model.DocCategory
import com.docsoffice.app.core.model.DocSource
import com.docsoffice.app.core.model.Document

@Entity(
    tableName = "documents",
    indices = [
        Index("mimeType"),
        Index("lastOpenedAt"),
        Index("displayName"),
        Index("source"),
        Index("extension"),
        Index("trashedAt"),
    ],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    val isFavorite: Boolean = false,
    val source: String = DocSource.OTHER.name,
    val pageCount: Int? = null,
    val trashedAt: Long? = null,
    val originalUri: String? = null,
)

fun DocumentEntity.toModel(): Document = Document(
    id = id,
    uri = uri,
    displayName = displayName,
    extension = extension,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    modifiedAt = modifiedAt,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    isFavorite = isFavorite,
    source = runCatching { DocSource.valueOf(source) }.getOrDefault(DocSource.OTHER),
    pageCount = pageCount,
    trashedAt = trashedAt,
    category = DocCategory.fromExtension(extension),
)

fun Document.toEntity(originalUri: String? = null): DocumentEntity = DocumentEntity(
    id = id,
    uri = uri,
    displayName = displayName,
    extension = extension,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    modifiedAt = modifiedAt,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    isFavorite = isFavorite,
    source = source.name,
    pageCount = pageCount,
    trashedAt = trashedAt,
    originalUri = originalUri,
)
