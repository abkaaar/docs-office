package com.docsoffice.app.core.files

import com.docsoffice.app.core.model.DocSource
import java.io.File
import java.security.MessageDigest

object PathClassifier {
    private val noiseDirNames = setOf(
        "cache",
        "caches",
        "thumbnail",
        "thumbnails",
        ".thumbnails",
        "tmp",
        "temp",
        ".nomedia",
    )

    fun isExcluded(path: String, userExcluded: Set<String>): Boolean {
        val normalized = path.replace('\\', '/')
        val segments = normalized.split('/')
        if (segments.any { it.startsWith('.') && it != "." && it != ".." }) return true
        if (segments.any { it.lowercase() in noiseDirNames }) return true
        return userExcluded.any { excluded ->
            normalized.startsWith(excluded.replace('\\', '/'), ignoreCase = true)
        }
    }

    fun classifySource(path: String): DocSource {
        val lower = path.replace('\\', '/').lowercase()
        return when {
            lower.contains("/whatsapp/") || lower.contains("/whatsapp business/") -> DocSource.WHATSAPP
            lower.contains("/telegram/") || lower.contains("/org.telegram.messenger") -> DocSource.TELEGRAM
            lower.contains("/download") -> DocSource.DOWNLOADS
            lower.contains("/scans") || lower.contains("/documents/scans") -> DocSource.SCANS
            else -> DocSource.OTHER
        }
    }

    fun stableId(uri: String, sizeBytes: Long, modifiedAt: Long): String {
        val input = "$uri|$sizeBytes|$modifiedAt"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun wellKnownRoots(): List<File> {
        val roots = mutableListOf<File>()
        val storage = File("/storage/emulated/0")
        if (storage.exists()) {
            roots += File(storage, "Download")
            roots += File(storage, "Downloads")
            roots += File(storage, "Documents")
            roots += File(storage, "WhatsApp/Media/WhatsApp Documents")
            roots += File(storage, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents")
            roots += File(storage, "Telegram/Telegram Documents")
            roots += File(storage, "Android/media/org.telegram.messenger/Telegram/Telegram Documents")
            roots += File(storage, "DCIM")
            roots += File(storage, "Pictures")
        }
        return roots.filter { it.exists() && it.canRead() }
    }
}
