package com.makd.afinity.ui.music.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    Dialog(
        onDismissRequest = {
            onApply(working)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier.fillMaxWidth().widthIn(max = 480.dp).fillMaxHeight(0.9f).padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.discover_filter_title),
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                        )
                        TextButton(onClick = { working = MusicFilters() }) {
                            Text(stringResource(R.string.discover_filter_clear_all))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FilterAccordionSection(
                        title = stringResource(R.string.library_filter_status),
                        summary =
                            if (working.favoritesOnly)
                                stringResource(R.string.filter_favorites)
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
                                    working = working.copy(favoritesOnly = !working.favoritesOnly)
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
                                placeholder = stringResource(R.string.library_filter_genres_hint),
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
                            )
                        }
                    }

                    if (options.years.isNotEmpty()) {
                        FilterAccordionSection(
                            title = stringResource(R.string.library_filter_years),
                            summary =
                                if (working.years.isEmpty()) anyLabel
                                else working.years.sortedDescending().joinToString(", "),
                            expanded = expandedSections.contains("years"),
                            onToggle = { toggleSection("years") },
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                options.years.forEach { year ->
                                    FilterChip(
                                        selected = working.years.contains(year),
                                        onClick = {
                                            working =
                                                working.copy(
                                                    years =
                                                        if (working.years.contains(year))
                                                            working.years - year
                                                        else working.years + year
                                                )
                                        },
                                        label = { Text(year.toString()) },
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                TextButton(
                    onClick = {
                        onApply(working)
                        onDismiss()
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }
}