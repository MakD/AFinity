package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.OptimizedAsyncImage
import org.jellyfin.sdk.model.api.PersonKind
import java.util.UUID

@Composable
fun CastSection(
    item: AfinityItem,
    onPersonClick: (UUID) -> Unit = {}
) {
    val cast = when (item) {
        is AfinityMovie -> item.people.filter { it.type == PersonKind.ACTOR }
        is AfinityShow -> item.people.filter { it.type == PersonKind.ACTOR }
        is AfinitySeason -> item.people.filter { it.type == PersonKind.ACTOR }
        else -> emptyList()
    }

    if (cast.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Cast",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(cast.take(10)) { person ->
                    CastMemberCard(
                        person = person,
                        onPersonClick = onPersonClick
                    )
                }
            }
        }
    }
}

@Composable
private fun CastMemberCard(
    person: AfinityPerson,
    onPersonClick: (UUID) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable (
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onPersonClick(person.id)
              },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OptimizedAsyncImage(
            imageUrl = person.image.uri?.toString(),
            contentDescription = person.name,
            blurHash = person.image.blurHash,
            targetWidth = 100.dp,
            targetHeight = 100.dp,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_person_placeholder),
            error = painterResource(id = R.drawable.ic_person_placeholder)
        )

        Text(
            text = person.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = if (person.role.isNotEmpty()) "as ${person.role}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(16.dp)
        )
    }
}