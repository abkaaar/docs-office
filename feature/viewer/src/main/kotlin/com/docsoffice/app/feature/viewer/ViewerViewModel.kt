package com.docsoffice.app.feature.viewer

import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.datastore.UserPreferencesRepository
import com.docsoffice.app.core.files.FileOperations
import com.docsoffice.app.core.model.DocCategory
import com.docsoffice.app.core.model.Document
import com.docsoffice.app.core.pdftools.PdfTools
import com.docsoffice.app.renderer.api.OpenFailure
import com.docsoffice.app.renderer.api.RenderSession
import com.docsoffice.app.renderer.api.UriDocumentSource
import com.docsoffice.app.renderer.ooxml.DocxRenderer
import com.docsoffice.app.renderer.ooxml.PptxRenderer
import com.docsoffice.app.renderer.ooxml.XlsxRenderer
import com.docsoffice.app.renderer.pdf.PdfDocumentRenderer
import com.docsoffice.app.renderer.text.TextLineLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

sealed interface ViewerUiState {
    data object Loading : ViewerUiState

    data class PdfReady(
        val document: Document,
        val pageCount: Int,
        val pageIndex: Int,
        val pageBitmap: Bitmap?,
        val theme: String,
        val renderWidth: Int,
        /** Nearby pages for continuous scroll; keyed by page index. */
        val pageBitmaps: Map<Int, Bitmap> = emptyMap(),
        val findQuery: String = "",
        /** 0-based pages that contain [findQuery] (embedded text layer only). */
        val findMatchPages: List<Int> = emptyList(),
        val findMatchIndex: Int = 0,
        val findStatus: String? = null,
        val hasTextLayer: Boolean? = null,
    ) : ViewerUiState

    data class TextReady(
        val document: Document,
        val lines: List<String>,
        val fontScale: Float,
        val monospace: Boolean,
        val wrap: Boolean,
        val theme: String,
        val findQuery: String = "",
        val isCsv: Boolean = false,
        val csvRows: List<List<String>> = emptyList(),
        /** True when more of the file can be loaded (large-file streaming). */
        val hasMore: Boolean = false,
        val nextByteOffset: Long = 0L,
        val fileSizeBytes: Long = 0L,
        val loadingMore: Boolean = false,
        /** Explicit partial-preview banner for oversized / chunked text. */
        val partialPreview: Boolean = false,
        val encodingLabel: String = "UTF-8",
    ) : ViewerUiState

    data class DocxReady(
        val document: Document,
        val blocks: List<DocxRenderer.RichBlock>,
        val fontScale: Float,
        val theme: String,
        val findQuery: String = "",
    ) : ViewerUiState

    data class SpreadsheetReady(
        val document: Document,
        val sheetNames: List<String>,
        val sheetIndex: Int,
        val rows: List<List<String>>,
        val cells: List<List<XlsxRenderer.SpreadsheetCell>> = emptyList(),
        val truncated: Boolean,
        val theme: String,
        val localPath: String,
        val selectedCell: Pair<Int, Int>? = null,
        val findQuery: String = "",
    ) : ViewerUiState

    data class SlidesReady(
        val document: Document,
        val slideCount: Int,
        val slideIndex: Int,
        val current: PptxRenderer.Slide?,
        val theme: String,
        val loadingSlide: Boolean = false,
    ) : ViewerUiState

    data class ImageReady(
        val document: Document,
        val siblingUris: List<String>,
        val index: Int,
        val theme: String,
    ) : ViewerUiState

