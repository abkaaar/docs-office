package com.docsoffice.app.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsoffice.app.core.designsystem.components.DocumentRow
import com.docsoffice.app.core.model.DocCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpen: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                label = { Text("Search documents") },
                placeholder = { Text("Name or content") },
            )
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.category == null && !state.thisMonthOnly,
                        onClick = {
                            viewModel.setCategory(null)
                            viewModel.setThisMonthOnly(false)
                        },
                        label = { Text("All") },
                    )
                }
                item {
                    FilterChip(
                        selected = state.category == DocCategory.PDF,
                        onClick = {
                            viewModel.setCategory(
                                if (state.category == DocCategory.PDF) null else DocCategory.PDF,
                            )
                        },
                        label = { Text("PDF") },
                    )
                }
                item {
                    FilterChip(
                        selected = state.category == DocCategory.EXCEL,
                        onClick = {
                            viewModel.setCategory(
                                if (state.category == DocCategory.EXCEL) null else DocCategory.EXCEL,
                            )
                        },
                        label = { Text("Excel") },
                    )
                }
                item {
                    FilterChip(
                        selected = state.thisMonthOnly,
                        onClick = { viewModel.setThisMonthOnly(!state.thisMonthOnly) },
                        label = { Text("This month") },
                    )
                }
                item {
                    FilterChip(
                        selected = state.contentSearch,
                        onClick = { viewModel.setContentSearch(!state.contentSearch) },
                        label = { Text("In content") },
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.query.isBlank()) {
                    if (state.recentDocuments.isNotEmpty()) {
                        item {
                            Text(
                                "Recent documents",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        items(state.recentDocuments, key = { "recent_${it.id}" }) { doc ->
                            DocumentRow(
                                document = doc,
                                onClick = { onOpen(doc.id) },
                            )
                        }
                    } else {
                        item {
                            Text(
                                "No recent documents yet. Open a file or search by name.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    if (state.recent.isNotEmpty()) {
                        item {
                            Text(
                                "Recent searches",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            )
                        }
                        items(state.recent, key = { "q_$it" }) { recent ->
                            Text(
                                text = recent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onQueryChange(recent) }
                                    .padding(vertical = 8.dp),
                            )
                        }
                        item {
                            TextButton(onClick = viewModel::clearHistory) { Text("Clear") }
                        }
                    }
                } else {
                    if (state.results.isEmpty()) {
                        item {
                            Text(
                                "Nothing matches “${state.query.trim()}”.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    items(state.results, key = { it.id }) { doc ->
                        DocumentRow(
                            document = doc,
                            onClick = {
                                viewModel.submitQuery()
                                onOpen(doc.id)
                            },
                        )
                    }
                }
            }
        }
    }
}
