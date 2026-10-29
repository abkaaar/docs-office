package com.docsoffice.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val encryptedName: String,
    val blobPath: String,
    val mimeType: String,
    val extension: String,
    val sizeBytes: Long,
    val createdAt: Long,
)
