package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.ui.audiobookshelf.library.components.AudiobookCard
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun AudiobookshelfHomeTab(
    sections: List<PersonalizedSection>,
    serverUrl: String?,
    onItemClick: (LibraryItem) -> Unit,
    isLoading: Boolean,
    widthSizeClass: WindowWidthSizeClass
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        sections.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No personalized content available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            val cardWidth = widthSizeClass.portraitWidth
            val cardHeight = CardDimensions.calculateHeight(cardWidth, 1f)
            val fixedRowHeight = cardHeight + 8.dp + 20.dp + 18.dp

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                sections.forEach { section ->
                    item(key = section.id) {
                        Column {
                            Spacer(modifier = Modifier.height(24.dp))
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = section.label,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                val uniqueItems = section.items.distinctBy { it.id }

                                LazyRow(
                                    modifier = Modifier.height(fixedRowHeight),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 0.dp)
                                ) {
                                    items(
                                        items = uniqueItems,
                                        key = { item -> "${section.id}_${item.id}" }
                                    ) { item ->
                                        AudiobookCard(
                                            item = item,
                                            serverUrl = serverUrl,
                                            onClick = { onItemClick(item) },
                                            modifier = Modifier.width(cardWidth)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
