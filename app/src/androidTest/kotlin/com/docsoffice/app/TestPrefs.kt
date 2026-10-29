package com.docsoffice.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream

/**
 * Onboarding helpers for instrumentation. Does not open a second DataStore.
 */
object TestPrefs {
    fun grantAllFilesAccess(packageName: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val cmd = "appops set $packageName MANAGE_EXTERNAL_STORAGE allow"
        automation.executeShellCommand(cmd).use { stream ->
            FileInputStream(stream.fileDescriptor).readBytes()
        }
    }

    fun <A : androidx.activity.ComponentActivity> dismissOnboardingIfShown(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<A>, A>,
    ) {
        grantAllFilesAccess(composeRule.activity.packageName)
        // appops can recreate the activity; wait until Compose is attached again.
        composeRule.waitUntil(timeoutMillis = 25_000) {
            hasAnyComposeNode(composeRule) {
                onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodesWithText("Docs Office").fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodesWithText("Allow access").fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodes(hasTestTag("home_search_fab")).fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodes(hasTestTag("nav_library_tools")).fetchSemanticsNodes().isNotEmpty()
            }
        }

        if (hasAnyComposeNode(composeRule) {
                onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
            }
        ) {
            composeRule.onNodeWithText("Skip").performClick()
            composeRule.waitForIdle()
        }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            hasAnyComposeNode(composeRule) {
                onAllNodesWithText("Allow access").fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodesWithText("Docs Office").fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodes(hasTestTag("home_search_fab")).fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodes(hasTestTag("nav_library_tools")).fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (hasAnyComposeNode(composeRule) {
                onAllNodesWithText("Allow access").fetchSemanticsNodes().isNotEmpty()
            }
        ) {
            composeRule.onNodeWithText("Allow access").performClick()
            composeRule.waitForIdle()
        }
    }

    private fun <A : androidx.activity.ComponentActivity> hasAnyComposeNode(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<A>, A>,
        block: AndroidComposeTestRule<ActivityScenarioRule<A>, A>.() -> Boolean,
    ): Boolean = runCatching { composeRule.block() }.getOrDefault(false)
}
