package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySources
import com.makd.afinity.ui.components.CircleFlagIcon
import com.makd.afinity.ui.components.circleFlagAsset
import com.makd.afinity.ui.components.languageToFlagCode
import org.jellyfin.sdk.model.api.MediaStreamType

private sealed interface LanguageChip {
    data class Flag(val assetUrl: String) : LanguageChip

    data object NoLanguage : LanguageChip

    data object Unidentified : LanguageChip

    data class Code(val text: String) : LanguageChip
}

private fun languageChips(
    item: AfinityItem,
    type: MediaStreamType,
    selectedSourceId: String?,
): List<LanguageChip> {
    val sources = (item as? AfinitySources)?.sources ?: return emptyList()
    val source = sources.find { it.id == selectedSourceId } ?: sources.firstOrNull()
    val seenCountries = LinkedHashSet<String>()
    val seenCodes = LinkedHashSet<String>()
    var hasNoLanguage = false
    var hasUnidentified = false
    val chips = mutableListOf<LanguageChip>()

    source
        ?.mediaStreams
        .orEmpty()
        .filter { it.type == type }
        .forEach { stream ->
            val raw = stream.language.trim()
            if (raw.isBlank()) return@forEach
            val lower = raw.lowercase()
            val base = lower.substringBefore('-')
            if (base.isBlank() || base == "root" || base == "mis") return@forEach
            if (base == "zxx") {
                if (!hasNoLanguage) {
                    hasNoLanguage = true
                    chips.add(LanguageChip.NoLanguage)
                }
                return@forEach
            }
            if (base == "und") {
                if (!hasUnidentified) {
                    hasUnidentified = true
                    chips.add(LanguageChip.Unidentified)
                }
                return@forEach
            }
            val country = languageToFlagCode(lower)
            if (country != null) {
                if (seenCountries.add(country)) {
                    chips.add(LanguageChip.Flag(circleFlagAsset(country)))
                }
            } else {
                val label = base.uppercase()
                if (seenCodes.add(label)) {
                    chips.add(LanguageChip.Code(label))
                }
            }
        }
    return chips
}

@Composable
fun MediaLanguageFlagsSection(
    item: AfinityItem,
    modifier: Modifier = Modifier,
    selectedSourceId: String? = null,
) {
    val audio =
        remember(item, selectedSourceId) {
            languageChips(item, MediaStreamType.AUDIO, selectedSourceId)
        }
    val subtitles =
        remember(item, selectedSourceId) {
            languageChips(item, MediaStreamType.SUBTITLE, selectedSourceId)
        }
    if (audio.isEmpty() && subtitles.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (audio.isNotEmpty()) {
            LanguageFlagRow(
                label = stringResource(R.string.media_languages_audio),
                chips = audio,
            )
        }
        if (subtitles.isNotEmpty()) {
            LanguageFlagRow(
                label = stringResource(R.string.media_languages_subtitles),
                chips = subtitles,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageFlagRow(label: String, chips: List<LanguageChip>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        chips.forEach { chip ->
            when (chip) {
                is LanguageChip.Flag ->
                    CircleFlagIcon(
                        url = chip.assetUrl,
                        size = 14.dp,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                LanguageChip.NoLanguage ->
                    LanguageCodeChip(
                        text = stringResource(R.string.media_language_none),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                LanguageChip.Unidentified ->
                    LanguageCodeChip(
                        text = stringResource(R.string.media_language_unidentified),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                is LanguageChip.Code ->
                    LanguageCodeChip(
                        text = chip.text,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
            }
        }
    }
}

@Composable
private fun LanguageCodeChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
