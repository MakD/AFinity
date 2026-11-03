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
import com.makd.afinity.ui.theme.isDarkSurface

private val WatchlistLight = Color(0xFFC66900)
private val WatchlistDark = Color(0xFFFFB74D)
private val WatchedLight = Color(0xFF2E7D32)
private val WatchedDark = Color(0xFF81C784)

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
            tint =
                if (isFavorite) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
            tint =
                when {
                    !isInWatchlist -> MaterialTheme.colorScheme.onSurfaceVariant
                    isDarkSurface() -> WatchlistDark
                    else -> WatchlistLight
                },
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
                    isPlayed -> if (isDarkSurface()) WatchedDark else WatchedLight
                    enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            modifier = Modifier.size(28.dp),
        )
    }
}
