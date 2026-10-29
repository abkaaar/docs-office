package com.docsoffice.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.files.PathClassifier
import com.docsoffice.app.core.files.SupportedFormats
import com.docsoffice.app.core.model.DocSource
import com.docsoffice.app.core.model.Document
import com.docsoffice.app.core.pdftools.CompressQuality
import com.docsoffice.app.core.pdftools.PdfTools
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class PdfToolAction {
    IMAGES_TO_PDF,
    MERGE,
    SPLIT,
    REORDER,
    ROTATE,
    DELETE_PAGES,
    COMPRESS,
    PDF_TO_IMAGES,
    METADATA,
}

data class PdfToolsUiState(
    val processing: Boolean = false,
    val message: String? = null,
    val lastSavedDocId: String? = null,
    val pendingAction: PdfToolAction? = null,
    val needsPageRange: Boolean = false,
    val pageRangeHint: String = "1-3,5",
)

@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfTools: PdfTools,
    private val repository: DocumentRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PdfToolsUiState())
    val state: StateFlow<PdfToolsUiState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var job: Job? = null
    private var pendingUris: List<Uri> = emptyList()

    fun clearMessage() {
        _state.update { it.copy(message = null, lastSavedDocId = null) }
    }

    fun dismissPageRange() {
        _state.update { it.copy(needsPageRange = false, pendingAction = null) }
        pendingUris = emptyList()
    }

    fun requestAction(action: PdfToolAction, uris: List<Uri>) {
        if (uris.isEmpty() && action != PdfToolAction.IMAGES_TO_PDF && action != PdfToolAction.MERGE) {
            _state.update { it.copy(message = "Choose a file first") }
            return
        }
        when (action) {
            PdfToolAction.SPLIT,
            PdfToolAction.REORDER,
            PdfToolAction.DELETE_PAGES,
            PdfToolAction.PDF_TO_IMAGES,
            -> {
                pendingUris = uris
                _state.update {
                    it.copy(
                        pendingAction = action,
                        needsPageRange = true,
                        pageRangeHint = when (action) {
                            PdfToolAction.REORDER -> "e.g. 3,1,2 (new order)"
                            PdfToolAction.DELETE_PAGES -> "pages to delete, e.g. 2,4"
                            PdfToolAction.PDF_TO_IMAGES -> "pages to export, or leave blank for all"
                            else -> "pages to keep, e.g. 1-3,5"
                        },
                        message = null,
                    )
                }
            }
            else -> runTool(action, uris, pageSpec = null)
        }
    }

    fun confirmPageRange(spec: String) {
        val action = _state.value.pendingAction ?: return
        val uris = pendingUris
        _state.update { it.copy(needsPageRange = false, pendingAction = null) }
        pendingUris = emptyList()
        runTool(action, uris, pageSpec = spec)
    }

    private fun runTool(action: PdfToolAction, uris: List<Uri>, pageSpec: String?) {
        if (_state.value.processing) return
        job?.cancel()
        job = viewModelScope.launch {
            mutex.withLock {
                _state.update { it.copy(processing = true, message = null, lastSavedDocId = null) }
                try {
                    withContext(Dispatchers.IO) {
                        when (action) {
                            PdfToolAction.IMAGES_TO_PDF -> {
                                require(uris.isNotEmpty()) { "Choose one or more images" }
                                val images = uris.mapIndexed { i, uri -> uriToFile(uri, "img_$i.jpg") }
                                val out = outputPdf("images")
                                pdfTools.imagesToPdf(images, out)
                                registerOutput(out)
                            }
                            PdfToolAction.MERGE -> {
                                require(uris.size >= 2) { "Choose at least two PDFs to merge" }
                                val pdfs = uris.mapIndexed { i, uri -> uriToFile(uri, "pdf_$i.pdf") }
                                val out = outputPdf("merged")
                                pdfTools.merge(pdfs, out)
                                registerOutput(out)
                            }
                            PdfToolAction.ROTATE -> {
                                val input = uriToFile(requireNotNull(uris.firstOrNull()) { "Choose a PDF" }, "in.pdf")
                                val out = outputPdf("rotated")
                                pdfTools.rotate(input, 90f, out)
                                registerOutput(out)
                            }
                            PdfToolAction.COMPRESS -> {
                                val input = uriToFile(requireNotNull(uris.firstOrNull()) { "Choose a PDF" }, "in.pdf")
                                val out = outputPdf("compressed")
                                val (_, saved) = pdfTools.compress(input, CompressQuality.MEDIUM, out)
                                val id = registerOutput(out)
                                _state.update {
                                    it.copy(
                                        message = if (saved > 0) {
                                            "Compressed ${out.name} (saved $saved bytes)"
                                        } else {
                                            "Saved ${out.name} (size unchanged or larger after raster compress)"
                                        },
                                        lastSavedDocId = id,
                                    )
                                }
                                return@withContext
                            }
                            PdfToolAction.METADATA -> {
                                val input = uriToFile(requireNotNull(uris.firstOrNull()) { "Choose a PDF" }, "meta.pdf")
                                val meta = pdfTools.metadata(input)
                                _state.update {
                                    it.copy(
                                        message = "${meta.fileName}: ${meta.pageCount} pages, ${meta.sizeBytes} bytes",
                                    )
                                }
                                return@withContext
                            }
                            PdfToolAction.SPLIT -> {
                                val input = uriToFile(requireNotNull(uris.firstOrNull()) { "Choose a PDF" }, "in.pdf")
                                val count = pdfTools.pageCount(input)
                                val indices = pdfTools.parsePageRange(pageSpec.orEmpty(), count)
                                val out = outputPdf("split")
                                pdfTools.split(input, indices, out)
                                registerOutput(out)
                            }
                            PdfToolAction.REORDER -> {
                                val input = uriToFile(requireNotNull(uris.firstOrNull()) { "Choose a PDF" }, "in.pdf")
                                val count = pdfTools.pageCount(input)
                                val indices = pdfTools.parsePageRange(pageSpec.orEmpty(), count)
                                require(indices.size == count && indices.toSet().size == count) {
                                    "Reorder must list each page 1–$count exactly once"
                                }
                                val out = outputPdf("reordered")
                                pdfTools.reorder(input, indices, out)
                                registerOutput(out)
                            }
                            PdfToolAction.DELETE_PAGES -> {
                                val input = uriToFile(requireNotNull(uris.firstOrNull()) { "Choose a PDF" }, "in.pdf")
                                val count = pdfTools.pageCount(input)
                                val indices = pdfTools.parsePageRange(pageSpec.orEmpty(), count)
                                val out = outputPdf("deleted_pages")
                                pdfTools.deletePages(input, indices, out)
                                registerOutput(out)
                            }
                            PdfToolAction.PDF_TO_IMAGES -> {
                                val input = uriToFile(requireNotNull(uris.firstOrNull()) { "Choose a PDF" }, "in.pdf")
                                val count = pdfTools.pageCount(input)
                                val indices = if (pageSpec.isNullOrBlank()) {
                                    null
                                } else {
                                    pdfTools.parsePageRange(pageSpec, count)
                                }
                                val dir = File(toolsDir(), "images_${System.currentTimeMillis()}").apply { mkdirs() }
                                val files = pdfTools.pdfPagesToImages(input, dir, indices)
                                _state.update {
                                    it.copy(message = "Exported ${files.size} image(s) to ${dir.name}")
                                }
                                return@withContext
                            }
                        }
                    }
                } catch (t: Throwable) {
                    _state.update { it.copy(message = t.message ?: "Tool failed") }
                } finally {
                    _state.update { it.copy(processing = false) }
                }
            }
        }
    }

    private fun toolsDir(): File =
        File(context.getExternalFilesDir(null), "PdfTools").apply { mkdirs() }

    private fun outputPdf(prefix: String): File =
        File(toolsDir(), "${prefix}_${System.currentTimeMillis()}.pdf")

    private suspend fun registerOutput(out: File): String {
        val uri = out.toURI().toString()
        val id = PathClassifier.stableId(uri, out.length(), out.lastModified())
        val doc = Document(
            id = id,
            uri = uri,
            displayName = out.name,
            extension = "pdf",
            mimeType = SupportedFormats.mimeTypeFor("pdf"),
            sizeBytes = out.length(),
            modifiedAt = out.lastModified(),
            addedAt = System.currentTimeMillis(),
            source = DocSource.OTHER,
            pageCount = runCatching { pdfTools.pageCount(out) }.getOrNull(),
        )
        repository.upsertAll(listOf(doc))
        _state.update { it.copy(message = "Saved ${out.name}", lastSavedDocId = id) }
        return id
    }

    private fun uriToFile(uri: Uri, name: String): File {
        val file = File(context.cacheDir, "pdftool_$name")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: error("Could not read selected file")
        return file
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
