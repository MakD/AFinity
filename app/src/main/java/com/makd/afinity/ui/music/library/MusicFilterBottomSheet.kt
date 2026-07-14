package com.makd.afinity.ui.music.library

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.makd.afinity.R
import com.makd.afinity.data.models.music.MusicFilterOptions
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.ui.components.filter.FilterAccordionSection
import com.makd.afinity.ui.components.filter.SearchableChipMultiSelect

@Composable
fun MusicFilterBottomSheet(
    filters: MusicFilters,
    options: MusicFilterOptions,
    onApply: (MusicFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember { mutableStateOf(filters) }
    var genreQuery by remember { mutableStateOf("") }
    var yearQuery by remember { mutableStateOf("") }

    var expandedSections by remember {
        mutableStateOf(
            buildSet {
                add("status")
                if (working.genres.isNotEmpty()) add("genres")
                if (working.years.isNotEmpty()) add("years")
            }
        )
    }
    fun toggleSection(key: String) {
        expandedSections =
            if (expandedSections.contains(key)) expandedSections - key else expandedSections + key
    }

    val anyLabel = stringResource(R.string.discover_filter_any)
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    Dialog(
        onDismissRequest = {
            onApply(working)
            onDismiss()
        },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true,
            ),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize().imePadding().pointerInput(Unit) {
                    detectTapGestures {
                        onApply(working)
                        onDismiss()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier.fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .heightIn(max = maxDialogHeight)
                        .padding(16.dp)
                        .pointerInput(Unit) { detectTapGestures {} },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier =
                            Modifier.weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.discover_filter_title),
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        FilterAccordionSection(
                            title = stringResource(R.string.library_filter_status),
                            summary =
                                if (working.favoritesOnly) stringResource(R.string.filter_favorites)
                                else anyLabel,
                            expanded = expandedSections.contains("status"),
                            onToggle = { toggleSection("status") },
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = working.favoritesOnly,
                                    onClick = {
                                        working =
                                            working.copy(favoritesOnly = !working.favoritesOnly)
                                    },
                                    label = { Text(stringResource(R.string.filter_favorites)) },
                                )
                            }
                        }

                        if (options.genres.isNotEmpty()) {
                            val selectedGenres = working.genres.toList()
                            val genreSuggestions =
                                options.genres.filter {
                                    !working.genres.contains(it) &&
                                        (genreQuery.isBlank() ||
                                            it.contains(genreQuery, ignoreCase = true))
                                }

                            FilterAccordionSection(
                                title = stringResource(R.string.library_filter_genres),
                                summary =
                                    if (selectedGenres.isEmpty()) anyLabel
                                    else selectedGenres.joinToString(", "),
                                expanded = expandedSections.contains("genres"),
                                onToggle = { toggleSection("genres") },
                            ) {
                                SearchableChipMultiSelect(
                                    label = null,
                                    placeholder =
                                        stringResource(R.string.library_filter_genres_hint),
                                    query = genreQuery,
                                    onQueryChange = { genreQuery = it },
                                    suggestions = genreSuggestions,
                                    suggestionLabel = { it },
                                    onSuggestionSelected = { genre ->
                                        working = working.copy(genres = working.genres + genre)
                                        genreQuery = ""
                                    },
                                    selected = selectedGenres,
                                    selectedLabel = { it },
                                    onRemoveSelected = { genre ->
                                        working = working.copy(genres = working.genres - genre)
                                    },
                                    onClearAll = { working = working.copy(genres = emptySet()) },
                                )
                            }
                        }

                        if (options.years.isNotEmpty()) {
                            val selectedYears = working.years.sortedDescending()
                            val yearSuggestions =
                                options.years.filter {
                                    !working.years.contains(it) &&
                                        (yearQuery.isBlank() || it.toString().contains(yearQuery))
                                }

                            FilterAccordionSection(
                                title = stringResource(R.string.library_filter_years),
                                summary =
                                    if (selectedYears.isEmpty()) anyLabel
                                    else selectedYears.joinToString(", "),
                                expanded = expandedSections.contains("years"),
                                onToggle = { toggleSection("years") },
                            ) {
                                SearchableChipMultiSelect(
                                    label = null,
                                    placeholder =
                                        stringResource(R.string.library_filter_years_hint),
                                    query = yearQuery,
                                    onQueryChange = { yearQuery = it },
                                    suggestions = yearSuggestions,
                                    suggestionLabel = { it.toString() },
                                    onSuggestionSelected = { year ->
                                        working = working.copy(years = working.years + year)
                                        yearQuery = ""
                                    },
                                    selected = selectedYears,
                                    selectedLabel = { it.toString() },
                                    onRemoveSelected = { year ->
                                        working = working.copy(years = working.years - year)
                                    },
                                    onClearAll = { working = working.copy(years = emptySet()) },
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { working = MusicFilters() },
                            enabled = working.isActive,
                        ) {
                            Text(stringResource(R.string.discover_filter_clear_all))
                        }
                        Button(
                            onClick = {
                                onApply(working)
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
}
