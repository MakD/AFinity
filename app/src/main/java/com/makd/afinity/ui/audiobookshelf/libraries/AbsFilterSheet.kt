package com.makd.afinity.ui.audiobookshelf.libraries

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
import com.makd.afinity.ui.components.AfinityToggleChip
import com.makd.afinity.ui.components.filter.FilterAccordionSection
import com.makd.afinity.ui.components.filter.FilterDialog
import com.makd.afinity.ui.components.filter.SearchableChipMultiSelect

@Composable
fun AbsFilterSheet(
    filters: AbsFilters,
    options: AbsFilterOptions,
    onApply: (AbsFilters) -> Unit,
    onDismiss: () -> Unit,
    progressOptions: List<AbsProgressFilter> = AbsProgressFilter.entries,
) {
    var working by remember { mutableStateOf(filters) }
    var expandedSections by remember {
        mutableStateOf(
            buildSet {
                add("progress")
                if (filters.genres.isNotEmpty()) add("genres")
                if (filters.tags.isNotEmpty()) add("tags")
                if (filters.series.isNotEmpty()) add("series")
                if (filters.authors.isNotEmpty()) add("authors")
                if (filters.narrators.isNotEmpty()) add("narrators")
                if (filters.publishers.isNotEmpty()) add("publishers")
                if (filters.languages.isNotEmpty()) add("languages")
                if (filters.decades.isNotEmpty()) add("decades")
                if (filters.abridgedOnly || filters.explicitOnly) add("features")
            },
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
        onClearAll = { working = AbsFilters() },
        onApply = {
            onApply(working)
            onDismiss()
        },
        clearAllEnabled = working.isActive,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        val progressLabels = progressOptions.associateWith { stringResource(it.labelRes) }
        FilterAccordionSection(
            title = stringResource(R.string.abs_filter_progress),
            summary =
                if (working.progress.isEmpty()) anyLabel
                else
                    progressOptions
                        .filter { it in working.progress }
                        .joinToString(", ") { progressLabels.getValue(it) },
            expanded = expandedSections.contains("progress"),
            onToggle = { toggleSection("progress") },
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                progressOptions.forEach { filter ->
                    AfinityToggleChip(
                        label = progressLabels.getValue(filter),
                        selected = filter in working.progress,
                        onSelectedChange = { on ->
                            working =
                                working.copy(
                                    progress =
                                        if (on) working.progress + filter
                                        else working.progress - filter,
                                )
                        },
                    )
                }
            }
        }

        ChipFilterSection(
            title = stringResource(R.string.library_filter_genres),
            hint = stringResource(R.string.library_filter_genres_hint),
            anyLabel = anyLabel,
            options = options.genres,
            selected = working.genres,
            label = { it },
            expanded = expandedSections.contains("genres"),
            onToggle = { toggleSection("genres") },
            onChange = { working = working.copy(genres = it) },
        )
        ChipFilterSection(
            title = stringResource(R.string.library_filter_tags),
            hint = stringResource(R.string.library_filter_tags_hint),
            anyLabel = anyLabel,
            options = options.tags,
            selected = working.tags,
            label = { it },
            expanded = expandedSections.contains("tags"),
            onToggle = { toggleSection("tags") },
            onChange = { working = working.copy(tags = it) },
        )
        ChipFilterSection(
            title = stringResource(R.string.abs_tab_series),
            hint = stringResource(R.string.abs_filter_series_hint),
            anyLabel = anyLabel,
            options = options.series,
            selected = working.series,
            label = { it },
            expanded = expandedSections.contains("series"),
            onToggle = { toggleSection("series") },
            onChange = { working = working.copy(series = it) },
        )
        ChipFilterSection(
            title = stringResource(R.string.abs_filter_authors),
            hint = stringResource(R.string.abs_filter_authors_hint),
            anyLabel = anyLabel,
            options = options.authors,
            selected = working.authors,
            label = { it },
            expanded = expandedSections.contains("authors"),
            onToggle = { toggleSection("authors") },
            onChange = { working = working.copy(authors = it) },
        )
        ChipFilterSection(
            title = stringResource(R.string.abs_filter_narrators),
            hint = stringResource(R.string.abs_filter_narrators_hint),
            anyLabel = anyLabel,
            options = options.narrators,
            selected = working.narrators,
            label = { it },
            expanded = expandedSections.contains("narrators"),
            onToggle = { toggleSection("narrators") },
            onChange = { working = working.copy(narrators = it) },
        )
        ChipFilterSection(
            title = stringResource(R.string.library_filter_publishers),
            hint = stringResource(R.string.library_filter_publishers_hint),
            anyLabel = anyLabel,
            options = options.publishers,
            selected = working.publishers,
            label = { it },
            expanded = expandedSections.contains("publishers"),
            onToggle = { toggleSection("publishers") },
            onChange = { working = working.copy(publishers = it) },
        )
        ChipFilterSection(
            title = stringResource(R.string.abs_filter_languages),
            hint = stringResource(R.string.abs_filter_languages_hint),
            anyLabel = anyLabel,
            options = options.languages,
            selected = working.languages,
            label = { it },
            expanded = expandedSections.contains("languages"),
            onToggle = { toggleSection("languages") },
            onChange = { working = working.copy(languages = it) },
        )
        val decadeLabels =
            options.decades.associateWith { stringResource(R.string.abs_filter_decade_fmt, it) }
        ChipFilterSection(
            title = stringResource(R.string.abs_filter_decades),
            hint = stringResource(R.string.abs_filter_decades_hint),
            anyLabel = anyLabel,
            options = options.decades,
            selected = working.decades,
            label = { decadeLabels[it] ?: it.toString() },
            expanded = expandedSections.contains("decades"),
            onToggle = { toggleSection("decades") },
            onChange = { working = working.copy(decades = it) },
        )

        if (options.hasAbridged || options.hasExplicit) {
            val abridgedLabel = stringResource(R.string.abs_filter_abridged)
            val explicitLabel = stringResource(R.string.abs_filter_explicit)
            FilterAccordionSection(
                title = stringResource(R.string.library_filter_features),
                summary =
                    listOfNotNull(
                            abridgedLabel.takeIf { working.abridgedOnly },
                            explicitLabel.takeIf { working.explicitOnly },
                        )
                        .joinToString(", ")
                        .ifEmpty { anyLabel },
                expanded = expandedSections.contains("features"),
                onToggle = { toggleSection("features") },
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (options.hasAbridged) {
                        AfinityToggleChip(
                            label = abridgedLabel,
                            selected = working.abridgedOnly,
                            onSelectedChange = { working = working.copy(abridgedOnly = it) },
                        )
                    }
                    if (options.hasExplicit) {
                        AfinityToggleChip(
                            label = explicitLabel,
                            selected = working.explicitOnly,
                            onSelectedChange = { working = working.copy(explicitOnly = it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ChipFilterSection(
    title: String,
    hint: String,
    anyLabel: String,
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (Set<T>) -> Unit,
) {
    if (options.isEmpty() && selected.isEmpty()) return
    var query by remember { mutableStateOf("") }
    val selectedList = selected.toList()
    val suggestions = options.filter {
        it !in selected && (query.isBlank() || label(it).contains(query, ignoreCase = true))
    }

    FilterAccordionSection(
        title = title,
        summary =
            if (selectedList.isEmpty()) anyLabel
            else selectedList.joinToString(", ", transform = label),
        expanded = expanded,
        onToggle = onToggle,
    ) {
        SearchableChipMultiSelect(
            label = null,
            placeholder = hint,
            query = query,
            onQueryChange = { query = it },
            suggestions = suggestions,
            suggestionLabel = label,
            onSuggestionSelected = { value ->
                onChange(selected + value)
                query = ""
            },
            selected = selectedList,
            selectedLabel = label,
            onRemoveSelected = { value -> onChange(selected - value) },
            onClearAll = { onChange(emptySet()) },
        )
    }
}
