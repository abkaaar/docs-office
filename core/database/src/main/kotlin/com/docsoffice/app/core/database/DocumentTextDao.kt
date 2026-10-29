package com.docsoffice.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentTextDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(text: DocumentTextEntity)

    @Query("DELETE FROM document_text WHERE docId = :docId")
    suspend fun deleteForDoc(docId: String)

    @Query(
        """
        SELECT d.* FROM documents d
        JOIN document_text t ON t.docId = d.id
        WHERE d.trashedAt IS NULL AND t.body LIKE '%' || :query || '%'
        LIMIT :limit
        """,
    )
    fun searchContent(query: String, limit: Int = 100): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT substr(body, max(1, instr(lower(body), lower(:needle)) - 40), 120)
        FROM document_text
        WHERE docId = :docId
        LIMIT 1
        """,
    )
    suspend fun snippet(docId: String, needle: String): String?
}
