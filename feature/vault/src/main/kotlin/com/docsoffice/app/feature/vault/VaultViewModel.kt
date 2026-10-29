package com.docsoffice.app.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsoffice.app.core.crypto.VaultAuth
import com.docsoffice.app.core.crypto.VaultItem
import com.docsoffice.app.core.crypto.VaultRepository
import com.docsoffice.app.core.crypto.VaultSession
import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.files.SupportedFormats
import com.docsoffice.app.core.model.DocSource
import com.docsoffice.app.core.model.Document
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultUiState(
    val unlocked: Boolean = false,
    val hasPin: Boolean = false,
    val items: List<VaultItem> = emptyList(),
    val error: String? = null,
    val setupPinMode: Boolean = false,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val auth: VaultAuth,
    private val session: VaultSession,
    private val vaultRepository: VaultRepository,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val error = MutableStateFlow<String?>(null)
    private val setupPinMode = MutableStateFlow(false)

    val state: StateFlow<VaultUiState> = combine(
        session.unlocked,
        auth.hasPin,
        vaultRepository.observeItems(),
        combine(error, setupPinMode) { err, setup -> err to setup },
    ) { unlocked, hasPin, items, meta ->
        VaultUiState(
            unlocked = unlocked,
            hasPin = hasPin,
            items = if (unlocked) items else emptyList(),
            error = meta.first,
            setupPinMode = meta.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    init {
        viewModelScope.launch {
            if (!auth.isPinConfigured()) {
                setupPinMode.value = true
            }
        }
    }

    fun setPin(pin: String, confirm: String) {
        viewModelScope.launch {
            if (pin != confirm) {
                error.value = "PINs do not match"
                return@launch
            }
            runCatching {
                auth.setPin(pin)
                session.unlock()
                setupPinMode.value = false
                error.value = null
            }.onFailure {
                error.value = it.message ?: "Could not set PIN"
            }
        }
    }

    fun unlockWithPin(pin: String) {
        viewModelScope.launch {
            if (auth.verifyPin(pin)) {
                session.unlock()
                error.value = null
            } else {
                error.value = "Incorrect PIN"
            }
        }
    }

    fun unlockAfterBiometric() {
        viewModelScope.launch {
            if (auth.isPinConfigured()) {
                session.unlock()
                error.value = null
            }
        }
    }

    fun lock() {
        session.lock()
        vaultRepository.clearDecryptCache()
    }

    fun openItem(itemId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            vaultRepository.openDecrypted(itemId)
                .onSuccess { file ->
                    val docId = registerTempDocument(itemId, file)
                    onReady(docId)
                }
                .onFailure { error.value = it.message ?: "Could not open item" }
        }
    }

    fun moveOut(itemId: String) {
        viewModelScope.launch {
            vaultRepository.moveOutToLibrary(itemId)
                .onFailure { error.value = it.message ?: "Move out failed" }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            vaultRepository.deleteItem(itemId)
                .onFailure { error.value = it.message ?: "Delete failed" }
        }
    }

    private suspend fun registerTempDocument(itemId: String, file: File): String {
        val displayName = file.name.removePrefix("${itemId}_")
        val extension = displayName.substringAfterLast('.', "")
        val id = "vault_open_$itemId"
        val doc = Document(
            id = id,
            uri = file.toURI().toString(),
            displayName = displayName,
            extension = extension,
            mimeType = SupportedFormats.mimeTypeFor(extension),
            sizeBytes = file.length(),
            modifiedAt = file.lastModified(),
            addedAt = System.currentTimeMillis(),
            source = DocSource.OTHER,
        )
        documentRepository.upsertAll(listOf(doc))
        return id
    }
}
