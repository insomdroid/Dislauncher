package com.insomdroid.dislauncher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherNavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingDrawerAndActivityResumeRemainUsable() {
        // The CI emulator starts with a clean app data directory and shows onboarding.
        composeRule.onNodeWithText("Continue").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Skip").assertIsDisplayed().performClick()

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Search Apps").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Search Apps").assertIsDisplayed()

        // Exercise the lifecycle transition that previously allowed background work to linger.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search Apps").assertIsDisplayed()

        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Search Apps").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Search Apps").assertIsDisplayed()
    }
}
