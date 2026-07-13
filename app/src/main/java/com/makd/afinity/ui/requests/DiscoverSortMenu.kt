package com.makd.afinity.ui.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .heightIn(max = maxDialogHeight)
                    .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier =
                        Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sort_title),
                        style =
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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

                    Spacer(modifier = Modifier.height(16.dp))

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

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = {
                            onSortSelected(selectedField, !isAscending)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.action_apply))
                    }
                }
            }
        }
    }
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