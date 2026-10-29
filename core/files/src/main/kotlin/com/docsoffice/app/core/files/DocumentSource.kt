package com.docsoffice.app.core.files

import com.docsoffice.app.core.model.Document

/**
 * Hides how a document was found. The rest of the app only sees [Document] rows.
 */
interface DocumentSource {
    suspend fun enumerate(onBatch: suspend (List<Document>) -> Unit)
}
