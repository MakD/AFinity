package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.preview.FontScalePreviews
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.preview.StressPreviews

@Composable
fun DetailPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else if (icon != null) {
                Icon(painter = icon, contentDescription = null)
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DetailPrimaryButtonPreviewContent() {
    PreviewHost {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailPrimaryButton(
                text = stringResource(R.string.action_resume_playback),
                onClick = {},
                icon = painterResource(id = R.drawable.ic_player_play_filled),
            )
            DetailPrimaryButton(
                text = stringResource(R.string.play_error_missing_source),
                onClick = {},
                icon = painterResource(id = R.drawable.ic_alert_triangle),
                enabled = false,
            )
            DetailPrimaryButton(
                text = stringResource(R.string.status_loading),
                onClick = {},
                enabled = false,
                isLoading = true,
            )
        }
    }
}

@FontScalePreviews
@Composable
private fun DetailPrimaryButtonFontScalePreview() {
    DetailPrimaryButtonPreviewContent()
}

@StressPreviews
@Composable
private fun DetailPrimaryButtonStressPreview() {
    DetailPrimaryButtonPreviewContent()
}
