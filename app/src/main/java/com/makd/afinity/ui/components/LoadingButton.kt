package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.preview.FontScalePreviews
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.preview.StressPreviews

@Composable
fun LoadingButton(
    loading: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun LoadingButtonPreviewContent() {
    PreviewHost {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingButton(
                loading = false,
                text = stringResource(R.string.btn_test_connection),
                onClick = {},
            )
            LoadingButton(
                loading = false,
                text = stringResource(R.string.jellyseerr_quick_connect_button),
                onClick = {},
            )
            LoadingButton(loading = true, text = "", onClick = {})
        }
    }
}

@FontScalePreviews
@Composable
private fun LoadingButtonFontScalePreview() {
    LoadingButtonPreviewContent()
}

@StressPreviews
@Composable
private fun LoadingButtonStressPreview() {
    LoadingButtonPreviewContent()
}
