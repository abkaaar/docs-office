package com.docsoffice.app.feature.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.docsoffice.app.renderer.ooxml.DocxRenderer
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    LaunchedEffect(configuration.screenWidthDp) {
        val widthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
        viewModel.setRenderWidth(widthPx)
    }

    fun launchOrToast(intent: Intent?, label: String) {
        if (intent == null) {
            Toast.makeText(context, "No app available to $label", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, label))
        }.onFailure {
            Toast.makeText(context, "Could not $label", Toast.LENGTH_SHORT).show()
        }
    }

    val share = { launchOrToast(viewModel.shareIntent(), "Share") }
    val openWith = { launchOrToast(viewModel.openWithIntent(), "Open with") }

    when (val s = state) {
        ViewerUiState.Loading -> ViewerLoadingSkeleton(onBack = onBack)
        is ViewerUiState.CannotOpen -> ViewerErrorScreen(
            document = s.document,
            reason = s.reason,
            kind = s.kind,
            onBack = onBack,
            onShare = share,
            onOpenWith = openWith,
        )
        is ViewerUiState.PdfReady -> PdfViewer(
            state = s,
            onBack = onBack,
            onPrev = viewModel::previousPage,
            onNext = viewModel::nextPage,
            onJump = viewModel::jumpToPage,
            onEnsurePage = viewModel::ensurePdfPage,
            onFavorite = viewModel::toggleFavorite,
            onBookmark = viewModel::bookmarkCurrentPage,
            onShare = share,
            onOpenWith = openWith,
            onTheme = viewModel::setReaderTheme,
            onFind = viewModel::setFindQuery,
            onFindPrev = viewModel::findPrevPdfMatch,
            onFindNext = viewModel::findNextPdfMatch,
        )
        is ViewerUiState.TextReady -> TextViewer(
            state = s,
            onBack = onBack,
            onFavorite = viewModel::toggleFavorite,
            onShare = share,
            onOpenWith = openWith,
            onLarger = { viewModel.setFontScale(s.fontScale + 0.1f) },
            onSmaller = { viewModel.setFontScale(s.fontScale - 0.1f) },
            onToggleMono = viewModel::toggleMonospace,
            onToggleWrap = viewModel::toggleWrap,
            onFind = viewModel::setFindQuery,
            onTheme = viewModel::setReaderTheme,
            onLoadMore = viewModel::loadMoreText,
        )
        is ViewerUiState.DocxReady -> DocxViewer(
            state = s,
            onBack = onBack,
            onFavorite = viewModel::toggleFavorite,
            onShare = share,
            onOpenWith = openWith,
            onLarger = { viewModel.setFontScale(s.fontScale + 0.1f) },
            onSmaller = { viewModel.setFontScale(s.fontScale - 0.1f) },
            onFind = viewModel::setFindQuery,
            onTheme = viewModel::setReaderTheme,
        )
        is ViewerUiState.ImageReady -> ImageViewer(
            state = s,
            onBack = onBack,
            onPrev = viewModel::previousImage,
            onNext = viewModel::nextImage,
            onFavorite = viewModel::toggleFavorite,
            onShare = share,
            onOpenWith = openWith,
        )
        is ViewerUiState.SpreadsheetReady -> SpreadsheetViewer(
            state = s,
            onBack = onBack,
            onSelectSheet = viewModel::selectSheet,
            onSelectCell = viewModel::selectCell,
            onFavorite = viewModel::toggleFavorite,
            onShare = share,
            onOpenWith = openWith,
            onFind = viewModel::setFindQuery,
            onTheme = viewModel::setReaderTheme,
        )
        is ViewerUiState.SlidesReady -> SlidesViewer(
            state = s,
            onBack = onBack,
            onPrev = viewModel::previousSlide,
            onNext = viewModel::nextSlide,
            onJump = viewModel::jumpToSlide,
            onFavorite = viewModel::toggleFavorite,
            onShare = share,
            onOpenWith = openWith,
            onTheme = viewModel::setReaderTheme,
        )
    }
}

@Composable
private fun themeMenu(onTheme: (String) -> Unit): List<Pair<String, () -> Unit>> = listOf(
    "Theme: Light" to { onTheme("light") },
    "Theme: Dark" to { onTheme("dark") },
    "Theme: Sepia" to { onTheme("sepia") },
)

