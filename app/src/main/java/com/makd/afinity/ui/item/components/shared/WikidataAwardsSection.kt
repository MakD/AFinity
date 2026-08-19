package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.wikidata.AwardResult
import com.makd.afinity.data.models.wikidata.WikidataAward
import com.makd.afinity.data.models.wikidata.WikidataAwards

val AwardGold = Color(0xFFD4AF37)

private const val OPEN_LIST_PREVIEW_GROUPS = 2

private val DOT_SIZE_DP = 8.dp

enum class AwardsSectionStyle {
    COLLAPSED_BAR,
    OPEN_LIST,
}

@Composable
fun WikidataAwardsSection(
    awards: WikidataAwards,
    style: AwardsSectionStyle,
    modifier: Modifier = Modifier,
) {
    if (!awards.found) return

    var showSheet by remember { mutableStateOf(false) }

    when (style) {
        AwardsSectionStyle.COLLAPSED_BAR ->
            AwardsSummaryBar(
                awards = awards,
                onClick = { showSheet = true },
                modifier = modifier,
            )
        AwardsSectionStyle.OPEN_LIST ->
            AwardsOpenList(
                awards = awards,
                onShowAll = { showSheet = true },
                modifier = modifier,
            )
    }

    if (showSheet) {
        AwardsSheet(awards = awards, onDismiss = { showSheet = false })
    }
}

@Composable
private fun AwardsSummaryBar(
    awards: WikidataAwards,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_laurel),
                contentDescription = null,
                tint = AwardGold,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = awardsSummary(awards),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.cd_awards_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AwardsOpenList(
    awards: WikidataAwards,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview =
        remember(awards.awards) {
            awards.awards
                .groupBy { it.year }
                .values
                .take(OPEN_LIST_PREVIEW_GROUPS)
                .flatten()
        }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.section_awards),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        AwardTimeline(awards = preview)

        if (awards.awards.size > preview.size) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.awards_show_all_fmt,
                        awards.awards.size,
                        awards.awards.size,
                    ),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = AwardGold,
                modifier = Modifier.clickable(onClick = onShowAll),
            )
        }
    }
}

@Composable
private fun AwardsSheet(awards: WikidataAwards, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Text(
                text = stringResource(R.string.section_awards),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = awardsSummary(awards),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            AwardTimeline(awards = awards.awards)
        }
    }
}

@Composable
private fun AwardTimeline(awards: List<WikidataAward>, modifier: Modifier = Modifier) {
    val groups = remember(awards) { awards.groupBy { it.year }.toList() }

    Column(modifier = modifier.fillMaxWidth()) {
        groups.forEachIndexed { index, (year, entries) ->
            AwardYearGroup(year = year, entries = entries, isLast = index == groups.lastIndex)
        }
    }
}

@Composable
private fun AwardYearGroup(year: Int?, entries: List<WikidataAward>, isLast: Boolean) {
    val hasWin = entries.any { it.result == AwardResult.WON }
    val markerColor = if (hasWin) AwardGold else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(DOT_SIZE_DP).clip(CircleShape).background(markerColor))
            }

            Text(
                text = year?.toString() ?: "—",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = markerColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier.width(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!isLast) {
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    )
                }
            }

            Column(
                modifier =
                    Modifier.weight(1f)
                        .padding(
                            start = 12.dp,
                            top = 12.dp,
                            bottom = if (isLast) 8.dp else 28.dp,
                        ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                entries.forEach { AwardEntryRow(award = it) }
            }
        }
    }
}

@Composable
private fun AwardEntryRow(award: WikidataAward) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = award.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            AwardResultPill(result = award.result)
        }

        val detail = (award.works + award.recipients).joinToString(", ")
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AwardResultPill(result: AwardResult) {
    val won = result == AwardResult.WON
    Surface(
        shape = RoundedCornerShape(4.dp),
        color =
            if (won) AwardGold.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
    ) {
        Text(
            text =
                stringResource(
                    if (won) R.string.awards_result_won else R.string.awards_result_nominated
                ),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                ),
            color = if (won) AwardGold else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

private val HEADLINE_BODIES =
    listOf(
        "Academy Award" to R.plurals.awards_body_oscar_fmt,
        "Primetime Emmy Award" to R.plurals.awards_body_emmy_fmt,
        "Daytime Emmy Award" to R.plurals.awards_body_emmy_fmt,
        "Golden Globe Award" to R.plurals.awards_body_golden_globe_fmt,
        "BAFTA Award" to R.plurals.awards_body_bafta_fmt,
        "Screen Actors Guild Award" to R.plurals.awards_body_sag_fmt,
        "Critics' Choice" to R.plurals.awards_body_critics_choice_fmt,
    )

fun omdbAwardsHeadline(awards: String?): String? =
    awards
        ?.takeIf { it.isNotBlank() }
        ?.split(".", limit = 2)
        ?.first()
        ?.trim()
        ?.takeIf {
            it.isNotBlank()
        }

@Composable
fun derivedAwardsHeadline(awards: WikidataAwards): String? {
    val match =
        HEADLINE_BODIES.firstNotNullOfOrNull { (prefix, plural) ->
            val wins =
                awards.awards.count { it.result == AwardResult.WON && it.name.startsWith(prefix) }
            if (wins > 0) return@firstNotNullOfOrNull Triple(plural, wins, true)

            val nominations =
                awards.awards.count {
                    it.result == AwardResult.NOMINATED && it.name.startsWith(prefix)
                }
            if (nominations > 0) Triple(plural, nominations, false) else null
        } ?: return null

    val (plural, count, won) = match
    val bodyText = pluralStringResource(plural, count, count)
    return stringResource(
        if (won) R.string.awards_headline_won_fmt else R.string.awards_headline_nominated_fmt,
        bodyText,
    )
}

@Composable
private fun awardsSummary(awards: WikidataAwards): String {
    val wins = awards.wins
    val nominations = awards.nominations
    val winsText = pluralStringResource(R.plurals.awards_wins_fmt, wins, wins)
    val nominationsText =
        pluralStringResource(R.plurals.awards_nominations_fmt, nominations, nominations)

    return when {
        wins > 0 && nominations > 0 ->
            stringResource(R.string.awards_summary_fmt, winsText, nominationsText)
        wins > 0 -> winsText
        else -> nominationsText
    }
}
