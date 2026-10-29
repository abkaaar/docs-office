package com.docsoffice.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docsoffice.app.core.designsystem.theme.ExcelGreen
import com.docsoffice.app.core.designsystem.theme.ImagesPurple
import com.docsoffice.app.core.designsystem.theme.PdfRed
import com.docsoffice.app.core.designsystem.theme.SlidesOrange
import com.docsoffice.app.core.designsystem.theme.TextSlate
import com.docsoffice.app.core.designsystem.theme.WordBlue
import com.docsoffice.app.core.model.DocCategory

@Composable
fun FileTypeChip(
    category: DocCategory,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (category) {
        DocCategory.PDF -> "PDF" to PdfRed
        DocCategory.WORD -> "DOC" to WordBlue
        DocCategory.EXCEL -> "XLS" to ExcelGreen
        DocCategory.SLIDES -> "PPT" to SlidesOrange
        DocCategory.TEXT -> "TXT" to TextSlate
        DocCategory.IMAGE -> "IMG" to ImagesPurple
        DocCategory.OTHER -> "FILE" to TextSlate
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

fun DocCategory.tint(): Color = when (this) {
    DocCategory.PDF -> PdfRed
    DocCategory.WORD -> WordBlue
    DocCategory.EXCEL -> ExcelGreen
    DocCategory.SLIDES -> SlidesOrange
    DocCategory.TEXT -> TextSlate
    DocCategory.IMAGE -> ImagesPurple
    DocCategory.OTHER -> TextSlate
}
