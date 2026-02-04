package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.audiobookshelf.BookChapter

@Composable
fun ChapterList(
    chapters: List<BookChapter>,
    currentPosition: Double?,
    onChapterClick: (BookChapter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Chapters (${chapters.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        chapters.forEachIndexed { index, chapter ->
            val isCurrentChapter = currentPosition?.let { pos ->
                pos >= chapter.start && pos < chapter.end
            } ?: false

            val isCompleted = currentPosition?.let { pos ->
                pos >= chapter.end
            } ?: false

            ChapterItem(
                chapter = chapter,
                index = index + 1,
                isCurrentChapter = isCurrentChapter,
                isCompleted = isCompleted,
                onClick = { onChapterClick(chapter) }
            )

            if (index < chapters.size - 1) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: BookChapter,
    index: Int,
    isCurrentChapter: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            isCurrentChapter -> {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "Currently playing",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            isCompleted -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            else -> {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatChapterDuration(chapter.end - chapter.start),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatChapterDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    return "${minutes}:${secs.toString().padStart(2, '0')}"
}
