package com.docsoffice.app.renderer.ooxml

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        } finally {
            file.delete()
        }
    }

    private fun minimalDocx(): ByteArray {
        val document = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <w:body>
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
