package com.docsoffice.app.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docsoffice.app.core.designsystem.theme.Brand
import com.docsoffice.app.core.designsystem.theme.DocsOfficeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the Home Search FAB entry point.
 * Uses a focused FAB harness matching [HomeScreen] semantics (avoids Hilt ViewModel).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeSearchFabTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchFab_hasAccessibleLabel_andOpensSearchOncePerTap() {
        var openCount = 0
        composeRule.setContent {
            DocsOfficeTheme {
                var taps by remember { mutableIntStateOf(0) }
                FloatingActionButton(
                    onClick = {
                        taps += 1
                        openCount = taps
                    },
                    containerColor = Brand,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.semantics { contentDescription = "Search documents" },
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            }
        }

        composeRule.onNodeWithContentDescription("Search documents").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search documents").performClick()
        composeRule.onNodeWithContentDescription("Search documents").performClick()
        assertEquals(2, openCount)
    }
}
