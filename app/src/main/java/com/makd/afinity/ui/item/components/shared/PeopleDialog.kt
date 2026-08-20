package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.ui.components.AsyncImage
import org.jellyfin.sdk.model.api.PersonKind
import java.util.UUID

@Composable
fun PeopleDialog(
    item: AfinityItem,
    onPersonClick: ((UUID) -> Unit)?,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    val cast = item.peopleOfKind(PersonKind.ACTOR)
    val guestStars = item.peopleOfKind(PersonKind.GUEST_STAR)

    if (cast.isEmpty() && guestStars.isEmpty()) return

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = stringResource(R.string.people_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp).weight(1f, fill = false),
                    contentPadding = PaddingValues(top = 8.dp),
                ) {
                    peopleGroup(
                        groupId = "cast",
                        titleRes = R.string.cast_section_title,
                        people = cast,
                        onPersonClick = onPersonClick,
                    )
                    peopleGroup(
                        groupId = "guest",
                        titleRes = R.string.guest_stars_section_title,
                        people = guestStars,
                        onPersonClick = onPersonClick,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.peopleGroup(
    groupId: String,
    titleRes: Int,
    people: List<AfinityPerson>,
    onPersonClick: ((UUID) -> Unit)?,
) {
    if (people.isEmpty()) return

    item(key = "$groupId:header") {
        Text(
            text =
                stringResource(
                    R.string.people_group_header_fmt,
                    stringResource(titleRes),
                    people.size,
                ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
        )
    }

    items(people, key = { "$groupId:${it.id}|${it.role}" }) { person ->
        PersonRow(person = person, onPersonClick = onPersonClick)
    }
}

@Composable
private fun PersonRow(person: AfinityPerson, onPersonClick: ((UUID) -> Unit)?) {
    val clickHandler = onPersonClick

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .then(
                    if (clickHandler != null)
                        Modifier.clickable { clickHandler(person.id) }
                    else Modifier
                )
                .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            imageUrl = person.image.uri?.toString(),
            contentDescription = person.name,
            blurHash = person.image.blurHash,
            targetWidth = 48.dp,
            targetHeight = 48.dp,
            modifier = Modifier.size(48.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_person_placeholder),
            error = painterResource(id = R.drawable.ic_person_placeholder),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (person.role.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.cast_role_format, person.role),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}