package com.docsoffice.app.core.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All mutations go through this class: copy, verify size, then delete.
 */
@Singleton
class FileOperations @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun trashDir(): File {
        val dir = File(context.filesDir, "trash")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun openCacheDir(): File {
        val dir = File(context.cacheDir, "open")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun fileFromUriString(uriString: String): File? {
        return try {
            when {
                uriString.startsWith("file:") -> File(URI(uriString))
                uriString.startsWith("/") -> File(uriString)
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun queryUriMetadata(uriString: String): UriMetadata {
        val uri = Uri.parse(uriString)
        val file = fileFromUriString(uriString)
        if (file != null && file.exists()) {
            val extension = file.extension.lowercase()
            return UriMetadata(
                displayName = file.name,
                mimeType = SupportedFormats.mimeTypeFor(extension.ifBlank { "bin" }),
                extension = extension,
                sizeBytes = file.length(),
            )
        }

        var displayName: String? = null
        var sizeBytes = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        val resolverMime = context.contentResolver.getType(uri)
        val name = displayName?.takeIf { it.isNotBlank() && !looksLikeOpaqueId(it) }
            ?: guessNameFromUri(uri)
            ?: "document"
        var extension = name.substringAfterLast('.', "").lowercase()
        if (extension !in SupportedFormats.extensions) {
            extension = SupportedFormats.extensionFromMime(resolverMime)
        }
        if (extension.isBlank()) {
            val mapped = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(resolverMime.orEmpty())
                .orEmpty()
                .lowercase()
            if (mapped.isNotBlank()) extension = mapped
        }
        val mimeType = resolverMime?.takeIf { it.isNotBlank() }
            ?: SupportedFormats.mimeTypeFor(extension.ifBlank { "bin" })
        return UriMetadata(
            displayName = if (name.contains('.') || extension.isBlank()) {
                name
            } else {
                "$name.$extension"
            },
            mimeType = mimeType,
            extension = extension,
            sizeBytes = sizeBytes,
        )
    }

    /**
     * Returns a local [File] that can be opened by file-based renderers.
     * `content://` URIs are copied into the app cache.
     */
    fun materializeReadableFile(uriString: String, preferredName: String? = null): File? {
        fileFromUriString(uriString)?.takeIf { it.exists() }?.let { return it }
        val uri = Uri.parse(uriString)
        val meta = queryUriMetadata(uriString)
        val safeName = (preferredName ?: meta.displayName)
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .ifBlank { "document" }
        val key = sha1(uriString).take(16)
        val dest = File(openCacheDir(), "${key}_$safeName")
        if (dest.exists() && dest.length() > 0L &&
            (meta.sizeBytes <= 0L || dest.length() == meta.sizeBytes)
        ) {
            return dest
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest.takeIf { it.exists() && it.length() > 0L }
        } catch (_: Throwable) {
            dest.delete()
            null
        }
    }

    fun copyVerify(source: File, destination: File): Boolean {
        destination.parentFile?.mkdirs()
        source.copyTo(destination, overwrite = true)
        return destination.exists() && destination.length() == source.length()
    }

    fun moveCopyVerifyDelete(source: File, destination: File): Boolean {
        if (!copyVerify(source, destination)) return false
        return source.delete()
    }

    fun rename(source: File, newName: String): File? {
        val dest = File(source.parentFile, newName)
        if (dest.exists()) return null
        return if (source.renameTo(dest)) dest else {
            if (moveCopyVerifyDelete(source, dest)) dest else null
        }
    }

    fun moveToTrash(source: File, documentId: String): File? {
        val dest = File(trashDir(), "${documentId}_${source.name}")
        return if (moveCopyVerifyDelete(source, dest)) dest else null
    }

    fun restoreFromTrash(trashFile: File, originalUri: String): File? {
        val original = fileFromUriString(originalUri) ?: return null
        original.parentFile?.mkdirs()
        return if (moveCopyVerifyDelete(trashFile, original)) original else null
    }

    fun permanentlyDelete(file: File): Boolean = !file.exists() || file.delete()

    fun shareIntent(uriString: String, mimeType: String, displayName: String): Intent {
        val file = fileFromUriString(uriString)
        val contentUri: Uri = if (file != null && file.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.parse(uriString)
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun openWithIntent(uriString: String, mimeType: String): Intent {
        val file = fileFromUriString(uriString)
        val contentUri: Uri = if (file != null && file.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.parse(uriString)
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun looksLikeOpaqueId(value: String): Boolean {
        val cleaned = value.substringAfterLast(':').substringAfterLast('/')
        return cleaned.all { it.isDigit() } || cleaned.matches(Regex("""msf:\d+""", RegexOption.IGNORE_CASE))
    }

    private fun guessNameFromUri(uri: Uri): String? {
        val segment = uri.lastPathSegment?.substringAfterLast('/') ?: return null
        if (looksLikeOpaqueId(segment)) return null
        return segment
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
