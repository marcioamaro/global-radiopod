package com.marcioamaro.mediapod.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.marcioamaro.mediapod.util.BackupRestoreManager
import kotlinx.coroutines.launch

data class BackupActions(val export: () -> Unit, val restore: () -> Unit)

@Composable
fun rememberSecureBackupActions(onResult: (Boolean, String) -> Unit): BackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    DisposableEffect(Unit) { onDispose { pendingPassword?.fill('\u0000') } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val key = pendingPassword
        pendingPassword = null
        if (uri != null && key != null) scope.launch {
            try {
                val ok = BackupRestoreManager.exportBackupToUri(context, uri, key)
                onResult(ok, if (ok) context.getString(com.marcioamaro.mediapod.R.string.backup_saved) else context.getString(com.marcioamaro.mediapod.R.string.backup_save_error))
            } finally { key.fill('\u0000') }
        } else key?.fill('\u0000')
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; password = ""; mode = "restore" }
    }
    if (mode != null) AlertDialog(
        onDismissRequest = { mode = null; password = ""; confirmation = ""; pendingUri = null },
        title = { Text(if (mode == "export") context.getString(com.marcioamaro.mediapod.R.string.backup_protect) else context.getString(com.marcioamaro.mediapod.R.string.backup_restore)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(if (mode == "export") context.getString(com.marcioamaro.mediapod.R.string.backup_password_help)
                    else context.getString(com.marcioamaro.mediapod.R.string.backup_legacy_help))
                OutlinedTextField(password, { password = it }, label = { Text(context.getString(com.marcioamaro.mediapod.R.string.backup_password)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (mode == "export") OutlinedTextField(confirmation, { confirmation = it }, label = { Text(context.getString(com.marcioamaro.mediapod.R.string.backup_confirm)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = mode == "restore" || (password.length >= 8 && password == confirmation), onClick = {
                val key = password.toCharArray()
                val exporting = mode == "export"
                mode = null; password = ""; confirmation = ""
                if (exporting) {
                    pendingPassword?.fill('\u0000'); pendingPassword = key
                    export.launch("MediaPod_Backup_${System.currentTimeMillis()}.enc")
                } else {
                    val uri = pendingUri; pendingUri = null
                    if (uri != null) scope.launch {
                        try {
                            val result = BackupRestoreManager.restoreBackupFromUri(context, uri, key)
                            val ok = result is BackupRestoreManager.RestoreResult.Success
                            onResult(ok, if (ok) context.getString(com.marcioamaro.mediapod.R.string.backup_restored) else context.getString(com.marcioamaro.mediapod.R.string.backup_restore_error))
                        } finally { key.fill('\u0000') }
                    } else key.fill('\u0000')
                }
            }) { Text(context.getString(com.marcioamaro.mediapod.R.string.library_continue)) }
        },
        dismissButton = { TextButton(onClick = { mode = null; password = ""; confirmation = ""; pendingUri = null }) { Text(context.getString(com.marcioamaro.mediapod.R.string.feature_cancel)) } }
    )
    return BackupActions(export = { password = ""; confirmation = ""; mode = "export" }, restore = { restore.launch(arrayOf("*/*")) })
}
