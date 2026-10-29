package com.docsoffice.app.ui

import android.content.Intent
import android.net.Uri

object IntentDocumentParser {
    fun extractUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    ?: intent.clipData?.getItemAt(0)?.uri
            }
            else -> null
        }
    }
}
