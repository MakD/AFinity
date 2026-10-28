package com.makd.afinity.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.preview.FontScalePreviews
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.preview.StressPreviews
import com.makd.afinity.ui.preview.WidthPreviews

@Composable
fun SectionRowHeader(
    title: String,
    modifier: Modifier = Modifier,
    onViewAllClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = afinitySectionTitleStyle(),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (onViewAllClick != null) {
            Box(
                modifier =
                    Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onViewAllClick,
                        )
                        .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = stringResource(R.string.cd_view_all),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionRowHeaderPreviewContent() {
    PreviewHost {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionRowHeader(
                title = stringResource(R.string.home_continue_watching),
                onViewAllClick = {},
            )
            SectionRowHeader(title = stringResource(R.string.watchlist_title), onViewAllClick = {})
            SectionRowHeader(title = stringResource(R.string.favorites_title))
            SectionRowHeader(
                title = "Because you watched ${PreviewData.LONG_TITLE}",
                onViewAllClick = {},
            )
        }
    }
}

@WidthPreviews
@Composable
private fun SectionRowHeaderWidthPreview() {
    SectionRowHeaderPreviewContent()
}

@FontScalePreviews
@Composable
private fun SectionRowHeaderFontScalePreview() {
    SectionRowHeaderPreviewContent()
}

@StressPreviews
@Composable
private fun SectionRowHeaderStressPreview() {
    SectionRowHeaderPreviewContent()
}
