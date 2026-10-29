package com.docsoffice.app.renderer.ooxml

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PptxRendererTest {
    @Test
    fun parseSlideContents_readsParagraphsAndNotes() {
        val file = File.createTempFile("sample", ".pptx")
        file.writeBytes(minimalPptx())
        try {
            val slides = PptxRenderer.parseSlideContents(file)
            assertEquals(2, slides.size)
            assertTrue(slides[0].body.contains("Welcome"))
            assertTrue(slides[0].body.contains("Agenda"))
            assertTrue(slides[1].body.contains("Thanks"))
            assertTrue(slides[0].notes.contains("Speak slowly"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseSlideContents_usesPresentationOrderNotFilename() {
        val file = File.createTempFile("ordered", ".pptx")
        file.writeBytes(
            pptxPackage(
                sldIdOrder = listOf("rId2", "rId1"),
                relationships = listOf(
                    Rel("rId1", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide", "slides/slide1.xml"),
                    Rel("rId2", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide", "slides/slide2.xml"),
                ),
                slides = mapOf(
                    "ppt/slides/slide1.xml" to slideXml("First on disk"),
                    "ppt/slides/slide2.xml" to slideXml("Shown first"),
                    "ppt/slides/slide99.xml" to slideXml("Orphan slide"),
                ),
            ),
        )
        try {
            val slides = PptxRenderer.parseSlideContents(file)
            assertEquals(2, slides.size)
            assertTrue(slides[0].body.contains("Shown first"))
            assertTrue(slides[1].body.contains("First on disk"))
            assertFalse(slides.any { it.body.contains("Orphan") })
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseSlideContents_extractsImagesAndNotesViaRels() {
        val file = File.createTempFile("media", ".pptx")
        val pngBytes = TINY_PNG
        file.writeBytes(
            pptxPackage(
                sldIdOrder = listOf("rId1"),
                relationships = listOf(
                    Rel("rId1", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide", "slides/slide1.xml"),
                ),
                slides = mapOf(
                    "ppt/slides/slide1.xml" to """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                          xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                          <p:cSld><p:spTree>
                            <p:pic>
                              <p:blipFill><a:blip r:embed="rId2"/></p:blipFill>
                            </p:pic>
                          </p:spTree></p:cSld>
                        </p:sld>
                    """.trimIndent(),
                ),
                slideRels = mapOf(
                    "ppt/slides/_rels/slide1.xml.rels" to """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/>
                          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="../notesSlides/notesSlide7.xml"/>
                        </Relationships>
                    """.trimIndent(),
                ),
                extras = mapOf(
                    "ppt/media/image1.png" to pngBytes,
                    "ppt/notesSlides/notesSlide7.xml" to """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                          xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                          <p:cSld><p:spTree>
                            <a:p><a:r><a:t>Rel-linked notes</a:t></a:r></a:p>
                          </p:spTree></p:cSld>
                        </p:notes>
                    """.trimIndent().toByteArray(Charsets.UTF_8),
                ),
            ),
        )
        val mediaDir = File.createTempFile("pptx-media", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            val slides = PptxRenderer.parseSlideContents(file, mediaDir)
            assertEquals(1, slides.size)
            assertEquals(1, slides[0].imagePaths.size)
            assertTrue(File(slides[0].imagePaths[0]).exists())
            assertTrue(slides[0].notes.contains("Rel-linked notes"))
            assertTrue(slides[0].body.isBlank() || slides[0].body == "(Empty slide)" || slides[0].imagePaths.isNotEmpty())
            assertFalse(slides[0].isBlank)
        } finally {
            file.delete()
            mediaDir.deleteRecursively()
        }
    }

    private data class Rel(val id: String, val type: String, val target: String)

    private fun slideXml(vararg paragraphs: String): String {
        val ps = paragraphs.joinToString("") { text ->
            """<p:sp><p:txBody><a:p><a:r><a:t>$text</a:t></a:r></a:p></p:txBody></p:sp>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
              xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld><p:spTree>$ps</p:spTree></p:cSld>
            </p:sld>
        """.trimIndent()
    }

    private fun minimalPptx(): ByteArray {
        val notes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
              xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld><p:spTree>
                <a:p><a:r><a:t>Speak slowly</a:t></a:r></a:p>
              </p:spTree></p:cSld>
            </p:notes>
        """.trimIndent()
        return pptxPackage(
            sldIdOrder = listOf("rId1", "rId2"),
            relationships = listOf(
                Rel("rId1", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide", "slides/slide1.xml"),
                Rel("rId2", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide", "slides/slide2.xml"),
            ),
            slides = mapOf(
                "ppt/slides/slide1.xml" to slideXml("Welcome", "Agenda"),
                "ppt/slides/slide2.xml" to slideXml("Thanks"),
            ),
            slideRels = mapOf(
                "ppt/slides/_rels/slide1.xml.rels" to """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="../notesSlides/notesSlide1.xml"/>
                    </Relationships>
                """.trimIndent(),
            ),
            extras = mapOf(
                "ppt/notesSlides/notesSlide1.xml" to notes.toByteArray(Charsets.UTF_8),
            ),
        )
    }

    private fun pptxPackage(
        sldIdOrder: List<String>,
        relationships: List<Rel>,
        slides: Map<String, String>,
        slideRels: Map<String, String> = emptyMap(),
        extras: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        val sldIds = sldIdOrder.joinToString("") { id ->
            """<p:sldId id="256" r:id="$id"/>"""
        }
        val presentation = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
              xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:sldIdLst>$sldIds</p:sldIdLst>
            </p:presentation>
        """.trimIndent()
        val rels = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            relationships.forEach { rel ->
                append("""<Relationship Id="${rel.id}" Type="${rel.type}" Target="${rel.target}"/>""")
            }
            append("</Relationships>")
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun putText(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            fun putBytes(name: String, body: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body)
                zip.closeEntry()
            }
            putText("ppt/presentation.xml", presentation)
            putText("ppt/_rels/presentation.xml.rels", rels)
            slides.forEach { (name, body) -> putText(name, body) }
            slideRels.forEach { (name, body) -> putText(name, body) }
            extras.forEach { (name, body) -> putBytes(name, body) }
        }
        return out.toByteArray()
    }

    companion object {
        // 1x1 PNG
        private val TINY_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53,
            0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00,
            0x00, 0x00, 0x03, 0x00, 0x01, 0x00, 0x05, 0xFE.toByte(),
            0xD4.toByte(), 0xEF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45,
            0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
    }
}
