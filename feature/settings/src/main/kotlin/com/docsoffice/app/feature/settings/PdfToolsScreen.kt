package com.docsoffice.app.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsoffice.app.core.designsystem.components.ToolTile

private data class PdfToolTile(
    val action: PdfToolAction?,
    val title: String,
    val icon: ImageVector,
    val subtitle: String? = null,
    val navigateScan: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onBack: () -> Unit,
    onOpenScan: () -> Unit = {},
    onOpenResult: (String) -> Unit = {},
    viewModel: PdfToolsViewModel = hiltViewModel(),
) {
    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var rangeText by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> selected = uris }

    val tiles = remember {
        listOf(
            PdfToolTile(PdfToolAction.IMAGES_TO_PDF, "Images to PDF", Icons.Default.PictureAsPdf),
            PdfToolTile(PdfToolAction.MERGE, "Merge PDFs", Icons.Default.CallMerge),
            PdfToolTile(PdfToolAction.SPLIT, "Split / extract", Icons.Default.ContentCut),
            PdfToolTile(PdfToolAction.REORDER, "Reorder pages", Icons.Default.Reorder),
            PdfToolTile(PdfToolAction.ROTATE, "Rotate 90°", Icons.Default.RotateRight),
            PdfToolTile(PdfToolAction.DELETE_PAGES, "Delete pages", Icons.Default.Delete),
            PdfToolTile(PdfToolAction.COMPRESS, "Compress", Icons.Default.Compress),
            PdfToolTile(PdfToolAction.PDF_TO_IMAGES, "PDF to images", Icons.Default.Image),
            PdfToolTile(null, "Scan documents", Icons.Default.DocumentScanner, navigateScan = true),
            PdfToolTile(PdfToolAction.METADATA, "Metadata", Icons.Default.Info),
        )
    }

    if (state.needsPageRange) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPageRange,
            title = { Text("Page selection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.pageRangeHint)
                    OutlinedTextField(
                        value = rangeText,
                        onValueChange = { rangeText = it },
                        label = { Text("Pages") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmPageRange(rangeText)
                        rangeText = ""
                    },
                ) { Text("Run") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissPageRange()
                    rangeText = ""
                }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF tools") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Merge, split, reorder, delete, and rotate preserve original PDF content. Compress and image export flatten pages. Originals are left unchanged.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.processing,
                    ) { Text("Choose files (${selected.size})") }
                    if (state.processing) {
                        CircularProgressIndicator()
                    }
                    state.message?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                        state.lastSavedDocId?.let { id ->
                            Button(
                                onClick = { onOpenResult(id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Open result") }
                        }
                    }
                }
            }
            items(tiles.size) { index ->
                val tile = tiles[index]
                ToolTile(
                    title = tile.title,
                    icon = tile.icon,
                    subtitle = tile.subtitle,
                    enabled = !state.processing,
                    onClick = {
                        if (tile.navigateScan) {
                            onOpenScan()
                        } else {
                            tile.action?.let { viewModel.requestAction(it, selected) }
                        }
                    },
                )
            }
        }
    }
}
