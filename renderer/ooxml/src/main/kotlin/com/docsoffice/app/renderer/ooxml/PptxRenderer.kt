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

class PptxRenderer : DocumentRenderer {
    override val supportedMimeTypes: Set<String> = setOf(
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
    )

    override suspend fun open(source: DocumentSourceHandle, password: String?): RenderSession {
        val file = runCatching { File(URI(source.uri)) }.getOrNull()
            ?: throw OpenFailure.Unsupported
        if (!file.exists()) throw OpenFailure.Corrupt
        val slides = parseSlideContents(file)
        return RenderSession.Reflow(
            blocks = flow {
                slides.forEachIndexed { index, slide ->
                    emit(DocBlock("Slide ${index + 1}\n${slide.body}"))
                }
            },
        )
    }

    data class Slide(
        val body: String,
        val notes: String = "",
        val imagePaths: List<String> = emptyList(),
        val title: String = "",
        val backgroundHint: String = "",
        val placeholders: List<String> = emptyList(),
    ) {
        val isBlank: Boolean
            get() = body.isBlank() && notes.isBlank() && imagePaths.isEmpty()
    }

    data class SlideCatalog(
        val slidePaths: List<String>,
    ) {
        val slideCount: Int get() = slidePaths.size
    }

    companion object {
        private const val MAX_IMAGES_PER_SLIDE = 12

        fun parseSlides(file: File): List<String> = parseSlideContents(file).map { it.body }

        fun openCatalog(file: File): SlideCatalog {
            OoxmlZip.resetBudget()
            val zip = OoxmlZip.open(file)
            zip.use { z ->
                return SlideCatalog(orderedSlidePaths(z))
            }
        }

        fun parseSlideAt(
            file: File,
            catalog: SlideCatalog,
            index: Int,
            mediaDir: File?,
        ): Slide {
            val path = catalog.slidePaths.getOrNull(index) ?: return Slide("(Empty slide)")
            OoxmlZip.resetBudget()
            val zip = OoxmlZip.open(file)
            zip.use { z ->
                mediaDir?.mkdirs()
                return parseOneSlide(z, path, index, mediaDir)
            }
        }

        fun parseSlideContents(file: File, mediaDir: File? = null): List<Slide> {
            OoxmlZip.resetBudget()
            val catalog = openCatalog(file)
            if (catalog.slideCount == 0) return emptyList()
            mediaDir?.let { dir ->
                if (dir.exists()) dir.deleteRecursively()
                dir.mkdirs()
            }
            return catalog.slidePaths.indices.map { index ->
                parseSlideAt(file, catalog, index, mediaDir)
            }
        }

        fun extractPlainText(file: File): String =
            parseSlideContents(file).joinToString("\n\n") { slide ->
                buildString {
                    if (slide.title.isNotBlank()) {
                        append(slide.title)
                        append('\n')
                    }
                    append(slide.body)
                    if (slide.notes.isNotBlank()) {
                        append("\nNotes: ")
                        append(slide.notes)
                    }
                }
            }

        private fun orderedSlidePaths(zip: ZipFile): List<String> {
            val presentation = OoxmlZip.readEntryText(zip, "ppt/presentation.xml").orEmpty()
            val relsXml = OoxmlZip.readEntryText(zip, "ppt/_rels/presentation.xml.rels").orEmpty()
            if (presentation.isNotBlank() && relsXml.isNotBlank()) {
                val rels = OoxmlZip.parseRelationships(relsXml)
                val sldIdLst = Regex(
                    """<p:sldIdLst\b[^>]*>(.*?)</p:sldIdLst>""",
                    RegexOption.DOT_MATCHES_ALL,
                ).find(presentation)?.groupValues?.get(1)
                    ?: Regex(
                        """<sldIdLst\b[^>]*>(.*?)</sldIdLst>""",
                        RegexOption.DOT_MATCHES_ALL,
                    ).find(presentation)?.groupValues?.get(1)
                    .orEmpty()

                val rIds = Regex("""r:id="([^"]+)"""")
                    .findAll(sldIdLst)
                    .map { it.groupValues[1] }
                    .toList()
                    .ifEmpty {
                        Regex("""\bid="(rId[^"]+)"""")
                            .findAll(sldIdLst)
                            .map { it.groupValues[1] }
                            .toList()
                    }

                val ordered = rIds.mapNotNull { rId ->
                    val rel = rels[rId] ?: return@mapNotNull null
                    if (!rel.type.contains("slide", ignoreCase = true) ||
                        rel.type.contains("notesSlide", ignoreCase = true)
                    ) {
                        return@mapNotNull null
                    }
                    OoxmlZip.resolveRelative(zip, "ppt/", rel.target)
                }
                if (ordered.isNotEmpty()) return ordered
            }

            // Fallback for malformed packages: filename sort
            return OoxmlZip.listEntries(zip)
                .filter { name -> name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
                .sortedBy { name ->
                    Regex("slide(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }
        }

        private fun parseOneSlide(
            zip: ZipFile,
            slidePath: String,
            index: Int,
            mediaDir: File?,
        ): Slide {
            val xml = OoxmlZip.readEntryText(zip, slidePath).orEmpty()
            val paragraphs = extractShapeParagraphs(xml)
            val title = paragraphs.firstOrNull().orEmpty()
            val body = paragraphs.joinToString("\n").ifBlank {
                extractTextRuns(xml).ifBlank { "" }
            }

            val slideBase = slidePath.substringBeforeLast('/') + "/"
            val relsPath = slidePath
                .replace("ppt/slides/", "ppt/slides/_rels/")
                .let { "$it.rels" }
                .let { alt ->
                    if (zip.getEntry(alt) != null) {
                        alt
                    } else {
                        "${slideBase}_rels/${slidePath.substringAfterLast('/')}.rels"
                    }
                }
            val rels = OoxmlZip.parseRelationships(
                OoxmlZip.readEntryText(zip, relsPath).orEmpty(),
            )

            val notes = resolveNotes(zip, slideBase, rels)
            val imagePaths = if (mediaDir != null) {
                extractImages(zip, xml, slideBase, rels, mediaDir, index)
            } else {
                emptyList()
            }

            val placeholders = buildList {
                if (xml.contains("chart", ignoreCase = true) && xml.contains("graphicData")) {
                    add("Chart")
                }
                if (xml.contains("dgm", ignoreCase = true) || xml.contains("smartArt", ignoreCase = true)) {
                    add("SmartArt")
                }
                if (xml.contains("video") || xml.contains("audio")) add("Media")
            }
            val bgHint = when {
                xml.contains("srgbClr", ignoreCase = true) -> {
                    Regex("""srgbClr[^>]*val="([A-Fa-f0-9]{6})"""").find(xml)?.groupValues?.get(1)
                        ?.let { "#$it" }
                        .orEmpty()
                }
                else -> ""
            }

            val displayBody = when {
                body.isNotBlank() -> body
                imagePaths.isNotEmpty() -> ""
                placeholders.isNotEmpty() -> placeholders.joinToString(" · ") { "[$it]" }
                else -> "(Empty slide)"
            }

            return Slide(
                body = displayBody,
                notes = notes,
                imagePaths = imagePaths,
                title = title,
                backgroundHint = bgHint,
                placeholders = placeholders,
            )
        }

        private fun extractShapeParagraphs(xml: String): List<String> {
            // Prefer text inside shapes / groups / tables; still uses a:p order in document.
            val shapeChunks = mutableListOf<String>()
            val shapeRegex = Regex(
                """<(?:p:sp|p:grpSp|p:pic|p:graphicFrame)\b[^>]*>.*?</(?:p:sp|p:grpSp|p:pic|p:graphicFrame)>""",
                RegexOption.DOT_MATCHES_ALL,
            )
            shapeRegex.findAll(xml).forEach { match ->
                val chunk = match.value
                if (chunk.contains("<a:tbl")) {
                    shapeChunks += extractTableRows(chunk)
                } else {
                    shapeChunks += XmlText.paragraphsFromDrawingXml(chunk)
                }
            }
            if (shapeChunks.isNotEmpty()) return shapeChunks.distinctPreserveOrder()
            return XmlText.paragraphsFromDrawingXml(xml)
        }

        /** Tab-separated table rows from DrawingML tables embedded on a slide. */
        private fun extractTableRows(xml: String): List<String> {
            return Regex("""<a:tr\b[^>]*>.*?</a:tr>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml)
                .map { row ->
                    Regex("""<a:tc\b[^>]*>.*?</a:tc>""", RegexOption.DOT_MATCHES_ALL)
                        .findAll(row.value)
                        .map { cell -> XmlText.paragraphsFromDrawingXml(cell.value).joinToString(" ") }
                        .filter { it.isNotBlank() }
                        .joinToString("\t")
                }
                .filter { it.isNotBlank() }
                .toList()
        }

        private fun extractTextRuns(xml: String): String {
            val runs = Regex("""<a:t(?:\s[^>]*)?>(.*?)</a:t>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml)
                .map { XmlText.decodeEntities(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .toList()
            return runs.joinToString(" ").trim()
        }

        private fun resolveNotes(
            zip: ZipFile,
            slideBase: String,
            rels: Map<String, OoxmlZip.Rel>,
        ): String {
            val notesRel = rels.values.firstOrNull {
                it.type.contains("notesSlide", ignoreCase = true)
            } ?: return ""
            val notesPath = OoxmlZip.resolveRelative(zip, slideBase, notesRel.target) ?: return ""
            val notesXml = OoxmlZip.readEntryText(zip, notesPath).orEmpty()
            // Notes slides often duplicate slide body; prefer paragraphs from notes text body.
            val paragraphs = XmlText.paragraphsFromDrawingXml(notesXml)
            return paragraphs
                .filterNot { it.equals("Click to edit Master text styles", ignoreCase = true) }
                .joinToString("\n")
                .trim()
        }

        private fun extractImages(
            zip: ZipFile,
            slideXml: String,
            slideBase: String,
            rels: Map<String, OoxmlZip.Rel>,
            mediaDir: File,
            slideIndex: Int,
        ): List<String> {
            val embedIds = LinkedHashSet<String>()
            Regex("""r:embed="([^"]+)"""")
                .findAll(slideXml)
                .forEach { embedIds += it.groupValues[1] }
            Regex("""r:link="([^"]+)"""")
                .findAll(slideXml)
                .forEach { embedIds += it.groupValues[1] }

            val imageRels = rels.values.filter {
                it.type.contains("/image", ignoreCase = true) ||
                    it.target.contains("/media/", ignoreCase = true)
            }
            val orderedIds = buildList {
                embedIds.forEach { id -> if (rels[id] != null) add(id) }
                imageRels.forEach { rel -> if (rel.id !in this) add(rel.id) }
            }.take(MAX_IMAGES_PER_SLIDE)

            val paths = mutableListOf<String>()
            orderedIds.forEachIndexed { imageIndex, rId ->
                val rel = rels[rId] ?: return@forEachIndexed
                val entry = OoxmlZip.resolveRelative(zip, slideBase, rel.target) ?: return@forEachIndexed
                if (!entry.contains("/media/", ignoreCase = true) &&
                    !IMAGE_EXTS.any { entry.endsWith(it, ignoreCase = true) }
                ) {
                    return@forEachIndexed
                }
                val ext = entry.substringAfterLast('.', "img")
                val dest = File(mediaDir, "s${slideIndex + 1}_i${imageIndex + 1}.$ext")
                if (OoxmlZip.extractEntry(zip, entry, dest)) {
                    paths += dest.absolutePath
                }
            }
            return paths
        }

        private fun List<String>.distinctPreserveOrder(): List<String> {
            val seen = LinkedHashSet<String>()
            return filter { seen.add(it) }
        }

        private val IMAGE_EXTS = listOf(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".tif", ".tiff", ".emf", ".wmf",
        )
    }
}
