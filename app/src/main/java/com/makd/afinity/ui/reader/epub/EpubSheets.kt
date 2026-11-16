package com.makd.afinity.ui.reader.epub

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.BookAnnotation
import com.makd.afinity.data.models.media.BookAnnotationKind
import com.makd.afinity.data.models.media.HighlightColor
import com.makd.afinity.ui.components.AfinityMenuItem
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.AfinitySegmentedTabs
import com.makd.afinity.ui.components.AfinitySlider
import com.makd.afinity.ui.components.SegmentedTab
import kotlin.math.roundToInt
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

private const val FONT_SIZE_MIN = 0.6f
private const val FONT_SIZE_MAX = 2.5f
private const val FONT_SIZE_STEPS = 18
private const val LINE_TIGHT = 1.2
private const val LINE_RELAXED = 1.8
private const val MARGIN_NARROW = 0.5
private const val MARGIN_WIDE = 2.0

@OptIn(ExperimentalReadiumApi::class)
@Composable
internal fun EpubDisplaySheet(
    preferences: EpubPreferences,
    onPreferencesChange: ((EpubPreferences) -> EpubPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    AfinityModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.reader_display),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            SheetLabel(stringResource(R.string.reader_theme))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ThemeSwatch(
                    null,
                    R.string.reader_theme_follow_app,
                    preferences,
                    onPreferencesChange,
                )
                ThemeSwatch(
                    Theme.LIGHT,
                    R.string.reader_theme_light,
                    preferences,
                    onPreferencesChange,
                )
                ThemeSwatch(
                    Theme.SEPIA,
                    R.string.reader_theme_sepia,
                    preferences,
                    onPreferencesChange,
                )
                ThemeSwatch(
                    Theme.DARK,
                    R.string.reader_theme_dark,
                    preferences,
                    onPreferencesChange,
                )
            }

            val size = (preferences.fontSize ?: 1.0).toFloat()
            SheetLabel(
                stringResource(R.string.reader_text_size) +
                    " · " +
                    stringResource(R.string.reader_progress_percent_fmt, (size * 100).roundToInt()),
            )
            AfinitySlider(
                value = size,
                onValueChange = { value ->
                    onPreferencesChange { it.copy(fontSize = ((value * 10).roundToInt() / 10.0)) }
                },
                valueRange = FONT_SIZE_MIN..FONT_SIZE_MAX,
                steps = FONT_SIZE_STEPS,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            val fonts =
                listOf(null, FontFamily.SERIF, FontFamily.SANS_SERIF, FontFamily.OPEN_DYSLEXIC)
            SheetLabel(stringResource(R.string.reader_font))
            AfinitySegmentedTabs(
                tabs =
                    listOf(
                        SegmentedTab(stringResource(R.string.reader_font_original)),
                        SegmentedTab(stringResource(R.string.reader_font_serif)),
                        SegmentedTab(stringResource(R.string.reader_font_sans)),
                        SegmentedTab(stringResource(R.string.reader_font_dyslexic)),
                    ),
                selectedIndex = fonts.indexOf(preferences.fontFamily).coerceAtLeast(0),
                onSelect = { index -> onPreferencesChange { it.copy(fontFamily = fonts[index]) } },
                scrollable = true,
            )

            val line = preferences.lineHeight
            SheetLabel(stringResource(R.string.reader_line_spacing))
            AfinitySegmentedTabs(
                tabs =
                    listOf(
                        SegmentedTab(stringResource(R.string.reader_spacing_tight)),
                        SegmentedTab(stringResource(R.string.reader_spacing_normal)),
                        SegmentedTab(stringResource(R.string.reader_spacing_relaxed)),
                    ),
                selectedIndex =
                    when {
                        line == null -> 1
                        line < 1.5 -> 0
                        else -> 2
                    },
                onSelect = { index ->
                    val next =
                        when (index) {
                            0 -> LINE_TIGHT
                            2 -> LINE_RELAXED
                            else -> null
                        }
                    onPreferencesChange {
                        it.copy(
                            lineHeight = next,
                            publisherStyles = if (next == null) null else false,
                        )
                    }
                },
            )

            val margins = preferences.pageMargins
            SheetLabel(stringResource(R.string.reader_margins))
            AfinitySegmentedTabs(
                tabs =
                    listOf(
                        SegmentedTab(stringResource(R.string.reader_margins_narrow)),
                        SegmentedTab(stringResource(R.string.reader_margins_normal)),
                        SegmentedTab(stringResource(R.string.reader_margins_wide)),
                    ),
                selectedIndex =
                    when {
                        margins == null || margins == 1.0 -> 1
                        margins < 1.0 -> 0
                        else -> 2
                    },
                onSelect = { index ->
                    val next =
                        when (index) {
                            0 -> MARGIN_NARROW
                            2 -> MARGIN_WIDE
                            else -> null
                        }
                    onPreferencesChange { it.copy(pageMargins = next) }
                },
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
    )
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ThemeSwatch(
    theme: Theme?,
    labelRes: Int,
    preferences: EpubPreferences,
    onPreferencesChange: ((EpubPreferences) -> EpubPreferences) -> Unit,
) {
    val selected = preferences.theme == theme
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.RadioButton) {
                    onPreferencesChange { it.copy(theme = theme) }
                }
                .padding(4.dp),
    ) {
        Box(
            modifier =
                Modifier.size(48.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) colors.primary else colors.outlineVariant,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (theme == null) {
                Row(modifier = Modifier.size(48.dp)) {
                    Box(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .background(Color(Theme.LIGHT.backgroundColor)),
                    )
                    Box(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .background(Color(Theme.DARK.backgroundColor)),
                    )
                }
            } else {
                Box(Modifier.size(48.dp).background(Color(theme.backgroundColor)))
                Text(
                    text = stringResource(R.string.reader_display_glyph),
                    color = Color(theme.contentColor),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
internal fun EpubContentsSheet(
    title: String,
    toc: List<EpubTocEntry>,
    progression: Double,
    chapterTitle: String?,
    annotations: List<BookAnnotation>,
    onEntryClick: (EpubTocEntry) -> Unit,
    onAnnotationClick: (BookAnnotation) -> Unit,
    onAnnotationDelete: (BookAnnotation) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }
    AfinityModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    stringResource(
                        R.string.book_progress_percent_fmt,
                        (progression * 100).roundToInt(),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        AfinitySegmentedTabs(
            tabs =
                listOf(
                    SegmentedTab(stringResource(R.string.reader_contents)),
                    SegmentedTab(
                        stringResource(R.string.reader_notes),
                        count = annotations.size.takeIf { it > 0 },
                    ),
                ),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(top = 12.dp),
        )
        if (tab == 0) {
            ContentsList(toc, progression, chapterTitle, onEntryClick)
        } else {
            NotesList(annotations, onAnnotationClick, onAnnotationDelete)
        }
    }
}

@Composable
private fun NotesList(
    annotations: List<BookAnnotation>,
    onClick: (BookAnnotation) -> Unit,
    onDelete: (BookAnnotation) -> Unit,
) {
    if (annotations.isEmpty()) {
        Text(
            text = stringResource(R.string.reader_notes_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        items(annotations, key = { it.id }) { annotation ->
            SwipeToDeleteRow(onDelete = { onDelete(annotation) }) {
                NoteRow(annotation = annotation, onClick = { onClick(annotation) })
            }
        }
    }
}

@Composable
private fun SwipeToDeleteRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            currentOnDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val active = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val color by
                animateColorAsState(
                    targetValue =
                        if (active) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        else Color.Transparent,
                    label = "note_dismiss_color",
                )
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.reader_note_delete),
                    tint =
                        MaterialTheme.colorScheme.onErrorContainer.copy(
                            alpha = if (active) 1f else 0f,
                        ),
                )
            }
        },
    ) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

