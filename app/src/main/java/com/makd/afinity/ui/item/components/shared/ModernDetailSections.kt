package com.makd.afinity.ui.item.components.shared

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.mdblist.MdbListRatingBadges
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.ratings.toDisplay
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind

private val ModernCardShape = RoundedCornerShape(12.dp)

@Composable
private fun modernCardColor() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

@Composable
private fun modernCardBorder() =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

@Composable
fun RatingChipsRow(
    item: AfinityItem,
    mdbRatings: List<MdbListRating>,
    mdbRatingBadges: MdbListRatingBadges,
    modifier: Modifier = Modifier,
) {
    val displays =
        remember(item, mdbRatings) {
            orderedRatingsFor(
                    item,
                    mdbRatings,
                )
                .mapNotNull { it.toDisplay() }
        }
    if (displays.isEmpty() && !mdbRatingBadges.hasAny) return

    val certifiedFresh = stringResource(R.string.rt_certified_fresh)
    val verifiedHot = stringResource(R.string.rt_verified_hot)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        if (mdbRatingBadges.certifiedFresh) {
            item(key = "badge_certified_fresh") {
                RatingChip(
                    iconRes = R.drawable.ic_certified_fresh,
                    contentDescription = "Tomatometer",
                    value = certifiedFresh,
                    subtext = null,
                )
            }
        }
        if (mdbRatingBadges.verifiedHot) {
            item(key = "badge_verified_hot") {
                RatingChip(
                    iconRes = R.drawable.ic_verified_hot,
                    contentDescription = "Popcornmeter",
                    value = verifiedHot,
                    subtext = null,
                )
            }
        }
        items(displays) { display ->
            RatingChip(
                iconRes = display.iconRes,
                contentDescription = display.sourceName,
                value = display.score,
                subtext = display.subtext,
            )
        }
    }
}

@Composable
private fun RatingChip(
    @DrawableRes iconRes: Int?,
    contentDescription: String,
    value: String,
    subtext: String?,
) {
    Surface(shape = ModernCardShape, color = modernCardColor(), border = modernCardBorder()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = contentDescription,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text(
                    text = contentDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            if (!subtext.isNullOrBlank()) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun DetailFactsPanel(item: AfinityItem, selectedSourceId: String?, modifier: Modifier = Modifier) {
    val directors = remember(item) { item.peopleOfKind(PersonKind.DIRECTOR).distinctBy { it.id } }
    val writers = remember(item) { item.peopleOfKind(PersonKind.WRITER).distinctBy { it.id } }
    val producers = remember(item) { item.peopleOfKind(PersonKind.PRODUCER).distinctBy { it.id } }
    val hasAudio =
        remember(item, selectedSourceId) {
            hasLanguageChips(item, MediaStreamType.AUDIO, selectedSourceId)
        }
    val hasSubtitles =
        remember(item, selectedSourceId) {
            hasLanguageChips(item, MediaStreamType.SUBTITLE, selectedSourceId)
        }
    val hasLinks = remember(item) { item.externalUrls.orEmpty().any { it.url != null } }

    if (
        directors.isEmpty() &&
            writers.isEmpty() &&
            producers.isEmpty() &&
            !hasAudio &&
            !hasSubtitles &&
            !hasLinks
    ) {
        return
    }

    Surface(
        shape = ModernCardShape,
        color = modernCardColor(),
        border = modernCardBorder(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (directors.isNotEmpty()) {
                FactRow(
                    label = pluralStringResource(R.plurals.detail_fact_director, directors.size)
                ) {
                    FactText(directors.joinToString(", ") { it.name })
                }
            }
            if (writers.isNotEmpty()) {
                FactRow(label = pluralStringResource(R.plurals.detail_fact_writer, writers.size)) {
                    FactText(writers.joinToString(", ") { it.name })
                }
            }
            if (producers.isNotEmpty()) {
                FactRow(
                    label = pluralStringResource(R.plurals.detail_fact_producer, producers.size)
                ) {
                    FactText(producers.joinToString(", ") { it.name })
                }
            }
            if (hasAudio) {
                FactRow(label = stringResource(R.string.detail_fact_audio)) {
                    LanguageChipsRow(
                        item = item,
                        type = MediaStreamType.AUDIO,
                        selectedSourceId = selectedSourceId,
                    )
                }
            }
            if (hasSubtitles) {
                FactRow(label = stringResource(R.string.detail_fact_subtitles)) {
                    LanguageChipsRow(
                        item = item,
                        type = MediaStreamType.SUBTITLE,
                        selectedSourceId = selectedSourceId,
                    )
                }
            }
            if (hasLinks) {
                FactRow(label = stringResource(R.string.detail_fact_links)) {
                    ExternalLinksSection(item = item)
                }
            }
        }
    }
}

@Composable
private fun FactRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Column(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun FactText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
