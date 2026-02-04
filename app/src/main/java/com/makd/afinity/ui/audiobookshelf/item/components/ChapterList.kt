package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
    val backgroundColor = if (isCurrentChapter) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val textColor = if (isCurrentChapter) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val iconTint = if (isCurrentChapter) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val contentAlpha = if (isCompleted && !isCurrentChapter) 0.5f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isCurrentChapter) {
                Icon(
                    imageVector = Icons.Filled.Equalizer,
                    contentDescription = "Playing",
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            } else if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Completed",
                    tint = iconTint.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
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
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = formatChapterDuration(chapter.end - chapter.start),
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrentChapter) textColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = contentAlpha
            ),
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatChapterDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    return "${minutes}:${secs.toString().padStart(2, '0')}"
}