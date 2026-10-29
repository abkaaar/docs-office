package com.docsoffice.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val docId: String,
    val position: Int,
    val updatedAt: Long,
)
