package com.docsoffice.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented: Home Search FAB → Search → Back.
 * Uses the production [DocsOfficeApp] Hilt graph (AndroidJUnitRunner).
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class HomeSearchNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun searchFab_opensSearch_andBackRestoresHome() {
        TestPrefs.dismissOnboardingIfShown(composeRule)

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag("home_search_fab").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("home_search_fab").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search documents").assertIsDisplayed()

        composeRule.onNodeWithTag("home_search_fab").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Search", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home_search_fab").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_search_fab").assertIsDisplayed()
    }
}
