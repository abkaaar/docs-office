package com.docsoffice.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_text")
data class DocumentTextEntity(
    @PrimaryKey val docId: String,
    val body: String,
)
