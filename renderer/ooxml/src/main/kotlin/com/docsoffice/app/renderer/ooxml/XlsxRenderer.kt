package com.docsoffice.app.renderer.ooxml

import com.docsoffice.app.renderer.api.DocumentRenderer
import com.docsoffice.app.renderer.api.DocumentSourceHandle
import com.docsoffice.app.renderer.api.OpenFailure
import com.docsoffice.app.renderer.api.RenderSession
import com.docsoffice.app.renderer.api.Row
import com.docsoffice.app.renderer.api.SheetRef
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipFile

class XlsxRenderer : DocumentRenderer {
    override val supportedMimeTypes: Set<String> = setOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    override suspend fun open(source: DocumentSourceHandle, password: String?): RenderSession {
        val file = runCatching { File(URI(source.uri)) }.getOrNull()
            ?: throw OpenFailure.Unsupported
        if (!file.exists()) throw OpenFailure.Corrupt
        val catalog = openCatalog(file)
        return RenderSession.Grid(
            sheets = catalog.sheets,
            readRows = { sheet, range ->
                val rows = readSheet(file, catalog, sheet.index)
                val last = rows.lastIndex.coerceAtLeast(0)
                rows.slice(range.first.coerceAtLeast(0)..range.last.coerceAtMost(last))
            },
        )
    }

    data class Workbook(
        val sheets: List<SheetRef>,
        val rowsBySheet: Map<Int, List<Row>>,
    )

    data class SheetCatalog(
        val sheets: List<SheetRef>,
        val sheetPaths: List<String>,
        val sharedStrings: List<String>,
        val styles: StyleCatalog = StyleCatalog(),
    )

    data class StyleCatalog(
        /** numFmtId → custom format code (built-ins omitted). */
        val customNumFmts: Map<Int, String> = emptyMap(),
        /** xf index → numFmtId */
        val cellNumFmtIds: List<Int> = emptyList(),
        /** xf index is a date/time style (from applyNumberFormats heuristics / built-in ids). */
        val dateStyleFlags: List<Boolean> = emptyList(),
    )

    /**
     * Rich cell used by the spreadsheet viewer.
     * [display] is what the grid shows; [raw] preserves the workbook value;
     * [formula] is the expression when present (not recalculated).
     * Merge: only the anchor has rowSpan/colSpan > 1; covered cells have [covered]=true.
     */
    data class SpreadsheetCell(
        val display: String = "",
        val raw: String = "",
        val formula: String? = null,
        val numFmtId: Int = 0,
        val rowSpan: Int = 1,
        val colSpan: Int = 1,
        val covered: Boolean = false,
    ) {
        val hasFormula: Boolean get() = !formula.isNullOrBlank()
        val formulaResultMissing: Boolean get() = hasFormula && raw.isBlank() && display.startsWith("(")
    }

