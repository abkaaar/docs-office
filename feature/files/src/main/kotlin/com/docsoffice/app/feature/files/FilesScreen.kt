package com.docsoffice.app.feature.files

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsoffice.app.core.datastore.SortMode
import com.docsoffice.app.core.designsystem.components.FileTypeChip
import com.docsoffice.app.core.designsystem.components.SelectionBar
import com.docsoffice.app.core.designsystem.components.formatBytes
import com.docsoffice.app.core.model.DocCategory
import com.docsoffice.app.core.model.DocSource
import com.docsoffice.app.core.model.Document

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    onOpen: (String) -> Unit,
    viewModel: FilesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sortMenu by remember { mutableStateOf(false) }
    val sources = listOf(DocSource.DOWNLOADS, DocSource.WHATSAPP, DocSource.TELEGRAM)
    val categories = listOf(
        DocCategory.PDF,
        DocCategory.WORD,
        DocCategory.EXCEL,
        DocCategory.SLIDES,
        DocCategory.TEXT,
        DocCategory.IMAGE,
    )

    Scaffold(
        topBar = {
            if (state.selecting) {
                SelectionBar(
                    count = state.selectedIds.size,
                    onClear = viewModel::clearSelection,
                    onShare = {
                        viewModel.shareSelected()?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "Share"))
                        }
                    },
                    onMove = viewModel::moveSelectedToVault,
                    onDelete = viewModel::deleteSelected,
                )
            } else {
                TopAppBar(
                    title = { Text("Files") },
                    actions = {
                        TextButton(onClick = { sortMenu = true }) {
                            Text("Sort: ${state.sort.name.lowercase()}")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.name.lowercase()) },
                                    onClick = {
                                        viewModel.setSort(mode)
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.setLayout(
                                    if (state.layout == FilesLayout.LIST) {
                                        FilesLayout.GRID
                                    } else {
                                        FilesLayout.LIST
                                    },
                                )
                            },
                        ) {
                            Icon(
                                imageVector = if (state.layout == FilesLayout.LIST) {
                                    Icons.Default.Apps
                                } else {
                                    Icons.AutoMirrored.Filled.List
                                },
                                contentDescription = "Toggle layout",
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.message?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .combinedClickable(onClick = viewModel::clearMessage),
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.source == null,
                        onClick = { viewModel.setSource(null) },
                        label = { Text("All") },
                    )
                }
                items(sources, key = { it.name }) { source ->
                    FilterChip(
                        selected = state.source == source,
                        onClick = {
                            viewModel.setSource(if (state.source == source) null else source)
                        },
                        label = { Text(source.name.lowercase().replaceFirstChar { it.titlecase() }) },
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.category == null,
                        onClick = { viewModel.setCategory(null) },
                        label = { Text("All types") },
                    )
                }
                items(categories, key = { it.name }) { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = {
                            viewModel.setCategory(if (state.category == category) null else category)
                        },
                        label = { Text(category.name) },
                    )
                }
            }

            when {
                state.documents.isEmpty() -> {
                    Text(
                        text = "No documents yet. Files you receive or download will appear here.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
                state.layout == FilesLayout.LIST -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.documents, key = { it.id }) { doc ->
                            FileListItem(
                                document = doc,
                                onClick = {
                                    if (state.selecting) {
                                        viewModel.toggleSelection(doc.id)
                                    } else {
                                        onOpen(doc.id)
                                    }
                                },
                                onLongClick = { viewModel.startSelection(doc.id) },
                                onFavoriteClick = { viewModel.toggleFavorite(doc.id) },
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.documents, key = { it.id }) { doc ->
                            val selected = doc.id in state.selectedIds
                            Column(
                                modifier = Modifier
                                    .heightIn(min = 96.dp)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        RoundedCornerShape(8.dp),
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (state.selecting) {
                                                viewModel.toggleSelection(doc.id)
                                            } else {
                                                onOpen(doc.id)
                                            }
                                        },
                                        onLongClick = { viewModel.startSelection(doc.id) },
                                    )
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FileTypeChip(category = doc.category)
                                Text(text = doc.displayName, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    document: Document,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FileTypeChip(category = document.category)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = document.displayName, maxLines = 2)
                Text(
                    text = "${formatBytes(document.sizeBytes)} · ${
                        document.source.name.lowercase().replaceFirstChar { it.titlecase() }
                    }",
                )
            }
            TextButton(onClick = onFavoriteClick) {
                Text(if (document.isFavorite) "★" else "☆")
            }
        }
    }
}
