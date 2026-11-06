package com.docsoffice.app.renderer.ooxml

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxRendererTest {
    @Test
    fun parseDocument_readsHeadingListAndTable() {
        val file = File.createTempFile("doc", ".docx")
        file.writeBytes(minimalDocx())
        try {
            val blocks = DocxRenderer.parseDocument(file)
            val text = blocks.joinToString("\n") { it.plainText }
            assertTrue(text.contains("Title Here"))
            assertTrue(text.contains("Bullet one") || text.contains("• Bullet one"))
            assertTrue(text.contains("A") && text.contains("B"))
            val table = blocks.firstOrNull { it.cells != null }
            assertNotNull(table)
            assertEquals(listOf(listOf("A", "B")), table!!.cells)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseDocument_preservesBlankCellsAndGridSpan() {
        val file = File.createTempFile("doc-blank", ".docx")
        file.writeBytes(
            docxWithBody(
                """
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t></w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>C</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc>
                      <w:tcPr><w:gridSpan w:val="2"/></w:tcPr>
                      <w:p><w:r><w:t>Wide</w:t></w:r></w:p>
                    </w:tc>
                    <w:tc><w:p><w:r><w:t>Z</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                """.trimIndent(),
            ),
        )
        try {
            val table = DocxRenderer.parseDocument(file).first { it.cells != null }
            assertEquals(listOf("A", "", "C"), table.cells!![0])
            assertEquals(listOf("Wide", "", "Z"), table.cells!![1])
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseDocument_keepsMultiParagraphCellNewlines() {
        val file = File.createTempFile("doc-mp", ".docx")
        file.writeBytes(
            docxWithBody(
                """
                <w:tbl>
                  <w:tr>
                    <w:tc>
                      <w:p><w:r><w:t>Line1</w:t></w:r></w:p>
                      <w:p><w:r><w:t>Line2</w:t></w:r></w:p>
                    </w:tc>
                    <w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                """.trimIndent(),
            ),
        )
        try {
            val table = DocxRenderer.parseDocument(file).first { it.cells != null }
            assertEquals("Line1\nLine2", table.cells!![0][0])
            assertEquals("B", table.cells!![0][1])
        } finally {
            file.delete()
        }
    }

    private fun minimalDocx(): ByteArray = docxWithBody(
        """
        <w:p>
          <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
          <w:r><w:rPr><w:b/></w:rPr><w:t>Title Here</w:t></w:r>
        </w:p>
        <w:p>
          <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
          <w:r><w:t>Bullet one</w:t></w:r>
        </w:p>
        <w:tbl>
          <w:tr>
            <w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>
            <w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc>
          </w:tr>
        </w:tbl>
        """.trimIndent(),
    )

    private fun docxWithBody(bodyInner: String): ByteArray {
        val document = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <w:body>
                $bodyInner
              </w:body>
            </w:document>
        """.trimIndent()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(document.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zip.write(
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""
                    .toByteArray(),
            )
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
