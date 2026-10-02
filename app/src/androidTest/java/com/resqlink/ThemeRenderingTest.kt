package com.resqlink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.resqlink.core.ui.theme.ResQLinkTheme
import com.resqlink.domain.model.ThemePreference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemeRenderingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightThemeUsesLightBackground() {
        val color = renderAndReadBackground(ThemePreference.LIGHT)
        assertTrue("Expected a light background, got $color", color.luminance() > 0.5f)
    }

    @Test
    fun darkThemeUsesDarkBackground() {
        val color = renderAndReadBackground(ThemePreference.DARK)
        assertTrue("Expected a dark background, got $color", color.luminance() < 0.5f)
    }

    private fun renderAndReadBackground(theme: ThemePreference) = with(composeRule) {
        setContent {
            ResQLinkTheme(theme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("theme-background"),
                )
            }
        }
        waitForIdle()
        val image = onNodeWithTag("theme-background").captureToImage()
        image.toPixelMap()[image.width / 2, image.height / 2]
    }
}
