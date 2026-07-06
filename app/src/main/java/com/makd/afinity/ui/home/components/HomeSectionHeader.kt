package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    startPadding: Dp = 0.dp,
    bottomPadding: Dp = 16.dp,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = startPadding, bottom = bottomPadding),
    )
}