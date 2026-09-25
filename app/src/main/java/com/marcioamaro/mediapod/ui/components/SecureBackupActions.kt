package com.marcioamaro.mediapod.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
    var legacyPasswordDialog by remember { mutableStateOf(false) }
    var legacyPassword by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            val ok = BackupRestoreManager.exportBackupToUri(context, uri)
            onResult(ok, if (ok) context.getString(com.marcioamaro.mediapod.R.string.backup_saved) else context.getString(com.marcioamaro.mediapod.R.string.backup_save_error))
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            when (BackupRestoreManager.restoreBackupFromUri(context, uri)) {
                BackupRestoreManager.RestoreResult.Success -> onResult(true, context.getString(com.marcioamaro.mediapod.R.string.backup_restored))
                BackupRestoreManager.RestoreResult.PasswordRequired -> {
                    pendingUri = uri
                    legacyPassword = ""
                    legacyPasswordDialog = true
                }
                is BackupRestoreManager.RestoreResult.Error -> onResult(false, context.getString(com.marcioamaro.mediapod.R.string.backup_restore_error))
            }
        }
    }
    if (legacyPasswordDialog) AlertDialog(
        onDismissRequest = { legacyPasswordDialog = false; legacyPassword = ""; pendingUri = null },
        title = { Text(context.getString(com.marcioamaro.mediapod.R.string.backup_restore)) },
        text = {
            Column {
                Text("Este é um backup antigo protegido por senha. Informe-a para restaurá-lo.")
                OutlinedTextField(
                    value = legacyPassword,
                    onValueChange = { legacyPassword = it },
                    label = { Text(context.getString(com.marcioamaro.mediapod.R.string.backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(enabled = legacyPassword.isNotBlank(), onClick = {
                val key = legacyPassword.toCharArray()
                val uri = pendingUri
                legacyPasswordDialog = false
                legacyPassword = ""
                pendingUri = null
                if (uri != null) scope.launch {
                    try {
                        val result = BackupRestoreManager.restoreBackupFromUri(context, uri, key)
                        val ok = result is BackupRestoreManager.RestoreResult.Success
                        onResult(ok, if (ok) context.getString(com.marcioamaro.mediapod.R.string.backup_restored) else context.getString(com.marcioamaro.mediapod.R.string.backup_restore_error))
                    } finally {
                        key.fill('\u0000')
                    }
                } else key.fill('\u0000')
            }) { Text(context.getString(com.marcioamaro.mediapod.R.string.library_continue)) }
        },
        dismissButton = { TextButton(onClick = { legacyPasswordDialog = false; legacyPassword = ""; pendingUri = null }) { Text(context.getString(com.marcioamaro.mediapod.R.string.feature_cancel)) } }
    )
    return BackupActions(
        export = { export.launch("MediaPod_Backup_${System.currentTimeMillis()}.enc") },
        restore = { restore.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
    )
}