@Composable
private fun NoteRow(annotation: BookAnnotation, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val date =
        remember(annotation.createdAt) {
            DateUtils.formatDateTime(
                context,
                annotation.createdAt,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
            )
        }
    val meta =
        listOfNotNull(
                annotation.label?.takeIf { it.isNotBlank() },
                stringResource(
                    R.string.reader_progress_percent_fmt,
                    (annotation.progression * 100).roundToInt(),
                ),
                date,
            )
            .joinToString(" · ")
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (annotation.kind == BookAnnotationKind.HIGHLIGHT) {
            Box(
                modifier =
                    Modifier.width(4.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color((annotation.color ?: HighlightColor.YELLOW).argb)),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_bookmark_filled),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    if (annotation.kind == BookAnnotationKind.HIGHLIGHT) {
                        "“" + annotation.text.orEmpty() + "”"
                    } else {
                        annotation.label?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.reader_bookmark)
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun HighlightActionsSheet(
    highlight: BookAnnotation,
    onColor: (HighlightColor) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AfinityModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                text = "“" + highlight.text.orEmpty() + "”",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HighlightColor.entries.forEach { color ->
                    val selected = (highlight.color ?: HighlightColor.YELLOW) == color
                    val colorName =
                        stringResource(
                            when (color) {
                                HighlightColor.YELLOW -> R.string.reader_color_yellow
                                HighlightColor.GREEN -> R.string.reader_color_green
                                HighlightColor.BLUE -> R.string.reader_color_blue
                                HighlightColor.PINK -> R.string.reader_color_pink
                            },
                        )
                    Box(
                        modifier =
                            Modifier.size(40.dp)
                                .clip(CircleShape)
                                .background(Color(color.argb))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) colors.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable(role = Role.RadioButton) { onColor(color) }
                                .semantics {
                                    contentDescription = colorName
                                    this.selected = selected
                                },
                    )
                }
            }
            AfinityMenuItem(
                text = stringResource(R.string.reader_copy),
                iconRes = R.drawable.ic_clipboard,
                onClick = onCopy,
            )
            AfinityMenuItem(
                text = stringResource(R.string.reader_highlight_delete),
                iconRes = R.drawable.ic_delete,
                destructive = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ContentsList(
    toc: List<EpubTocEntry>,
    progression: Double,
    chapterTitle: String?,
    onEntryClick: (EpubTocEntry) -> Unit,
) {
    val currentIndex = toc.indexAt(progression, chapterTitle)
    val currentStart = toc.getOrNull(currentIndex)?.progression ?: 0.0
    val listState =
        rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    val colors = MaterialTheme.colorScheme
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        itemsIndexed(toc) { index, entry ->
            val isCurrent = index == currentIndex
            val done = !isCurrent && (entry.progression ?: 1.0) < currentStart
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isCurrent) colors.secondaryContainer else Color.Transparent)
                        .clickable(role = Role.Button) { onEntryClick(entry) }
                        .padding(
                            start = 12.dp + (entry.depth * 16).dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
            ) {
                Text(
                    text = entry.title,
                    style =
                        if (entry.hasChildren) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Medium else null,
                    color =
                        when {
                            isCurrent -> colors.onSecondaryContainer
                            entry.hasChildren -> colors.primary
                            done -> colors.onSurfaceVariant
                            else -> colors.onSurface
                        },
                )
                if (isCurrent) {
                    val fraction = toc.chapterFraction(index, progression) ?: 0.0
                    Text(
                        text =
                            stringResource(
                                R.string.reader_toc_current_fmt,
                                (fraction * 100).roundToInt(),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
