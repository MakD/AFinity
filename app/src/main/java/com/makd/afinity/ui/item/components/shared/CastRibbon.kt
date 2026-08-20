package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.utils.horizontalBleed
import org.jellyfin.sdk.model.api.PersonKind
import java.util.UUID

private val RibbonAvatarSize = 56.dp
private val RibbonItemWidth = 72.dp

private data class RibbonEntry(val person: AfinityPerson, val isGuest: Boolean)

@Composable
fun CastRibbon(
    item: AfinityItem,
    onSeeAllClick: () -> Unit,
    onPersonClick: ((UUID) -> Unit)? = null,
    horizontalPadding: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val cast = item.peopleOfKind(PersonKind.ACTOR)
    val guestStars = item.peopleOfKind(PersonKind.GUEST_STAR)

    val entries =
        remember(cast, guestStars) {
            cast.map { RibbonEntry(it, false) } + guestStars.map { RibbonEntry(it, true) }
        }

    if (entries.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LazyRow(
            modifier = Modifier.horizontalBleed(horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
        ) {
            items(entries, key = { "${it.person.id}|${it.person.role}|${it.isGuest}" }) { entry ->
                RibbonFace(entry = entry, onPersonClick = onPersonClick)
            }

            item(key = "see_all") { SeeAllChevron(onClick = onSeeAllClick) }
        }

        if (guestStars.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(10.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )

                Text(
                    text = stringResource(R.string.guest_stars_section_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RibbonFace(entry: RibbonEntry, onPersonClick: ((UUID) -> Unit)?) {
    val person = entry.person
    val clickHandler = onPersonClick
    val ringColor =
        if (entry.isGuest) MaterialTheme.colorScheme.primary else Color.Transparent
    val roleLabel =
        if (person.role.isNotEmpty()) stringResource(R.string.cast_role_format, person.role) else ""

    Column(
        modifier =
            Modifier.width(RibbonItemWidth)
                .then(
                    if (clickHandler != null)
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            clickHandler(person.id)
                        }
                    else Modifier
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsyncImage(
            imageUrl = person.image.uri?.toString(),
            contentDescription = person.name,
            blurHash = person.image.blurHash,
            targetWidth = RibbonAvatarSize,
            targetHeight = RibbonAvatarSize,
            modifier =
                Modifier.size(RibbonAvatarSize)
                    .border(2.dp, ringColor, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_person_placeholder),
            error = painterResource(id = R.drawable.ic_person_placeholder),
        )

        Text(
            text = person.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Text(
            text = roleLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(14.dp),
        )
    }
}

@Composable
private fun SeeAllChevron(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.height(RibbonAvatarSize)
                .width(40.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = stringResource(R.string.people_dialog_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}