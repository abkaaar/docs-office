package com.docsoffice.app.core.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.docsoffice.app.core.datastore.UserPreferencesRepository
import com.docsoffice.app.core.model.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafDocumentSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
) : DocumentSource {
    override suspend fun enumerate(onBatch: suspend (List<Document>) -> Unit) {
        val uris = preferences.safTreeUris.first()
        if (uris.isEmpty()) return
        val batch = ArrayList<Document>(500)
        for (uriString in uris) {
            val treeUri = Uri.parse(uriString)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            walkChildren(treeUri, rootDocId) { document ->
                batch += document
                if (batch.size >= 500) {
                    onBatch(batch.toList())
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) onBatch(batch.toList())
    }

    private suspend fun walkChildren(
        treeUri: Uri,
        parentDocId: String,
        onDocument: suspend (Document) -> Unit,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx) ?: continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walkChildren(treeUri, docId, onDocument)
                        continue
                    }
                    val extension = name.substringAfterLast('.', "").lowercase()
                    if (extension !in SupportedFormats.extensions) continue
                    val size = cursor.getLong(sizeIdx)
                    val modified = cursor.getLong(modIdx)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                    onDocument(
                        Document(
                            id = PathClassifier.stableId(childUri, size, modified),
                            uri = childUri,
                            displayName = name,
                            extension = extension,
                            mimeType = SupportedFormats.mimeTypeFor(extension),
                            sizeBytes = size,
                            modifiedAt = modified,
                            addedAt = System.currentTimeMillis(),
                            source = PathClassifier.classifySource(name),
                        ),
                    )
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "SAF walk failed")
        }
    }
}
