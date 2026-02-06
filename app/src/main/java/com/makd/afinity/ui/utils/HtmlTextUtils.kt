package com.makd.afinity.ui.utils

import android.text.Spanned
import android.text.style.URLSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.core.text.HtmlCompat

fun htmlToAnnotatedString(html: String, linkColor: Color = Color(0xFF6495ED)): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    return spannedToAnnotatedString(spanned, linkColor)
}

private fun spannedToAnnotatedString(spanned: Spanned, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val text = spanned.toString()
        val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)

        var currentIndex = 0

        val sortedSpans = urlSpans.sortedBy { spanned.getSpanStart(it) }

        sortedSpans.forEach { urlSpan ->
            val start = spanned.getSpanStart(urlSpan)
            val end = spanned.getSpanEnd(urlSpan)

            if (currentIndex < start) {
                append(text.substring(currentIndex, start))
            }

            withLink(
                LinkAnnotation.Url(
                    url = urlSpan.url,
                    styles =
                        TextLinkStyles(
                            style =
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                )
                        ),
                )
            ) {
                append(text.substring(start, end))
            }

            currentIndex = end
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
