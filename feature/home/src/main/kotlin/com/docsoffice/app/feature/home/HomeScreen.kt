package com.docsoffice.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsoffice.app.core.designsystem.components.CategoryTile
import com.docsoffice.app.core.designsystem.components.DocumentRow
import com.docsoffice.app.core.designsystem.theme.Brand
import com.docsoffice.app.core.model.DocCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onOpenCategory: (DocCategory) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Docs Office") },
                actions = {
                    IconButton(onClick = viewModel::refreshScan) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan again")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenSearch,
                containerColor = Brand,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .testTag("home_search_fab")
                    .semantics { contentDescription = "Search documents" },
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
            }
        },
    ) { padding ->
        when (val s = state) {
            HomeState.Loading -> {
                Column(Modifier.padding(padding).padding(16.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Text("Loading library…")
                }
            }

            is HomeState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    item {
                        Text(
                            "Manage any document on your device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    if (!s.scan.isComplete) {
                        item {
                            Column(Modifier.padding(16.dp)) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    s.scan.message.ifBlank {
                                        "Found ${s.scan.foundCount} documents so far"
                                    },
                                )
                            }
                        }
                    }

                    if (s.counts.any { it.count > 0 }) {
                        item {
                            Text(
                                "Categories",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(
                                    items = s.counts.filter { it.count > 0 },
                                    key = { it.category.name },
                                ) { count ->
                                    CategoryTile(
                                        count = count,
                                        onClick = { onOpenCategory(count.category) },
                                    )
                                }
                            }
                        }
                    }

                    if (s.favorites.isNotEmpty()) {
                        item {
                            Text(
                                "Favorites",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        items(
                            items = s.favorites,
                            key = { "fav-${it.id}" },
                        ) { doc ->
                            DocumentRow(document = doc, onClick = { onOpen(doc.id) })
                        }
                    }

                    if (s.recents.isNotEmpty()) {
                        item {
                            Text(
                                "Recents",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        items(
                            items = s.recents,
                            key = { "recent-${it.id}" },
                        ) { doc ->
                            DocumentRow(document = doc, onClick = { onOpen(doc.id) })
                        }
                    }

                    if (s.scan.isComplete &&
                        s.counts.none { it.count > 0 } &&
                        s.favorites.isEmpty() &&
                        s.recents.isEmpty()
                    ) {
                        item {
                            Text(
                                "No documents yet. Download or receive a file, then tap Scan again. Browse everything in Files.",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
