package com.makd.afinity.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.makd.afinity.R
import com.makd.afinity.data.repository.server.ServerVersionSupport

@Composable
fun UnsupportedServerDialog(
    version: String?,
    onSwitchAccount: () -> Unit,
    onServerSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_alert_triangle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(text = stringResource(R.string.server_unsupported_title)) },
        text = {
            Text(
                text =
                    if (version.isNullOrBlank()) {
                        stringResource(
                            R.string.server_unsupported_body_unknown,
                            ServerVersionSupport.minimumDisplay,
                        )
                    } else {
                        stringResource(
                            R.string.server_unsupported_body,
                            version,
                            ServerVersionSupport.minimumDisplay,
                        )
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onSwitchAccount) {
                Text(text = stringResource(R.string.server_unsupported_switch_account))
            }
        },
        dismissButton = {
            TextButton(onClick = onServerSettings) {
                Text(text = stringResource(R.string.server_management_title))
            }
        },
    )
}
