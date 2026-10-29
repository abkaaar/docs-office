package com.docsoffice.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docsoffice.app.core.designsystem.components.ToolTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryToolsScreen(
    onOpenVault: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenTools: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Tools") })
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    "Work with PDFs and manage your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ToolTile(
                    title = "PDF tools",
                    icon = Icons.Default.PictureAsPdf,
                    onClick = onOpenTools,
                    testTag = "library_tools_pdf",
                )
            }
            item {
                ToolTile(
                    title = "Scan documents",
                    icon = Icons.Default.DocumentScanner,
                    onClick = onOpenScan,
                    testTag = "library_tools_scan",
                )
            }
            item {
                ToolTile(
                    title = "Private vault",
                    icon = Icons.Default.Lock,
                    onClick = onOpenVault,
                    testTag = "library_tools_vault",
                )
            }
            item {
                ToolTile(
                    title = "Trash",
                    icon = Icons.Default.Delete,
                    onClick = onOpenTrash,
                    testTag = "library_tools_trash",
                )
            }
        }
    }
}
