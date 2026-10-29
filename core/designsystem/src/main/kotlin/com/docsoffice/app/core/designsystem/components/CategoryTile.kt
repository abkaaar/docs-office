package com.docsoffice.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.docsoffice.app.core.model.CategoryCount
import com.docsoffice.app.core.model.DocCategory

@Composable
fun CategoryTile(
    count: CategoryCount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (count.category) {
        DocCategory.PDF -> "PDF"
        DocCategory.WORD -> "Word"
        DocCategory.EXCEL -> "Excel"
        DocCategory.SLIDES -> "Slides"
        DocCategory.TEXT -> "Text"
        DocCategory.IMAGE -> "Images"
        DocCategory.OTHER -> "Other"
    }
    Column(
        modifier = modifier
            .size(width = 104.dp, height = 88.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        FileTypeChip(category = count.category)
        Column {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = count.count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
