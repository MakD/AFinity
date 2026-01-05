package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

@Composable
fun SeasonSelector(
    availableSeasons: Int,
    selectedSeasons: List<Int>,
    onSeasonsChange: (List<Int>) -> Unit,
    disabledSeasons: List<Int> = emptyList(),
    modifier: Modifier = Modifier
) {
    val selectableSeasons = (1..availableSeasons).filter { it !in disabledSeasons }
    val allSelectableSelected = selectableSeasons.all { it in selectedSeasons }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Select Seasons",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (disabledSeasons.isNotEmpty()) {
            Text(
                text = "Season${if (disabledSeasons.size > 1) "s" else ""} ${disabledSeasons.joinToString(", ")} already available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium
            )
        }

        if (selectableSeasons.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = allSelectableSelected && selectableSeasons.isNotEmpty(),
                    onClick = {
                        onSeasonsChange(
                            if (allSelectableSelected) {
                                emptyList()
                            } else {
                                selectableSeasons
                            }
                        )
                    },
                    label = {
                        Text(
                            if (disabledSeasons.isEmpty()) "All Seasons"
                            else "All Remaining Seasons"
                        )
                    }
                )
            }
        }

        if (availableSeasons > 0) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(availableSeasons) { index ->
                    val seasonNumber = index + 1
                    val isDisabled = seasonNumber in disabledSeasons

                    FilterChip(
                        selected = if (isDisabled) true else selectedSeasons.contains(seasonNumber),
                        onClick = {
                            if (!isDisabled) {
                                onSeasonsChange(
                                    if (selectedSeasons.contains(seasonNumber)) {
                                        selectedSeasons - seasonNumber
                                    } else {
                                        selectedSeasons + seasonNumber
                                    }
                                )
                            }
                        },
                        enabled = !isDisabled,
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Season $seasonNumber")

                                if (isDisabled) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check),
                                        contentDescription = "Included",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        } else {
            Text(
                text = "No season information available. All seasons will be requested.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}