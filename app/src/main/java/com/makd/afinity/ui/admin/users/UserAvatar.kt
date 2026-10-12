package com.makd.afinity.ui.admin.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.components.UserAvatar

internal const val USER_AVATAR_MAX_WIDTH = 320

internal val UserPlayingColor = Color(0xFF4CAF50)

@Composable
internal fun AdminUserAvatar(
    imageUrl: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
) {
    Box(
        modifier =
            modifier.size(size).graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        UserAvatar(
            imageUrl = imageUrl,
            name = name,
            size = size,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        if (isPlaying) {
            PlayingBadge(modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun PlayingBadge(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.user_playing_now)
    Box(
        modifier =
            modifier
                .size(18.dp)
                .drawBehind { drawCircle(color = Color.Black, blendMode = BlendMode.Clear) }
                .padding(2.dp)
                .background(color = UserPlayingColor, shape = CircleShape)
                .semantics { contentDescription = label }
    )
}
