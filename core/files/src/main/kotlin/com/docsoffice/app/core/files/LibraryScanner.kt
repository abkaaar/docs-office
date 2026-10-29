package com.docsoffice.app.core.files

import android.os.Environment
import com.docsoffice.app.core.database.DocumentRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryScanner @Inject constructor(
    private val filesystemSource: FilesystemDocumentSource,
    private val safSource: SafDocumentSource,
    private val repository: DocumentRepository,
    private val progressStore: ScanProgressStore,
) {
    suspend fun scan() {
        progressStore.update(0, isComplete = false, message = "Scanning documents…")
        var found = 0
        try {
            val useFilesystem = Environment.isExternalStorageManager() ||
                android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R

            val sources: List<DocumentSource> = buildList {
                if (useFilesystem) add(filesystemSource)
                add(safSource)
            }

            for (source in sources) {
                source.enumerate { batch ->
                    repository.upsertAll(batch)
                    found += batch.size
                    progressStore.update(
                        foundCount = found,
                        isComplete = false,
                        message = "Found $found documents so far",
                    )
                }
            }
            progressStore.update(
                foundCount = found,
                isComplete = true,
                message = if (found == 0) "No documents yet" else "Found $found documents",
            )
        } catch (t: Throwable) {
            Timber.e(t, "Scan failed")
            progressStore.update(found, isComplete = true, message = "Scan interrupted")
            throw t
        }
    }
}
