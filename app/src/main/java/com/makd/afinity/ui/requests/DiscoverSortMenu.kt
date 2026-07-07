package com.makd.afinity.ui.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.MovieSortField
import com.makd.afinity.data.models.jellyseerr.TvSortField

@Composable
private fun movieSortFieldLabel(field: MovieSortField): String =
    when (field) {
        MovieSortField.POPULARITY -> stringResource(R.string.sort_option_popularity)
        MovieSortField.RELEASE_DATE -> stringResource(R.string.sort_option_release_date)
        MovieSortField.RATING -> stringResource(R.string.sort_option_rating)
        MovieSortField.TITLE -> stringResource(R.string.sort_option_title)
    }

@Composable
private fun tvSortFieldLabel(field: TvSortField): String =
    when (field) {
        TvSortField.POPULARITY -> stringResource(R.string.sort_option_popularity)
        TvSortField.FIRST_AIR_DATE -> stringResource(R.string.sort_option_release_date)
        TvSortField.RATING -> stringResource(R.string.sort_option_rating)
        TvSortField.TITLE -> stringResource(R.string.sort_option_title)
    }

@Composable
fun MovieDiscoverSortDialog(
    currentField: MovieSortField,
    currentDescending: Boolean,
    onDismiss: () -> Unit,
    onSortSelected: (MovieSortField, Boolean) -> Unit,
) {
    DiscoverSortDialog(
        fields = MovieSortField.entries,
        currentField = currentField,
        currentDescending = currentDescending,
        labelFor = { movieSortFieldLabel(it) },
        onDismiss = onDismiss,
        onSortSelected = onSortSelected,
    )
}

@Composable
fun TvDiscoverSortDialog(
    currentField: TvSortField,
    currentDescending: Boolean,
    onDismiss: () -> Unit,
    onSortSelected: (TvSortField, Boolean) -> Unit,
) {
    DiscoverSortDialog(
        fields = TvSortField.entries,
        currentField = currentField,
        currentDescending = currentDescending,
        labelFor = { tvSortFieldLabel(it) },
        onDismiss = onDismiss,
        onSortSelected = onSortSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DiscoverSortDialog(
    fields: List<T>,
    currentField: T,
    currentDescending: Boolean,
    labelFor: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSortSelected: (T, Boolean) -> Unit,
) {
    var isAscending by remember { mutableStateOf(!currentDescending) }
    var selectedField by remember { mutableStateOf(currentField) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isAscending,
                        onClick = { isAscending = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.sort_ascending))
                    }

                    SegmentedButton(
                        selected = !isAscending,
                        onClick = { isAscending = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.sort_descending))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    fields.forEach { field ->
                        DiscoverSortFieldRow(
                            label = labelFor(field),
                            selected = selectedField == field,
                            onSelect = { selectedField = field },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSortSelected(selectedField, !isAscending)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DiscoverSortFieldRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}