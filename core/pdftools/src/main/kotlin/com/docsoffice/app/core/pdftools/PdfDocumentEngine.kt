package com.docsoffice.app.core.pdftools

import java.io.File

/**
 * Structural PDF operations that should preserve original page content where possible.
 */
interface PdfDocumentEngine {
    fun pageCount(input: File): Int
    fun metadata(input: File): PdfMetadata
    fun merge(inputs: List<File>, output: File): File
    fun split(input: File, pageIndices: List<Int>, output: File): File
    fun deletePages(input: File, deleteIndices: List<Int>, output: File): File
    fun reorder(input: File, newOrder: List<Int>, output: File): File
    fun rotate(input: File, degrees: Int, output: File): File

    /** Extract plain text per page (0-based). Empty strings for blank pages. */
    fun extractTextByPage(input: File): List<String>
}