@Composable
private fun PdfViewer(
    state: ViewerUiState.PdfReady,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
    onEnsurePage: (Int) -> Unit,
    onFavorite: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onTheme: (String) -> Unit,
    onFind: (String) -> Unit,
    onFindPrev: () -> Unit,
    onFindNext: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showScrubber by remember { mutableStateOf(false) }
    var continuous by remember { mutableStateOf(true) }
    var findOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.pageIndex)

    LaunchedEffect(state.pageIndex, continuous) {
        if (!continuous) return@LaunchedEffect
        if (listState.firstVisibleItemIndex != state.pageIndex) {
            listState.scrollToItem(state.pageIndex.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)))
        }
    }
    LaunchedEffect(continuous, state.pageCount) {
        if (!continuous) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                onJump(index)
                onEnsurePage(index)
                onEnsurePage(index + 1)
                onEnsurePage(index - 1)
            }
    }

    ViewerShell(
        title = "Page ${state.pageIndex + 1} of ${state.pageCount}",
        subtitle = state.document.displayName,
        theme = state.theme,
        onBack = onBack,
        onFavorite = onFavorite,
        onShare = onShare,
        onOpenWith = onOpenWith,
        overflowItems = listOf(
            "Bookmark page" to onBookmark,
            "Jump / scrubber" to { showScrubber = !showScrubber },
            if (continuous) "Single page" to { continuous = false }
            else "Continuous scroll" to { continuous = true },
            "Find in PDF…" to { findOpen = true },
        ) + themeMenu(onTheme),
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onPrev, enabled = state.pageIndex > 0) { Text("Previous") }
                TextButton(onClick = onNext, enabled = state.pageIndex + 1 < state.pageCount) {
                    Text("Next")
                }
            }
        },
    ) { _, _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (findOpen) {
                FindBar(
                    query = state.findQuery,
                    onQuery = onFind,
                    onClose = { findOpen = false },
                )
                state.findStatus?.let { status ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(
                                onClick = onFindPrev,
                                enabled = state.findMatchPages.isNotEmpty(),
                            ) { Text("Prev") }
                            TextButton(
                                onClick = onFindNext,
                                enabled = state.findMatchPages.isNotEmpty(),
                            ) { Text("Next") }
                        }
                    }
                }
            }
            if (showScrubber && state.pageCount > 1) {
                Slider(
                    value = state.pageIndex.toFloat(),
                    onValueChange = { onJump(it.roundToInt()) },
                    valueRange = 0f..(state.pageCount - 1).toFloat(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (continuous) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(readerBackground(state.theme)),
                ) {
                    items(state.pageCount, key = { it }) { index ->
                        val bitmap = state.pageBitmaps[index]
                            ?: state.pageBitmap?.takeIf { index == state.pageIndex }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.FillWidth,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(480.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                                LaunchedEffect(index) { onEnsurePage(index) }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(readerBackground(state.theme))
                        .pointerInput(state.pageIndex) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .pointerInput(state.pageIndex) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.1f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.5f
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    state.pageBitmap?.let { bitmap: Bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = state.document.displayName,
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY,
                                ),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextViewer(
    state: ViewerUiState.TextReady,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onLarger: () -> Unit,
    onSmaller: () -> Unit,
    onToggleMono: () -> Unit,
    onToggleWrap: () -> Unit,
    onFind: (String) -> Unit,
    onTheme: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    var findOpen by remember { mutableStateOf(false) }
    val fg = readerForeground(state.theme)
    val subtitle = when {
        state.isCsv -> "CSV table"
        state.partialPreview -> "Partial preview · ${state.encodingLabel}"
        else -> null
    }
    ViewerShell(
        title = state.document.displayName,
        subtitle = subtitle,
        theme = state.theme,
        onBack = onBack,
        onFavorite = onFavorite,
        onShare = onShare,
        onOpenWith = onOpenWith,
        overflowItems = listOf(
            "Find" to { findOpen = true },
            "A+" to onLarger,
            "A-" to onSmaller,
            if (state.isCsv) "Wrap" to onToggleWrap else "Mono" to onToggleMono,
            "Wrap lines" to onToggleWrap,
        ) + themeMenu(onTheme),
    ) { _, _ ->
        Column(modifier = Modifier.fillMaxSize().background(readerBackground(state.theme))) {
            if (findOpen) {
                FindBar(query = state.findQuery, onQuery = onFind, onClose = { findOpen = false })
            }
            if (state.partialPreview && !state.isCsv) {
                Text(
                    text = "Showing a portion of this file (${state.encodingLabel}). " +
                        "Content below is not the full document until Load more reaches the end.",
                    color = fg.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.isCsv) {
                CsvTable(state.csvRows, state.findQuery, fg)
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (!state.wrap) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                        .padding(16.dp),
                ) {
                    itemsIndexed(state.lines) { _, line ->
                        val match = state.findQuery.isNotBlank() &&
                            line.contains(state.findQuery, ignoreCase = true)
                        Text(
                            text = line.ifEmpty { " " },
                            color = if (match) Color(0xFFB45309) else fg,
                            fontSize = (14 * state.fontScale).sp,
                            fontFamily = if (state.monospace) FontFamily.Monospace else FontFamily.Default,
                            maxLines = if (state.wrap) Int.MAX_VALUE else 1,
                            overflow = if (state.wrap) TextOverflow.Clip else TextOverflow.Ellipsis,
                        )
                    }
                    if (state.hasMore) {
                        item {
                            androidx.compose.material3.Button(
                                onClick = onLoadMore,
                                enabled = !state.loadingMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(if (state.loadingMore) "Loading…" else "Load more")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CsvTable(rows: List<List<String>>, findQuery: String, fg: Color) {
    val cols = (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .horizontalScroll(hScroll)
            .verticalScroll(vScroll)
            .padding(8.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.border(0.5.dp, fg.copy(alpha = 0.2f))) {
                repeat(cols) { col ->
                    val cell = row.getOrNull(col).orEmpty()
                    val match = findQuery.isNotBlank() && cell.contains(findQuery, ignoreCase = true)
                    Text(
                        text = cell,
                        color = if (match) Color(0xFFB45309) else fg,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .width(120.dp)
                            .border(0.5.dp, fg.copy(alpha = 0.15f))
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DocxViewer(
    state: ViewerUiState.DocxReady,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onLarger: () -> Unit,
    onSmaller: () -> Unit,
    onFind: (String) -> Unit,
    onTheme: (String) -> Unit,
) {
    var findOpen by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    val fg = readerForeground(state.theme)
    ViewerShell(
        title = state.document.displayName,
        subtitle = "Word preview",
        theme = state.theme,
        onBack = onBack,
        onFavorite = onFavorite,
        onShare = onShare,
        onOpenWith = onOpenWith,
        overflowItems = listOf(
            "Find" to { findOpen = true },
            "A+" to onLarger,
            "A-" to onSmaller,
        ) + themeMenu(onTheme),
    ) { _, _ ->
        Column(modifier = Modifier.fillMaxSize().background(readerBackground(state.theme))) {
            if (findOpen) {
                FindBar(query = state.findQuery, onQuery = onFind, onClose = { findOpen = false })
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 2.5f)
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.blocks) { block ->
                    DocxBlock(block, state.fontScale, state.findQuery, fg)
                }
            }
        }
    }
}

@Composable
private fun DocxBlock(
    block: DocxRenderer.RichBlock,
    fontScale: Float,
    findQuery: String,
    fg: Color,
) {
    val match = findQuery.isNotBlank() && block.plainText.contains(findQuery, ignoreCase = true)
    val size = when (block.headingLevel) {
        1 -> 22f
        2 -> 18f
        3 -> 16f
        else -> 14f
    } * fontScale
    if (block.imagePath != null) {
        AsyncImage(
            model = File(block.imagePath),
            contentDescription = "Document image",
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            contentScale = ContentScale.Fit,
        )
    }
    if (block.plainText.isNotBlank()) {
        Text(
            text = block.plainText,
            color = if (match) Color(0xFFB45309) else fg,
            fontSize = size.sp,
            fontWeight = if (block.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (block.italic) {
                androidx.compose.ui.text.font.FontStyle.Italic
            } else {
                androidx.compose.ui.text.font.FontStyle.Normal
            },
            textDecoration = when {
                block.underline || block.hyperlink != null -> TextDecoration.Underline
                else -> TextDecoration.None
            },
            modifier = if (block.isTableRow) {
                Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, fg.copy(alpha = 0.25f))
                    .padding(8.dp)
            } else {
                Modifier.fillMaxWidth()
            },
        )
    }
}

@Composable
private fun ImageViewer(
    state: ViewerUiState.ImageReady,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    ViewerShell(
        title = state.document.displayName,
        subtitle = "${state.index + 1} / ${state.siblingUris.size}",
        theme = state.theme,
        onBack = onBack,
        onFavorite = onFavorite,
        onShare = onShare,
        onOpenWith = onOpenWith,
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onPrev, enabled = state.index > 0) { Text("Previous") }
                TextButton(
                    onClick = onNext,
                    enabled = state.index + 1 < state.siblingUris.size,
                ) { Text("Next") }
            }
        },
    ) { _, _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(readerBackground(state.theme))
                .pointerInput(state.index) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(state.siblingUris.getOrNull(state.index) ?: state.document.uri)
                    .size(2048)
                    .build(),
                contentDescription = state.document.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpreadsheetViewer(
    state: ViewerUiState.SpreadsheetReady,
    onBack: () -> Unit,
    onSelectSheet: (Int) -> Unit,
    onSelectCell: (Int, Int) -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onFind: (String) -> Unit,
    onTheme: (String) -> Unit,
) {
    val columnCount = (state.rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
    val fg = readerForeground(state.theme)
    val gridLine = fg.copy(alpha = 0.18f)
    val headerBg = fg.copy(alpha = 0.06f)
    val hScroll = rememberScrollState()
    val cellWidth = 120.dp
    val rowHeaderWidth = 44.dp
    var findOpen by remember { mutableStateOf(false) }

    ViewerShell(
        title = state.document.displayName,
        subtitle = state.sheetNames.getOrNull(state.sheetIndex),
        theme = state.theme,
        onBack = onBack,
        onFavorite = onFavorite,
        onShare = onShare,
        onOpenWith = onOpenWith,
        overflowItems = listOf("Find" to { findOpen = true }) + themeMenu(onTheme),
        bottomBar = {
            if (state.sheetNames.size > 1) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.sheetNames) { index, name ->
                        FilterChip(
                            selected = index == state.sheetIndex,
                            onClick = { onSelectSheet(index) },
                            label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { _, _ ->
        Column(modifier = Modifier.fillMaxSize().background(readerBackground(state.theme))) {
            if (findOpen) {
                FindBar(query = state.findQuery, onQuery = onFind, onClose = { findOpen = false })
            }
            state.selectedCell?.let { (r, c) ->
                val rich = state.cells.getOrNull(r)?.getOrNull(c)
                val text = rich?.display ?: state.rows.getOrNull(r)?.getOrNull(c).orEmpty()
                val formulaLine = rich?.formula?.let { "=$it" }
                val detail = buildString {
                    append("R${r + 1}C${c + 1}: $text")
                    if (formulaLine != null) {
                        append("\n")
                        append(formulaLine)
                        if (rich.raw.isNotBlank()) append(" → ${rich.raw}")
                    }
                }
                Text(
                    detail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .padding(8.dp),
                    color = fg,
                    fontSize = 13.sp,
                    maxLines = 4,
                )
            }
            if (state.rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val gridWidth = rowHeaderWidth + cellWidth * columnCount
                Column(modifier = Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                    Row(
                        modifier = Modifier
                            .width(gridWidth)
                            .background(headerBg)
                            .border(0.5.dp, gridLine),
                    ) {
                        Text("", modifier = Modifier.width(rowHeaderWidth).padding(6.dp), fontSize = 11.sp)
                        repeat(columnCount) { col ->
                            Text(
                                columnLetter(col),
                                color = fg.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .width(cellWidth)
                                    .border(0.5.dp, gridLine)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                    LazyColumn(modifier = Modifier.width(gridWidth).fillMaxHeight()) {
                        itemsIndexed(state.rows, key = { i, _ -> i }) { rowIndex, row ->
                            val isHeader = rowIndex == 0
                            Row(
                                modifier = Modifier
                                    .width(gridWidth)
                                    .background(if (isHeader) headerBg else Color.Transparent)
                                    .border(0.5.dp, gridLine),
                            ) {
                                Text(
                                    (rowIndex + 1).toString(),
                                    color = fg.copy(alpha = 0.55f),
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .width(rowHeaderWidth)
                                        .border(0.5.dp, gridLine)
                                        .padding(4.dp, 8.dp),
                                )
                                repeat(columnCount) { col ->
                                    val rich = state.cells.getOrNull(rowIndex)?.getOrNull(col)
                                    if (rich?.covered == true) {
                                        val spannedByEarlier = (0 until col).any { c ->
                                            val earlier = state.cells.getOrNull(rowIndex)?.getOrNull(c)
                                            earlier != null && !earlier.covered &&
                                                c + earlier.colSpan > col
                                        }
                                        if (!spannedByEarlier) {
                                            // Later rows under a vertical merge: keep column alignment.
                                            Box(
                                                modifier = Modifier
                                                    .width(cellWidth)
                                                    .heightIn(min = 36.dp),
                                            )
                                        }
                                        return@repeat
                                    }
                                    val cell = rich?.display ?: row.getOrNull(col).orEmpty()
                                    val colSpan = (rich?.colSpan ?: 1).coerceAtLeast(1)
                                    val rowSpan = (rich?.rowSpan ?: 1).coerceAtLeast(1)
                                    val selected = state.selectedCell == (rowIndex to col)
                                    val match = state.findQuery.isNotBlank() &&
                                        cell.contains(state.findQuery, ignoreCase = true)
                                    Text(
                                        cell,
                                        color = when {
                                            match -> Color(0xFFB45309)
                                            else -> fg
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .width(cellWidth * colSpan)
                                            .heightIn(min = 36.dp * rowSpan)
                                            .zIndex(if (rowSpan > 1 || colSpan > 1) 1f else 0f)
                                            .border(
                                                if (selected) 2.dp else 0.5.dp,
                                                if (selected) MaterialTheme.colorScheme.primary else gridLine,
                                            )
                                            .clickable { onSelectCell(rowIndex, col) }
                                            .padding(8.dp),
                                    )
                                }
                            }
                        }
                        if (state.truncated) {
                            item {
                                Text(
                                    "Showing first ${state.rows.size} rows",
                                    color = fg.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlidesViewer(
    state: ViewerUiState.SlidesReady,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onTheme: (String) -> Unit,
) {
    val slide = state.current
    val fg = readerForeground(state.theme)
    var scale by remember(state.slideIndex) { mutableFloatStateOf(1f) }
    var showNotes by remember { mutableStateOf(false) }
    val bgColor = remember(slide?.backgroundHint) {
        runCatching {
            slide?.backgroundHint?.takeIf { it.startsWith("#") }?.let { Color(android.graphics.Color.parseColor(it)) }
        }.getOrNull() ?: Color.White
    }

    ViewerShell(
        title = "Slide ${state.slideIndex + 1} of ${state.slideCount}",
        subtitle = state.document.displayName,
        theme = state.theme,
        onBack = onBack,
        onFavorite = onFavorite,
        onShare = onShare,
        onOpenWith = onOpenWith,
        overflowItems = listOf(
            if (showNotes) "Hide notes" to { showNotes = false }
            else "Speaker notes" to { showNotes = true },
        ) + themeMenu(onTheme),
        bottomBar = {
            Column {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.slideCount) { index ->
                        FilterChip(
                            selected = index == state.slideIndex,
                            onClick = { onJump(index) },
                            label = { Text("${index + 1}") },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onPrev, enabled = state.slideIndex > 0) { Text("Previous") }
                    TextButton(
                        onClick = onNext,
                        enabled = state.slideIndex + 1 < state.slideCount,
                    ) { Text("Next") }
                }
            }
        },
    ) { _, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(readerBackground(state.theme))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .border(1.dp, fg.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .pointerInput(state.slideIndex) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (state.loadingSlide || slide == null) {
                    CircularProgressIndicator()
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        slide.imagePaths.forEach { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = slide.title.ifBlank { "Slide image" },
                                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        slide.placeholders.forEach { label ->
                            Text(
                                "[$label]",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        if (slide.body.isNotBlank() && slide.body != "(Empty slide)") {
                            Text(
                                slide.body,
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 22.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (slide.imagePaths.isEmpty() && slide.placeholders.isEmpty()) {
                            Text("(Empty slide)", color = Color.Gray)
                        }
                    }
                }
            }
            if (showNotes && !slide?.notes.isNullOrBlank()) {
                HorizontalDivider(color = fg.copy(alpha = 0.15f))
                Text("Notes", style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.7f))
                Text(slide?.notes.orEmpty(), color = fg.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun FindBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            textStyle = TextStyle(fontSize = 16.sp),
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Find in document", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            },
        )
        TextButton(onClick = onClose) { Text("Close") }
    }
}

private fun columnLetter(index: Int): String {
    var n = index + 1
    val sb = StringBuilder()
    while (n > 0) {
        n--
        sb.insert(0, ('A' + n % 26))
        n /= 26
    }
    return sb.toString()
}
