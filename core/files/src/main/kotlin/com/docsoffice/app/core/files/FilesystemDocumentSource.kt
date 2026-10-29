package com.docsoffice.app.core.files

import android.os.Environment
import com.docsoffice.app.core.datastore.UserPreferencesRepository
import com.docsoffice.app.core.model.Document
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilesystemDocumentSource @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : DocumentSource {
    override suspend fun enumerate(onBatch: suspend (List<Document>) -> Unit) {
        val excluded = preferences.excludedFolders.first()
        val batch = ArrayList<Document>(500)
        val roots = PathClassifier.wellKnownRoots().ifEmpty {
            listOfNotNull(Environment.getExternalStorageDirectory())
        }
        for (root in roots) {
            walk(root, excluded) { document ->
                batch += document
                if (batch.size >= 500) {
                    onBatch(batch.toList())
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
        }
    }

    private suspend fun walk(
        dir: File,
        excluded: Set<String>,
        onDocument: suspend (Document) -> Unit,
    ) {
        val children = dir.listFiles() ?: return
        for (file in children) {
            if (PathClassifier.isExcluded(file.absolutePath, excluded)) continue
            if (file.isDirectory) {
                walk(file, excluded, onDocument)
                continue
            }
            val extension = file.extension.lowercase()
            if (extension !in SupportedFormats.extensions) continue
            try {
                val header = file.inputStream().use { stream ->
                    val bytes = ByteArray(8)
                    val read = stream.read(bytes)
                    if (read <= 0) ByteArray(0) else bytes.copyOf(read)
                }
                if (!SupportedFormats.headerLooksPlausible(extension, header)) {
                    Timber.w("Header mismatch for extension=%s", extension)
                }
                val now = System.currentTimeMillis()
                val modified = file.lastModified()
                val uri = file.toURI().toString()
                onDocument(
                    Document(
                        id = PathClassifier.stableId(uri, file.length(), modified),
                        uri = uri,
                        displayName = file.name,
                        extension = extension,
                        mimeType = SupportedFormats.mimeTypeFor(extension),
                        sizeBytes = file.length(),
                        modifiedAt = modified,
                        addedAt = now,
                        source = PathClassifier.classifySource(file.absolutePath),
                    ),
                )
            } catch (t: Throwable) {
                Timber.w(t, "Skipping unreadable file")
            }
        }
    }
}
