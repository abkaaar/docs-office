package com.docsoffice.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.docsoffice.app.R
import com.docsoffice.app.feature.files.FilesScreen
import com.docsoffice.app.feature.home.HomeScreen
import com.docsoffice.app.feature.onboarding.OnboardingScreen
import com.docsoffice.app.feature.search.SearchScreen
import com.docsoffice.app.feature.scan.ScanScreen
import com.docsoffice.app.feature.settings.LibraryToolsScreen
import com.docsoffice.app.feature.settings.PdfToolsScreen
import com.docsoffice.app.feature.settings.SettingsScreen
import com.docsoffice.app.feature.settings.TrashScreen
import com.docsoffice.app.feature.vault.VaultScreen
import com.docsoffice.app.feature.viewer.ViewerScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val FILES = "files?category={category}"
    const val SEARCH = "search"
    const val LIBRARY_TOOLS = "library_tools"
    const val MORE = "more"
    const val TRASH = "trash"
    const val SCAN = "scan"
    const val TOOLS = "tools"
    const val VAULT = "vault"
    const val VIEWER = "viewer/{documentId}"

    fun files(category: String? = null): String =
        if (category == null) "files?category=" else "files?category=$category"

    fun viewer(documentId: String) = "viewer/$documentId"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val topLevel = listOf(
    TopLevelDestination(Routes.HOME, R.string.nav_home, Icons.Default.Home),
    TopLevelDestination("files?category=", R.string.nav_files, Icons.Default.Folder),
    TopLevelDestination(Routes.LIBRARY_TOOLS, R.string.nav_tools, Icons.Default.Build),
    TopLevelDestination(Routes.MORE, R.string.nav_more, Icons.Default.MoreHoriz),
)

@Composable
fun DocsOfficeNavHost(
    openDocumentId: String? = null,
    onOpenDocumentConsumed: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val onboardingDone by viewModel.onboardingCompleted.collectAsStateWithLifecycle(initialValue = null)
    val navController = rememberNavController()

    if (onboardingDone == null) return

    val startDestination = if (onboardingDone == true) Routes.HOME else Routes.ONBOARDING
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.HOME ||
        currentRoute?.startsWith("files") == true ||
        currentRoute == Routes.LIBRARY_TOOLS ||
        currentRoute == Routes.MORE

    LaunchedEffect(openDocumentId, onboardingDone) {
        if (onboardingDone == true && openDocumentId != null) {
            navController.navigate(Routes.viewer(openDocumentId)) {
                launchSingleTop = true
            }
            onOpenDocumentConsumed()
        }
    }

    // Zero content insets so nested TopAppBars own the status bar; only bottomBar padding applies.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevel.forEach { dest ->
                        val selected = when (dest.route) {
                            Routes.HOME -> currentRoute == Routes.HOME
                            Routes.LIBRARY_TOOLS -> currentRoute == Routes.LIBRARY_TOOLS
                            Routes.MORE -> currentRoute == Routes.MORE
                            else -> currentRoute?.startsWith("files") == true
                        }
                        val label = stringResource(dest.labelRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (dest.route == Routes.HOME) {
                                    navController.popBackStack(Routes.HOME, inclusive = false)
                                } else {
                                    navController.navigate(dest.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = label) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("nav_${dest.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpen = { id -> navController.navigate(Routes.viewer(id)) },
                    onOpenCategory = { category ->
                        navController.navigate(Routes.files(category.name))
                    },
                    onOpenSearch = {
                        navController.navigate(Routes.SEARCH) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Routes.FILES,
                arguments = listOf(
                    navArgument("category") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) {
                FilesScreen(
                    onOpen = { id -> navController.navigate(Routes.viewer(id)) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpen = { id -> navController.navigate(Routes.viewer(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LIBRARY_TOOLS) {
                LibraryToolsScreen(
                    onOpenVault = { navController.navigate(Routes.VAULT) },
                    onOpenTrash = { navController.navigate(Routes.TRASH) },
                    onOpenScan = { navController.navigate(Routes.SCAN) },
                    onOpenTools = { navController.navigate(Routes.TOOLS) },
                )
            }
            composable(Routes.MORE) {
                SettingsScreen()
            }
            composable(Routes.VAULT) {
                VaultScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { id -> navController.navigate(Routes.viewer(id)) },
                )
            }
            composable(Routes.TRASH) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SCAN) {
                ScanScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Routes.viewer(id)) {
                            popUpTo(Routes.SCAN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.TOOLS) {
                PdfToolsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenScan = { navController.navigate(Routes.SCAN) },
                    onOpenResult = { id -> navController.navigate(Routes.viewer(id)) },
                )
            }
            composable(
                route = Routes.VIEWER,
                arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
            ) {
                ViewerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
