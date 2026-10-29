package com.docsoffice.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented: Library Tools tab vs More (Settings) — tools live under Tools, not More.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LibraryToolsNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun libraryTools_visible_andAbsentFromMore() {
        TestPrefs.dismissOnboardingIfShown(composeRule)

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag("nav_library_tools").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("nav_library_tools").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("library_tools_pdf").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("library_tools_pdf").assertIsDisplayed()
        composeRule.onNodeWithTag("library_tools_vault").assertIsDisplayed()
        composeRule.onNodeWithTag("library_tools_trash").assertIsDisplayed()
        composeRule.onNodeWithTag("library_tools_scan").assertIsDisplayed()

        composeRule.onNodeWithTag("library_tools_pdf").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("PDF tools").fetchSemanticsNodes().isNotEmpty()
        }
        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_more").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        // Tools tile grid must not appear under More/Settings.
        composeRule.onAllNodesWithTag("library_tools_pdf").assertCountEquals(0)
        composeRule.onNodeWithText("Theme").assertIsDisplayed()
        composeRule.onNodeWithText("Purge expired trash now").assertIsDisplayed()
    }
}
