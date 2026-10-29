package com.docsoffice.app.renderer.ooxml

import com.docsoffice.app.renderer.api.DocBlock
import com.docsoffice.app.renderer.api.DocumentRenderer
import com.docsoffice.app.renderer.api.DocumentSourceHandle
import com.docsoffice.app.renderer.api.OpenFailure
import com.docsoffice.app.renderer.api.RenderSession
import java.io.File
import java.net.URI
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.flow

class DocxRenderer : DocumentRenderer {
    override val supportedMimeTypes: Set<String> = setOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    override suspend fun open(source: DocumentSourceHandle, password: String?): RenderSession {
        val file = runCatching { File(URI(source.uri)) }.getOrNull()
            ?: throw OpenFailure.Unsupported
        if (!file.exists()) throw OpenFailure.Corrupt
        val blocks = parseDocument(file).map { it.plainText }
        return RenderSession.Reflow(
            blocks = flow { blocks.forEach { emit(DocBlock(it)) } },
        )
    }

    data class RichBlock(
        val plainText: String,
        val kind: Kind = Kind.PARAGRAPH,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val headingLevel: Int = 0,
        val listLevel: Int = 0,
        val isTableRow: Boolean = false,
        val imagePath: String? = null,
        val hyperlink: String? = null,
    ) {
        enum class Kind { PARAGRAPH, HEADING, LIST_ITEM, TABLE_ROW, IMAGE, HORIZONTAL_RULE }
    }

