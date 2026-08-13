package com.makd.afinity.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    startPadding: Dp = 0.dp,
    bottomPadding: Dp = 16.dp,
    onViewAllClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .then(
                    if (onViewAllClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onViewAllClick,
                        )
                    } else Modifier
                )
                .padding(start = startPadding, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = if (onViewAllClick != null) 1 else Int.MAX_VALUE,
            overflow = if (onViewAllClick != null) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = if (onViewAllClick != null) Modifier.weight(1f, fill = false) else Modifier,
        )

        if (onViewAllClick != null) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.cd_view_all),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(24.dp),
            )
        }
    }
}
