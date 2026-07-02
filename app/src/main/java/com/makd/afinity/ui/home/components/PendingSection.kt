package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import com.makd.afinity.ui.utils.shimmerEffect

@Composable
fun PendingSection(
    title: String,
    isSpotlight: Boolean,
    onVisible: () -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    var hasBeenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasBeenVisible) {
            onVisible()
            hasBeenVisible = true
        }
    }

    Column(modifier = modifier.padding(horizontal = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (isSpotlight) {
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.78f),
                shape = RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
            ) {
                Box(modifier = Modifier.fillMaxSize().shimmerEffect())
            }
        } else {
            val cardWidth = widthSizeClass.portraitWidth
            val cardHeight =
                CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
            val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

            LazyRow(
                modifier = Modifier.height(fixedRowHeight),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                items(6) {
                    Column(modifier = Modifier.width(cardWidth)) {
                        Card(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT),
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        ) {
                            Box(modifier = Modifier.fillMaxSize().shimmerEffect())
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier =
                                Modifier.width(cardWidth * 0.8f)
                                    .height(14.dp)
                                    .padding(horizontal = 4.dp)
                                    .shimmerEffect()
                        )
                    }
                }
            }
        }
    }
}
