package com.makd.afinity.ui.admin.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.makd.afinity.R

class AdminNavigationGuard {
    var hasUnsavedChanges by mutableStateOf(false)
}

val LocalAdminNavigationGuard = staticCompositionLocalOf<AdminNavigationGuard?> { null }

@Composable
fun ReportUnsavedChanges(hasUnsavedChanges: Boolean) {
    val guard = LocalAdminNavigationGuard.current ?: return
    DisposableEffect(guard, hasUnsavedChanges) {
        guard.hasUnsavedChanges = hasUnsavedChanges
        onDispose { guard.hasUnsavedChanges = false }
    }
}

@Composable
fun AdminDiscardDialog(message: String, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.admin_metadata_discard_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        confirmButton = {
            TextButton(
                onClick = onDiscard,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.admin_metadata_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.admin_metadata_keep_editing))
            }
        },
    )
}
