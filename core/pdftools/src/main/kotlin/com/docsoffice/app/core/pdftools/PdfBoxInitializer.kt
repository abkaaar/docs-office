package com.docsoffice.app.core.pdftools

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

fun interface PdfBoxInit {
    fun ensureInitialized()
}

@Singleton
class PdfBoxInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
) : PdfBoxInit {
    private val ready = AtomicBoolean(false)

    override fun ensureInitialized() {
        if (ready.get()) return
        synchronized(ready) {
            if (ready.get()) return
            PDFBoxResourceLoader.init(context.applicationContext)
            ready.set(true)
        }
    }
}
