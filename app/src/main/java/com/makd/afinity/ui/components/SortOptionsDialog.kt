@file:OptIn(ExperimentalMaterial3Api::class)

package com.makd.afinity.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.makd.afinity.R

@Composable
fun <T> SortOptionsDialog(
    options: List<T>,
    current: T,
    currentDescending: Boolean,
    optionLabel: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSortSelected: (T, Boolean) -> Unit,
) {
    var isAscending by remember { mutableStateOf(!currentDescending) }
    var selected by remember { mutableStateOf(current) }
    val maxDialogHeight = windowHeightDp() * 0.92f

    Dialog(
        onDismissRequest = {
            onSortSelected(selected, !isAscending)
            onDismiss()
        },
        properties = afinityDialogProperties(usePlatformDefaultWidth = false),
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
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
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
                        options.forEach { option ->
                            RadioOptionRow(
                                label = optionLabel(option),
                                selected = selected == option,
                                onClick = { selected = option },
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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
                            onSortSelected(selected, !isAscending)
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.action_apply))
                    }
                }
            }
        }
    }
}
