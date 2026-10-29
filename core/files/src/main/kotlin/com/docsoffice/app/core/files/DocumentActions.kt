package com.docsoffice.app.core.files

import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.files.PathClassifier.stableId
import com.docsoffice.app.core.model.Document
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentActions @Inject constructor(
    private val repository: DocumentRepository,
    private val fileOperations: FileOperations,
) {
    suspend fun moveToTrash(document: Document): Boolean {
        val entity = repository.getEntityById(document.id) ?: return false
        val source = fileOperations.fileFromUriString(entity.uri) ?: return false
        val trashFile = fileOperations.moveToTrash(source, document.id) ?: return false
        repository.markTrashed(document.id, trashFile.toURI().toString(), entity.uri)
        return true
    }

    suspend fun restore(document: Document): Boolean {
        val entity = repository.getEntityById(document.id) ?: return false
        val originalUri = entity.originalUri ?: return false
        val trashFile = fileOperations.fileFromUriString(entity.uri) ?: return false
        val restored = fileOperations.restoreFromTrash(trashFile, originalUri) ?: return false
        repository.markRestored(document.id, restored.toURI().toString())
        return true
    }

    suspend fun purge(document: Document): Boolean {
        val entity = repository.getEntityById(document.id) ?: return false
        fileOperations.fileFromUriString(entity.uri)?.let { fileOperations.permanentlyDelete(it) }
        repository.deleteByIds(listOf(document.id))
        return true
    }

    suspend fun purgeExpired(retentionDays: Int): Int {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        val expired = repository.trashedBefore(cutoff)
        expired.forEach { entity ->
            fileOperations.fileFromUriString(entity.uri)?.let { fileOperations.permanentlyDelete(it) }
            repository.deleteByIds(listOf(entity.id))
        }
        return expired.size
    }

    suspend fun rename(document: Document, newName: String): Boolean {
        val source = fileOperations.fileFromUriString(document.uri) ?: return false
        val renamed = fileOperations.rename(source, newName) ?: return false
        val newUri = renamed.toURI().toString()
        val newId = stableId(newUri, renamed.length(), renamed.lastModified())
        repository.renameDocument(document.id, newId, newName, newUri)
        return true
    }

    suspend fun copyTo(document: Document, destinationDir: File): Boolean {
        val source = fileOperations.fileFromUriString(document.uri) ?: return false
        val dest = File(destinationDir, source.name)
        return fileOperations.copyVerify(source, dest)
    }
}
