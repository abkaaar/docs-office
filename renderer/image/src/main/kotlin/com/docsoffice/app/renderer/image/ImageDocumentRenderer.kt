package com.docsoffice.app.renderer.image

import com.docsoffice.app.renderer.api.DocumentRenderer
import com.docsoffice.app.renderer.api.DocumentSourceHandle
import com.docsoffice.app.renderer.api.OpenFailure
import com.docsoffice.app.renderer.api.RenderSession
import java.io.File
import java.net.URI
import kotlinx.coroutines.flow.flowOf

class ImageDocumentRenderer : DocumentRenderer {
    override val supportedMimeTypes: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
    )

    override suspend fun open(source: DocumentSourceHandle, password: String?): RenderSession {
        val file = runCatching { File(URI(source.uri)) }.getOrNull()
        if (file == null || !file.exists()) throw OpenFailure.Corrupt
        // Image viewing is handled in Compose with Coil; session signals a single "page".
        return RenderSession.Reflow(blocks = flowOf())
    }
}
