package com.docsoffice.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val tagId: Long = 0,
    val name: String,
    val color: String = "#0E7490",
)

@Entity(tableName = "document_tags", primaryKeys = ["docId", "tagId"])
data class DocumentTagCrossRef(
    val docId: String,
    val tagId: Long,
)

@Entity(tableName = "bookmarks", primaryKeys = ["docId", "page"])
data class BookmarkEntity(
    val docId: String,
    val page: Int,
    val label: String,
)
