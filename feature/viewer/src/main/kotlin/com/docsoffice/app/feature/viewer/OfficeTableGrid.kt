package com.docsoffice.app.feature.viewer

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared horizontal-scroll table for CSV, DOCX, and PPTX structured cell matrices.
 */
@Composable
fun OfficeTableGrid(
    rows: List<List<String>>,
    findQuery: String,
    fg: Color,
    fontScale: Float = 1f,
    cellWidth: Dp = 120.dp,
    firstRowHeader: Boolean = true,
    scrollable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val cols = (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val cellSp = ReaderTextStyles.cellSize(fontScale)
    val gridLine = fg.copy(alpha = 0.2f)
    val cellBorder = fg.copy(alpha = 0.15f)

    val scrollMods = if (scrollable) {
        Modifier
            .horizontalScroll(hScroll)
            .verticalScroll(vScroll)
    } else {
        Modifier.horizontalScroll(hScroll)
    }

    Column(
        modifier
            .fillMaxWidth()
            .then(scrollMods)
            .padding(vertical = 4.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.border(0.5.dp, gridLine)) {
                repeat(cols) { col ->
                    val cell = row.getOrNull(col).orEmpty()
                    val match = findQuery.isNotBlank() &&
                        cell.contains(findQuery, ignoreCase = true)
                    Text(
                        text = cell.ifEmpty { " " },
                        color = if (match) ReaderTextStyles.findMatch else fg,
                        fontSize = cellSp,
                        fontFamily = ReaderTextStyles.gridFamily,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (firstRowHeader && rowIndex == 0) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        modifier = Modifier
                            .width(cellWidth)
                            .border(0.5.dp, cellBorder)
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}
