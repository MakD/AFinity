package com.makd.afinity.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

@Composable
fun DownloadListItemRow(
    imageUrl: String?,
    title: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    imageAlpha: Float = 1f,
    onClick: (() -> Unit)? = null,
    supportingContent: @Composable () -> Unit,
) {
    ListItem(
        modifier =
            if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            AsyncImage(
                imageUrl = imageUrl,
                contentDescription = null,
                modifier =
                    Modifier.width(56.dp)
                        .aspectRatio(aspectRatio)
                        .clip(RoundedCornerShape(6.dp))
                        .alpha(imageAlpha),
                contentScale = ContentScale.Crop,
                targetWidth = 56.dp,
                targetHeight = 56.dp / aspectRatio,
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = supportingContent,
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.cd_delete_download),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        },
    )
}