package com.makd.afinity.ui.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.ui.components.AsyncImage
import java.util.concurrent.TimeUnit

@Composable
fun MusicTrackRow(
    track: AfinityTrack,
    isPlaying: Boolean = false,
    trackNumber: Int? = null,
    showAlbumArt: Boolean = true,
    onClick: () -> Unit,
    onInstantMix: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onAddNext: (() -> Unit)? = null,
    onAddLast: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val hasMenuItems = onInstantMix != null || onStartRadio != null || onAddNext != null || onAddLast != null || onFavorite != null || onAddToPlaylist != null || onRemoveFromPlaylist != null || onDownload != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAlbumArt) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    imageUrl = track.images.primary?.toString(),
                    contentDescription = track.album,
                    blurHash = track.images.primaryImageBlurHash,
                    targetWidth = 48.dp,
                    targetHeight = 48.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_music),
                            contentDescription = "Now playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        } else if (trackNumber != null) {
            Text(
                text = trackNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                track.artist ?: track.artists.firstOrNull(),
                if (showAlbumArt) track.album else null,
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isDownloaded) {
            Icon(
                painter = painterResource(R.drawable.ic_sd_card),
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp).size(14.dp),
            )
        }

        Text(
            text = formatDuration(track.runtimeTicks / 10_000L),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )

        if (hasMenuItems) {
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dots_vertical),
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (onFavorite != null) {
                        DropdownMenuItem(
                            text = { Text(if (track.favorite) "Remove from Favorites" else "Add to Favorites") },
                            onClick = { showMenu = false; onFavorite() },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(if (track.favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                                    contentDescription = null,
                                    tint = if (track.favorite) Color.Red else LocalContentColor.current,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    if (onInstantMix != null) {
                        DropdownMenuItem(
                            text = { Text("Instant Mix") },
                            onClick = { showMenu = false; onInstantMix() },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_compass),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    if (onStartRadio != null) {
                        DropdownMenuItem(
                            text = { Text("Start Radio") },
                            onClick = { showMenu = false; onStartRadio() },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_radio),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    if (onAddNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play Next") },
                            onClick = { showMenu = false; onAddNext() },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_player_skip_forward),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    if (onAddLast != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Queue") },
                            onClick = { showMenu = false; onAddLast() },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_playlist_alt),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist") },
                            onClick = { showMenu = false; onAddToPlaylist() },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_playlist),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from Playlist") },
                            onClick = { showMenu = false; onRemoveFromPlaylist() },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    if (onDownload != null) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            onClick = { showMenu = false; onDownload() },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_download),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(minutes, seconds)
}