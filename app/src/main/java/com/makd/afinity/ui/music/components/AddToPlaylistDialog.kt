package com.makd.afinity.ui.music.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.components.CreateNamedItemDialog
import com.makd.afinity.ui.components.PickerChoice
import com.makd.afinity.ui.components.PickerListDialog
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaType

private enum class DialogScreen {
    List,
    Create,
}

@Composable
fun AddToPlaylistDialog(
    trackIds: List<UUID>,
    viewModel: AddToPlaylistViewModel,
    onDismiss: () -> Unit,
    onResult: (AddToPlaylistResult) -> Unit,
    mediaType: MediaType = MediaType.AUDIO,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(DialogScreen.List) }

    LaunchedEffect(Unit) { viewModel.loadPlaylists() }

    LaunchedEffect(state.result) {
        val result = state.result ?: return@LaunchedEffect
        onResult(result)
        viewModel.clearResult()
        onDismiss()
    }

    when (screen) {
        DialogScreen.List -> {
            val choices =
                state.playlists.map { playlist ->
                    PickerChoice(
                        key = playlist.id.toString(),
                        name = playlist.name,
                        subtitle =
                            playlist.songCount
                                ?.takeIf { it > 0 }
                                ?.let { count ->
                                    pluralStringResource(
                                        R.plurals.playlist_choice_songs,
                                        count,
                                        count,
                                    )
                                },
                        imageUrl = playlist.images.primary?.toString(),
                        blurHash = playlist.images.primaryImageBlurHash,
                    )
                }
            PickerListDialog(
                title = stringResource(R.string.music_action_add_to_playlist),
                choices = choices,
                isLoading = state.isLoadingPlaylists,
                isSubmitting = state.isSubmitting,
                emptyText = stringResource(R.string.music_no_playlists_yet),
                createLabel = stringResource(R.string.music_new_playlist_title),
                onCreateNew = { screen = DialogScreen.Create },
                onDismiss = onDismiss,
                onSelect = { choice ->
                    val playlist = state.playlists.firstOrNull { it.id.toString() == choice.key }
                    if (playlist != null && !state.isSubmitting) {
                        viewModel.addToPlaylist(playlist, trackIds)
                    }
                },
            )
        }

        DialogScreen.Create ->
            CreateNamedItemDialog(
                title = stringResource(R.string.music_new_playlist_title),
                nameLabel = stringResource(R.string.music_new_playlist_name_label),
                toggleLabel = stringResource(R.string.music_playlist_public_label),
                toggleHint = { isPublic ->
                    stringResource(
                        if (isPublic) R.string.music_playlist_visible_all
                        else R.string.music_playlist_visible_only_you
                    )
                },
                isLoading = state.isSubmitting,
                onDismiss = { screen = DialogScreen.List },
                onCreate = { name, isPublic ->
                    viewModel.createPlaylist(name, trackIds, isPublic, mediaType)
                },
            )
    }
}
