package com.docsoffice.app.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsoffice.app.core.datastore.UserPreferencesRepository
import com.docsoffice.app.core.files.ScanDocumentsWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val page: Int = 0,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun nextPage() {
        _state.value = _state.value.copy(page = (_state.value.page + 1).coerceAtMost(2))
    }

    fun previousPage() {
        _state.value = _state.value.copy(page = (_state.value.page - 1).coerceAtLeast(0))
    }

    /** Skip intro pages and land on the storage-access step. */
    fun skipToAccess() {
        _state.value = _state.value.copy(page = 2)
    }

    fun completeAndStartScan() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            ScanDocumentsWorker.enqueueReplace(context)
        }
    }

    fun saveSafUri(uri: String) {
        viewModelScope.launch {
            preferences.addSafTreeUri(uri)
            preferences.setOnboardingCompleted(true)
            ScanDocumentsWorker.enqueueReplace(context)
        }
    }
}
