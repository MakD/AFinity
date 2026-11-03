package com.makd.afinity.ui.admin.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.ui.components.AfinityTextField

@StringRes
private fun providerTypeLabelRes(type: String?): Int? =
    when (type) {
        "Album" -> R.string.admin_identify_id_album
        "ReleaseGroup" -> R.string.admin_identify_id_release_group
        "Movie" -> R.string.admin_identify_id_movie
        "BoxSet" -> R.string.admin_identify_id_collection
        "Series" -> R.string.admin_identify_id_series
        "Season" -> R.string.admin_identify_id_season
        "Episode" -> R.string.admin_identify_id_episode
        "Artist",
        "OtherArtist" -> R.string.admin_identify_id_artist
        "AlbumArtist" -> R.string.admin_identify_id_album_artist
        "Track" -> R.string.admin_identify_id_track
        "Recording" -> R.string.admin_identify_id_recording
        "Person" -> R.string.admin_identify_id_person
        "Book" -> R.string.admin_identify_id_book
        else -> null
    }

@Composable
fun providerFieldLabel(provider: ExternalIdProvider, duplicateName: Boolean): String {
    val labelRes = providerTypeLabelRes(provider.type)
    return if (duplicateName && labelRes != null) stringResource(labelRes, provider.name)
    else provider.name
}

@Composable
fun ProviderIdFieldGrid(
    providers: List<ExternalIdProvider>,
    providerIds: Map<String, String>,
    onIdChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val duplicateNames =
        remember(providers) {
            providers.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        providers.chunked(2).forEach { rowProviders ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowProviders.forEach { provider ->
                    AfinityTextField(
                        value = providerIds[provider.key] ?: "",
                        onValueChange = { onIdChange(provider.key, it) },
                        label =
                            providerFieldLabel(
                                provider = provider,
                                duplicateName = provider.name in duplicateNames,
                            ),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                    )
                }
                if (rowProviders.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
