package com.makd.afinity.ui.item.components.shared

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import org.jellyfin.sdk.model.api.PersonKind

@Composable
fun OverviewSection(item: AfinityItem) {
    var isExpanded by remember { mutableStateOf(false) }
    var isEllipsized by remember { mutableStateOf(false) }

    if (item.overview.isNotEmpty()) {
        Column {
            Text(
                text = item.overview,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize(),
                onTextLayout = { result ->
                    if (!isExpanded) {
                        isEllipsized = result.hasVisualOverflow
                    }
                },
            )

            if (isEllipsized || isExpanded) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                isExpanded = !isExpanded
                            }
                            .padding(vertical = 4.dp),
                ) {
                    Text(
                        text =
                            if (isExpanded) stringResource(R.string.action_see_less)
                            else stringResource(R.string.action_see_more),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Icon(
                        painter =
                            if (isExpanded) painterResource(id = R.drawable.ic_keyboard_arrow_up)
                            else painterResource(id = R.drawable.ic_keyboard_arrow_down),
                        contentDescription =
                            if (isExpanded) stringResource(R.string.cd_collapse)
                            else stringResource(R.string.cd_expand),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun TaglineSection(item: AfinityItem) {
    val tagline =
        when (item) {
            is AfinityMovie -> item.tagline
            is AfinityShow -> item.tagline
            else -> null
        }

    tagline
        ?.takeIf { it.isNotBlank() }
        ?.let { taglineText ->
            Text(
                text = taglineText,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
}

@Composable
fun DirectorSection(item: AfinityItem) {
    val directors =
        when (item) {
            is AfinityMovie -> item.people.filter { it.type == PersonKind.DIRECTOR }
            is AfinityShow -> item.people.filter { it.type == PersonKind.DIRECTOR }
            else -> emptyList()
        }

    if (directors.isNotEmpty()) {
        Text(
            text =
                stringResource(R.string.director_prefix, directors.joinToString(", ") { it.name }),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WriterSection(item: AfinityItem) {
    val writers =
        when (item) {
            is AfinityMovie -> item.people.filter { it.type == PersonKind.WRITER }
            is AfinityShow -> item.people.filter { it.type == PersonKind.WRITER }
            else -> emptyList()
        }

    if (writers.isNotEmpty()) {
        Text(
            text = stringResource(R.string.writers_prefix, writers.joinToString(", ") { it.name }),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
