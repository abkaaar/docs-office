package com.docsoffice.app.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsoffice.app.core.designsystem.components.formatBytes
import com.docsoffice.app.core.designsystem.theme.SepiaBackground
import com.docsoffice.app.core.designsystem.theme.SepiaOnBackground
import com.docsoffice.app.core.model.Document

enum class ViewerErrorKind {
    CORRUPT,
    PASSWORD,
    TOO_LARGE,
    UNSUPPORTED,
    GENERIC,
}

fun viewerErrorKind(reason: String): ViewerErrorKind {
    val r = reason.lowercase()
    return when {
        "password" in r -> ViewerErrorKind.PASSWORD
        "large" in r || "memory" in r -> ViewerErrorKind.TOO_LARGE
        "damaged" in r || "corrupt" in r || "incomplete" in r -> ViewerErrorKind.CORRUPT
        "open with" in r || "unsupported" in r || "older office" in r || "can't be opened" in r ->
            ViewerErrorKind.UNSUPPORTED
        else -> ViewerErrorKind.GENERIC
    }
}

@Composable
fun readerBackground(theme: String): Color = when (theme) {
    "dark" -> Color(0xFF121212)
    "sepia" -> SepiaBackground
    else -> Color.White
}

@Composable
fun readerForeground(theme: String): Color = when (theme) {
    "dark" -> Color(0xFFEDEDED)
    "sepia" -> SepiaOnBackground
    else -> Color.Black
}

@Composable
fun ViewerLoadingSkeleton(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            ViewerTopBar(
                title = "Opening…",
                subtitle = null,
                chromeVisible = true,
                onBack = onBack,
                onFavorite = null,
                onShare = null,
                onOpenWith = null,
                overflowItems = emptyList(),
                extraActions = {},
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
            Text("Preparing document…", style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.7f))
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerShell(
    title: String,
    subtitle: String?,
    theme: String,
    onBack: () -> Unit,
    onFavorite: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onOpenWith: (() -> Unit)?,
    overflowItems: List<Pair<String, () -> Unit>> = emptyList(),
    extraActions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable (chromeVisible: Boolean, toggleChrome: () -> Unit) -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(true) }
    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ViewerTopBar(
                    title = title,
                    subtitle = subtitle,
                    chromeVisible = chromeVisible,
                    onBack = onBack,
                    onFavorite = onFavorite,
                    onShare = onShare,
                    onOpenWith = onOpenWith,
                    overflowItems = overflowItems,
                    extraActions = extraActions,
                )
            }
        },
        bottomBar = {
            if (chromeVisible && bottomBar != null) {
                bottomBar()
            }
        },
        containerColor = readerBackground(theme),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { chromeVisible = !chromeVisible },
        ) {
            content(chromeVisible) { chromeVisible = !chromeVisible }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerTopBar(
    title: String,
    subtitle: String?,
    chromeVisible: Boolean,
    onBack: () -> Unit,
    onFavorite: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onOpenWith: (() -> Unit)?,
    overflowItems: List<Pair<String, () -> Unit>>,
    extraActions: @Composable RowScope.() -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Back" },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        actions = {
            extraActions()
            if (onFavorite != null) {
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Favorite" },
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                }
            }
            if (onShare != null) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Share" },
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                }
            }
            if (overflowItems.isNotEmpty() || onOpenWith != null) {
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "More options" },
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        onOpenWith?.let {
                            DropdownMenuItem(
                                text = { Text("Open with…") },
                                onClick = {
                                    menuOpen = false
                                    it()
                                },
                            )
                        }
                        overflowItems.forEach { (label, action) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    menuOpen = false
                                    action()
                                },
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (chromeVisible) 1f else 0f),
        ),
    )
}

@Composable
fun ViewerErrorScreen(
    document: Document,
    reason: String,
    kind: ViewerErrorKind = viewerErrorKind(reason),
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
) {
    val title = when (kind) {
        ViewerErrorKind.PASSWORD -> "Password required"
        ViewerErrorKind.TOO_LARGE -> "File too large"
        ViewerErrorKind.CORRUPT -> "Damaged file"
        ViewerErrorKind.UNSUPPORTED -> "Can't open here"
        ViewerErrorKind.GENERIC -> "Couldn't open"
    }
    val details = buildList {
        if (document.extension.isNotBlank()) add(document.extension.uppercase())
        if (document.sizeBytes > 0) add(formatBytes(document.sizeBytes))
    }.joinToString(" · ")

    Scaffold(
        topBar = {
            ViewerTopBar(
                title = document.displayName.ifBlank { title },
                subtitle = null,
                chromeVisible = true,
                onBack = onBack,
                onFavorite = null,
                onShare = null,
                onOpenWith = null,
                overflowItems = emptyList(),
                extraActions = {},
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (details.isNotBlank()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(reason, style = MaterialTheme.typography.bodyLarge)
            if (kind == ViewerErrorKind.PASSWORD) {
                Text(
                    "No password is stored or logged. This app cannot unlock the PDF itself " +
                        "with the current Android PdfRenderer API.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Button(
                onClick = onOpenWith,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { Text(if (kind == ViewerErrorKind.PASSWORD) "Open with… to unlock" else "Open with…") }
            androidx.compose.material3.OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { Text("Share") }
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text("Cancel")
            }
        }
    }
}