    companion object {
        fun parseParagraphs(file: File): List<String> =
            parseDocument(file).map { it.plainText }.filter { it.isNotBlank() }

        fun parseDocument(file: File, mediaDir: File? = null): List<RichBlock> {
            OoxmlZip.resetBudget()
            val zip = OoxmlZip.open(file)
            zip.use { z ->
                val xml = OoxmlZip.readEntryText(z, "word/document.xml")
                    ?: throw OpenFailure.Corrupt
                mediaDir?.let {
                    if (it.exists()) it.deleteRecursively()
                    it.mkdirs()
                }
                val rels = OoxmlZip.parseRelationships(
                    OoxmlZip.readEntryText(z, "word/_rels/document.xml.rels").orEmpty(),
                )
                val headerBlocks = extractHeaderFooterBlocks(z, isHeader = true)
                val body = parseBody(xml, z, rels, mediaDir)
                val footerBlocks = extractHeaderFooterBlocks(z, isHeader = false)
                return headerBlocks + body + footerBlocks
            }
        }

        /**
         * Best-effort header/footer text from the first header/footer part.
         * Not paginated; shown once at the start/end of the reflow preview.
         */
        private fun extractHeaderFooterBlocks(zip: ZipFile, isHeader: Boolean): List<RichBlock> {
            val prefix = if (isHeader) "word/header" else "word/footer"
            val label = if (isHeader) "Header" else "Footer"
            val entry = zip.entries().asSequence()
                .map { it.name }
                .firstOrNull { it.startsWith(prefix) && it.endsWith(".xml") }
                ?: return emptyList()
            val xml = OoxmlZip.readEntryText(zip, entry) ?: return emptyList()
            val text = XmlText.stripTags(xml).trim()
            if (text.isBlank()) return emptyList()
            return listOf(
                RichBlock(
                    plainText = "$label: $text",
                    kind = RichBlock.Kind.PARAGRAPH,
                    italic = true,
                ),
            )
        }

        fun extractPlainText(file: File): String = parseParagraphs(file).joinToString("\n")

        private fun parseBody(
            xml: String,
            zip: ZipFile,
            rels: Map<String, OoxmlZip.Rel>,
            mediaDir: File?,
        ): List<RichBlock> {
            val blocks = mutableListOf<RichBlock>()
            // Tables first as row strings, then paragraphs outside tables roughly by document order:
            // Walk top-level siblings via regex for tbl and p.
            var index = 0
            while (index < xml.length) {
                val tblStart = xml.indexOf("<w:tbl", index)
                val pStart = xml.indexOf("<w:p", index)
                when {
                    tblStart >= 0 && (pStart < 0 || tblStart < pStart) -> {
                        val end = xml.indexOf("</w:tbl>", tblStart)
                        if (end < 0) break
                        val tableXml = xml.substring(tblStart, end + 8)
                        blocks += parseTable(tableXml)
                        index = end + 8
                    }
                    pStart >= 0 -> {
                        val end = xml.indexOf("</w:p>", pStart)
                        if (end < 0) break
                        val pXml = xml.substring(pStart, end + 6)
                        parseParagraph(pXml, zip, rels, mediaDir)?.let { blocks += it }
                        index = end + 6
                    }
                    else -> break
                }
            }
            if (blocks.isEmpty()) {
                val fallback = XmlText.stripTags(xml)
                if (fallback.isNotBlank()) {
                    blocks += RichBlock(fallback)
                }
            }
            return blocks
        }

        private fun parseTable(tableXml: String): List<RichBlock> {
            val rows = XmlText.extractBetween(tableXml, "<w:tr", "</w:tr>")
            return rows.map { row ->
                val cells = XmlText.extractBetween(row, "<w:tc", "</w:tc>")
                    .map { cell -> XmlText.stripTags("<w:tc$cell</w:tc>") }
                    .filter { it.isNotBlank() }
                RichBlock(
                    plainText = cells.joinToString(" │ "),
                    kind = RichBlock.Kind.TABLE_ROW,
                    isTableRow = true,
                )
            }.filter { it.plainText.isNotBlank() }
        }

        private fun parseParagraph(
            pXml: String,
            zip: ZipFile,
            rels: Map<String, OoxmlZip.Rel>,
            mediaDir: File?,
        ): RichBlock? {
            val style = Regex("""w:val="(Heading\d|Title|ListParagraph|[^"]+)"""")
                .find(pXml)?.groupValues?.get(1).orEmpty()
            val headingLevel = Regex("""Heading(\d)""").find(style)?.groupValues?.get(1)?.toIntOrNull()
                ?: if (style.equals("Title", ignoreCase = true)) 1 else 0
            val numPr = pXml.contains("<w:numPr") || style.contains("List", ignoreCase = true)
            val bold = pXml.contains("w:b/>") || pXml.contains("<w:b ") || pXml.contains("<w:b>")
            val italic = pXml.contains("w:i/>") || pXml.contains("<w:i ")
            val underline = pXml.contains("<w:u ")
            val text = buildString {
                Regex("""<w:t(?:\s[^>]*)?>(.*?)</w:t>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(pXml)
                    .forEach { append(XmlText.decodeEntities(it.groupValues[1])) }
            }.trim()

            val blip = Regex("""r:embed="([^"]+)"""").find(pXml)?.groupValues?.get(1)
            var imagePath: String? = null
            if (blip != null && mediaDir != null) {
                val rel = rels[blip]
                if (rel != null && rel.type.contains("image", ignoreCase = true)) {
                    val entry = OoxmlZip.resolveRelative(zip, "word/", rel.target)
                    if (entry != null) {
                        val dest = File(mediaDir, "img_${blip}.${entry.substringAfterLast('.')}")
                        if (OoxmlZip.extractEntry(zip, entry, dest)) {
                            imagePath = dest.absolutePath
                        }
                    }
                }
            }

            val hyperlink = Regex("""r:id="([^"]+)"""").find(pXml)?.groupValues?.get(1)
                ?.let { id -> rels[id]?.takeIf { it.type.contains("hyperlink", ignoreCase = true) }?.target }

            if (text.isBlank() && imagePath == null) return null
            val kind = when {
                imagePath != null && text.isBlank() -> RichBlock.Kind.IMAGE
                headingLevel > 0 -> RichBlock.Kind.HEADING
                numPr -> RichBlock.Kind.LIST_ITEM
                else -> RichBlock.Kind.PARAGRAPH
            }
            val display = when {
                numPr && text.isNotBlank() -> "• $text"
                else -> text
            }
            return RichBlock(
                plainText = display.ifBlank { " " },
                kind = kind,
                bold = bold || headingLevel > 0,
                italic = italic,
                underline = underline,
                headingLevel = headingLevel,
                listLevel = if (numPr) 1 else 0,
                imagePath = imagePath,
                hyperlink = hyperlink,
            )
        }
    }
}
