package com.makd.afinity.ui.settings.update

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import com.makd.afinity.R

private const val LIGHT_SURFACE_LUMINANCE = 0.5f
private val ALERT_BAR_START = 6.sp
private val ALERT_BAR_WIDTH = 3.sp
private val ALERT_BAR_END = 6.sp

private val ALERT_MARKER_REGEX =
    "^[ \\t]*>[ \\t]?\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)][ \\t]*$"
        .toRegex(RegexOption.IGNORE_CASE)
private val QUOTE_PREFIX_REGEX = "^[ \\t]*>[ \\t]?".toRegex()
private val CODE_FENCE_REGEX = "^[ \\t]*(```|~~~)".toRegex()
private val LEADING_HEADING_REGEX = "^\\s*#[ \\t]+v?(\\S+)[ \\t]*#*[ \\t]*(\\r?\\n|$)".toRegex()

private enum class ChangelogAlertType {
    NOTE,
    TIP,
    IMPORTANT,
    WARNING,
    CAUTION,
}

private sealed interface ChangelogBlock {
    data class Section(val text: String) : ChangelogBlock

    data class Alert(val type: ChangelogAlertType, val text: String) : ChangelogBlock
}

private fun stripVersionHeading(text: String, tagName: String): String {
    val match = LEADING_HEADING_REGEX.find(text) ?: return text
    val heading = match.groupValues[1]
    return if (heading.equals(tagName.removePrefix("v"), ignoreCase = true)) {
        text.substring(match.range.last + 1)
    } else {
        text
    }
}

private fun splitChangelog(text: String): List<ChangelogBlock> {
    val blocks = mutableListOf<ChangelogBlock>()
    val pending = StringBuilder()
    val lines = text.lines()
    var inCodeFence = false
    var index = 0

    fun flushPending() {
        if (pending.isNotBlank()) blocks += ChangelogBlock.Section(pending.toString())
        pending.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        if (CODE_FENCE_REGEX.containsMatchIn(line)) inCodeFence = !inCodeFence
        val startsQuote = index == 0 || !QUOTE_PREFIX_REGEX.containsMatchIn(lines[index - 1])
        val marker = if (inCodeFence || !startsQuote) null else ALERT_MARKER_REGEX.matchEntire(line)
        if (marker == null) {
            pending.appendLine(line)
            index++
            continue
        }
        flushPending()
        val body = StringBuilder()
        index++
        while (index < lines.size && QUOTE_PREFIX_REGEX.containsMatchIn(lines[index])) {
            body.appendLine(lines[index].replaceFirst(QUOTE_PREFIX_REGEX, ""))
            index++
        }
        val type = ChangelogAlertType.valueOf(marker.groupValues[1].uppercase())
        blocks += ChangelogBlock.Alert(type, body.toString().trim())
    }
    flushPending()
    return blocks
}

private class ChangelogAlertStyle(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    val light: Color,
    val dark: Color,
)

private fun ChangelogAlertType.style(): ChangelogAlertStyle =
    when (this) {
        ChangelogAlertType.NOTE ->
            ChangelogAlertStyle(
                R.drawable.ic_info_outlined,
                R.string.update_alert_note,
                Color(0xFF0969DA),
                Color(0xFF4493F8),
            )
        ChangelogAlertType.TIP ->
            ChangelogAlertStyle(
                R.drawable.ic_bulb_filled,
                R.string.update_alert_tip,
                Color(0xFF1A7F37),
                Color(0xFF3FB950),
            )
        ChangelogAlertType.IMPORTANT ->
            ChangelogAlertStyle(
                R.drawable.ic_exclamation_circle,
                R.string.update_alert_important,
                Color(0xFF8250DF),
                Color(0xFFAB7DF8),
            )
        ChangelogAlertType.WARNING ->
            ChangelogAlertStyle(
                R.drawable.ic_alert_triangle,
                R.string.update_alert_warning,
                Color(0xFF9A6700),
                Color(0xFFD29922),
            )
        ChangelogAlertType.CAUTION ->
            ChangelogAlertStyle(
                R.drawable.ic_alert_octagon_filled,
                R.string.update_alert_caution,
                Color(0xFFCF222E),
                Color(0xFFF85149),
            )
    }

