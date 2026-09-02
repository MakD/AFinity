package com.makd.afinity.ui.music.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.ui.music.components.MusicAlbumCard

private val AlbumCardWidth = 140.dp

@Composable
fun AlbumRelatedSection(
    title: String,
    albums: List<AfinityAlbum>,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
    showArtist: Boolean = true,
    onViewAllClick: (() -> Unit)? = null,
) {
    if (albums.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.then(
                        if (onViewAllClick != null)
                            Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onViewAllClick,
                            )
                        else Modifier
                    )
                    .padding(top = 32.dp, bottom = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onViewAllClick != null) Modifier.weight(1f, fill = false) else Modifier,
            )

            if (onViewAllClick != null) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = stringResource(R.string.cd_view_all),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).size(24.dp),
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                Box(modifier = Modifier.width(AlbumCardWidth)) {
                    MusicAlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album) },
                        showArtist = showArtist,
                    )
                }
            }
        }
    }
}