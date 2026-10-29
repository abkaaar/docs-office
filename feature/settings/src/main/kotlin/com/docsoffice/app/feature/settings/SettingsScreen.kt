package com.docsoffice.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsoffice.app.core.datastore.AppThemeMode
import com.docsoffice.app.core.datastore.SortMode

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.addExcluded(uri.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.theme == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(mode.name.lowercase()) },
                    )
                }
            }

            Text("Default sort", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.sort == mode,
                        onClick = { viewModel.setSort(mode) },
                        label = { Text(mode.name.lowercase()) },
                    )
                }
            }

            Text("Reader theme", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("light", "dark", "sepia").forEach { theme ->
                    FilterChip(
                        selected = state.readerTheme == theme,
                        onClick = { viewModel.setReaderTheme(theme) },
                        label = { Text(theme) },
                    )
                }
            }

            Text(
                "Trash retention: ${state.trashDays} days",
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 30, 60).forEach { days ->
                    FilterChip(
                        selected = state.trashDays == days,
                        onClick = { viewModel.setTrashDays(days) },
                        label = { Text("$days days") },
                    )
                }
            }

            Text("Excluded folders", style = MaterialTheme.typography.titleMedium)
            state.excludedFolders.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add excluded folder") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Analytics / crash reporting")
                Switch(
                    checked = state.analytics,
                    onCheckedChange = viewModel::setAnalytics,
                )
            }

            TextButton(onClick = viewModel::purgeTrashNow) {
                Text("Purge expired trash now")
            }
        }
    }
}
