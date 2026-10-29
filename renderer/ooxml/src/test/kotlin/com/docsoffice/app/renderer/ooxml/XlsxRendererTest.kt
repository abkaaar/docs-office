package com.docsoffice.app.renderer.ooxml

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxRendererTest {
    @Test
    fun columnIndex_mapsLetters() {
        assertEquals(0, XlsxRenderer.columnIndex("A"))
        assertEquals(25, XlsxRenderer.columnIndex("Z"))
        assertEquals(26, XlsxRenderer.columnIndex("AA"))
    }

    @Test
    fun parseMergeCells_andApplyMergeSpans_marksCoveredWithoutDuplicating() {
        val xml = """
            <worksheet>
              <sheetData>
                <row r="1"><c r="A1"><v>Merged</v></c><c r="B1"><v></v></c></row>
                <row r="2"><c r="A2"><v></v></c><c r="B2"><v></v></c></row>
              </sheetData>
              <mergeCells count="1"><mergeCell ref="A1:B2"/></mergeCells>
            </worksheet>
        """.trimIndent()
        val merges = XlsxRenderer.parseMergeCells(xml)
        assertEquals(1, merges.size)
        val rows = listOf(
            listOf(
                XlsxRenderer.SpreadsheetCell(display = "Merged", raw = "Merged"),
                XlsxRenderer.SpreadsheetCell(),
            ),
            listOf(XlsxRenderer.SpreadsheetCell(), XlsxRenderer.SpreadsheetCell()),
        )
        val spanned = XlsxRenderer.applyMergeSpans(rows, merges, maxRows = 50, maxCols = 50)
        assertEquals("Merged", spanned[0][0].display)
        assertEquals(2, spanned[0][0].rowSpan)
        assertEquals(2, spanned[0][0].colSpan)
        assertTrue(spanned[0][1].covered)
        assertTrue(spanned[1][0].covered)
        assertTrue(spanned[1][1].covered)
        assertEquals("", spanned[0][1].display)
    }

    @Test
    fun parseCell_formatsPercentAndUsesCachedFormulaResult() {
        val styles = XlsxRenderer.StyleCatalog(
            customNumFmts = emptyMap(),
            cellNumFmtIds = listOf(10),
            dateStyleFlags = listOf(false),
        )
        val formulaCell = """<c r="A1" s="0"><f>B1/100</f><v>0.255</v></c>"""
        val parsed = XlsxRenderer.parseCell(formulaCell, emptyList(), styles)
        assertEquals("B1/100", parsed.formula)
        assertEquals("0.255", parsed.raw)
        assertEquals("25.50%", parsed.display)

        val missing = XlsxRenderer.parseCell(
            """<c r="A2"><f>SUM(1,2)</f></c>""",
            emptyList(),
            XlsxRenderer.StyleCatalog(),
        )
        assertEquals("(no cached result)", missing.display)
        assertEquals("SUM(1,2)", missing.formula)
    }

    @Test
    fun numberFormats_currencyAndInteger() {
        assertEquals("1,234", XlsxNumberFormats.format("1234", 3, null))
        assertEquals("12.50%", XlsxNumberFormats.format("0.125", 10, null))
        assertEquals("42", XlsxNumberFormats.format("42", 1, null))
    }

    @Test
    fun parseWorkbook_readsSheetNamesAndSparseCells() {
        val file = File.createTempFile("sample", ".xlsx")
        file.writeBytes(minimalXlsx())
        try {
            val catalog = XlsxRenderer.openCatalog(file)
            assertEquals(listOf("Invoices", "Summary"), catalog.sheets.map { it.name })
            val rows = XlsxRenderer.readSheet(file, catalog, 0)
            assertTrue(rows.isNotEmpty())
            assertEquals("Item", rows[0].cells[0])
            assertEquals("Qty", rows[0].cells[2]) // sparse: B empty, C = Qty
            assertEquals("Apples", rows[1].cells[0])
            assertEquals("3", rows[1].cells[2])
        } finally {
            file.delete()
        }
    }

    private fun minimalXlsx(): ByteArray {
        val shared = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="3" uniqueCount="3">
              <si><t>Item</t></si>
              <si><t>Qty</t></si>
              <si><t>Apples</t></si>
            </sst>
        """.trimIndent()
        val workbook = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Invoices" sheetId="1" r:id="rId1"/>
                <sheet name="Summary" sheetId="2" r:id="rId2"/>
              </sheets>
            </workbook>
        """.trimIndent()
        val rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
            </Relationships>
        """.trimIndent()
        val sheet1 = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="C1" t="s"><v>1</v></c>
                </row>
                <row r="2">
                  <c r="A2" t="s"><v>2</v></c>
                  <c r="C2"><v>3</v></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()
        val sheet2 = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1"><v>1</v></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
            </Types>
        """.trimIndent()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("xl/workbook.xml", workbook)
            put("xl/_rels/workbook.xml.rels", rels)
            put("xl/sharedStrings.xml", shared)
            put("xl/worksheets/sheet1.xml", sheet1)
            put("xl/worksheets/sheet2.xml", sheet2)
        }
        return out.toByteArray()
    }
}