    data class CannotOpen(
        val document: Document,
        val reason: String,
        val kind: ViewerErrorKind = ViewerErrorKind.GENERIC,
    ) : ViewerUiState
}

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val preferences: UserPreferencesRepository,
    private val fileOperations: FileOperations,
    private val pdfTools: PdfTools,
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])
    private val pdfRenderer = PdfDocumentRenderer()
    private var pdfSession: RenderSession.Paged? = null
    private var renderWidthPx: Int = 1080
    private var sheetCatalog: XlsxRenderer.SheetCatalog? = null
    private var spreadsheetFile: File? = null
    private var slideCatalog: PptxRenderer.SlideCatalog? = null
    private var slidesFile: File? = null
    private var slidesMediaDir: File? = null
    private var docxMediaDir: File? = null
    private var pdfTextByPage: List<String> = emptyList()
    private var pdfSourceFile: File? = null
    private var openJob: Job? = null
    private var pageJob: Job? = null
    private var sheetJob: Job? = null
    private var slideJob: Job? = null
    private var findJob: Job? = null
    private var textLoadJob: Job? = null
    private val pdfMutex = Mutex()
    @Volatile private var cleared = false
    /** Document generation; ignore stale renders after open/close/switch. */
    @Volatile private var pdfGeneration: Int = 0
    private val renderingPages = ConcurrentHashMap.newKeySet<Int>()
    /** Bounds concurrent background page renders during rapid scroll. */
    private val prefetchLimiter = Semaphore(PDF_MAX_CONCURRENT_PREFETCH)
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)
    private val cacheEvictions = AtomicInteger(0)
    private val activePrefetch = AtomicInteger(0)
    private val peakCacheBytes = AtomicLong(0)
    /**
     * Memory-bounded page cache (~24 MiB). sizeOf returns KB; maxSize is KB.
     * Avoids retaining unbounded full-resolution bitmaps across large PDFs.
     * Keys are page indices for the current [pdfGeneration] only (evicted on open/close).
     */
    private val bitmapCache = object : LruCache<Int, Bitmap>(PDF_CACHE_MAX_KB) {
        override fun sizeOf(key: Int, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)

        override fun entryRemoved(
            evicted: Boolean,
            key: Int,
            oldValue: Bitmap,
            newValue: Bitmap?,
        ) {
            if (evicted) cacheEvictions.incrementAndGet()
            if (!evicted || oldValue === newValue || oldValue.isRecycled) return
            val held = (_state.value as? ViewerUiState.PdfReady)?.pageBitmaps?.values.orEmpty()
            if (oldValue !in held) {
                oldValue.recycle()
            }
        }
    }
    private val lastBitmap = AtomicReference<Bitmap?>(null)

    /** Snapshot for stress tests / diagnostics. */
    fun pdfCacheTelemetry(): PdfCacheTelemetry = PdfCacheTelemetry(
        generation = pdfGeneration,
        cacheSizeKb = bitmapCache.size(),
        maxSizeKb = bitmapCache.maxSize(),
        hits = cacheHits.get(),
        misses = cacheMisses.get(),
        evictions = cacheEvictions.get(),
        activePrefetchJobs = activePrefetch.get(),
        peakCacheBytes = peakCacheBytes.get(),
        renderingPageCount = renderingPages.size,
    )

    private val _state = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    init {
        openJob = viewModelScope.launch { open() }
    }

    private fun publishState(value: ViewerUiState) {
        if (!cleared) _state.value = value
    }

    fun setRenderWidth(widthPx: Int) {
        if (widthPx <= 0) return
        val next = widthPx.coerceIn(720, 1600)
        if (next == renderWidthPx) return
        renderWidthPx = next
        val current = _state.value as? ViewerUiState.PdfReady ?: return
        if (current.renderWidth != next) {
            publishState(current.copy(pageBitmaps = emptyMap(), pageBitmap = null, renderWidth = next))
            bitmapCache.evictAll()
            pageJob?.cancel()
            pageJob = viewModelScope.launch { renderPdfPage(current.pageIndex, forceWidth = next) }
        }
    }

    fun nextPage() {
        val current = _state.value as? ViewerUiState.PdfReady ?: return
        if (current.pageIndex + 1 < current.pageCount) {
            pageJob?.cancel()
            pageJob = viewModelScope.launch { renderPdfPage(current.pageIndex + 1) }
        }
    }

    fun previousPage() {
        val current = _state.value as? ViewerUiState.PdfReady ?: return
        if (current.pageIndex > 0) {
            pageJob?.cancel()
            pageJob = viewModelScope.launch { renderPdfPage(current.pageIndex - 1) }
        }
    }

    fun jumpToPage(index: Int) {
        pageJob?.cancel()
        pageJob = viewModelScope.launch { renderPdfPage(index) }
    }

    /** Prefetch a page into the continuous-scroll bitmap map without forcing a jump. */
    fun ensurePdfPage(index: Int) {
        val current = _state.value as? ViewerUiState.PdfReady ?: return
        if (index !in 0 until current.pageCount) return
        val cached = bitmapCache.get(index)
        if (current.pageBitmaps.containsKey(index) || cached != null) {
            if (cached != null) cacheHits.incrementAndGet()
            if (cached != null && !current.pageBitmaps.containsKey(index)) {
                publishPdfPageMap(current.pageIndex)
            }
            return
        }
        cacheMisses.incrementAndGet()
        prefetchPdfPage(index, current.renderWidth, preview = false)
    }

    /** Load the next chunk of a large text file without reloading prior lines. */
    fun loadMoreText() {
        val current = _state.value as? ViewerUiState.TextReady ?: return
        if (!current.hasMore || current.loadingMore || current.isCsv) return
        textLoadJob?.cancel()
        textLoadJob = viewModelScope.launch(Dispatchers.IO) {
            publishState(current.copy(loadingMore = true))
            try {
                val file = resolveFile(current.document)
                if (file == null || !file.exists()) {
                    cannotOpen(current.document, "This file was moved or deleted.", ViewerErrorKind.CORRUPT)
                    return@launch
                }
                val chunk = TextLineLoader.loadLinesChunk(file, current.nextByteOffset)
                if (cleared || !isActive) return@launch
                val latest = _state.value as? ViewerUiState.TextReady ?: return@launch
                publishState(
                    latest.copy(
                        lines = latest.lines + chunk.lines,
                        hasMore = !chunk.exhausted,
                        nextByteOffset = chunk.nextByteOffset,
                        loadingMore = false,
                        partialPreview = !chunk.exhausted || latest.partialPreview,
                        encodingLabel = chunk.encoding,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: OutOfMemoryError) {
                val latest = _state.value as? ViewerUiState.TextReady
                if (latest != null) publishState(latest.copy(loadingMore = false, hasMore = false))
            } catch (_: Throwable) {
                val latest = _state.value as? ViewerUiState.TextReady
                if (latest != null) {
                    publishState(latest.copy(loadingMore = false))
                    cannotOpen(latest.document, "Could not read more of this file.", ViewerErrorKind.CORRUPT)
                }
            }
        }
    }

    fun nextSlide() {
        val current = _state.value as? ViewerUiState.SlidesReady ?: return
        if (current.slideIndex + 1 < current.slideCount) loadSlide(current.slideIndex + 1)
    }

    fun previousSlide() {
        val current = _state.value as? ViewerUiState.SlidesReady ?: return
        if (current.slideIndex > 0) loadSlide(current.slideIndex - 1)
    }

    fun jumpToSlide(index: Int) = loadSlide(index)

    fun toggleFavorite() {
        viewModelScope.launch { repository.toggleFavorite(documentId) }
    }

    fun bookmarkCurrentPage() {
        val current = _state.value as? ViewerUiState.PdfReady ?: return
        viewModelScope.launch {
            repository.addBookmark(
                docId = current.document.id,
                page = current.pageIndex,
                label = "Page ${current.pageIndex + 1}",
            )
        }
    }

    fun setFontScale(scale: Float) {
        when (val current = _state.value) {
            is ViewerUiState.TextReady ->
                _state.value = current.copy(fontScale = scale.coerceIn(0.8f, 2.5f))
            is ViewerUiState.DocxReady ->
                _state.value = current.copy(fontScale = scale.coerceIn(0.8f, 2.5f))
            else -> Unit
        }
    }

    fun toggleMonospace() {
        val current = _state.value as? ViewerUiState.TextReady ?: return
        _state.value = current.copy(monospace = !current.monospace)
    }

    fun toggleWrap() {
        val current = _state.value as? ViewerUiState.TextReady ?: return
        _state.value = current.copy(wrap = !current.wrap)
    }

    fun setFindQuery(query: String) {
        findJob?.cancel()
        findJob = viewModelScope.launch {
            delay(150)
            when (val current = _state.value) {
                is ViewerUiState.TextReady -> publishState(current.copy(findQuery = query))
                is ViewerUiState.DocxReady -> publishState(current.copy(findQuery = query))
                is ViewerUiState.SpreadsheetReady -> publishState(current.copy(findQuery = query))
                is ViewerUiState.PdfReady -> applyPdfFind(current, query)
                else -> Unit
            }
        }
    }

    fun findNextPdfMatch() {
        val current = _state.value as? ViewerUiState.PdfReady ?: return
        if (current.findMatchPages.isEmpty()) return
        val next = (current.findMatchIndex + 1) % current.findMatchPages.size
        jumpToPdfFindMatch(current, next)
    }

    fun findPrevPdfMatch() {
        val current = _state.value as? ViewerUiState.PdfReady ?: return
        if (current.findMatchPages.isEmpty()) return
        val prev = (current.findMatchIndex - 1 + current.findMatchPages.size) % current.findMatchPages.size
        jumpToPdfFindMatch(current, prev)
    }

    private fun jumpToPdfFindMatch(current: ViewerUiState.PdfReady, matchIndex: Int) {
        val page = current.findMatchPages[matchIndex]
        publishState(
            current.copy(
                findMatchIndex = matchIndex,
                findStatus = "Match ${matchIndex + 1} of ${current.findMatchPages.size} (page ${page + 1})",
            ),
        )
        jumpToPage(page)
    }

    private fun applyPdfFind(current: ViewerUiState.PdfReady, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            publishState(
                current.copy(
                    findQuery = query,
                    findMatchPages = emptyList(),
                    findMatchIndex = 0,
                    findStatus = null,
                ),
            )
            return
        }
        if (pdfTextByPage.isEmpty() || current.hasTextLayer == false) {
            publishState(
                current.copy(
                    findQuery = query,
                    findMatchPages = emptyList(),
                    findMatchIndex = 0,
                    findStatus = "No searchable text in this PDF.",
                ),
            )
            return
        }
        val needle = trimmed.lowercase()
        val matches = pdfTextByPage.mapIndexedNotNull { index, text ->
            if (text.lowercase().contains(needle)) index else null
        }
        if (matches.isEmpty()) {
            publishState(
                current.copy(
                    findQuery = query,
                    findMatchPages = emptyList(),
                    findMatchIndex = 0,
                    findStatus = "No matches for “$trimmed”.",
                ),
            )
            return
        }
        val updated = current.copy(
            findQuery = query,
            findMatchPages = matches,
            findMatchIndex = 0,
            findStatus = "Match 1 of ${matches.size} (page ${matches[0] + 1})",
        )
        publishState(updated)
        jumpToPage(matches[0])
    }

    fun selectCell(row: Int, col: Int) {
        val current = _state.value as? ViewerUiState.SpreadsheetReady ?: return
        _state.value = current.copy(selectedCell = row to col)
    }

    fun nextImage() {
        val current = _state.value as? ViewerUiState.ImageReady ?: return
        if (current.index + 1 < current.siblingUris.size) {
            _state.value = current.copy(index = current.index + 1)
        }
    }

    fun previousImage() {
        val current = _state.value as? ViewerUiState.ImageReady ?: return
        if (current.index > 0) {
            _state.value = current.copy(index = current.index - 1)
        }
    }

    fun setReaderTheme(theme: String) {
        viewModelScope.launch {
            preferences.setReaderTheme(theme)
            when (val s = _state.value) {
                is ViewerUiState.PdfReady -> _state.value = s.copy(theme = theme)
                is ViewerUiState.TextReady -> _state.value = s.copy(theme = theme)
                is ViewerUiState.DocxReady -> _state.value = s.copy(theme = theme)
                is ViewerUiState.SpreadsheetReady -> _state.value = s.copy(theme = theme)
                is ViewerUiState.SlidesReady -> _state.value = s.copy(theme = theme)
                is ViewerUiState.ImageReady -> _state.value = s.copy(theme = theme)
                else -> Unit
            }
        }
    }

    fun shareIntent() = currentDocument()?.let {
        fileOperations.shareIntent(it.uri, it.mimeType, it.displayName)
    }

    fun openWithIntent() = currentDocument()?.let {
        fileOperations.openWithIntent(it.uri, it.mimeType)
    }

    fun currentDocument(): Document? = when (val s = _state.value) {
        is ViewerUiState.PdfReady -> s.document
        is ViewerUiState.TextReady -> s.document
        is ViewerUiState.DocxReady -> s.document
        is ViewerUiState.ImageReady -> s.document
        is ViewerUiState.SpreadsheetReady -> s.document
        is ViewerUiState.SlidesReady -> s.document
        is ViewerUiState.CannotOpen -> s.document
        else -> null
    }

    override fun onCleared() {
        cleared = true
        pdfGeneration++
        openJob?.cancel()
        pageJob?.cancel()
        sheetJob?.cancel()
        slideJob?.cancel()
        findJob?.cancel()
        textLoadJob?.cancel()
        renderingPages.clear()
        (_state.value as? ViewerUiState.PdfReady)?.let {
            publishState(it.copy(pageBitmaps = emptyMap(), pageBitmap = null))
        }
        pdfSession?.close()
        pdfSession = null
        bitmapCache.evictAll()
        lastBitmap.getAndSet(null)?.takeIf { !it.isRecycled }?.recycle()
        sheetCatalog = null
        spreadsheetFile = null
        slideCatalog = null
        slidesFile = null
        slidesMediaDir?.deleteRecursively()
        slidesMediaDir = null
        docxMediaDir?.deleteRecursively()
        docxMediaDir = null
        pdfTextByPage = emptyList()
        pdfSourceFile = null
        super.onCleared()
    }

    private suspend fun open() {
        _state.value = ViewerUiState.Loading
        val document = repository.getById(documentId)
        if (document == null) {
            cannotOpen(
                Document(
                    id = documentId,
                    uri = "",
                    displayName = "Unknown",
                    extension = "",
                    mimeType = "application/octet-stream",
                    sizeBytes = 0,
                    modifiedAt = 0,
                    addedAt = 0,
                ),
                "File not found",
                ViewerErrorKind.GENERIC,
            )
            return
        }
        repository.setLastOpenedAt(document.id)
        val theme = preferences.readerTheme.first()
        val enriched = enrichDocument(document)
        val ext = enriched.extension.lowercase()

        when {
            ext in LEGACY_OFFICE -> cannotOpen(
                enriched,
                "This older Office format needs another app. Use Open with…",
                ViewerErrorKind.UNSUPPORTED,
            )
            ext == "pdf" || enriched.category == DocCategory.PDF -> openPdf(enriched, theme)
            ext in setOf("xlsx", "xlsm") -> openSpreadsheet(enriched, theme)
            ext in setOf("pptx", "pptm") -> openSlides(enriched, theme)
            ext == "docx" -> openDocx(enriched, theme)
            ext == "csv" -> openCsv(enriched, theme)
            enriched.category == DocCategory.TEXT || ext in TEXT_EXTS -> openText(enriched, theme)
            enriched.category == DocCategory.IMAGE || ext in IMAGE_EXTS -> openImage(enriched, theme)
            else -> cannotOpen(
                enriched,
                "This file can't be opened here. Use Open with… to view it in another app.",
                ViewerErrorKind.UNSUPPORTED,
            )
        }
    }

    private fun enrichDocument(document: Document): Document {
        val meta = fileOperations.queryUriMetadata(document.uri)
        val extension = meta.extension.ifBlank { document.extension }.lowercase()
        return document.copy(
            displayName = meta.displayName.ifBlank { document.displayName },
            extension = extension,
            mimeType = meta.mimeType.ifBlank { document.mimeType },
            sizeBytes = if (meta.sizeBytes > 0) meta.sizeBytes else document.sizeBytes,
            category = DocCategory.fromExtension(extension),
        )
    }

    private suspend fun resolveFile(document: Document): File? = withContext(Dispatchers.IO) {
        fileOperations.materializeReadableFile(document.uri, document.displayName)
    }

    private suspend fun openDocx(document: Document, theme: String) = withContext(Dispatchers.IO) {
        try {
            val file = resolveFile(document)
                ?: return@withContext cannotOpen(document, "Could not read this file.")
            if (file.length() > MAX_TEXT_BYTES) {
                return@withContext cannotOpen(
                    document,
                    "This file is too large to preview. Use Open with…",
                    ViewerErrorKind.TOO_LARGE,
                )
            }
            val mediaDir = File(fileOperations.openCacheDir(), "docx-${document.id}").also {
                docxMediaDir = it
            }
            val blocks = DocxRenderer.parseDocument(file, mediaDir)
            publishState(
                ViewerUiState.DocxReady(
                    document = document,
                    blocks = blocks.ifEmpty {
                        listOf(DocxRenderer.RichBlock("(Empty document)"))
                    },
                    fontScale = 1f,
                    theme = theme,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            cannotOpen(document, "Not enough memory to open this file.", ViewerErrorKind.TOO_LARGE)
        } catch (_: SecurityException) {
            cannotOpen(document, "This document is too large or unsafe to open.", ViewerErrorKind.TOO_LARGE)
        } catch (_: Throwable) {
            cannotOpen(document, "This file can't be opened here.", ViewerErrorKind.CORRUPT)
        }
    }

    private suspend fun openSlides(document: Document, theme: String) =
        withContext(Dispatchers.IO) {
            try {
                val file = resolveFile(document)
                    ?: return@withContext cannotOpen(document, "Could not read this file.")
                if (file.length() > MAX_OFFICE_BYTES) {
                    return@withContext cannotOpen(
                        document,
                        "This presentation is too large to preview. Use Open with…",
                        ViewerErrorKind.TOO_LARGE,
                    )
                }
                val catalog = PptxRenderer.openCatalog(file)
                if (catalog.slideCount == 0) {
                    return@withContext cannotOpen(
                        document,
                        "No slides found in this presentation. The file may be damaged.",
                        ViewerErrorKind.CORRUPT,
                    )
                }
                slideCatalog = catalog
                slidesFile = file
                slidesMediaDir = File(fileOperations.openCacheDir(), "pptx-${document.id}").also {
                    if (it.exists()) it.deleteRecursively()
                    it.mkdirs()
                }
                val saved = savedStateHandle.get<Int>(KEY_POS)
                    ?: repository.getReadingProgress(document.id)
                    ?: 0
                val index = saved.coerceIn(0, catalog.slideCount - 1)
                val slide = PptxRenderer.parseSlideAt(file, catalog, index, slidesMediaDir)
                publishState(
                    ViewerUiState.SlidesReady(
                        document = document,
                        slideCount = catalog.slideCount,
                        slideIndex = index,
                        current = slide,
                        theme = theme,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: OutOfMemoryError) {
                cannotOpen(document, "Not enough memory to open this presentation.", ViewerErrorKind.TOO_LARGE)
            } catch (_: SecurityException) {
                cannotOpen(document, "This presentation is too large or unsafe to open.", ViewerErrorKind.TOO_LARGE)
            } catch (_: java.util.zip.ZipException) {
                cannotOpen(document, "This presentation looks damaged or incomplete.", ViewerErrorKind.CORRUPT)
            } catch (_: Throwable) {
                cannotOpen(document, "This presentation can't be opened here.", ViewerErrorKind.CORRUPT)
            }
        }

    private fun loadSlide(index: Int) {
        val current = _state.value as? ViewerUiState.SlidesReady ?: return
        val catalog = slideCatalog ?: return
        val file = slidesFile ?: return
        val clamped = index.coerceIn(0, catalog.slideCount - 1)
        publishState(current.copy(loadingSlide = true, slideIndex = clamped))
        slideJob?.cancel()
        slideJob = viewModelScope.launch(Dispatchers.IO) {
            val slide = PptxRenderer.parseSlideAt(file, catalog, clamped, slidesMediaDir)
            if (cleared || !isActive) return@launch
            savedStateHandle[KEY_POS] = clamped
            repository.setReadingProgress(current.document.id, clamped)
            val latest = _state.value as? ViewerUiState.SlidesReady ?: current
            publishState(
                latest.copy(
                    slideIndex = clamped,
                    current = slide,
                    loadingSlide = false,
                ),
            )
        }
    }

    private suspend fun openSpreadsheet(document: Document, theme: String) =
        withContext(Dispatchers.IO) {
            try {
                val file = resolveFile(document)
                    ?: return@withContext cannotOpen(document, "Could not read this file.")
                if (file.length() > MAX_OFFICE_BYTES) {
                    return@withContext cannotOpen(
                        document,
                        "This spreadsheet is too large to preview. Use Open with…",
                        ViewerErrorKind.TOO_LARGE,
                    )
                }
                val catalog = XlsxRenderer.openCatalog(file)
                sheetCatalog = catalog
                spreadsheetFile = file
                val saved = savedStateHandle.get<Int>(KEY_POS) ?: 0
                val index = saved.coerceIn(0, (catalog.sheets.size - 1).coerceAtLeast(0))
                val cells = XlsxRenderer.readSheetCells(file, catalog, index)
                val rows = cells.map { row -> row.map { it.display } }
                publishState(
                    ViewerUiState.SpreadsheetReady(
                        document = document,
                        sheetNames = catalog.sheets.map { it.name },
                        sheetIndex = index,
                        rows = rows,
                        cells = cells,
                        truncated = rows.size >= XlsxRenderer.DEFAULT_MAX_ROWS,
                        theme = theme,
                        localPath = file.absolutePath,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: OutOfMemoryError) {
                cannotOpen(document, "Not enough memory to open this spreadsheet.", ViewerErrorKind.TOO_LARGE)
            } catch (_: SecurityException) {
                cannotOpen(document, "This spreadsheet is too large or unsafe to open.", ViewerErrorKind.TOO_LARGE)
            } catch (_: Throwable) {
                cannotOpen(document, "This spreadsheet can't be opened here.", ViewerErrorKind.CORRUPT)
            }
        }

    fun selectSheet(index: Int) {
        val current = _state.value as? ViewerUiState.SpreadsheetReady ?: return
        if (index == current.sheetIndex) return
        sheetJob?.cancel()
        sheetJob = viewModelScope.launch(Dispatchers.IO) {
            val file = spreadsheetFile ?: File(current.localPath)
            if (!file.exists()) return@launch
            val catalog = sheetCatalog ?: XlsxRenderer.openCatalog(file).also { sheetCatalog = it }
            spreadsheetFile = file
            savedStateHandle[KEY_POS] = index
            publishState(
                current.copy(
                    rows = emptyList(),
                    cells = emptyList(),
                    selectedCell = null,
                    sheetIndex = index,
                ),
            )
            val cells = XlsxRenderer.readSheetCells(file, catalog, index)
            val rows = cells.map { row -> row.map { it.display } }
            if (cleared || !isActive) return@launch
            val latest = _state.value as? ViewerUiState.SpreadsheetReady ?: current
            publishState(
                latest.copy(
                    sheetIndex = index,
                    rows = rows,
                    cells = cells,
                    truncated = rows.size >= XlsxRenderer.DEFAULT_MAX_ROWS,
                ),
            )
        }
    }

    private suspend fun openPdf(document: Document, theme: String) = withContext(Dispatchers.IO) {
        try {
            val generation = ++pdfGeneration
            renderingPages.clear()
            bitmapCache.evictAll()
            cacheHits.set(0)
            cacheMisses.set(0)
            cacheEvictions.set(0)
            peakCacheBytes.set(0)
            pdfMutex.withLock {
                pdfSession?.close()
                pdfSession = null
            }
            val file = resolveFile(document)
                ?: return@withContext cannotOpen(document, "Could not read this file.")
            pdfSourceFile = file
            pdfTextByPage = runCatching { pdfTools.extractTextByPage(file) }.getOrDefault(emptyList())
            val hasText = pdfTextByPage.any { it.isNotBlank() }
            val session = pdfRenderer.open(
                UriDocumentSource(file.toURI().toString()),
                password = null,
            ) as RenderSession.Paged
            if (cleared || generation != pdfGeneration) {
                session.close()
                return@withContext
            }
            pdfSession = session
            val saved = savedStateHandle.get<Int>(KEY_POS)
                ?: repository.getReadingProgress(document.id)
                ?: 0
            val page = saved.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0))
            val width = renderWidthPx
            val bitmap = renderPdfBitmap(page, width) ?: return@withContext
            publishPdfBitmap(page, bitmap)
            publishState(
                ViewerUiState.PdfReady(
                    document = document,
                    pageCount = session.pageCount,
                    pageIndex = page,
                    pageBitmap = bitmap,
                    theme = theme,
                    renderWidth = width,
                    pageBitmaps = mapOf(page to bitmap),
                    hasTextLayer = hasText,
                ),
            )
            warmPdfCache(page, width)
            publishPdfPageMap(page)
        } catch (e: CancellationException) {
            throw e
        } catch (_: OpenFailure.PasswordRequired) {
            cannotOpen(
                document,
                "This PDF is password-protected. Android's built-in PDF renderer cannot unlock " +
                    "passworded files in-app. Use Open with… to unlock it in another app.",
                ViewerErrorKind.PASSWORD,
            )
        } catch (_: OpenFailure.Corrupt) {
            cannotOpen(document, "This PDF looks damaged or incomplete.", ViewerErrorKind.CORRUPT)
        } catch (_: OpenFailure.Unsupported) {
            cannotOpen(document, "This file can't be opened here.", ViewerErrorKind.UNSUPPORTED)
        } catch (_: OutOfMemoryError) {
            cannotOpen(document, "Not enough memory to open this PDF.", ViewerErrorKind.TOO_LARGE)
        } catch (_: Throwable) {
            cannotOpen(document, "This file can't be opened here.", ViewerErrorKind.CORRUPT)
        }
    }

    private suspend fun renderPdfBitmap(index: Int, width: Int): Bitmap? = pdfMutex.withLock {
        if (cleared) return null
        val session = pdfSession ?: return null
        session.renderPage(index, width) as Bitmap
    }

    private suspend fun renderPdfPage(index: Int, forceWidth: Int? = null) =
        withContext(Dispatchers.IO) {
            val current = _state.value as? ViewerUiState.PdfReady ?: return@withContext
            val width = forceWidth ?: current.renderWidth
            val clamped = index.coerceIn(0, (current.pageCount - 1).coerceAtLeast(0))
            try {
                val cached = if (forceWidth == null) bitmapCache.get(clamped) else null
                val bitmap = cached ?: (renderPdfBitmap(clamped, width) ?: return@withContext).also {
                    bitmapCache.put(clamped, it)
                }
                if (cleared || !isActive) return@withContext
                publishPdfBitmap(clamped, bitmap)
                repository.setReadingProgress(current.document.id, clamped)
                savedStateHandle[KEY_POS] = clamped
                publishState(
                    current.copy(
                        pageIndex = clamped,
                        pageBitmap = bitmap,
                        renderWidth = width,
                    ),
                )
                warmPdfCache(clamped, width)
                publishPdfPageMap(clamped)
            } catch (e: CancellationException) {
                throw e
            } catch (_: OutOfMemoryError) {
                cannotOpen(current.document, "Not enough memory to show this page.", ViewerErrorKind.TOO_LARGE)
            } catch (_: Throwable) {
                cannotOpen(current.document, "Could not render this page.", ViewerErrorKind.CORRUPT)
            }
        }

    private fun publishPdfBitmap(index: Int, bitmap: Bitmap) {
        bitmapCache.put(index, bitmap)
        lastBitmap.set(bitmap)
    }

    private fun publishPdfPageMap(center: Int) {
        if (cleared) return
        val current = _state.value as? ViewerUiState.PdfReady ?: return
        val window = (center - PDF_VISIBLE_RADIUS..center + PDF_VISIBLE_RADIUS)
            .filter { it in 0 until current.pageCount }
            .mapNotNull { i -> bitmapCache.get(i)?.let { bmp -> i to bmp } }
            .toMap()
        if (window != current.pageBitmaps) {
            publishState(
                current.copy(
                    pageBitmaps = window,
                    pageBitmap = window[current.pageIndex] ?: current.pageBitmap,
                ),
            )
        }
    }

    private fun warmPdfCache(index: Int, width: Int) {
        val pageCount = (_state.value as? ViewerUiState.PdfReady)?.pageCount ?: return
        // Full-res neighbors; lower-res for ±2 to reduce memory under rapid scroll.
        listOf(
            (index - 1) to false,
            (index + 1) to false,
            (index - 2) to true,
            (index + 2) to true,
        ).forEach { (i, preview) ->
            if (i !in 0 until pageCount) return@forEach
            if (bitmapCache.get(i) != null) return@forEach
            prefetchPdfPage(i, width, preview = preview)
        }
    }

    private fun prefetchPdfPage(index: Int, width: Int, preview: Boolean) {
        if (cleared) return
        if (!renderingPages.add(index)) return
        val generation = pdfGeneration
        val renderWidth = if (preview) (width / 2).coerceAtLeast(360) else width
        viewModelScope.launch(Dispatchers.IO) {
            activePrefetch.incrementAndGet()
            try {
                prefetchLimiter.withPermit {
                    if (cleared || generation != pdfGeneration) return@withPermit
                    val bmp = renderPdfBitmap(index, renderWidth) ?: return@withPermit
                    if (cleared || !isActive || generation != pdfGeneration) {
                        if (!bmp.isRecycled) bmp.recycle()
                        return@withPermit
                    }
                    // Prefer keeping a higher-res bitmap if one already exists.
                    val existing = bitmapCache.get(index)
                    if (existing != null && existing.width >= bmp.width) {
                        if (!bmp.isRecycled) bmp.recycle()
                    } else {
                        bitmapCache.put(index, bmp)
                        peakCacheBytes.updateAndGet { cur ->
                            maxOf(cur, bitmapCache.size().toLong() * 1024L)
                        }
                    }
                    publishPdfPageMap((_state.value as? ViewerUiState.PdfReady)?.pageIndex ?: index)
                }
            } finally {
                renderingPages.remove(index)
                activePrefetch.decrementAndGet()
            }
        }
    }

    private suspend fun openText(document: Document, theme: String) = withContext(Dispatchers.IO) {
        try {
            val file = resolveFile(document)
                ?: return@withContext cannotOpen(document, "Could not read this file.")
            val size = file.length()
            val chunk = TextLineLoader.loadLinesChunk(
                file,
                startByte = 0L,
                maxLines = TextLineLoader.DEFAULT_CHUNK_LINES,
            )
            val partial = !chunk.exhausted || size > MAX_TEXT_BYTES
            val banner = if (partial) {
                listOf(
                    "Partial preview · ${formatBytes(size)} · ${chunk.encoding} · " +
                        "Scroll and tap Load more to continue",
                )
            } else {
                emptyList()
            }
            publishState(
                ViewerUiState.TextReady(
                    document = document,
                    lines = banner + chunk.lines,
                    fontScale = 1f,
                    monospace = document.extension in setOf("json", "log", "xml"),
                    wrap = true,
                    theme = theme,
                    hasMore = !chunk.exhausted,
                    nextByteOffset = chunk.nextByteOffset,
                    fileSizeBytes = size,
                    partialPreview = partial,
                    encodingLabel = chunk.encoding,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            cannotOpen(document, "Not enough memory to open this file.", ViewerErrorKind.TOO_LARGE)
        } catch (_: Throwable) {
            cannotOpen(document, "This file can't be opened here.", ViewerErrorKind.CORRUPT)
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }

    private suspend fun openCsv(document: Document, theme: String) = withContext(Dispatchers.IO) {
        try {
            val file = resolveFile(document)
                ?: return@withContext cannotOpen(document, "Could not read this file.")
            if (file.length() > MAX_TEXT_BYTES) {
                return@withContext cannotOpen(
                    document,
                    "This file is too large to preview. Use Open with…",
                    ViewerErrorKind.TOO_LARGE,
                )
            }
            val rows = TextLineLoader.parseCsvRows(file)
            publishState(
                ViewerUiState.TextReady(
                    document = document,
                    lines = emptyList(),
                    fontScale = 1f,
                    monospace = true,
                    wrap = false,
                    theme = theme,
                    isCsv = true,
                    csvRows = rows,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            cannotOpen(document, "Not enough memory to open this file.", ViewerErrorKind.TOO_LARGE)
        } catch (_: Throwable) {
            cannotOpen(document, "This file can't be opened here.", ViewerErrorKind.CORRUPT)
        }
    }

    private suspend fun openImage(document: Document, theme: String) = withContext(Dispatchers.IO) {
        try {
            val file = resolveFile(document)
                ?: return@withContext cannotOpen(document, "Could not read this image.")
            val siblings = file.parentFile
                ?.listFiles()
                ?.filter { it.extension.lowercase() in IMAGE_EXTS }
                ?.sortedBy { it.name.lowercase() }
                ?.map { it.toURI().toString() }
                .orEmpty()
                .ifEmpty { listOf(file.toURI().toString()) }
            val index = siblings.indexOfFirst {
                it.endsWith(file.name, ignoreCase = true)
            }.coerceAtLeast(0)
            publishState(ViewerUiState.ImageReady(document, siblings, index, theme))
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            cannotOpen(document, "Not enough memory to open this image.", ViewerErrorKind.TOO_LARGE)
        } catch (_: Throwable) {
            cannotOpen(document, "This image can't be opened here.", ViewerErrorKind.CORRUPT)
        }
    }

    private fun cannotOpen(
        document: Document,
        reason: String,
        kind: ViewerErrorKind = viewerErrorKind(reason),
    ) {
        publishState(ViewerUiState.CannotOpen(document, reason, kind))
    }

    companion object {
        private const val KEY_POS = "viewer_position"
        private const val MAX_TEXT_BYTES = 20L * 1024L * 1024L
        private const val MAX_OFFICE_BYTES = 80L * 1024L * 1024L
        /** ~24 MiB of decoded page bitmaps (LruCache sizeOf uses KB). */
        private const val PDF_CACHE_MAX_KB = 24 * 1024
        private const val PDF_VISIBLE_RADIUS = 2
        private const val PDF_MAX_CONCURRENT_PREFETCH = 2
        private val LEGACY_OFFICE = setOf("doc", "xls", "ppt")
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif")
        private val TEXT_EXTS = setOf("txt", "md", "json", "log", "xml")
    }
}

data class PdfCacheTelemetry(
    val generation: Int,
    val cacheSizeKb: Int,
    val maxSizeKb: Int,
    val hits: Int,
    val misses: Int,
    val evictions: Int,
    val activePrefetchJobs: Int,
    val peakCacheBytes: Long,
    val renderingPageCount: Int,
)
