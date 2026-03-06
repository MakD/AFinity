package com.makd.afinity.ui.home.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import com.makd.afinity.ui.utils.shimmerEffect

@Composable
fun ContinueWatchingSkeleton(widthSizeClass: WindowWidthSizeClass) {
    val cardWidth = widthSizeClass.portraitWidth

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Box(
            modifier =
                Modifier.width(200.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .shimmerEffect()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(5) {
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
                    ) {
                        Box(modifier = Modifier.fillMaxSize().shimmerEffect())
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier =
                            Modifier.width(cardWidth * 0.8f)
                                .height(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp),
                                )
                                .shimmerEffect()
                    )
                }
            }
        }
    }
}

@Composable
fun MoviesSectionSkeleton(widthSizeClass: WindowWidthSizeClass) {
    val cardWidth = widthSizeClass.portraitWidth

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Box(
            modifier =
                Modifier.width(150.dp)
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp),
                    )
                    .shimmerEffect()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
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
                            Modifier.width(cardWidth * 0.9f)
                                .height(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp),
                                )
                                .shimmerEffect()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier =
                            Modifier.width(cardWidth * 0.6f)
                                .height(12.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp),
                                )
                                .shimmerEffect()
                    )
                }
            }
        }
    }
}

@Composable
fun TvSeriesSectionSkeleton(widthSizeClass: WindowWidthSizeClass) {
    val cardWidth = widthSizeClass.portraitWidth

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Box(
            modifier =
                Modifier.width(180.dp)
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp),
                    )
                    .shimmerEffect()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
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
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize().shimmerEffect())

                            Box(
                                modifier =
                                    Modifier.align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .width(50.dp)
                                        .height(20.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            RoundedCornerShape(4.dp),
                                        )
                                        .shimmerEffect()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier =
                            Modifier.width(cardWidth * 0.9f)
                                .height(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp),
                                )
                                .shimmerEffect()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier =
                            Modifier.width(cardWidth * 0.7f)
                                .height(12.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp),
                                )
                                .shimmerEffect()
                    )
                }
            }
        }
    }
}
