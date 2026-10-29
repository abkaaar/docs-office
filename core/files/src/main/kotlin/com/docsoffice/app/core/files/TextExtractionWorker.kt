package com.docsoffice.app.core.files

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.database.DocumentTextDao
import com.docsoffice.app.core.database.DocumentTextEntity
import com.docsoffice.app.core.model.DocCategory
import com.docsoffice.app.renderer.ooxml.DocxRenderer
import com.docsoffice.app.renderer.ooxml.PptxRenderer
import com.docsoffice.app.renderer.ooxml.XlsxRenderer
import com.docsoffice.app.renderer.text.TextLineLoader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class TextExtractionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: DocumentRepository,
    private val textDao: DocumentTextDao,
    private val fileOperations: FileOperations,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val docs = repository.observeAll().first().take(200)
        for (doc in docs) {
            val file = fileOperations.materializeReadableFile(doc.uri, doc.displayName) ?: continue
            if (!file.exists() || file.length() > 15L * 1024L * 1024L) continue
            val ext = doc.extension.lowercase()
            val body = runCatching {
                when (doc.category) {
                    DocCategory.TEXT -> TextLineLoader.loadLines(file).joinToString("\n")
                    DocCategory.WORD -> if (ext == "docx") DocxRenderer.extractPlainText(file) else ""
                    DocCategory.EXCEL ->
                        if (ext == "xlsx" || ext == "xlsm") XlsxRenderer.extractPlainText(file) else ""
                    DocCategory.SLIDES ->
                        if (ext == "pptx" || ext == "pptm") PptxRenderer.extractPlainText(file) else ""
                    else -> ""
                }
            }.getOrDefault("")
            if (body.isBlank()) continue
            textDao.deleteForDoc(doc.id)
            textDao.upsert(DocumentTextEntity(docId = doc.id, body = body.take(200_000)))
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE = "text_extraction"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<TextExtractionWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
