package com.makd.afinity.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

@Composable
fun AfinitySplashScreen(
    statusText: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                contentDescription = stringResource(R.string.cd_app_logo),
                modifier = Modifier.size(160.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.splash_powered_by),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            val showProgress = progress != null
            val currentProgress = progress ?: 0f

            LinearProgressIndicator(
                progress = { currentProgress },
                modifier =
                    Modifier.width(240.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .alpha(if (showProgress) 1f else 0f),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    stringResource(R.string.splash_progress_fmt, (currentProgress * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.alpha(if (showProgress) 1f else 0f),
            )
        }
    }
}
