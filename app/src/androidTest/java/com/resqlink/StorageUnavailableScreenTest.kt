package com.resqlink

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.resqlink.core.ui.theme.ResQLinkTheme
import com.resqlink.feature.app.StorageUnavailableScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StorageUnavailableScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failureScreenExplainsPreservationAndOffersRetry() {
        var retries = 0
        compose.setContent { ResQLinkTheme(com.resqlink.domain.model.ThemePreference.SYSTEM) { StorageUnavailableScreen { retries++ } } }
        compose.onNodeWithText("Saved data is unavailable").assertExists()
        compose.onNodeWithText("Your saved files have been kept.", substring = true).assertExists()
        compose.onNodeWithText("For urgent help", substring = true).assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }
}
