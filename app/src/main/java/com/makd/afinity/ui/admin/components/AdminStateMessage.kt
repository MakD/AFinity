package com.makd.afinity.ui.admin.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.components.EmptyState

@Composable
fun AdminStateMessage(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = painterResource(iconRes),
            title = title,
            message = message,
            badgeSize = 72.dp,
            iconSize = 32.dp,
        )
        if (actionText != null && onAction != null) {
            Button(
                onClick = onAction,
                colors =
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(text = actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
