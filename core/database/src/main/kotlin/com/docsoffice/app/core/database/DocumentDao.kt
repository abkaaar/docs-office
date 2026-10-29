package com.docsoffice.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query(
        """
        SELECT * FROM documents
        WHERE trashedAt IS NULL
        ORDER BY modifiedAt DESC
        """,
    )
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE trashedAt IS NULL AND lastOpenedAt IS NOT NULL
        ORDER BY lastOpenedAt DESC
        LIMIT :limit
        """,
    )
    fun recents(limit: Int): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE trashedAt IS NULL AND isFavorite = 1
        ORDER BY displayName ASC
        """,
    )
    fun favorites(): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE trashedAt IS NOT NULL
        ORDER BY trashedAt DESC
        """,
    )
    fun observeTrash(): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM documents
        WHERE trashedAt IS NULL AND lower(extension) IN (:extensions)
        """,
    )
    fun countByExtensions(extensions: List<String>): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE trashedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM documents
        WHERE trashedAt IS NULL AND displayName LIKE '%' || :query || '%'
        ORDER BY modifiedAt DESC
        LIMIT :limit
        """,
    )
    fun searchByName(query: String, limit: Int = 100): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE trashedAt IS NULL
          AND (:query = '' OR displayName LIKE '%' || :query || '%')
          AND (:source IS NULL OR source = :source)
          AND (:extension IS NULL OR lower(extension) = lower(:extension))
          AND (:minModified IS NULL OR modifiedAt >= :minModified)
          AND (:maxModified IS NULL OR modifiedAt <= :maxModified)
        ORDER BY
          CASE WHEN :sort = 'name' THEN displayName END ASC,
          CASE WHEN :sort = 'size' THEN sizeBytes END DESC,
          CASE WHEN :sort = 'date' THEN modifiedAt END DESC
        LIMIT :limit
        """,
    )
    fun searchFiltered(
        query: String,
        source: String?,
        extension: String?,
        minModified: Long?,
        maxModified: Long?,
        sort: String,
        limit: Int = 200,
    ): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(documents: List<DocumentEntity>)

    @Query("UPDATE documents SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE documents SET lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun setLastOpenedAt(id: String, openedAt: Long)

    @Query(
        """
        UPDATE documents
        SET trashedAt = :trashedAt, uri = :trashUri, originalUri = :originalUri
        WHERE id = :id
        """,
    )
    suspend fun markTrashed(id: String, trashedAt: Long, trashUri: String, originalUri: String)

    @Query(
        """
        UPDATE documents
        SET trashedAt = NULL, uri = :uri, originalUri = NULL
        WHERE id = :id
        """,
    )
    suspend fun markRestored(id: String, uri: String)

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM documents WHERE trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun trashedBefore(before: Long): List<DocumentEntity>

    @Query("SELECT id, uri, sizeBytes, modifiedAt FROM documents WHERE trashedAt IS NULL")
    suspend fun allIdentityRows(): List<DocumentIdentity>

    @Transaction
    suspend fun replaceBatch(documents: List<DocumentEntity>) {
        upsertAll(documents)
    }
}

data class DocumentIdentity(
    val id: String,
    val uri: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)
