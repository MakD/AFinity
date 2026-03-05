package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.makd.afinity.R
import com.makd.afinity.data.models.tmdb.TmdbReview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsSection(reviews: List<TmdbReview>, modifier: Modifier = Modifier) {
    if (reviews.isEmpty()) return

    var selectedReview by remember { mutableStateOf<TmdbReview?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Reviews (${reviews.size})",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(reviews.take(10)) { review ->
                ReviewCard(review = review, onReadMoreClick = { selectedReview = review })
            }
        }
    }

    if (selectedReview != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedReview = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Review by ${selectedReview!!.author}",
                        style =
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    selectedReview!!.author_details?.rating?.let { rating ->
                        val percentage = (rating * 10).toInt()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.background(
                                        Color(0xFFFFD700).copy(alpha = 0.15f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_star),
                                contentDescription = "Rating",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$percentage%",
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                color = Color(0xFFFFD700),
                            )
                        }
                    }
                }

                ProvideTextStyle(
                    value =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                ) {
                    RichText(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                        Markdown(content = selectedReview!!.content)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: TmdbReview, onReadMoreClick: () -> Unit) {
    var isExpandable by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.width(340.dp).height(200.dp).clickable { onReadMoreClick() },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = review.author,
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                review.author_details?.rating?.let { rating ->
                    val percentage = (rating * 10).toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.background(
                                    Color(0xFFFFD700).copy(alpha = 0.15f),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_star),
                            contentDescription = "Rating",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$percentage%",
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = Color(0xFFFFD700),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = parseBasicMarkdown(review.content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.hasVisualOverflow) {
                        isExpandable = true
                    }
                },
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isExpandable) {
                Text(
                    text = "Read more",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun parseBasicMarkdown(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    return remember(text) {
        buildAnnotatedString {
            var currentIndex = 0

            val pattern =
                Regex(
                    "(?m)^>\\s+(.*)$|" +
                        "(?m)^[-*]\\s+(.*)$|" +
                        "\\*\\*(.*?)\\*\\*|" +
                        "__(.*?)__|" +
                        "\\*(.*?)\\*|" +
                        "_(.*?)_|" +
                        "\\[(.*?)\\]\\((.*?)\\)|" +
                        "(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])"
                )

            val matches = pattern.findAll(text)

            for (match in matches) {
                append(text.substring(currentIndex, match.range.first))

                when {
                    match.groups[1] != null -> {
                        withStyle(SpanStyle(color = quoteColor, fontStyle = FontStyle.Italic)) {
                            append("┃ ${match.groupValues[1]}")
                        }
                    }
                    match.groups[2] != null -> {
                        append("• ${match.groupValues[2]}")
                    }
                    match.groups[3] != null -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(match.groupValues[3])
                        }
                    }
                    match.groups[4] != null -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(match.groupValues[4])
                        }
                    }
                    match.groups[5] != null -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(match.groupValues[5])
                        }
                    }
                    match.groups[6] != null -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(match.groupValues[6])
                        }
                    }
                    match.groups[7] != null -> {
                        withStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold,
                            )
                        ) {
                            append(match.groupValues[7])
                        }
                    }
                    match.groups[9] != null -> {
                        withStyle(
                            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                        ) {
                            append(match.groupValues[9])
                        }
                    }
                }
                currentIndex = match.range.last + 1
            }
            append(text.substring(currentIndex))
        }
    }
}
