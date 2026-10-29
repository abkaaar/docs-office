package com.docsoffice.app.core.pdftools

import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfBoxAndroidEngine @Inject constructor() : PdfDocumentEngine {

    override fun pageCount(input: File): Int = open(input) { it.numberOfPages }

    override fun metadata(input: File): PdfMetadata = open(input) { doc ->
        PdfMetadata(
            pageCount = doc.numberOfPages,
            fileName = input.name,
            sizeBytes = input.length(),
        )
    }

    override fun merge(inputs: List<File>, output: File): File {
        require(inputs.isNotEmpty()) { "Select at least one PDF" }
        ensureParent(output)
        val merger = PDFMergerUtility()
        inputs.forEach { file ->
            require(file.exists()) { "Missing PDF: ${file.name}" }
            merger.addSource(file)
        }
        merger.destinationFileName = output.absolutePath
        try {
            merger.mergeDocuments(null)
        } catch (t: Throwable) {
            output.delete()
            throw mapError(t)
        }
        require(output.exists() && output.length() > 0) { "Merge produced an empty file" }
        return output
    }

    override fun split(input: File, pageIndices: List<Int>, output: File): File {
        require(pageIndices.isNotEmpty()) { "Select at least one page" }
        return open(input) { source ->
            val total = source.numberOfPages
            pageIndices.forEach { index ->
                require(index in 0 until total) { "Page ${index + 1} is out of range (1–$total)" }
            }
            ensureParent(output)
            PDDocument().use { dest ->
                // importPage already adds the page to dest
                pageIndices.forEach { index ->
                    dest.importPage(source.getPage(index))
                }
                write(dest, output)
            }
            output
        }
    }

    override fun deletePages(input: File, deleteIndices: List<Int>, output: File): File {
        require(deleteIndices.isNotEmpty()) { "Select at least one page to delete" }
        return open(input) { source ->
            val total = source.numberOfPages
            require(deleteIndices.size < total) { "Cannot delete every page" }
            deleteIndices.forEach { index ->
                require(index in 0 until total) { "Page ${index + 1} is out of range (1–$total)" }
            }
            val keep = (0 until total).filter { it !in deleteIndices.toSet() }
            ensureParent(output)
            PDDocument().use { dest ->
                keep.forEach { index ->
                    dest.importPage(source.getPage(index))
                }
                write(dest, output)
            }
            output
        }
    }

    override fun reorder(input: File, newOrder: List<Int>, output: File): File =
        split(input, newOrder, output)

    override fun rotate(input: File, degrees: Int, output: File): File {
        val step = ((degrees % 360) + 360) % 360
        require(step % 90 == 0) { "Rotation must be a multiple of 90 degrees" }
        return open(input) { source ->
            ensureParent(output)
            PDDocument().use { dest ->
                for (i in 0 until source.numberOfPages) {
                    val page = dest.importPage(source.getPage(i))
                    page.rotation = (page.rotation + step) % 360
                }
                write(dest, output)
            }
            output
        }
    }

    override fun extractTextByPage(input: File): List<String> = open(input) { doc ->
        val stripper = PDFTextStripper()
        (1..doc.numberOfPages).map { page ->
            stripper.startPage = page
            stripper.endPage = page
            stripper.getText(doc).orEmpty()
        }
    }

    private fun <T> open(input: File, block: (PDDocument) -> T): T {
        require(input.exists()) { "PDF not found: ${input.name}" }
        return try {
            PDDocument.load(input).use(block)
        } catch (t: Throwable) {
            throw mapError(t)
        }
    }

    private fun write(doc: PDDocument, output: File) {
        try {
            FileOutputStream(output).use { doc.save(it) }
        } catch (t: Throwable) {
            output.delete()
            throw mapError(t)
        }
    }

    private fun ensureParent(output: File) {
        output.parentFile?.mkdirs()
    }

    private fun mapError(t: Throwable): Throwable {
        val msg = t.message.orEmpty()
        return when {
            msg.contains("password", ignoreCase = true) ||
                msg.contains("encrypted", ignoreCase = true) ||
                t.javaClass.simpleName.contains("InvalidPassword", ignoreCase = true) ->
                IllegalStateException("This PDF is password-protected and cannot be processed.", t)
            else -> t
        }
    }
}
