package com.docsoffice.app.renderer.api

import java.io.Closeable
import kotlinx.coroutines.flow.Flow

interface DocumentSourceHandle {
    val uri: String
}

data class UriDocumentSource(
    override val uri: String,
) : DocumentSourceHandle

interface DocumentRenderer {
    val supportedMimeTypes: Set<String>
    suspend fun open(source: DocumentSourceHandle, password: String? = null): RenderSession
}

sealed interface RenderSession : Closeable {
    class Paged(
        val pageCount: Int,
        val renderPage: suspend (index: Int, widthPx: Int) -> Any,
        private val onClose: () -> Unit = {},
    ) : RenderSession {
        override fun close() = onClose()
    }

    class Reflow(
        val blocks: Flow<DocBlock>,
        private val onClose: () -> Unit = {},
    ) : RenderSession {
        override fun close() = onClose()
    }

    class Grid(
        val sheets: List<SheetRef>,
        val readRows: suspend (SheetRef, IntRange) -> List<Row>,
        private val onClose: () -> Unit = {},
    ) : RenderSession {
        override fun close() = onClose()
    }
}

data class DocBlock(val text: String)
data class SheetRef(val name: String, val index: Int)
data class Row(val cells: List<String>)

sealed class OpenFailure : Exception() {
    data object PasswordRequired : OpenFailure() {
        private fun readResolve(): Any = PasswordRequired
    }

    data object Corrupt : OpenFailure() {
        private fun readResolve(): Any = Corrupt
    }

    data object Unsupported : OpenFailure() {
        private fun readResolve(): Any = Unsupported
    }

    data object TooLarge : OpenFailure() {
        private fun readResolve(): Any = TooLarge
    }
}
