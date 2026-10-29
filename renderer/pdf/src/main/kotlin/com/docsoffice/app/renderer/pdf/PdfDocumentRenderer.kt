package com.docsoffice.app.renderer.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.docsoffice.app.renderer.api.DocumentRenderer
import com.docsoffice.app.renderer.api.DocumentSourceHandle
import com.docsoffice.app.renderer.api.OpenFailure
import com.docsoffice.app.renderer.api.RenderSession
import java.io.File
import java.net.URI

class PdfDocumentRenderer : DocumentRenderer {
    override val supportedMimeTypes: Set<String> = setOf("application/pdf")

    override suspend fun open(source: DocumentSourceHandle, password: String?): RenderSession {
        val file = runCatching { File(URI(source.uri)) }.getOrNull()
            ?: throw OpenFailure.Unsupported
        if (!file.exists()) throw OpenFailure.Corrupt
        val descriptor = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (_: Throwable) {
            throw OpenFailure.Corrupt
        }
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (_: SecurityException) {
            descriptor.close()
            throw OpenFailure.PasswordRequired
        } catch (_: Throwable) {
            descriptor.close()
            throw OpenFailure.Corrupt
        }
        return RenderSession.Paged(
            pageCount = renderer.pageCount,
            renderPage = { index, widthPx ->
                renderer.openPage(index).use { page ->
                    val height = (widthPx.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            },
            onClose = {
                renderer.close()
                descriptor.close()
            },
        )
    }

    companion object {
        fun mapOpenError(error: Throwable): OpenFailure = when (error) {
            is OpenFailure -> error
            is SecurityException -> OpenFailure.PasswordRequired
            else -> OpenFailure.Corrupt
        }
    }
}
