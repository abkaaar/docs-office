package com.docsoffice.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun link(ref: DocumentTagCrossRef)

    @Query("DELETE FROM document_tags WHERE docId = :docId AND tagId = :tagId")
    suspend fun unlink(docId: String, tagId: Long)

    @Query(
        """
        SELECT t.* FROM tags t
        JOIN document_tags dt ON dt.tagId = t.tagId
        WHERE dt.docId = :docId
        """,
    )
    fun tagsForDocument(docId: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Query("SELECT * FROM bookmarks WHERE docId = :docId ORDER BY page ASC")
    fun bookmarks(docId: String): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmarks WHERE docId = :docId AND page = :page")
    suspend fun deleteBookmark(docId: String, page: Int)
}
