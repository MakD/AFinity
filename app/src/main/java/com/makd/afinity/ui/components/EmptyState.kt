package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.preview.WindowPreviews

@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 120.dp,
    iconSize: Dp = 56.dp,
    badgeAlpha: Float = 1f,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(32.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(badgeSize)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                alpha = badgeAlpha
                            ),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@WindowPreviews
@Composable
private fun EmptyStateWindowPreview() {
    PreviewHost(modifier = Modifier.fillMaxSize()) {
        EmptyState(
            icon = painterResource(id = R.drawable.ic_server),
            title = stringResource(R.string.empty_servers_title),
            message = stringResource(R.string.empty_servers_message),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
