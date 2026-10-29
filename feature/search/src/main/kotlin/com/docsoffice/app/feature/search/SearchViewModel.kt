package com.docsoffice.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.model.DocCategory
import com.docsoffice.app.core.model.DocSource
import com.docsoffice.app.core.model.Document
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Document> = emptyList(),
    val recentDocuments: List<Document> = emptyList(),
    val recent: List<String> = emptyList(),
    val source: DocSource? = null,
    val category: DocCategory? = null,
    val thisMonthOnly: Boolean = false,
    val contentSearch: Boolean = false,
    val searching: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: DocumentRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val source = MutableStateFlow<DocSource?>(null)
    private val category = MutableStateFlow<DocCategory?>(null)
    private val thisMonthOnly = MutableStateFlow(false)
    private val contentSearch = MutableStateFlow(false)

    private data class Filters(
        val query: String,
        val source: DocSource?,
        val category: DocCategory?,
        val thisMonthOnly: Boolean,
        val contentSearch: Boolean,
        val recent: List<String>,
    )

    val state: StateFlow<SearchUiState> = combine(
        combine(query.debounce(150), source, category) { q, src, cat -> Triple(q, src, cat) },
        combine(thisMonthOnly, contentSearch, repository.recentSearches()) { month, content, recent ->
            Triple(month, content, recent)
        },
        repository.recents(limit = 30),
    ) { left, right, recentDocs ->
        Triple(
            Filters(
                query = left.first,
                source = left.second,
                category = left.third,
                thisMonthOnly = right.first,
                contentSearch = right.second,
                recent = right.third,
            ),
            recentDocs,
            Unit,
        )
    }.flatMapLatest { (filters, recentDocs, _) ->
        val blank = filters.query.isBlank()
        if (blank) {
            flowOf(
                SearchUiState(
                    query = filters.query,
                    results = emptyList(),
                    recentDocuments = recentDocs,
                    recent = filters.recent,
                    source = filters.source,
                    category = filters.category,
                    thisMonthOnly = filters.thisMonthOnly,
                    contentSearch = filters.contentSearch,
                    searching = false,
                ),
            )
        } else {
            val minModified = if (filters.thisMonthOnly) {
                Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.timeInMillis
            } else {
                null
            }
            val extension = when (filters.category) {
                DocCategory.PDF -> "pdf"
                DocCategory.WORD -> "docx"
                DocCategory.EXCEL -> "xlsx"
                DocCategory.SLIDES -> "pptx"
                else -> null
            }
            val resultsFlow = if (filters.contentSearch) {
                repository.searchContent(filters.query)
            } else {
                repository.searchFiltered(
                    query = filters.query,
                    source = filters.source,
                    extension = extension,
                    minModified = minModified,
                    sort = "date",
                )
            }
            resultsFlow.map { results ->
                val filtered = when (filters.category) {
                    DocCategory.TEXT -> results.filter { it.category == DocCategory.TEXT }
                    DocCategory.IMAGE -> results.filter { it.category == DocCategory.IMAGE }
                    else -> results
                }
                SearchUiState(
                    query = filters.query,
                    results = filtered,
                    recentDocuments = recentDocs,
                    recent = filters.recent,
                    source = filters.source,
                    category = filters.category,
                    thisMonthOnly = filters.thisMonthOnly,
                    contentSearch = filters.contentSearch,
                    searching = false,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun submitQuery() {
        viewModelScope.launch { repository.addSearchQuery(query.value) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }

    fun setSource(value: DocSource?) {
        source.value = value
    }

    fun setCategory(value: DocCategory?) {
        category.value = value
    }

    fun setThisMonthOnly(value: Boolean) {
        thisMonthOnly.value = value
    }

    fun setContentSearch(value: Boolean) {
        contentSearch.value = value
    }
}
