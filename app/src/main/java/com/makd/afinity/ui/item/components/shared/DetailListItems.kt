package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.ui.components.afinitySectionTitleStyle
import com.makd.afinity.ui.utils.gapIfNotEmpty

val DetailSectionGap = 16.dp

val LocalDetailLayout = compositionLocalOf { DetailLayout.CLASSIC }

fun LazyListScope.detailItem(
    key: Any,
    horizontalPadding: Dp,
    gap: Dp = DetailSectionGap,
    contentType: Any? = null,
    content: @Composable () -> Unit,
) {
    item(key = key, contentType = contentType) {
        DetailItemBox(horizontalPadding = horizontalPadding, gap = gap, content = content)
    }
}

@Composable
fun DetailItemBox(horizontalPadding: Dp, gap: Dp, content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxWidth().gapIfNotEmpty(gap).padding(horizontal = horizontalPadding)
    ) {
        content()
    }
}

@Composable
fun DetailSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = afinitySectionTitleStyle(),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
