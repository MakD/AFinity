package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.ui.components.AsyncImage

@Composable
fun EpgChannelCell(
    channel: AfinityChannel,
    onClick: () -> Unit,
    cellWidth: Dp,
    cellHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(cellWidth)
                .height(cellHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f).padding(4.dp), contentAlignment = Alignment.Center) {
            if (channel.images.primary != null) {
                AsyncImage(
                    imageUrl = channel.images.primary.toString(),
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_live_tv_nav),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text =
                buildString {
                    channel.channelNumber?.let { append("$it ") }
                    append(channel.name)
                },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
