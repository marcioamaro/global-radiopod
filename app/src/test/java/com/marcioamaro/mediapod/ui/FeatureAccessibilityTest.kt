package com.marcioamaro.mediapod.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.marcioamaro.mediapod.ui.components.BookmarkEditor
import com.marcioamaro.mediapod.ui.components.PreferenceToggle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "pt-rBR")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeatureAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun toggleHasNamedAccessibleTargetAtLeast48Dp() {
        var selected = false
        compose.setContent { PreferenceToggle("Downloads somente por Wi-Fi", false) { selected = it } }
        compose.onNodeWithText("Downloads somente por Wi-Fi").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(selected)
    }
    @Test fun bookmarkEditorSupportsLargeTextAndRejectsNegativeTimes() {
        var result: Long? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                BookmarkEditor(15000, "Nota", {}, { time, _ -> result = time })
            }
        }
        compose.onNodeWithText("Tempo em segundos").performScrollTo().performTextReplacement("-1")
        compose.onNodeWithText("Salvar").assertIsNotEnabled()
        compose.onNodeWithText("Tempo em segundos").performTextReplacement("60")
        compose.onNodeWithText("Salvar").performClick()
        assertEquals(60000L, result)
    }
}
