package com.makd.afinity.ui.audiobookshelf.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.Bookmark
import com.makd.afinity.ui.components.AfinityTextField
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<Bookmark>,
    isLoading: Boolean,
    currentTimeSeconds: Double,
    onSeekToBookmark: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onCreateBookmark: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTitleInput by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.abs_bookmarks_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            if (!isLoading && bookmarks.isEmpty()) {
                Text(
                    text = stringResource(R.string.abs_bookmarks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }

            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(bookmarks, key = { it.time }) { bookmark ->
                    val isCurrent = kotlin.math.abs(bookmark.time - currentTimeSeconds) < 1.0
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable { onSeekToBookmark(bookmark) }
                                .background(
                                    if (isCurrent)
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainer
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bookmark_filled),
                                contentDescription = null,
                                tint =
                                    if (isCurrent) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        Spacer(modifier = Modifier.size(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text =
                                    bookmark.title.ifBlank {
                                        stringResource(R.string.abs_bookmark_untitled)
                                    },
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight =
                                            if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                    ),
                                color =
                                    if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatBookmarkTime(bookmark.time),
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }

                        IconButton(onClick = { onDeleteBookmark(bookmark) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.abs_bookmark_delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showTitleInput) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    AfinityTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = stringResource(R.string.abs_bookmark_title_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                showTitleInput = false
                                title = ""
                            }
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Button(
                            enabled = title.isNotBlank(),
                            onClick = {
                                onCreateBookmark(title.trim())
                                showTitleInput = false
                                title = ""
                            },
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Button(
                        onClick = {
                            title = formatBookmarkTime(currentTimeSeconds)
                            showTitleInput = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.abs_bookmark_add_at_fmt,
                                    formatBookmarkTime(currentTimeSeconds),
                                ),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun formatBookmarkTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}
