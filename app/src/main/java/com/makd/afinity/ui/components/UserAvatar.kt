package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

@Composable
fun UserAvatar(
    imageUrl: String?,
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier.size(size).background(containerColor, CircleShape).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name?.trim()?.firstOrNull()

        when {
            imageUrl != null ->
                AsyncImage(
                    imageUrl = imageUrl,
                    contentDescription = contentDescription,
                    targetWidth = size,
                    targetHeight = size,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            initial != null ->
                Text(
                    text = initial.uppercase(),
                    style =
                        when {
                            size < 32.dp -> MaterialTheme.typography.labelMedium
                            size < 48.dp -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleLarge
                        },
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            else ->
                Icon(
                    painter = painterResource(id = R.drawable.ic_user_circle),
                    contentDescription = contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(size * 0.6f),
                )
        }
    }
}
