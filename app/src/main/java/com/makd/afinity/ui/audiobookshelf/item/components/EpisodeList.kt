package com.makd.afinity.ui.audiobookshelf.item.components

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val episodeDateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

fun LazyListScope.episodeListItems(
    episodes: List<PodcastEpisode>,
    onEpisodePlay: (PodcastEpisode) -> Unit,
    expandedEpisodeId: String?,
    onExpandEpisode: (String?) -> Unit,
    episodeProgressMap: Map<String, MediaProgress> = emptyMap(),
) {
    items(items = episodes, key = { it.id }) { episode ->
        val isExpanded = expandedEpisodeId == episode.id

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            ExpandableEpisodeItem(
                episode = episode,
                isExpanded = isExpanded,
                onPlay = { onEpisodePlay(episode) },
                onExpandToggle = { onExpandEpisode(if (isExpanded) null else episode.id) },
                progress = episodeProgressMap[episode.id],
            )
        }
    }
}

@Composable
private fun ExpandableEpisodeItem(
    episode: PodcastEpisode,
    isExpanded: Boolean,
    onPlay: () -> Unit,
    onExpandToggle: () -> Unit,
    progress: MediaProgress? = null,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onExpandToggle() }
                .padding(12.dp)
                .animateContentSize(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                episode.publishedAt?.let { timestamp ->
                    Text(
                        text = formatDate(timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                episode.duration?.let { duration ->
                    if (episode.publishedAt != null) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (progress != null && !progress.isFinished && progress.progress > 0) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    CircularProgressIndicator(
                        progress = { progress.progress.toFloat() },
                        modifier = Modifier.size(12.dp),
                        color = Color(0xFFFFC107),
                        strokeWidth = 2.dp,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    val remainingSeconds = progress.duration - progress.currentTime
                    Text(
                        text = "${formatDuration(remainingSeconds)} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            episode.description?.let { description ->
                Spacer(modifier = Modifier.height(6.dp))

                HtmlText(
                    html = description,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
                    renderHtml = isExpanded,
                    onClick = onExpandToggle,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (progress != null && progress.isFinished) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "Finished",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(40.dp),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_play_filled),
                    contentDescription = "Play episode",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    textColor: Int,
    renderHtml: Boolean,
    onClick: () -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                textSize = 14f
                setLinkTextColor(textColor)
                movementMethod = LinkOrExpandMovementMethod
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.maxLines = maxLines
            textView.ellipsize = android.text.TextUtils.TruncateAt.END

            textView.setOnClickListener { onClick() }

            if (renderHtml) {
                textView.movementMethod = LinkOrExpandMovementMethod
                textView.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            } else {
                textView.movementMethod = null
                textView.text =
                    HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
            }
        },
    )
}

private fun formatDate(timestamp: Long): String {
    return episodeDateFormatter.format(Date(timestamp))
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

object LinkOrExpandMovementMethod : LinkMovementMethod() {
    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.action

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            var x = event.x.toInt()
            var y = event.y.toInt()

            x -= widget.totalPaddingLeft
            y -= widget.totalPaddingTop
            x += widget.scrollX
            y += widget.scrollY

            val layout = widget.layout
            val line = layout.getLineForVertical(y)
            val off = layout.getOffsetForHorizontal(line, x.toFloat())

            val links = buffer.getSpans(off, off, ClickableSpan::class.java)

            if (links.isNotEmpty()) {
                return super.onTouchEvent(widget, buffer, event)
            } else {
                if (action == MotionEvent.ACTION_UP) {
                    widget.performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }
}
