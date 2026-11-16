package com.makd.afinity.ui.audiobook

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.audiobooks.JellyfinAudiobookLoader
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.MediaChangeSource
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.media.asBook
import com.makd.afinity.data.models.media.withUserDataPatch
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.ui.components.DownloadConfirm
import com.makd.afinity.ui.components.DownloadConfirmKind
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TICKS_PER_SECOND = 10_000_000.0

data class AudiobookDetailUiState(
    val parts: List<AfinityAudiobook> = emptyList(),
    val book: AfinityAudiobook? = null,
    val isLoading: Boolean = true,
    @StringRes val errorRes: Int? = null,
)

@HiltViewModel
class AudiobookDetailViewModel
@Inject
constructor(
    private val audiobookLoader: JellyfinAudiobookLoader,
    private val userDataRepository: UserDataRepository,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val mediaChangeManager: MediaChangeManager,
    private val itemStore: ItemStore,
    private val downloadRepository: DownloadRepository,
    private val offlineModeManager: OfflineModeManager,
    downloadPermissions: DownloadPermissions,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val itemId: UUID = UUID.fromString(savedStateHandle.get<String>("itemId")!!)

    val isOffline: StateFlow<Boolean> = offlineModeManager.isOffline

    private val _uiState = MutableStateFlow(AudiobookDetailUiState())
    val uiState: StateFlow<AudiobookDetailUiState> =
        itemStore.project(viewModelScope, _uiState) { state ->
            val parts = live(state.parts)
            if (parts === state.parts) state else state.copy(parts = parts, book = parts.asBook())
        }

    val isDownloadAllowedByServer: StateFlow<Boolean> = downloadPermissions.isAllowedByServer

    val canDownloadOnNetwork: StateFlow<Boolean> = downloadPermissions.isAllowedOnNetwork

    private val partDownloads: StateFlow<List<DownloadInfo>> =
        combine(
                downloadRepository.getAllDownloadsFlow(),
                _uiState.map { state -> state.parts.map { it.id }.toSet() }.distinctUntilChanged(),
            ) { downloads, ids ->
                downloads.filter { it.itemId in ids }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadInfo: StateFlow<DownloadInfo?> =
        combine(partDownloads, _uiState.map { it.parts.size }.distinctUntilChanged()) {
                downloads,
                partCount ->
                aggregateDownload(downloads, partCount)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startDownload(volumeId: String? = null) {
        viewModelScope.launch {
            downloadRepository.startAudiobookDownload(itemId, volumeId).onFailure {
                Timber.w(it, "Failed to start audiobook download for $itemId")
            }
        }
    }

    fun pauseDownload() {
        viewModelScope.launch {
            partDownloads.value
                .filter {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                }
                .forEach { downloadRepository.pauseDownload(it.id) }
        }
    }

    fun resumeDownload() {
        viewModelScope.launch {
            partDownloads.value
                .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
                .forEach { downloadRepository.resumeDownload(it.id) }
            if (partDownloads.value.size < _uiState.value.parts.size) {
                downloadRepository.startAudiobookDownload(itemId)
            }
        }
    }

    fun cancelOrDeleteDownload() {
        viewModelScope.launch {
            partDownloads.value.forEach { download ->
                if (download.status == DownloadStatus.COMPLETED) {
                    downloadRepository.deleteDownload(download.id)
                } else {
                    downloadRepository.cancelDownload(download.id)
                }
            }
        }
    }

    fun downloadConfirm(title: String): DownloadConfirm? {
        val downloads = partDownloads.value
        val aggregate = downloadInfo.value ?: return null
        return when (aggregate.status) {
            DownloadStatus.COMPLETED ->
                DownloadConfirm(
                    DownloadConfirmKind.DELETE,
                    title,
                    downloads.size,
                    downloads.sumOf { it.totalBytes },
                )
            DownloadStatus.DOWNLOADING,
            DownloadStatus.QUEUED,
            DownloadStatus.PAUSED,
            DownloadStatus.FAILED ->
                DownloadConfirm(
                    DownloadConfirmKind.CANCEL,
                    title,
                    downloads.size,
                    downloads.sumOf { it.bytesDownloaded },
                )
            else -> null
        }
    }

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorRes = null) }
            audiobookLoader
                .loadParts(itemId, offline = offlineModeManager.isCurrentlyOffline())
                .onSuccess { parts ->
                    if (parts.isNullOrEmpty()) {
                        _uiState.update {
                            it.copy(isLoading = false, errorRes = R.string.item_error_not_found)
                        }
                        return@onSuccess
                    }
                    _uiState.update {
                        it.copy(
                            parts = parts,
                            book = parts.asBook(),
                            isLoading = false,
                            errorRes = null,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    if (e !is NoActiveSessionException) {
                        Timber.e(e, "Failed to load audiobook $itemId")
                    }
                    _uiState.update {
                        it.copy(isLoading = false, errorRes = R.string.error_load_content_generic)
                    }
                }
        }
    }

    fun toggleFavorite() {
        val book = uiState.value.book ?: return
        val target = !book.favorite
        book.playableParts
            .filter { it.favorite != target }
            .forEach { part ->
                itemUserDataDelegate.toggleFavorite(
                    scope = viewModelScope,
                    item = part,
                    updateOptimisticUI = {
                        itemStore.applyPatch(part.id, UserDataPatch(favorite = target))
                    },
                    revertUI = {
                        itemStore.applyPatch(part.id, UserDataPatch(favorite = part.favorite))
                    },
                )
            }
    }

    fun togglePlayed() {
        val book = uiState.value.book ?: return
        val target = !book.played
        val changing = book.playableParts.filter { it.played != target }
        changing.forEach { part -> itemStore.applyPatch(part.id, playedPatch(part, target)) }
        viewModelScope.launch {
            changing.forEach { part ->
                val patch = playedPatch(part, target)
                val revert =
                    UserDataPatch(
                        played = part.played,
                        playbackPositionTicks = part.playbackPositionTicks,
                    )
                try {
                    val success =
                        if (target) userDataRepository.markWatched(part.id)
                        else userDataRepository.markUnwatched(part.id)
                    if (success) {
                        mediaChangeManager.publishKnownChange(
                            updatedItem = part.withUserDataPatch(patch),
                            source = MediaChangeSource.MANUAL,
                            patch = patch,
                        )
                    } else {
                        itemStore.applyPatch(part.id, revert)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to toggle played state for ${part.id}")
                    itemStore.applyPatch(part.id, revert)
                }
            }
        }
    }

    private fun playedPatch(part: AfinityAudiobook, played: Boolean): UserDataPatch =
        UserDataPatch(
            played = played,
            playbackPositionTicks = if (played) 0L else part.playbackPositionTicks,
        )
}

private fun aggregateDownload(downloads: List<DownloadInfo>, partCount: Int): DownloadInfo? {
    if (downloads.isEmpty() || partCount == 0) return null
    val status =
        when {
            downloads.size >= partCount &&
                downloads.all { it.status == DownloadStatus.COMPLETED } -> DownloadStatus.COMPLETED
            downloads.any { it.status == DownloadStatus.DOWNLOADING } -> DownloadStatus.DOWNLOADING
            downloads.any { it.status == DownloadStatus.QUEUED } -> DownloadStatus.QUEUED
            downloads.any { it.status == DownloadStatus.FAILED } -> DownloadStatus.FAILED
            else -> DownloadStatus.PAUSED
        }
    val progress =
        downloads
            .sumOf { if (it.status == DownloadStatus.COMPLETED) 1.0 else it.progress.toDouble() }
            .div(partCount)
            .toFloat()
            .coerceIn(0f, 1f)
    return downloads
        .first()
        .copy(
            status = status,
            progress = progress,
            bytesDownloaded = downloads.sumOf { it.bytesDownloaded },
            totalBytes = downloads.sumOf { it.totalBytes },
        )
}

internal fun AfinityAudiobook.positionSeconds(): Double? =
    when {
        played -> runtimeTicks / TICKS_PER_SECOND
        playbackPositionTicks > 0 -> playbackPositionTicks / TICKS_PER_SECOND
        else -> null
    }
