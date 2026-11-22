package com.makd.afinity.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

val FAB_CONTENT_BOTTOM_PADDING = 80.dp

@Composable
fun rememberFilterSortToolbarScrollBehavior(): FloatingToolbarScrollBehavior =
    FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom,
    )

@Composable
fun FilterSortToolbar(
    modifier: Modifier = Modifier,
    onFilterClick: (() -> Unit)? = null,
    filterActive: Boolean = false,
    filterCount: Int? = null,
    onSortClick: (() -> Unit)? = null,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        scrollBehavior = scrollBehavior,
    ) {
        if (onFilterClick != null) {
            FilterSortToolbarAction(
                label = stringResource(R.string.cd_filter_fab),
                iconRes = if (filterActive) R.drawable.ic_filter_active else R.drawable.ic_filter,
                selected = filterActive,
                count = filterCount?.takeIf { it > 0 },
                onClick = onFilterClick,
            )
        }
        if (onSortClick != null) {
            FilterSortToolbarAction(
                label = stringResource(R.string.cd_sort_fab),
                iconRes = R.drawable.ic_arrows_sort,
                selected = false,
                count = null,
                onClick = onSortClick,
            )
        }
    }
}

@Composable
private fun FilterSortToolbarAction(
    label: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    count: Int?,
    onClick: () -> Unit,
) {
    val colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()
    val containerColor = if (selected) colors.toolbarContentColor else Color.Transparent
    val contentColor = if (selected) colors.toolbarContainerColor else colors.toolbarContentColor
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            if (count != null) {
                AfinityBadge(
                    text = count.toString(),
                    containerColor = colors.toolbarContainerColor,
                    contentColor = colors.toolbarContentColor,
                )
            }
        }
    }
}
