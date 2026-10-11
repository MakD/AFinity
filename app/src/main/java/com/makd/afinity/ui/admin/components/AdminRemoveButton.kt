package com.makd.afinity.ui.admin.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

private const val DISABLED_ALPHA = 0.38f

@Composable
fun AdminRemoveButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tint = MaterialTheme.colorScheme.error
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(
            painter = painterResource(id = R.drawable.ic_close),
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = DISABLED_ALPHA),
            modifier = Modifier.size(18.dp),
        )
    }
}
