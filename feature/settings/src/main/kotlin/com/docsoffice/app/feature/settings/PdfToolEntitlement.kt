package com.docsoffice.app.feature.settings

/**
 * PDF tools are available without subscription. Kept for compatibility with older tests.
 */
object PdfToolEntitlement {
    const val deniedMessage = ""

    fun canExecute(isPro: Boolean): Boolean = true
}
