package com.docsoffice.app.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        viewModel.setPermissionDenied(!granted)
        if (granted) viewModel.showCamera()
    }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addFromGallery(uris)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.savedDocId) {
        state.savedDocId?.let(onSaved)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan documents") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.discardSession()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { gallery.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Add from photos")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Capture pages with the camera, adjust corners, then save a PDF. Processing stays on device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                !hasCameraPermission || state.permissionDenied -> {
                    Text("Camera permission is required for scanning.")
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Grant camera permission") }
                    OutlinedButton(
                        onClick = { gallery.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Add from photos instead") }
                }
                state.phase == ScanPhase.CAMERA -> {
                    CameraCapturePane(
                        enabled = !state.capturing,
                        onCaptureFile = viewModel::createCaptureFile,
                        onStarted = viewModel::onCaptureStarted,
                        onSaved = viewModel::onCaptureSaved,
                        onFailed = viewModel::onCaptureFailed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    if (state.pagePaths.isNotEmpty()) {
                        OutlinedButton(
                            onClick = viewModel::showPages,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Review ${state.pagePaths.size} page(s)") }
                    }
                }
                state.phase == ScanPhase.REVIEW -> {
                    val path = state.pendingPath
                    if (path != null) {
                        CropReviewPane(
                            imagePath = path,
                            corners = state.corners,
                            enhanceMode = state.enhanceMode,
                            autoDetected = state.autoDetected,
                            detecting = state.detecting,
                            onCornersChange = viewModel::updateCorners,
                            onCycleEnhance = viewModel::cycleEnhanceMode,
                            onRetake = viewModel::retake,
                            onAccept = viewModel::acceptPage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
                state.phase == ScanPhase.PAGES -> {
                    PagesPane(
                        pagePaths = state.pagePaths,
                        saving = state.saving,
                        onRemove = viewModel::removePage,
                        onMoveUp = { index -> if (index > 0) viewModel.movePage(index, index - 1) },
                        onMoveDown = { index ->
                            if (index < state.pagePaths.lastIndex) viewModel.movePage(index, index + 1)
                        },
                        onAddMore = viewModel::showCamera,
                        onSave = viewModel::saveAsPdf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CameraCapturePane(
    enabled: Boolean,
    onCaptureFile: () -> File,
    onStarted: () -> Unit,
    onSaved: (File) -> Unit,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                            )
                        }.onFailure {
                            Log.e("ScanScreen", "Camera bind failed", it)
                            onFailed(it.message ?: "Camera unavailable")
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            },
        )
        Button(
            onClick = {
                if (!enabled) return@Button
                onStarted()
                val file = onCaptureFile()
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(
                    options,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onSaved(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onFailed(exception.message ?: "Capture failed")
                        }
                    },
                )
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Text("  Capture page", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun CropReviewPane(
    imagePath: String,
    corners: QuadCorners,
    enhanceMode: EnhanceMode,
    autoDetected: Boolean,
    detecting: Boolean,
    onCornersChange: (QuadCorners) -> Unit,
    onCycleEnhance: () -> Unit,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragIndex by remember { mutableStateOf(-1) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { size = it },
        ) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = "Captured page",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(corners, size) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragIndex = nearestCorner(offset, corners, size)
                            },
                            onDragEnd = { dragIndex = -1 },
                            onDragCancel = { dragIndex = -1 },
                            onDrag = { change, _ ->
                                change.consume()
                                if (dragIndex < 0 || size.width == 0 || size.height == 0) return@detectDragGestures
                                val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                onCornersChange(corners.withCorner(dragIndex, nx to ny))
                            },
                        )
                    },
            ) {
                if (size.width == 0) return@Canvas
                val points = listOf(
                    Offset(corners.topLeft.first * size.width, corners.topLeft.second * size.height),
                    Offset(corners.topRight.first * size.width, corners.topRight.second * size.height),
                    Offset(corners.bottomRight.first * size.width, corners.bottomRight.second * size.height),
                    Offset(corners.bottomLeft.first * size.width, corners.bottomLeft.second * size.height),
                )
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    lineTo(points[1].x, points[1].y)
                    lineTo(points[2].x, points[2].y)
                    lineTo(points[3].x, points[3].y)
                    close()
                }
                drawPath(path, Color.Cyan.copy(alpha = 0.35f))
                drawPath(path, Color.Cyan, style = Stroke(width = 4f))
                points.forEach { drawCircle(Color.Yellow, radius = 18f, center = it) }
            }
        }
        Text(
            when {
                detecting -> "Detecting document edges…"
                autoDetected -> "Auto-detected edges — drag corners to adjust. Mode: ${enhanceMode.name.replace('_', ' ')}"
                else -> "Adjust corners to crop. Mode: ${enhanceMode.name.replace('_', ' ')}"
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCycleEnhance, modifier = Modifier.weight(1f)) {
                Text("Enhance")
            }
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text("Retake")
            }
            Button(onClick = onAccept, modifier = Modifier.weight(1f), enabled = !detecting) {
                Text("Accept")
            }
        }
    }
}

@Composable
private fun PagesPane(
    pagePaths: List<String>,
    saving: Boolean,
    onRemove: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onAddMore: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${pagePaths.size} page(s)", style = MaterialTheme.typography.titleMedium)
        LazyRow(
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(pagePaths) { index, path ->
                Column(modifier = Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Page ${index + 1}",
                        modifier = Modifier
                            .width(120.dp)
                            .height(160.dp)
                            .aspectRatio(3f / 4f),
                        contentScale = ContentScale.Crop,
                    )
                    Text("Page ${index + 1}")
                    Row {
                        TextButton(onClick = { onMoveUp(index) }, enabled = index > 0) { Text("↑") }
                        TextButton(onClick = { onMoveDown(index) }, enabled = index < pagePaths.lastIndex) { Text("↓") }
                    }
                    TextButton(onClick = { onRemove(index) }) { Text("Remove") }
                }
            }
        }
        OutlinedButton(onClick = onAddMore, modifier = Modifier.fillMaxWidth(), enabled = !saving) {
            Text("Capture more")
        }
        Button(
            onClick = onSave,
            enabled = !saving && pagePaths.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "Saving…" else "Save as PDF")
        }
    }
}

private fun nearestCorner(offset: Offset, corners: QuadCorners, size: IntSize): Int {
    if (size.width == 0 || size.height == 0) return -1
    val points = listOf(
        Offset(corners.topLeft.first * size.width, corners.topLeft.second * size.height),
        Offset(corners.topRight.first * size.width, corners.topRight.second * size.height),
        Offset(corners.bottomRight.first * size.width, corners.bottomRight.second * size.height),
        Offset(corners.bottomLeft.first * size.width, corners.bottomLeft.second * size.height),
    )
    var best = -1
    var bestDist = 80f * 80f
    points.forEachIndexed { index, point ->
        val dx = point.x - offset.x
        val dy = point.y - offset.y
        val d = dx * dx + dy * dy
        if (d < bestDist) {
            bestDist = d
            best = index
        }
    }
    return best
}

private fun QuadCorners.withCorner(index: Int, value: Pair<Float, Float>): QuadCorners = when (index) {
    0 -> copy(topLeft = value)
    1 -> copy(topRight = value)
    2 -> copy(bottomRight = value)
    3 -> copy(bottomLeft = value)
    else -> this
}
