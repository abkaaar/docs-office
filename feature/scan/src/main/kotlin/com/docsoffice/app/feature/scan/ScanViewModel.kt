package com.docsoffice.app.feature.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.files.PathClassifier
import com.docsoffice.app.core.files.SupportedFormats
import com.docsoffice.app.core.model.DocSource
import com.docsoffice.app.core.model.Document
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


enum class ScanPhase { CAMERA, REVIEW, PAGES }

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.CAMERA,
    val pagePaths: List<String> = emptyList(),
    val pendingPath: String? = null,
    val corners: QuadCorners = QuadCorners.insetDefaults(),
    val enhanceMode: EnhanceMode = EnhanceMode.COLOR,
    val autoDetected: Boolean = false,
    val detecting: Boolean = false,
    val capturing: Boolean = false,
    val saving: Boolean = false,
    val savedDocId: String? = null,
    val error: String? = null,
    val permissionDenied: Boolean = false,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfTools: PdfTools,
    private val repository: DocumentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var saveJob: Job? = null
    private val sessionDir: File =
        File(context.cacheDir, "scan_session_${System.currentTimeMillis()}").apply { mkdirs() }

    fun setPermissionDenied(denied: Boolean) {
        _state.update { it.copy(permissionDenied = denied, error = if (denied) "Camera permission is required to scan" else it.error) }
    }

    fun showCamera() {
        _state.update { it.copy(phase = ScanPhase.CAMERA, error = null) }
    }

    fun showPages() {
        _state.update { it.copy(phase = ScanPhase.PAGES, error = null) }
    }

    fun onCaptureStarted() {
        _state.update { it.copy(capturing = true, error = null) }
    }

    fun onCaptureFailed(message: String) {
        _state.update { it.copy(capturing = false, error = message) }
    }

    fun onCaptureSaved(file: File) {
        _state.update {
            it.copy(
                capturing = false,
                pendingPath = file.absolutePath,
                corners = QuadCorners.insetDefaults(),
                autoDetected = false,
                detecting = true,
                enhanceMode = EnhanceMode.COLOR,
                phase = ScanPhase.REVIEW,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: return@withContext EdgeDetectResult(QuadCorners.insetDefaults(), 0f, false)
                try {
                    DocumentEdgeDetector.detect(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
            _state.update {
                if (it.pendingPath != file.absolutePath) it
                else {
                    it.copy(
                        corners = result.corners,
                        autoDetected = result.autoDetected,
                        detecting = false,
                    )
                }
            }
        }
    }

    fun createCaptureFile(): File =
        File(sessionDir, "capture_${System.currentTimeMillis()}.jpg")

    fun updateCorners(corners: QuadCorners) {
        _state.update { it.copy(corners = corners) }
    }

    fun cycleEnhanceMode() {
        _state.update {
            val next = when (it.enhanceMode) {
                EnhanceMode.COLOR -> EnhanceMode.GRAYSCALE
                EnhanceMode.GRAYSCALE -> EnhanceMode.DOCUMENT_BW
                EnhanceMode.DOCUMENT_BW -> EnhanceMode.COLOR
            }
            it.copy(enhanceMode = next)
        }
    }

    fun retake() {
        val pending = _state.value.pendingPath
        if (pending != null) File(pending).delete()
        _state.update {
            it.copy(pendingPath = null, phase = ScanPhase.CAMERA, error = null)
        }
    }

    fun acceptPage() {
        viewModelScope.launch {
            mutex.withLock {
                val pending = _state.value.pendingPath ?: return@withLock
                val corners = _state.value.corners
                val mode = _state.value.enhanceMode
                try {
                    val outPath = withContext(Dispatchers.Default) {
                        val source = BitmapFactory.decodeFile(pending)
                            ?: error("Could not decode capture")
                        try {
                            val processed = DocumentImageProcessor.process(source, corners, mode)
                            try {
                                val out = File(sessionDir, "page_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(out).use { stream ->
                                    processed.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                                }
                                out.absolutePath
                            } finally {
                                processed.recycle()
                            }
                        } finally {
                            source.recycle()
                        }
                    }
                    File(pending).delete()
                    _state.update {
                        it.copy(
                            pagePaths = it.pagePaths + outPath,
                            pendingPath = null,
                            phase = ScanPhase.PAGES,
                            error = null,
                        )
                    }
                } catch (t: Throwable) {
                    _state.update { it.copy(error = t.message ?: "Could not process page") }
                }
            }
        }
    }

    fun addFromGallery(uris: List<Uri>) {
        viewModelScope.launch {
            mutex.withLock {
                val added = withContext(Dispatchers.IO) {
                    uris.mapIndexed { index, uri ->
                        val out = File(sessionDir, "gallery_${System.currentTimeMillis()}_$index.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(out).use { output -> input.copyTo(output) }
                        } ?: error("Could not read image")
                        out.absolutePath
                    }
                }
                _state.update {
                    it.copy(
                        pagePaths = it.pagePaths + added,
                        phase = ScanPhase.PAGES,
                        error = null,
                    )
                }
            }
        }
    }

    fun removePage(index: Int) {
        _state.update { state ->
            val paths = state.pagePaths.toMutableList()
            if (index in paths.indices) {
                File(paths.removeAt(index)).delete()
            }
            state.copy(pagePaths = paths)
        }
    }

    fun movePage(from: Int, to: Int) {
        _state.update { state ->
            val paths = state.pagePaths.toMutableList()
            if (from !in paths.indices || to !in paths.indices) return@update state
            val item = paths.removeAt(from)
            paths.add(to, item)
            state.copy(pagePaths = paths)
        }
    }

    fun discardSession() {
        saveJob?.cancel()
        sessionDir.listFiles()?.forEach { it.delete() }
        _state.update {
            ScanUiState(phase = ScanPhase.CAMERA)
        }
    }

    fun saveAsPdf() {
        if (_state.value.saving) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            mutex.withLock {
                val paths = _state.value.pagePaths
                if (paths.isEmpty()) {
                    _state.update { it.copy(error = "Add at least one page") }
                    return@withLock
                }
                _state.update { it.copy(saving = true, error = null) }
                try {
                    val id = withContext(Dispatchers.IO) {
                        val images = paths.map { File(it) }
                        val scansDir = File(context.getExternalFilesDir(null), "Scans").apply { mkdirs() }
                        val out = File(scansDir, "Scan_${System.currentTimeMillis()}.pdf")
                        pdfTools.imagesToPdf(images, out)
                        val uri = out.toURI().toString()
                        val docId = PathClassifier.stableId(uri, out.length(), out.lastModified())
                        val doc = Document(
                            id = docId,
                            uri = uri,
                            displayName = out.name,
                            extension = "pdf",
                            mimeType = SupportedFormats.mimeTypeFor("pdf"),
                            sizeBytes = out.length(),
                            modifiedAt = out.lastModified(),
                            addedAt = System.currentTimeMillis(),
                            source = DocSource.SCANS,
                            pageCount = images.size,
                        )
                        repository.upsertAll(listOf(doc))
                        paths.forEach { File(it).delete() }
                        docId
                    }
                    _state.update {
                        it.copy(saving = false, savedDocId = id, pagePaths = emptyList(), phase = ScanPhase.CAMERA)
                    }
                } catch (t: Throwable) {
                    _state.update { it.copy(saving = false, error = t.message ?: "Save failed") }
                }
            }
        }
    }

    override fun onCleared() {
        saveJob?.cancel()
        // Keep accepted pages if process is killed mid-save is rare; clean uncommitted session files.
        if (!_state.value.saving) {
            sessionDir.listFiles()?.forEach { it.delete() }
            sessionDir.delete()
        }
        super.onCleared()
    }
}