@Composable
private fun rememberChangelogStyle(): RichTextStyle {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    return remember(colors, typography) {
        RichTextStyle(
            headingStyle = { level, textStyle ->
                when (level) {
                    1 -> typography.titleLarge
                    2 -> typography.titleMedium
                    3 -> typography.titleSmall
                    else -> textStyle.copy(fontWeight = FontWeight.Bold)
                }
            },
            stringStyle =
                RichTextStringStyle(
                    codeStyle =
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = colors.surfaceContainerHighest,
                        ),
                    linkStyle =
                        TextLinkStyles(
                            style = SpanStyle(color = colors.primary),
                            pressedStyle =
                                SpanStyle(
                                    color = colors.primary,
                                    textDecoration = TextDecoration.Underline,
                                ),
                        ),
                ),
        )
    }
}

@Composable
internal fun ChangelogContent(body: String, tagName: String) {
    val blocks = remember(body, tagName) { splitChangelog(stripVersionHeading(body, tagName)) }
    val style = rememberChangelogStyle()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ChangelogBlock.Section -> ChangelogMarkdown(text = block.text, style = style)
                is ChangelogBlock.Alert -> ChangelogAlert(alert = block, style = style)
            }
        }
    }
}

@Composable
private fun ChangelogMarkdown(text: String, style: RichTextStyle) {
    RichText(modifier = Modifier.fillMaxWidth(), style = style) {
        Markdown(content = formatChangelog(text))
    }
}

@Composable
private fun ChangelogAlert(alert: ChangelogBlock.Alert, style: RichTextStyle) {
    val alertStyle = alert.type.style()
    val isLight =
        MaterialTheme.colorScheme.surfaceContainerLow.luminance() > LIGHT_SURFACE_LUMINANCE
    val color = if (isLight) alertStyle.light else alertStyle.dark
    val textStart =
        with(LocalDensity.current) {
            ALERT_BAR_START.toDp() + ALERT_BAR_WIDTH.toDp() + ALERT_BAR_END.toDp()
        }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .drawBehind {
                    val barWidth = ALERT_BAR_WIDTH.toPx()
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(ALERT_BAR_START.toPx(), 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(barWidth / 2),
                    )
                }
                .padding(start = textStart),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(id = alertStyle.icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(alertStyle.label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
        if (alert.text.isNotBlank()) ChangelogMarkdown(text = alert.text, style = style)
    }
}

private const val OUTSIDE_LINK = "(?<!\\]\\()(?<![\\[<])"
private const val GITHUB_REPO_URL = "https://github\\.com/[^/]+/[^/]+"
private const val APP_REPO = "MakD/AFinity"

private fun formatChangelog(text: String): String {
    var formatted = text

    val commitRegex =
        "$OUTSIDE_LINK$GITHUB_REPO_URL/(?:commit|pull/\\d+/(?:commits|changes))/([0-9a-f]{7})[0-9a-f]*"
            .toRegex()
    formatted =
        commitRegex.replace(formatted) { matchResult ->
            val url = matchResult.value
            val shortHash = matchResult.groupValues[1]
            "[`$shortHash`]($url)"
        }

    val issueRegex =
        "${OUTSIDE_LINK}https://github\\.com/([^/]+/[^/]+)/(?:pull|issues)/(\\d+)".toRegex()
    formatted =
        issueRegex.replace(formatted) { matchResult ->
            val url = matchResult.value
            val repo = matchResult.groupValues[1]
            val number = matchResult.groupValues[2]
            val label =
                if (repo.equals(APP_REPO, ignoreCase = true)) "#$number" else "$repo#$number"
            "[$label]($url)"
        }

    val compareRegex =
        "$OUTSIDE_LINK$GITHUB_REPO_URL/compare/([\\w\\d\\.-]+)\\.\\.\\.([\\w\\d\\.-]+)".toRegex()
    formatted =
        compareRegex.replace(formatted) { matchResult ->
            val url = matchResult.value
            val oldVersion = matchResult.groupValues[1]
            val newVersion = matchResult.groupValues[2]
            "[$oldVersion...$newVersion]($url)"
        }

    val userRegex = "${OUTSIDE_LINK}https://github\\.com/([a-zA-Z0-9-]+)(?=[\\s)]|$)".toRegex()
    formatted =
        userRegex.replace(formatted) { matchResult ->
            val url = matchResult.value
            val user = matchResult.groupValues[1]
            "[@$user]($url)"
        }

    return formatted
}
