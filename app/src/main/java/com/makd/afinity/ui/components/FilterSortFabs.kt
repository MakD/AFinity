package com.makd.afinity.ui.components

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

val FAB_CONTENT_BOTTOM_PADDING = 80.dp

@Composable
fun FilterFab(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null,
) {
    FloatingActionButton(onClick = onClick, modifier = modifier) {
        val painter =
            painterResource(id = if (active) R.drawable.ic_filter_active else R.drawable.ic_filter)
        val description = stringResource(R.string.cd_filter_fab)
        if (badgeCount != null && badgeCount > 0) {
            BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) {
                Icon(painter = painter, contentDescription = description)
            }
        } else {
            Icon(painter = painter, contentDescription = description)
        }
    }
}

@Composable
fun SortFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_arrows_sort),
            contentDescription = stringResource(R.string.cd_sort_fab),
        )
    }
}
