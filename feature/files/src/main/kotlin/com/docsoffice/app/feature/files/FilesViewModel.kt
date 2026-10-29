package com.docsoffice.app.feature.files

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsoffice.app.core.crypto.VaultRepository
import com.docsoffice.app.core.crypto.VaultSession
import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.datastore.SortMode
import com.docsoffice.app.core.datastore.UserPreferencesRepository
import com.docsoffice.app.core.files.DocumentActions
import com.docsoffice.app.core.files.FileOperations
import com.docsoffice.app.core.model.DocCategory
import com.docsoffice.app.core.model.DocSource
import com.docsoffice.app.core.model.Document
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FilesLayout { LIST, GRID }

data class FilesUiState(
    val documents: List<Document> = emptyList(),
    val source: DocSource? = null,
    val category: DocCategory? = null,
    val sort: SortMode = SortMode.DATE,
    val layout: FilesLayout = FilesLayout.LIST,
    val selectedIds: Set<String> = emptySet(),
    val selecting: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val documentActions: DocumentActions,
    private val fileOperations: FileOperations,
    private val vaultRepository: VaultRepository,
    private val vaultSession: VaultSession,
    preferences: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryArg = savedStateHandle.get<String>("category")
    private val initialCategory = categoryArg?.let {
        runCatching { DocCategory.valueOf(it) }.getOrNull()
    }

    private val filters = MutableStateFlow(
        FilesUiState(category = initialCategory),
    )

    init {
        viewModelScope.launch {
            preferences.defaultSort.collect { sort ->
                filters.update { current ->
                    if (current.sort == SortMode.DATE) current.copy(sort = sort) else current
                }
            }
        }
    }

    val state: StateFlow<FilesUiState> = combine(
        repository.observeAll(),
        filters,
    ) { docs, ui ->
        val filtered = docs
            .filter { ui.source == null || it.source == ui.source }
            .filter { ui.category == null || it.category == ui.category }
            .sortedWith(sortComparator(ui.sort))
        ui.copy(documents = filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilesUiState())

    fun setSource(source: DocSource?) {
        filters.update { it.copy(source = source) }
    }

    fun setCategory(category: DocCategory?) {
        filters.update { it.copy(category = category) }
    }

    fun setSort(sort: SortMode) {
        filters.update { it.copy(sort = sort) }
    }

    fun setLayout(layout: FilesLayout) {
        filters.update { it.copy(layout = layout) }
    }

    fun startSelection(id: String) {
        filters.update {
            it.copy(selecting = true, selectedIds = setOf(id))
        }
    }

    fun toggleSelection(id: String) {
        filters.update { state ->
            val next = state.selectedIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            state.copy(
                selecting = next.isNotEmpty(),
                selectedIds = next,
            )
        }
    }

    fun clearSelection() {
        filters.update { it.copy(selecting = false, selectedIds = emptySet()) }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val ids = filters.value.selectedIds
            state.value.documents.filter { it.id in ids }.forEach { documentActions.moveToTrash(it) }
            clearSelection()
        }
    }

    fun tagSelected(tagName: String = "Tagged") {
        viewModelScope.launch {
            val tagId = repository.createTag(tagName)
            filters.value.selectedIds.forEach { docId ->
                repository.assignTag(docId, tagId)
            }
            clearSelection()
        }
    }

    fun moveSelectedToVault() {
        viewModelScope.launch {
            if (!vaultSession.isUnlocked()) {
                filters.update {
                    it.copy(message = "Unlock the Vault first (More → Private vault)")
                }
                return@launch
            }
            val docs = state.value.documents.filter { it.id in filters.value.selectedIds }
            var moved = 0
            docs.forEach { doc ->
                vaultRepository.moveDocumentToVault(doc).onSuccess { moved++ }
            }
            filters.update {
                it.copy(
                    selecting = false,
                    selectedIds = emptySet(),
                    message = if (moved > 0) "Moved $moved to vault" else "Could not move to vault",
                )
            }
        }
    }

    fun clearMessage() {
        filters.update { it.copy(message = null) }
    }

    fun shareSelected(): Intent? {
        val docs = state.value.documents.filter { it.id in state.value.selectedIds }
        val first = docs.firstOrNull() ?: return null
        return fileOperations.shareIntent(first.uri, first.mimeType, first.displayName)
    }

    fun rename(document: Document, newName: String) {
        viewModelScope.launch { documentActions.rename(document, newName) }
    }

    private fun sortComparator(sort: SortMode): Comparator<Document> = when (sort) {
        SortMode.NAME -> compareBy { it.displayName.lowercase() }
        SortMode.SIZE -> compareByDescending { it.sizeBytes }
        SortMode.DATE -> compareByDescending { it.modifiedAt }
    }
}
