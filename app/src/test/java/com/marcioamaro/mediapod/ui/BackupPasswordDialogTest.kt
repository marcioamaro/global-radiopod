package com.marcioamaro.mediapod.ui

import android.app.Application
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.marcioamaro.mediapod.ui.components.rememberSecureBackupActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "pt-rBR")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupPasswordDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun exportRequiresLongEnoughMatchingPasswordsAndCanBeCancelled() {
        compose.setContent {
            val actions = rememberSecureBackupActions { _, _ -> }
            Button(onClick = actions.export) { Text("Exportar") }
        }
        compose.onNodeWithText("Exportar").performClick()
        compose.onNodeWithText("Continuar").assertIsNotEnabled()
        compose.onNodeWithText("Senha").performTextInput("safe-password")
        compose.onNodeWithText("Confirmar senha").performTextInput("different")
        compose.onNodeWithText("Continuar").assertIsNotEnabled()
        compose.onNodeWithText("Confirmar senha").performTextReplacement("safe-password")
        compose.onNodeWithText("Continuar").assertIsEnabled()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Proteger backup").assertDoesNotExist()
    }
}
