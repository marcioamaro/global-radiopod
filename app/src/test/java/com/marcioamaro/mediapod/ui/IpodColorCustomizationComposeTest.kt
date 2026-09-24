package com.marcioamaro.mediapod.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.marcioamaro.mediapod.data.model.IpodPalette
import com.marcioamaro.mediapod.ui.components.AmbilWarnaColorPickerDialog
import com.marcioamaro.mediapod.ui.components.ClickWheel
import com.marcioamaro.mediapod.ui.components.ColorPickerTarget
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class IpodColorCustomizationComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testAmbilWarnaDialogRendersTitleAndPreview() {
        var selectedColor: Long? = null
        var dismissed = false

        val initialPalette = IpodPalette(
            bodyColor = 0xFFF1F5F9,
            wheelColor = 0xFFE2E4E8,
            wheelTextColor = 0xFF475569,
            centerButtonColor = 0xFFFFFFFF
        )

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp, 600.dp)) {
                    AmbilWarnaColorPickerDialog(
                        title = "Cor da Carcaça (AmbilWarna)",
                        initialColor = 0xFFF1F5F9,
                        target = ColorPickerTarget.CHASSIS,
                        currentPalette = initialPalette,
                        onColorSelected = { selectedColor = it },
                        onDismissRequest = { dismissed = true }
                    )
                }
            }
        }

        // Title and buttons must exist in tree
        composeTestRule.onNodeWithText("Cor da Carcaça (AmbilWarna)", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("PRÉVIA REALISTA DO IPOD", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Confirmar", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Cancelar", useUnmergedTree = true).assertExists()

        // Clicking confirm executes callback
        composeTestRule.onNodeWithTag("ambilwarna_confirm_button", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        assertNotNull(selectedColor)
    }

    @Test
    fun testAmbilWarnaDialogDisplaysWcagWarningOnLowContrast() {
        // Black body with black wheel
        val lowContrastPalette = IpodPalette(
            bodyColor = 0xFF0F172A,
            wheelColor = 0xFF0F172A,
            wheelTextColor = 0xFF0F172A,
            centerButtonColor = 0xFF0F172A
        )

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp, 600.dp)) {
                    AmbilWarnaColorPickerDialog(
                        title = "Cor da Click Wheel",
                        initialColor = 0xFF0F172A,
                        target = ColorPickerTarget.CLICK_WHEEL,
                        currentPalette = lowContrastPalette,
                        onColorSelected = {},
                        onDismissRequest = {}
                    )
                }
            }
        }

        // WCAG Warning must exist in tree for low-contrast combination
        composeTestRule.onNodeWithText("Aviso WCAG: Baixo contraste ou cores muito semelhantes.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun testClickWheelRendersWithConfiguredColors() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(300.dp, 300.dp)) {
                    ClickWheel(
                        onRotaryScroll = {},
                        onCenterClick = {},
                        onMenuClick = {},
                        onPlayPauseClick = {},
                        onPrevClick = {},
                        onNextClick = {},
                        wheelColor = androidx.compose.ui.graphics.Color(0xFFDC2626),
                        textColor = androidx.compose.ui.graphics.Color.White,
                        centerButtonColor = androidx.compose.ui.graphics.Color(0xFF111111)
                    )
                }
            }
        }

        // ClickWheel MENU label must exist in tree
        composeTestRule.onNodeWithText("MENU", useUnmergedTree = true).assertExists()
    }
}
