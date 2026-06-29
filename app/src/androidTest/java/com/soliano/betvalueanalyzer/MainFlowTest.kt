package com.soliano.betvalueanalyzer

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboarding_to_keyless_automatic_feed() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("confirm_age").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }

        if (composeRule.onAllNodesWithTag("confirm_age").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("confirm_age").performClick()
        }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Prochains événements sportifs").assertExists()
        composeRule.onNodeWithText("Détection et prédictions Internet sans clé").assertExists()

        composeRule.onNodeWithTag("nav_Sports").performClick()
        composeRule.onNodeWithText("Mes sports").assertExists()
        composeRule.onNodeWithText("Ligues, tournois et GP").assertExists()

        composeRule.onNodeWithTag("nav_Settings").performClick()
        composeRule.onNodeWithText("Internet public, sans clé").assertExists()
        composeRule.onNodeWithText("Flux public actif").assertExists()
    }
}
