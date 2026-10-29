package com.docsoffice.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.datastore.AppThemeMode
import com.docsoffice.app.core.datastore.UserPreferencesRepository
import com.docsoffice.app.core.designsystem.theme.DocsOfficeTheme
import com.docsoffice.app.core.files.FileOperations
import com.docsoffice.app.core.files.PathClassifier
import com.docsoffice.app.core.model.Document
import com.docsoffice.app.ui.DocsOfficeNavHost
import com.docsoffice.app.ui.IntentDocumentParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var preferences: UserPreferencesRepository

    @Inject
    lateinit var documentRepository: DocumentRepository

    @Inject
    lateinit var fileOperations: FileOperations

    private var openDocumentId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(
                initialValue = AppThemeMode.SYSTEM,
            )
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            DocsOfficeTheme(darkTheme = darkTheme) {
                DocsOfficeNavHost(
                    openDocumentId = openDocumentId,
                    onOpenDocumentConsumed = { openDocumentId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = IntentDocumentParser.extractUri(intent) ?: return
        lifecycleScope.launch {
            val id = ingestExternalUri(uri)
            if (id != null) openDocumentId = id
        }
    }

    private suspend fun ingestExternalUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        val existing = documentRepository.getByUri(uriString)
        if (existing != null) {
            documentRepository.setLastOpenedAt(existing.id)
            return@withContext existing.id
        }
        val meta = fileOperations.queryUriMetadata(uriString)
        val id = PathClassifier.stableId(
            uriString,
            meta.sizeBytes,
            System.currentTimeMillis(),
        )
        val doc = Document(
            id = id,
            uri = uriString,
            displayName = meta.displayName,
            extension = meta.extension,
            mimeType = meta.mimeType,
            sizeBytes = meta.sizeBytes,
            modifiedAt = System.currentTimeMillis(),
            addedAt = System.currentTimeMillis(),
        )
        documentRepository.upsertAll(listOf(doc))
        documentRepository.setLastOpenedAt(id)
        id
    }
}
