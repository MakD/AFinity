package com.makd.afinity.ui.music.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.music.MusicFilterOptions
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.ui.components.AfinityToggleChip
import com.makd.afinity.ui.components.filter.FilterAccordionSection
import com.makd.afinity.ui.components.filter.FilterDialog
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

    FilterDialog(
        onDismiss = {
            onApply(working)
            onDismiss()
        },
        onClearAll = { working = MusicFilters() },
        onApply = {
            onApply(working)
            onDismiss()
        },
        clearAllEnabled = working.isActive,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        FilterAccordionSection(
            title = stringResource(R.string.library_filter_status),
            summary =
                if (working.favoritesOnly) stringResource(R.string.filter_favorites) else anyLabel,
            expanded = expandedSections.contains("status"),
            onToggle = { toggleSection("status") },
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AfinityToggleChip(
                    label = stringResource(R.string.filter_favorites),
                    selected = working.favoritesOnly,
                    onSelectedChange = { working = working.copy(favoritesOnly = it) },
                )
            }
        }

        if (options.genres.isNotEmpty()) {
            val selectedGenres = working.genres.toList()
            val genreSuggestions =
                options.genres.filter {
                    !working.genres.contains(it) &&
                        (genreQuery.isBlank() || it.contains(genreQuery, ignoreCase = true))
                }

            FilterAccordionSection(
                title = stringResource(R.string.library_filter_genres),
                summary =
                    if (selectedGenres.isEmpty()) anyLabel else selectedGenres.joinToString(", "),
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
                    if (selectedYears.isEmpty()) anyLabel else selectedYears.joinToString(", "),
                expanded = expandedSections.contains("years"),
                onToggle = { toggleSection("years") },
            ) {
                SearchableChipMultiSelect(
                    label = null,
                    placeholder = stringResource(R.string.library_filter_years_hint),
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
}
