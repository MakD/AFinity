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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.utils.shimmerEffect

@Composable
fun MediaRowSkeleton(cardWidth: Dp, height: Dp, itemCount: Int = 6) {
    LazyRow(
        modifier = Modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(itemCount) {
            Column(modifier = Modifier.width(cardWidth)) {
                Card(
                    modifier =
                        Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT),
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
