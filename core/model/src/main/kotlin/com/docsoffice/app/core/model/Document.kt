package com.docsoffice.app.core.model

data class Document(
    val id: String,
    val uri: String,
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    val isFavorite: Boolean = false,
    val source: DocSource = DocSource.OTHER,
    val pageCount: Int? = null,
    val trashedAt: Long? = null,
    val category: DocCategory = DocCategory.fromExtension(extension),
)

enum class DocSource {
    DOWNLOADS,
    WHATSAPP,
    TELEGRAM,
    SCANS,
    OTHER,
}

enum class DocCategory {
    PDF,
    WORD,
    EXCEL,
    SLIDES,
    TEXT,
    IMAGE,
    OTHER,
    ;

    companion object {
        fun fromExtension(extension: String): DocCategory {
            return when (extension.lowercase()) {
                "pdf" -> PDF
                "doc", "docx" -> WORD
                "xls", "xlsx", "xlsm" -> EXCEL
                "ppt", "pptx", "pptm" -> SLIDES
                "txt", "md", "csv", "json", "log", "xml" -> TEXT
                "jpg", "jpeg", "png", "webp", "gif" -> IMAGE
                else -> OTHER
            }
        }
    }
}

data class CategoryCount(
    val category: DocCategory,
    val count: Int,
)

data class ScanProgress(
    val foundCount: Int,
    val isComplete: Boolean,
    val message: String = "",
)
