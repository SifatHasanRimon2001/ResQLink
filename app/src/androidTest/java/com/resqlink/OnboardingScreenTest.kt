package com.resqlink

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.resqlink.core.ui.theme.ResQLinkTheme
import com.resqlink.domain.model.ThemePreference
import com.resqlink.feature.onboarding.OnboardingScreen
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun firstPageExplainsOfflineSafety() {
        composeRule.setContent {
            ResQLinkTheme(ThemePreference.LIGHT) { OnboardingScreen(onComplete = {}) }
        }
        composeRule.onNodeWithText("Safety, without the noise.").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
    }
}
