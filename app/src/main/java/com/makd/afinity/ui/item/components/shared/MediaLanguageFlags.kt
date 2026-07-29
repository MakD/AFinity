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
import org.jellyfin.sdk.model.api.MediaStreamType

private val languageToCountry: Map<String, String> =
    mapOf(
        "english" to "us",
        "eng" to "us",
        "japanese" to "jp",
        "jpn" to "jp",
        "spanish" to "es",
        "spa" to "es",
        "french" to "fr",
        "fre" to "fr",
        "fra" to "fr",
        "german" to "de",
        "ger" to "de",
        "deu" to "de",
        "italian" to "it",
        "ita" to "it",
        "korean" to "kr",
        "kor" to "kr",
        "chinese" to "cn",
        "chi" to "cn",
        "zho" to "cn",
        "russian" to "ru",
        "rus" to "ru",
        "portuguese" to "pt",
        "por" to "pt",
        "hindi" to "in",
        "hin" to "in",
        "dutch" to "nl",
        "dut" to "nl",
        "nld" to "nl",
        "arabic" to "sa",
        "ara" to "sa",
        "bengali" to "in",
        "ben" to "in",
        "czech" to "cz",
        "ces" to "cz",
        "danish" to "dk",
        "dan" to "dk",
        "greek" to "gr",
        "ell" to "gr",
        "finnish" to "fi",
        "fin" to "fi",
        "hebrew" to "il",
        "heb" to "il",
        "hungarian" to "hu",
        "hun" to "hu",
        "indonesian" to "id",
        "ind" to "id",
        "norwegian" to "no",
        "nor" to "no",
        "polish" to "pl",
        "pol" to "pl",
        "persian" to "ir",
        "per" to "ir",
        "fas" to "ir",
        "romanian" to "ro",
        "ron" to "ro",
        "rum" to "ro",
        "swedish" to "se",
        "swe" to "se",
        "thai" to "th",
        "tha" to "th",
        "turkish" to "tr",
        "tur" to "tr",
        "ukrainian" to "ua",
        "ukr" to "ua",
        "vietnamese" to "vn",
        "vie" to "vn",
        "malay" to "my",
        "msa" to "my",
        "may" to "my",
        "swahili" to "ke",
        "swa" to "ke",
        "tagalog" to "ph",
        "tgl" to "ph",
        "filipino" to "ph",
        "tamil" to "in",
        "tam" to "in",
        "telugu" to "in",
        "tel" to "in",
        "marathi" to "in",
        "mar" to "in",
        "punjabi" to "in",
        "pan" to "in",
        "urdu" to "pk",
        "urd" to "pk",
        "sinhala" to "lk",
        "sin" to "lk",
        "nepali" to "np",
        "nep" to "np",
        "pashto" to "af",
        "pus" to "af",
        "kurdish" to "iq",
        "kur" to "iq",
        "slovak" to "sk",
        "slk" to "sk",
        "slovenian" to "si",
        "slv" to "si",
        "serbian" to "rs",
        "srp" to "rs",
        "croatian" to "hr",
        "hrv" to "hr",
        "bulgarian" to "bg",
        "bul" to "bg",
        "macedonian" to "mk",
        "mkd" to "mk",
        "albanian" to "al",
        "sqi" to "al",
        "estonian" to "ee",
        "est" to "ee",
        "latvian" to "lv",
        "lav" to "lv",
        "lithuanian" to "lt",
        "lit" to "lt",
        "icelandic" to "is",
        "isl" to "is",
        "georgian" to "ge",
        "kat" to "ge",
        "armenian" to "am",
        "hye" to "am",
        "mongolian" to "mn",
        "mon" to "mn",
        "kazakh" to "kz",
        "kaz" to "kz",
        "uzbek" to "uz",
        "uzb" to "uz",
        "azerbaijani" to "az",
        "aze" to "az",
        "belarusian" to "by",
        "bel" to "by",
        "amharic" to "et",
        "amh" to "et",
        "zulu" to "za",
        "zul" to "za",
        "afrikaans" to "za",
        "afr" to "za",
        "hausa" to "ng",
        "hau" to "ng",
        "yoruba" to "ng",
        "yor" to "ng",
        "igbo" to "ng",
        "ibo" to "ng",
        "brazilian" to "br",
        "bra" to "br",
        "catalan" to "es-ct",
        "cat" to "es-ct",
        "ca" to "es-ct",
        "galician" to "es-ga",
        "glg" to "es-ga",
        "gl" to "es-ga",
        "basque" to "es-pv",
        "baq" to "es-pv",
        "eus" to "es-pv",
    )

private sealed interface LanguageChip {
    data class Flag(val assetUrl: String) : LanguageChip

    data object NoLanguage : LanguageChip

    data object Unidentified : LanguageChip

    data class Code(val text: String) : LanguageChip
}

private fun languageChips(item: AfinityItem, type: MediaStreamType): List<LanguageChip> {
    val sources = (item as? AfinitySources)?.sources ?: return emptyList()
    val seenCountries = LinkedHashSet<String>()
    val seenCodes = LinkedHashSet<String>()
    var hasNoLanguage = false
    var hasUnidentified = false
    val chips = mutableListOf<LanguageChip>()

    sources
        .flatMap { it.mediaStreams }
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
            // Region variant wins so pt-BR -> br, pt-PT -> pt, en-GB -> gb, en-US -> us.
            val region = lower.substringAfter('-', "").takeIf { it.length == 2 }
            val country = region ?: languageToCountry[base] ?: languageToCountry[lower]
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
fun MediaLanguageFlagsSection(item: AfinityItem, modifier: Modifier = Modifier) {
    val audio = remember(item) { languageChips(item, MediaStreamType.AUDIO) }
    val subtitles = remember(item) { languageChips(item, MediaStreamType.SUBTITLE) }
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
