package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private fun String.withAbsWidth(px: Int): String {
    if (startsWith("file://")) return this
    val sep = if ('?' in this) "&" else "?"
    return "${this}${sep}width=$px"
}

@Composable
fun SeriesCoverGrid(coverUrls: List<String>, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier =
            modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color.Transparent)
    ) {
        val density = LocalDensity.current
        val gridPx = with(density) { maxWidth.roundToPx() }

        when {
            coverUrls.isEmpty() -> {}
            coverUrls.size == 1 -> {
                AsyncImage(
                    model = coverUrls[0].withAbsWidth(gridPx),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            coverUrls.size == 2 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = coverUrls[0].withAbsWidth(gridPx / 2),
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 1.dp),
                        contentScale = ContentScale.Crop,
                    )
                    AsyncImage(
                        model = coverUrls[1].withAbsWidth(gridPx / 2),
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 1.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            coverUrls.size == 3 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = coverUrls[0].withAbsWidth(gridPx / 2),
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 1.dp),
                        contentScale = ContentScale.Crop,
                    )

                    Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 1.dp)) {
                        AsyncImage(
                            model = coverUrls[1].withAbsWidth(gridPx / 2),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 1.dp),
                            contentScale = ContentScale.Crop,
                        )

                        AsyncImage(
                            model = coverUrls[2].withAbsWidth(gridPx / 2),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 1.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = coverUrls[0].withAbsWidth(gridPx / 2),
                            contentDescription = null,
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .padding(end = 1.dp, bottom = 1.dp),
                            contentScale = ContentScale.Crop,
                        )
                        AsyncImage(
                            model = coverUrls[1].withAbsWidth(gridPx / 2),
                            contentDescription = null,
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = 1.dp, bottom = 1.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = coverUrls[2].withAbsWidth(gridPx / 2),
                            contentDescription = null,
                            modifier =
                                Modifier.weight(1f).fillMaxHeight().padding(end = 1.dp, top = 1.dp),
                            contentScale = ContentScale.Crop,
                        )
                        AsyncImage(
                            model = coverUrls[3].withAbsWidth(gridPx / 2),
                            contentDescription = null,
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = 1.dp, top = 1.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}
