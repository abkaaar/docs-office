package com.docsoffice.app.core.files

object SupportedFormats {
    val extensions: Set<String> = setOf(
        "pdf",
        "txt", "md", "csv", "json", "log", "xml",
        "jpg", "jpeg", "png", "webp", "gif",
        "doc", "docx",
        "xls", "xlsx",
        "ppt", "pptx", "pptm",
    )

    fun extensionFromMime(mimeType: String?): String {
        if (mimeType.isNullOrBlank()) return ""
        return when (mimeType.lowercase().substringBefore(';').trim()) {
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "text/markdown" -> "md"
            "text/csv", "application/csv" -> "csv"
            "application/json" -> "json"
            "text/xml", "application/xml" -> "xml"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.ms-excel" -> "xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.ms-powerpoint" -> "ppt"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            "application/vnd.ms-powerpoint.presentation.macroenabled.12" -> "pptm"
            else -> ""
        }
    }

    fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "log" -> "text/plain"
        "xml" -> "text/xml"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "pptm" -> "application/vnd.ms-powerpoint.presentation.macroEnabled.12"
        else -> "application/octet-stream"
    }

    /** Lightweight magic-byte check; mismatches are flagged rather than hidden. */
    fun headerLooksPlausible(extension: String, header: ByteArray): Boolean {
        if (header.isEmpty()) return true
        return when (extension.lowercase()) {
            "pdf" -> header.size >= 4 &&
                header[0] == 0x25.toByte() &&
                header[1] == 0x50.toByte() &&
                header[2] == 0x44.toByte() &&
                header[3] == 0x46.toByte()
            "png" -> header.size >= 4 &&
                header[0] == 0x89.toByte() &&
                header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() &&
                header[3] == 0x47.toByte()
            "jpg", "jpeg" -> header.size >= 2 &&
                header[0] == 0xFF.toByte() &&
                header[1] == 0xD8.toByte()
            "gif" -> header.size >= 3 &&
                header[0] == 0x47.toByte() &&
                header[1] == 0x49.toByte() &&
                header[2] == 0x46.toByte()
            "docx", "xlsx", "pptx", "pptm" -> header.size >= 2 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte()
            else -> true
        }
    }
}