    companion object {
        const val DEFAULT_MAX_ROWS = 10_000
        const val DEFAULT_MAX_COLS = 128

        fun openCatalog(file: File): SheetCatalog {
            OoxmlZip.resetBudget()
            val zip = OoxmlZip.open(file)
            zip.use { z ->
                val sharedStrings = parseSharedStrings(z)
                val styles = parseStyles(z)
                val (names, paths) = parseSheetIndex(z)
                val sheets = names.mapIndexed { index, name -> SheetRef(name = name, index = index) }
                return SheetCatalog(
                    sheets = sheets.ifEmpty { listOf(SheetRef("Sheet1", 0)) },
                    sheetPaths = paths.ifEmpty {
                        OoxmlZip.listEntries(z)
                            .filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
                            .sortedBy {
                                Regex("sheet(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                            }
                    },
                    sharedStrings = sharedStrings,
                    styles = styles,
                )
            }
        }

        fun readSheet(
            file: File,
            catalog: SheetCatalog,
            sheetIndex: Int,
            maxRows: Int = DEFAULT_MAX_ROWS,
            maxCols: Int = DEFAULT_MAX_COLS,
        ): List<Row> = readSheetCells(file, catalog, sheetIndex, maxRows, maxCols)
            .map { row -> Row(row.map { it.display }) }

        fun readSheetCells(
            file: File,
            catalog: SheetCatalog,
            sheetIndex: Int,
            maxRows: Int = DEFAULT_MAX_ROWS,
            maxCols: Int = DEFAULT_MAX_COLS,
        ): List<List<SpreadsheetCell>> {
            val path = catalog.sheetPaths.getOrNull(sheetIndex) ?: return emptyList()
            val zip = OoxmlZip.open(file)
            zip.use { z ->
                val stream = OoxmlZip.openEntry(z, path) ?: return emptyList()
                stream.use { input ->
                    val xml = input.bufferedReader(Charsets.UTF_8).readText()
                    val rows = parseSheetCellRows(
                        xml.byteInputStream(),
                        catalog.sharedStrings,
                        catalog.styles,
                        maxRows,
                        maxCols,
                    )
                    val merges = parseMergeCells(xml)
                    return applyMergeSpans(rows, merges, maxRows, maxCols)
                }
            }
        }

        /** Parses A1:B2-style merge ranges from worksheet XML. */
        internal fun parseMergeCells(sheetXml: String): List<IntRangePair> {
            val results = ArrayList<IntRangePair>()
            Regex("""<mergeCell\b[^>]*ref="([A-Z]+)(\d+):([A-Z]+)(\d+)"[^>]*/>""")
                .findAll(sheetXml)
                .forEach { m ->
                    val c1 = columnIndex(m.groupValues[1])
                    val r1 = m.groupValues[2].toIntOrNull()?.minus(1) ?: return@forEach
                    val c2 = columnIndex(m.groupValues[3])
                    val r2 = m.groupValues[4].toIntOrNull()?.minus(1) ?: return@forEach
                    results += IntRangePair(
                        rowStart = minOf(r1, r2),
                        rowEnd = maxOf(r1, r2),
                        colStart = minOf(c1, c2),
                        colEnd = maxOf(c1, c2),
                    )
                }
            return results
        }

        /**
         * Marks merge anchors with spans and covered cells without duplicating values.
         */
        internal fun applyMergeSpans(
            rows: List<List<SpreadsheetCell>>,
            merges: List<IntRangePair>,
            maxRows: Int,
            maxCols: Int,
        ): List<List<SpreadsheetCell>> {
            if (merges.isEmpty()) return rows
            val mutable = rows.map { it.toMutableList() }.toMutableList()
            fun ensure(r: Int, c: Int) {
                while (mutable.size <= r && mutable.size < maxRows) {
                    mutable += MutableList(maxCols) { SpreadsheetCell() }
                }
                val row = mutable.getOrNull(r) ?: return
                while (row.size <= c && row.size < maxCols) row += SpreadsheetCell()
            }
            merges.forEach { merge ->
                if (merge.rowStart >= maxRows || merge.colStart >= maxCols) return@forEach
                val rowEnd = merge.rowEnd.coerceAtMost(maxRows - 1)
                val colEnd = merge.colEnd.coerceAtMost(maxCols - 1)
                ensure(merge.rowStart, merge.colStart)
                val anchor = mutable[merge.rowStart].getOrNull(merge.colStart) ?: SpreadsheetCell()
                mutable[merge.rowStart][merge.colStart] = anchor.copy(
                    rowSpan = (rowEnd - merge.rowStart + 1).coerceAtLeast(1),
                    colSpan = (colEnd - merge.colStart + 1).coerceAtLeast(1),
                    covered = false,
                )
                for (r in merge.rowStart..rowEnd) {
                    for (c in merge.colStart..colEnd) {
                        if (r == merge.rowStart && c == merge.colStart) continue
                        ensure(r, c)
                        val existing = mutable[r].getOrNull(c) ?: SpreadsheetCell()
                        mutable[r][c] = existing.copy(
                            display = "",
                            covered = true,
                            rowSpan = 1,
                            colSpan = 1,
                        )
                    }
                }
            }
            return mutable.map { it.toList() }
        }

        /** @deprecated Prefer [applyMergeSpans]; kept for older unit tests that fill values. */
        internal fun applyMerges(
            rows: List<List<String>>,
            merges: List<IntRangePair>,
            maxRows: Int,
            maxCols: Int,
        ): List<List<String>> {
            val cells = rows.map { row -> row.map { SpreadsheetCell(display = it, raw = it) } }
            return applyMergeSpans(cells, merges, maxRows, maxCols).map { row ->
                row.map { if (it.covered) "" else it.display }
            }
        }

        data class IntRangePair(
            val rowStart: Int,
            val rowEnd: Int,
            val colStart: Int,
            val colEnd: Int,
        )

        fun parseWorkbook(file: File): Workbook {
            val catalog = openCatalog(file)
            val rowsBySheet = catalog.sheets.associate { sheet ->
                sheet.index to readSheet(file, catalog, sheet.index)
            }
            return Workbook(catalog.sheets, rowsBySheet)
        }

        fun extractPlainText(file: File): String {
            val wb = parseWorkbook(file)
            return wb.rowsBySheet.values.flatten()
                .joinToString("\n") { it.cells.joinToString("\t") }
        }

        internal fun parseStyles(zip: ZipFile): StyleCatalog {
            val xml = OoxmlZip.readEntryText(zip, "xl/styles.xml") ?: return StyleCatalog()
            val custom = LinkedHashMap<Int, String>()
            Regex("""<numFmt\b[^>]*numFmtId="(\d+)"[^>]*formatCode="([^"]*)"[^>]*/>""")
                .findAll(xml)
                .forEach { m ->
                    custom[m.groupValues[1].toInt()] = XmlText.decodeEntities(m.groupValues[2])
                }
            Regex("""<numFmt\b[^>]*formatCode="([^"]*)"[^>]*numFmtId="(\d+)"[^>]*/>""")
                .findAll(xml)
                .forEach { m ->
                    custom[m.groupValues[2].toInt()] = XmlText.decodeEntities(m.groupValues[1])
                }

            val cellXfs = Regex(
                """<cellXfs\b[^>]*>(.*?)</cellXfs>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(xml)?.groupValues?.get(1).orEmpty()

            val numFmtIds = ArrayList<Int>()
            val dateFlags = ArrayList<Boolean>()
            Regex("""<xf\b[^>]*>""").findAll(cellXfs).forEach { m ->
                val tag = m.value
                val id = Regex("""numFmtId="(\d+)"""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                numFmtIds += id
                val pattern = custom[id] ?: XlsxNumberFormats.builtinPattern(id)
                val isDate = id in DATE_BUILTIN_IDS ||
                    (pattern != null && DATE_HINT.containsMatchIn(pattern.lowercase()))
                dateFlags += isDate
            }
            return StyleCatalog(custom, numFmtIds, dateFlags)
        }

        private val DATE_BUILTIN_IDS = setOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47)
        private val DATE_HINT = Regex("""[ydh]|am/pm|mm:ss|:mm""")

        private fun parseSharedStrings(zip: ZipFile): List<String> {
            val xml = OoxmlZip.readEntryText(zip, "xl/sharedStrings.xml") ?: return emptyList()
            return XmlText.extractBetween(xml, "<si", "</si>")
                .map { part -> XmlText.stripTags("<si$part</si>").trim() }
        }

        private fun parseSheetIndex(zip: ZipFile): Pair<List<String>, List<String>> {
            val workbook = OoxmlZip.readEntryText(zip, "xl/workbook.xml").orEmpty()
            val rels = OoxmlZip.readEntryText(zip, "xl/_rels/workbook.xml.rels").orEmpty()
            val relTargets = Regex("""Id="([^"]+)"[^>]*Target="([^"]+)"""")
                .findAll(rels)
                .associate { it.groupValues[1] to it.groupValues[2] }
                .ifEmpty {
                    Regex("""Target="([^"]+)"[^>]*Id="([^"]+)"""")
                        .findAll(rels)
                        .associate { it.groupValues[2] to it.groupValues[1] }
                }

            val sheetTags = Regex("""<sheet\b[^>]*>""").findAll(workbook).map { it.value }.toList()
            if (sheetTags.isEmpty()) return emptyList<String>() to emptyList()

            val names = mutableListOf<String>()
            val paths = mutableListOf<String>()
            sheetTags.forEachIndexed { index, tag ->
                val name = Regex("""name="([^"]+)"""").find(tag)?.groupValues?.get(1)
                    ?: "Sheet${index + 1}"
                val rId = Regex("""r:id="([^"]+)"""").find(tag)?.groupValues?.get(1)
                    ?: Regex("""id="([^"]+)"""").find(tag)?.groupValues?.get(1)
                val target = rId?.let { relTargets[it] }
                val resolved = when {
                    target != null -> OoxmlZip.resolveEntry(zip, target)
                        ?: "xl/${target.removePrefix("/")}"
                    else -> "xl/worksheets/sheet${index + 1}.xml"
                }
                names += XmlText.decodeEntities(name)
                paths += resolved
            }
            return names to paths
        }

        internal fun parseSheetCellRows(
            input: InputStream,
            sharedStrings: List<String>,
            styles: StyleCatalog,
            maxRows: Int,
            maxCols: Int,
        ): List<List<SpreadsheetCell>> {
            val reader = input.bufferedReader(Charsets.UTF_8)
            val rows = ArrayList<List<SpreadsheetCell>>(minOf(maxRows, 256))
            val buffer = StringBuilder(64 * 1024)
            val chunk = CharArray(16 * 1024)
            while (rows.size < maxRows) {
                val read = reader.read(chunk)
                if (read < 0) {
                    drainCellRows(buffer, rows, sharedStrings, styles, maxRows, maxCols, final = true)
                    break
                }
                buffer.append(chunk, 0, read)
                drainCellRows(buffer, rows, sharedStrings, styles, maxRows, maxCols, final = false)
                if (buffer.length > 2_000_000) {
                    val keepFrom = (buffer.length - 256_000).coerceAtLeast(0)
                    buffer.delete(0, keepFrom)
                }
            }
            return rows
        }

        internal fun parseSheetRows(
            input: InputStream,
            sharedStrings: List<String>,
            maxRows: Int,
            maxCols: Int,
        ): List<List<String>> = parseSheetCellRows(input, sharedStrings, StyleCatalog(), maxRows, maxCols)
            .map { row -> row.map { it.display } }

        private fun drainCellRows(
            buffer: StringBuilder,
            rows: MutableList<List<SpreadsheetCell>>,
            sharedStrings: List<String>,
            styles: StyleCatalog,
            maxRows: Int,
            maxCols: Int,
            final: Boolean,
        ) {
            while (rows.size < maxRows) {
                val start = indexOfIgnoreCase(buffer, "<row")
                if (start < 0) {
                    if (!final && buffer.length > 8) buffer.delete(0, buffer.length - 8)
                    else if (final) buffer.clear()
                    return
                }
                if (start > 0) buffer.delete(0, start)
                val end = indexOfIgnoreCase(buffer, "</row>")
                if (end < 0) {
                    if (final) buffer.clear()
                    return
                }
                val rowXml = buffer.substring(0, end + 6)
                buffer.delete(0, end + 6)
                rows += parseCellRow(rowXml, sharedStrings, styles, maxCols)
            }
        }

        private fun parseCellRow(
            rowXml: String,
            sharedStrings: List<String>,
            styles: StyleCatalog,
            maxCols: Int,
        ): List<SpreadsheetCell> {
            val cells = ArrayList<SpreadsheetCell>(16)
            val cellRegex = Regex("""<c\b[^>]*>.*?</c>|<c\b[^>]*/>""", RegexOption.DOT_MATCHES_ALL)
            cellRegex.findAll(rowXml).forEach { match ->
                val cell = match.value
                val ref = Regex("""\br="([A-Z]+)(\d+)"""").find(cell)?.groupValues?.get(1)
                val col = ref?.let { columnIndex(it) } ?: cells.size
                if (col >= maxCols) return@forEach
                while (cells.size < col) cells += SpreadsheetCell()
                val parsed = parseCell(cell, sharedStrings, styles)
                if (cells.size == col) cells += parsed
                else if (cells.size > col) cells[col] = parsed
            }
            if (cells.size > maxCols) return cells.subList(0, maxCols).toList()
            return cells
        }

        internal fun parseCell(
            cell: String,
            sharedStrings: List<String>,
            styles: StyleCatalog,
        ): SpreadsheetCell {
            val type = Regex("""\bt="([^"]+)"""").find(cell)?.groupValues?.get(1)
            val styleIdx = Regex("""\bs="(\d+)"""").find(cell)?.groupValues?.get(1)?.toIntOrNull()
            val numFmtId = styleIdx?.let { styles.cellNumFmtIds.getOrNull(it) } ?: 0
            val isDate = styleIdx?.let { styles.dateStyleFlags.getOrNull(it) } == true
            val custom = styles.customNumFmts[numFmtId]
            val formula = Regex("""<f(?:\s[^>]*)?>(.*?)</f>""", RegexOption.DOT_MATCHES_ALL)
                .find(cell)?.groupValues?.get(1)
                ?.let { XmlText.decodeEntities(it).trim() }
                ?.takeIf { it.isNotEmpty() }

            val raw = when (type) {
                "s" -> {
                    val idx = Regex("""<v>(\d+)</v>""").find(cell)?.groupValues?.get(1)?.toIntOrNull()
                    sharedStrings.getOrNull(idx ?: -1).orEmpty()
                }
                "inlineStr", "str" -> {
                    val isText = Regex("""<is>.*?</is>""", RegexOption.DOT_MATCHES_ALL)
                        .find(cell)?.value
                    when {
                        isText != null -> XmlText.stripTags(isText).trim()
                        else -> Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
                            .find(cell)?.groupValues?.get(1)
                            ?.let { XmlText.decodeEntities(it) }
                            .orEmpty()
                    }
                }
                "b" -> when (Regex("""<v>(.*?)</v>""").find(cell)?.groupValues?.get(1)) {
                    "1" -> "TRUE"
                    "0" -> "FALSE"
                    else -> ""
                }
                "e" -> {
                    // Formula error cached in <v>
                    Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
                        .find(cell)?.groupValues?.get(1)
                        ?.let { XmlText.decodeEntities(it) }
                        .orEmpty()
                }
                else -> Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
                    .find(cell)?.groupValues?.get(1)
                    ?.let { XmlText.decodeEntities(it) }
                    .orEmpty()
            }

            val display = when {
                type == "e" && raw.isNotBlank() -> raw // #DIV/0! etc.
                formula != null && raw.isBlank() -> "(no cached result)"
                type == "s" || type == "inlineStr" || type == "str" || type == "b" -> raw
                raw.isNotBlank() -> XlsxNumberFormats.format(raw, numFmtId, custom, isDate)
                formula != null -> "(no cached result)"
                else -> ""
            }

            return SpreadsheetCell(
                display = display,
                raw = raw,
                formula = formula,
                numFmtId = numFmtId,
            )
        }

        internal fun columnIndex(letters: String): Int {
            var value = 0
            for (ch in letters.uppercase()) {
                if (ch !in 'A'..'Z') break
                value = value * 26 + (ch - 'A' + 1)
            }
            return (value - 1).coerceAtLeast(0)
        }

        private fun indexOfIgnoreCase(buffer: StringBuilder, needle: String): Int {
            val text = buffer
            val n = needle.length
            if (n == 0 || text.length < n) return -1
            outer@ for (i in 0..text.length - n) {
                for (j in 0 until n) {
                    if (text[i + j].lowercaseChar() != needle[j].lowercaseChar()) continue@outer
                }
                return i
            }
            return -1
        }
    }
}
