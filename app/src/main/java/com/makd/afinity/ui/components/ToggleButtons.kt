package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

@Composable
fun FavoriteToggleButton(isFavorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter =
                if (isFavorite) painterResource(id = R.drawable.ic_favorite_filled)
                else painterResource(id = R.drawable.ic_favorite),
            contentDescription =
                if (isFavorite) stringResource(R.string.cd_favorite_remove)
                else stringResource(R.string.cd_favorite_add),
            tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
fun WatchlistToggleButton(
    isInWatchlist: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter =
                if (isInWatchlist) painterResource(id = R.drawable.ic_bookmark_filled)
                else painterResource(id = R.drawable.ic_bookmark),
            contentDescription =
                if (isInWatchlist) stringResource(R.string.cd_watchlist_remove)
                else stringResource(R.string.cd_watchlist_add),
            tint = if (isInWatchlist) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
fun WatchedToggleButton(
    isPlayed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(
            painter =
                if (isPlayed) painterResource(id = R.drawable.ic_circle_check)
                else painterResource(id = R.drawable.ic_circle_check_outline),
            contentDescription =
                if (isPlayed) stringResource(R.string.cd_watched_unmark)
                else stringResource(R.string.cd_watched_mark),
            tint =
                when {
                    isPlayed -> Color.Green
                    enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            modifier = Modifier.size(28.dp),
        )
    }
}