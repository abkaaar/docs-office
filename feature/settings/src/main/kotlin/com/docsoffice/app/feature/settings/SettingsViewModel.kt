package com.docsoffice.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsoffice.app.core.datastore.AppThemeMode
import com.docsoffice.app.core.datastore.SortMode
import com.docsoffice.app.core.datastore.UserPreferencesRepository
import com.docsoffice.app.core.files.DocumentActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val theme: AppThemeMode = AppThemeMode.SYSTEM,
    val sort: SortMode = SortMode.DATE,
    val trashDays: Int = 30,
    val analytics: Boolean = false,
    val excludedFolders: Set<String> = emptySet(),
    val readerTheme: String = "light",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val documentActions: DocumentActions,
) : ViewModel() {

    val state = combine(
        combine(
            preferences.themeMode,
            preferences.defaultSort,
            preferences.trashRetentionDays,
        ) { theme, sort, days -> Triple(theme, sort, days) },
        combine(
            preferences.analyticsEnabled,
            preferences.excludedFolders,
            preferences.readerTheme,
        ) { analytics, excluded, reader -> Triple(analytics, excluded, reader) },
    ) { left, right ->
        SettingsUiState(
            theme = left.first,
            sort = left.second,
            trashDays = left.third,
            analytics = right.first,
            excludedFolders = right.second,
            readerTheme = right.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(mode: AppThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setSort(sort: SortMode) {
        viewModelScope.launch { preferences.setDefaultSort(sort) }
    }

    fun setTrashDays(days: Int) {
        viewModelScope.launch { preferences.setTrashRetentionDays(days) }
    }

    fun setAnalytics(enabled: Boolean) {
        viewModelScope.launch { preferences.setAnalyticsEnabled(enabled) }
    }

    fun addExcluded(path: String) {
        viewModelScope.launch { preferences.addExcludedFolder(path) }
    }

    fun setReaderTheme(theme: String) {
        viewModelScope.launch { preferences.setReaderTheme(theme) }
    }

    fun purgeTrashNow() {
        viewModelScope.launch {
            documentActions.purgeExpired(state.value.trashDays)
        }
    }
}
