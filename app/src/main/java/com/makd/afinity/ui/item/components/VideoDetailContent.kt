package com.makd.afinity.ui.item.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMusicVideo
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import com.makd.afinity.ui.item.components.shared.baseMediaDetailItems
import com.makd.afinity.ui.item.components.shared.detailItem
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaStreamType

fun LazyListScope.videoDetailItems(
    item: AfinityItem,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    onPlayClick: (AfinityItem, PlaybackSelection) -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    onPersonClick: ((String) -> Unit)?,
    onMusicArtistClick: ((String) -> Unit)?,
    onMusicAlbumClick: ((String) -> Unit)?,
    widthSizeClass: WindowWidthSizeClass,
    horizontalPadding: Dp,
    detailLayout: DetailLayout,
    selectedSourceId: String? = null,
    showExternalLinks: Boolean = true,
) {
    baseMediaDetailItems(
        item = item,
        selectedSourceId = selectedSourceId,
        showExternalLinks = showExternalLinks,
        specialFeatures = specialFeatures,
        containingBoxSets = containingBoxSets,
        tmdbReviews = emptyList(),
        onSpecialFeatureClick = onSpecialFeatureClick,
        onBoxSetClick = onItemClick,
        onPersonClick =
            onPersonClick?.let { click -> { personId: UUID -> click(personId.toString()) } },
        widthSizeClass = widthSizeClass,
        horizontalPadding = horizontalPadding,
        detailLayout = detailLayout,
    ) {
        if (item is AfinityMusicVideo && (item.artists.isNotEmpty() || item.album != null)) {
            detailItem("music_video_credits", horizontalPadding) {
                MusicVideoCredits(
                    video = item,
                    onArtistClick = onMusicArtistClick,
                    onAlbumClick = onMusicAlbumClick,
                )
            }
        }

        if (item.chapters.isNotEmpty()) {
            detailItem("chapters", horizontalPadding) {
                ChaptersSection(
                    chapters = item.chapters,
                    itemId = item.id,
                    baseUrl = baseUrl,
                    onChapterClick = { startPositionMs ->
                        val source =
                            item.sources.firstOrNull { it.id == selectedSourceId }
                                ?: item.sources.firstOrNull()
                        onPlayClick(
                            item,
                            PlaybackSelection(
                                mediaSourceId = source?.id ?: "",
                                audioStreamIndex = null,
                                subtitleStreamIndex = null,
                                videoStreamIndex =
                                    source
                                        ?.mediaStreams
                                        ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                                        ?.index ?: 0,
                                startPositionMs = startPositionMs,
                            ),
                        )
                    },
                    widthSizeClass = widthSizeClass,
                )
            }
        }
    }
}

@Composable
private fun MusicVideoCredits(
    video: AfinityMusicVideo,
    onArtistClick: ((String) -> Unit)?,
    onAlbumClick: ((String) -> Unit)?,
) {
    val style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val linkStyle = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (video.artists.isNotEmpty()) {
            val template =
                pluralStringResource(
                    R.plurals.music_video_artists_prefix,
                    video.artists.size,
                    LINK_MARKER,
                )
            val linkedIds = video.artistItems.associate { it.name to it.id }
            Text(
                text =
                    creditText(template) {
                        video.artists.forEachIndexed { index, name ->
                            if (index > 0) append(", ")
                            val id = linkedIds[name]
                            if (id == null || onArtistClick == null) {
                                append(name)
                            } else {
                                withLink(
                                    LinkAnnotation.Clickable(
                                        tag = "artist_$id",
                                        styles = linkStyle,
                                        linkInteractionListener = { onArtistClick(id.toString()) },
                                    ),
                                ) {
                                    append(name)
                                }
                            }
                        }
                    },
                style = style,
                color = color,
            )
        }
        val album = video.album
        if (album != null) {
            val template = stringResource(R.string.music_video_album_prefix, LINK_MARKER)
            val albumId = video.albumId
            Text(
                text =
                    creditText(template) {
                        if (albumId == null || onAlbumClick == null) {
                            append(album)
                        } else {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "album_$albumId",
                                    styles = linkStyle,
                                    linkInteractionListener = { onAlbumClick(albumId.toString()) },
                                ),
                            ) {
                                append(album)
                            }
                        }
                    },
                style = style,
                color = color,
            )
        }
    }
}

private const val LINK_MARKER = "\u0000"

private fun creditText(
    template: String,
    values: AnnotatedString.Builder.() -> Unit,
): AnnotatedString {
    val markerAt = template.indexOf(LINK_MARKER)
    return buildAnnotatedString {
        if (markerAt < 0) {
            append(template)
            values()
        } else {
            append(template.substring(0, markerAt))
            values()
            append(template.substring(markerAt + LINK_MARKER.length))
        }
    }
}
