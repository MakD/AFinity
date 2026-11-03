package com.makd.afinity.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private val MenuIconSize = 20.dp

@Composable
fun AfinityMenuItem(
    text: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    iconTint: Color? = null,
    enabled: Boolean = true,
    trailingText: String? = null,
) {
    val errorColor = MaterialTheme.colorScheme.error
    DropdownMenuItem(
        text = { Text(text = text, color = if (destructive) errorColor else Color.Unspecified) },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint ?: if (destructive) errorColor else LocalContentColor.current,
                modifier = Modifier.size(MenuIconSize),
            )
        },
        trailingIcon =
            trailingText?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
    )
}
