package com.docsoffice.app.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.files.ScanDocumentsWorker
import com.docsoffice.app.core.files.ScanProgressStore
import com.docsoffice.app.core.model.CategoryCount
import com.docsoffice.app.core.model.Document
import com.docsoffice.app.core.model.ScanProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(
        val recents: List<Document>,
        val favorites: List<Document>,
        val counts: List<CategoryCount>,
        val scan: ScanProgress,
    ) : HomeState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    documents: DocumentRepository,
    progressStore: ScanProgressStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        documents.recents(limit = 10),
        documents.favorites(),
        documents.categoryCounts(),
        progressStore.progress,
    ) { recents, favorites, counts, scan ->
        HomeState.Ready(
            recents = recents,
            favorites = favorites,
            counts = counts,
            scan = scan,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState.Loading)

    fun refreshScan() {
        ScanDocumentsWorker.enqueueReplace(context)
    }
}
