package com.docsoffice.app.core.files

data class UriMetadata(
    val displayName: String,
    val mimeType: String,
    val extension: String,
    val sizeBytes: Long,
)
