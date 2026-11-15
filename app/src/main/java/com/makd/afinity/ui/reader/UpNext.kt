package com.makd.afinity.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.ui.book.seriesLine
import com.makd.afinity.ui.components.AsyncImage

@Composable
internal fun UpNextPage(next: AfinityBook, onRead: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(ReaderTopInsets)
                .windowInsetsPadding(ReaderBottomInsets)
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = stringResource(R.string.reader_finished),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.reader_up_next),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        AsyncImage(
            imageUrl = next.images.primary?.toString(),
            blurHash = next.images.primaryImageBlurHash,
            contentDescription = stringResource(R.string.cd_book_cover),
            targetWidth = UpNextCoverWidth,
            targetHeight = UpNextCoverHeight,
            modifier =
                Modifier.size(UpNextCoverWidth, UpNextCoverHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHigh),
        )
        seriesLine(next.seriesName, next.seriesIndex, next.format)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Text(
            text = next.name,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 320.dp).padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRead) {
            Icon(
                painter = painterResource(R.drawable.ic_book),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.reader_read_now),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
internal fun BoxScope.UpNextBanner(
    visible: Boolean,
    next: AfinityBook,
    onRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Row(
            modifier =
                Modifier.windowInsetsPadding(ReaderBottomInsets)
                    .padding(16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                imageUrl = next.images.primary?.toString(),
                blurHash = next.images.primaryImageBlurHash,
                contentDescription = null,
                targetWidth = 40.dp,
                targetHeight = 60.dp,
                modifier = Modifier.size(40.dp, 60.dp).clip(RoundedCornerShape(6.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reader_finished_up_next),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
                Text(
                    text = next.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onRead) { Text(stringResource(R.string.reader_read_now)) }
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.reader_up_next_dismiss),
                )
            }
        }
    }
}

@Composable
internal fun ReaderNextButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_player_skip_forward),
            contentDescription = stringResource(R.string.reader_next_in_series),
        )
    }
}

private val UpNextCoverWidth = 140.dp
private val UpNextCoverHeight = 210.dp
