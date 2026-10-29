package com.docsoffice.app.feature.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsoffice.app.core.designsystem.theme.Accent
import com.docsoffice.app.core.designsystem.theme.Brand
import com.docsoffice.app.core.designsystem.theme.BrandDark
import com.docsoffice.app.core.designsystem.R as DsR

private data class OnboardingPage(
    val titleRes: Int,
    val bodyRes: Int,
)

private val pages = listOf(
    OnboardingPage(R.string.onboarding_title_manage, R.string.onboarding_body_manage),
    OnboardingPage(R.string.onboarding_title_private, R.string.onboarding_body_private),
    OnboardingPage(R.string.onboarding_title_access, R.string.onboarding_body_access),
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val context = LocalContext.current

    LaunchedEffect(state.page) {
        if (pagerState.currentPage != state.page) {
            pagerState.animateScrollToPage(state.page)
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            viewModel.completeAndStartScan()
            onFinished()
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.completeAndStartScan()
            onFinished()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            viewModel.saveSafUri(uri.toString())
            onFinished()
        }
    }

    fun requestFullAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                viewModel.completeAndStartScan()
                onFinished()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                manageStorageLauncher.launch(intent)
            }
        } else {
            legacyPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Brand.copy(alpha = 0.18f),
            Color(0xFFF7FAFF),
            Color.White,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(DsR.drawable.brand_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.3).sp,
                        ),
                        color = BrandDark,
                    )
                }
                if (state.page < 2) {
                    TextButton(onClick = viewModel::skipToAccess) {
                        Text(stringResource(R.string.onboarding_skip), color = Brand)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                OnboardingPageContent(pageIndex = page)
            }

            PageDots(
                count = pages.size,
                selected = state.page,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp),
            )

            AnimatedContent(
                targetState = state.page < 2,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 4 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically { it / 4 })
                },
                label = "onboarding_cta",
                modifier = Modifier.padding(bottom = 20.dp),
            ) { intro ->
                if (intro) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { viewModel.previousPage() },
                            enabled = state.page > 0,
                        ) {
                            Text(stringResource(R.string.onboarding_back))
                        }
                        Button(
                            onClick = { viewModel.nextPage() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand),
                            modifier = Modifier.height(48.dp),
                        ) {
                            Text(stringResource(R.string.onboarding_next))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { requestFullAccess() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        ) {
                            Text(stringResource(R.string.onboarding_allow))
                        }
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(stringResource(R.string.onboarding_choose_folders))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(pageIndex: Int) {
    val page = pages[pageIndex]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingHero(pageIndex = pageIndex)
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
            ),
            textAlign = TextAlign.Center,
            color = BrandDark,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingHero(pageIndex: Int) {
    val floatTransition = rememberInfiniteTransition(label = "logo_float")
    val floatOffset by floatTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float_y",
    )
    val scalePulse by floatTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (pageIndex) {
            0 -> Image(
                painter = painterResource(DsR.drawable.brand_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        translationY = floatOffset
                        scaleX = scalePulse
                        scaleY = scalePulse
                    }
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Fit,
            )
            1 -> PrivacyIllustration(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer { translationY = floatOffset * 0.5f },
            )
            else -> AccessIllustration(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer { translationY = floatOffset * 0.5f },
            )
        }
    }
}

@Composable
private fun PrivacyIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Back orange sheet
        drawRoundRect(
            color = Accent.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.12f, h * 0.18f),
            size = Size(w * 0.55f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.08f),
        )
        // Mid blue sheet
        drawRoundRect(
            color = Brand,
            topLeft = Offset(w * 0.28f, h * 0.14f),
            size = Size(w * 0.55f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.08f),
        )
        // Front white sheet
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.58f),
            cornerRadius = CornerRadius(w * 0.08f),
        )
        // Lock body
        val lockCx = w * 0.5f
        val lockTop = h * 0.38f
        drawRoundRect(
            color = BrandDark,
            topLeft = Offset(lockCx - w * 0.1f, lockTop + h * 0.08f),
            size = Size(w * 0.2f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.03f),
        )
        drawArc(
            color = BrandDark,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(lockCx - w * 0.08f, lockTop),
            size = Size(w * 0.16f, h * 0.14f),
            style = Stroke(width = w * 0.035f),
        )
    }
}

@Composable
private fun AccessIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Folder back
        drawRoundRect(
            color = BrandDark,
            topLeft = Offset(w * 0.18f, h * 0.28f),
            size = Size(w * 0.64f, h * 0.42f),
            cornerRadius = CornerRadius(w * 0.05f),
        )
        // Folder tab
        val tab = Path().apply {
            moveTo(w * 0.18f, h * 0.34f)
            lineTo(w * 0.18f, h * 0.26f)
            quadraticTo(w * 0.18f, h * 0.22f, w * 0.24f, h * 0.22f)
            lineTo(w * 0.42f, h * 0.22f)
            quadraticTo(w * 0.46f, h * 0.22f, w * 0.48f, h * 0.26f)
            lineTo(w * 0.52f, h * 0.34f)
            close()
        }
        drawPath(tab, color = Brand)
        // Folder front
        drawRoundRect(
            color = Brand,
            topLeft = Offset(w * 0.18f, h * 0.34f),
            size = Size(w * 0.64f, h * 0.36f),
            cornerRadius = CornerRadius(w * 0.05f),
        )
        // Accent paper peek
        drawRoundRect(
            color = Accent,
            topLeft = Offset(w * 0.3f, h * 0.3f),
            size = Size(w * 0.4f, h * 0.08f),
            cornerRadius = CornerRadius(w * 0.02f),
        )
        // Check mark
        val check = Path().apply {
            moveTo(w * 0.42f, h * 0.5f)
            lineTo(w * 0.48f, h * 0.56f)
            lineTo(w * 0.6f, h * 0.42f)
        }
        drawPath(
            check,
            color = Color.White,
            style = Stroke(width = w * 0.04f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

@Composable
private fun PageDots(
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (active) 22.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (active) Brand else Brand.copy(alpha = 0.25f)),
            )
        }
    }
}
