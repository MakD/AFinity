package com.makd.afinity.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class SegmentedTab(val label: String, val count: Int? = null)

@Composable
fun AfinitySegmentedTabs(
    tabs: List<SegmentedTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    horizontalPadding: Dp = 16.dp,
) {
    if (scrollable) {
        Row(modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.width(horizontalPadding))
            SegmentedTabsRow(tabs, selectedIndex, onSelect, Modifier)
            Spacer(modifier = Modifier.width(horizontalPadding))
        }
    } else {
        SegmentedTabsRow(
            tabs = tabs,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        )
    }
}

@Composable
private fun SegmentedTabsRow(
    tabs: List<SegmentedTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    tab.count
                        ?.takeIf { it > 0 }
                        ?.let { count ->
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }
        }
    }
}
