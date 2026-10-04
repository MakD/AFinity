package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.BookChapter

private val ListenedGreen = Color(0xFF4CAF50)
private const val CHAPTERS_BEFORE_CURRENT = 1
private const val CHAPTERS_AFTER_CURRENT = 4

fun LazyListScope.chapterWindowItems(
    chapters: List<BookChapter>,
    currentPosition: Double?,
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    showListened: Boolean,
    onShowListenedChange: (Boolean) -> Unit,
    onChapterClick: (BookChapter) -> Unit,
    headerTrailing: String?,
) {
    val currentIndex =
        currentPosition?.let { pos -> chapters.indexOfFirst { pos >= it.start && pos < it.end } }
            ?: -1
    val anchor = currentIndex.coerceAtLeast(0)
    val listenedCount = (anchor - CHAPTERS_BEFORE_CURRENT).coerceAtLeast(0)
    val windowStart = if (showAll || showListened) 0 else listenedCount
    val windowEnd =
        if (showAll) chapters.size
        else (anchor + CHAPTERS_AFTER_CURRENT + 1).coerceAtMost(chapters.size)
    val windowSize = CHAPTERS_BEFORE_CURRENT + CHAPTERS_AFTER_CURRENT + 1

    item(key = "chapters_header") {
        AbsSectionTitle(
            text = stringResource(R.string.chapters_title),
            trailing = headerTrailing,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }

    if (!showAll && listenedCount > 0) {
        item(key = "chapters_folded") {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { onShowListenedChange(!showListened) }
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = null,
                        tint = ListenedGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.abs_chapters_listened_fmt,
                            listenedCount,
                            listenedCount,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter =
                        painterResource(
                            id =
                                if (showListened) R.drawable.ic_keyboard_arrow_up
                                else R.drawable.ic_keyboard_arrow_down
                        ),
                    contentDescription =
                        if (showListened) stringResource(R.string.cd_collapse)
                        else stringResource(R.string.cd_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    val fadeLast = !showAll && windowEnd < chapters.size
    val lastWindowIndex = windowEnd - windowStart - 1

    itemsIndexed(
        items = chapters.subList(windowStart, windowEnd),
        key = { _, chapter -> "chapter_${chapter.start}_${chapter.end}_${chapter.title}" },
    ) { offset, chapter ->
        val isCurrentChapter =
            currentPosition?.let { pos -> pos >= chapter.start && pos < chapter.end } ?: false
        val isCompleted = currentPosition?.let { pos -> pos >= chapter.end } ?: false

        Box(
            modifier =
                Modifier.animateItem()
                    .fadeOutBottom(fadeLast && offset == lastWindowIndex)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            ChapterItem(
                chapter = chapter,
                index = windowStart + offset + 1,
                isCurrentChapter = isCurrentChapter,
                isCompleted = isCompleted,
                onClick = { onChapterClick(chapter) },
            )
        }
    }

    val hasHidden = windowStart > 0 || windowEnd < chapters.size
    if (hasHidden || (showAll && chapters.size > windowSize)) {
        item(key = "chapters_toggle") {
            ListExpandButton(expanded = showAll, onToggle = { onShowAllChange(!showAll) })
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: BookChapter,
    index: Int,
    isCurrentChapter: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor =
        when {
            isCurrentChapter -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        }

    val textColor =
        when {
            isCurrentChapter -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }

    val iconTint =
        when {
            isCurrentChapter -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    val contentAlpha = if (isCompleted && !isCurrentChapter) 0.6f else 1f

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
            when {
                isCurrentChapter -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_audio),
                        contentDescription = stringResource(R.string.cd_abs_playing),
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                isCompleted -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = stringResource(R.string.cd_abs_completed),
                        tint = ListenedGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }
                else -> {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal,
                color = textColor.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = formatChapterDuration(chapter.end - chapter.start),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (isCurrentChapter) textColor.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatChapterDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
