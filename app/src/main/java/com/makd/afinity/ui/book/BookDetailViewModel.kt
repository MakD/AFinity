package com.makd.afinity.ui.book

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.books.BookMetadataStore
import com.makd.afinity.data.books.BookSeriesResolver
import com.makd.afinity.data.books.PaceKind
import com.makd.afinity.data.books.ReadingPaceStore
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.MediaChangeSource
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.extensions.toAfinityBook
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.media.withUserDataPatch
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.ui.item.delegates.ItemDownloadDelegate
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

data class BookDetailUiState(
    val book: AfinityBook? = null,
    val seriesBooks: List<AfinityBook> = emptyList(),
    val seriesStartIndex: Int = 0,
    val isLoading: Boolean = true,
    @StringRes val errorRes: Int? = null,
)

@HiltViewModel
class BookDetailViewModel
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val userDataRepository: UserDataRepository,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val itemDownloadDelegate: ItemDownloadDelegate,
    private val downloadRepository: DownloadRepository,
    private val bookMetadataStore: BookMetadataStore,
    private val mediaChangeManager: MediaChangeManager,
    private val itemStore: ItemStore,
    private val paceStore: ReadingPaceStore,
    private val seriesResolver: BookSeriesResolver,
    downloadPermissions: DownloadPermissions,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val itemId: UUID = UUID.fromString(savedStateHandle.get<String>("itemId")!!)

    val isDownloadAllowedByServer: StateFlow<Boolean> = downloadPermissions.isAllowedByServer

    val canDownloadOnNetwork: StateFlow<Boolean> = downloadPermissions.isAllowedOnNetwork

    val downloadInfo: StateFlow<DownloadInfo?> =
        downloadRepository
            .getAllDownloadsFlow()
            .map { downloads ->
                downloads.filter { it.itemId == itemId }.maxByOrNull { it.updatedAt }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> =
        itemStore.project(viewModelScope, _uiState) { state ->
            val book = liveOrNull(state.book)
            val seriesBooks = live(state.seriesBooks)
            if (book === state.book && seriesBooks === state.seriesBooks) state
            else state.copy(book = book, seriesBooks = seriesBooks)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val secondsLeft: StateFlow<Double?> =
        uiState
            .map { it.book }
            .distinctUntilChanged()
            .mapLatest { book -> book?.let { estimateSecondsLeft(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun estimateSecondsLeft(book: AfinityBook): Double? {
        if (book.played || book.playbackPositionTicks <= 0L || book.runtimeTicks <= 0L) return null
        val pages = book.pageCount
        if (pages != null) {
            val page = (book.playbackPositionTicks / AfinityBook.TICKS_PER_PAGE).toInt()
            val left = (pages - 1 - page).coerceAtLeast(0)
            if (left == 0) return null
            return left * paceStore.millisPerUnit(PaceKind.PAGES) / 1000.0
        }
        val positions = paceStore.textLength(book.id) ?: return null
        val progress =
            (book.playbackPositionTicks.toDouble() / book.runtimeTicks).coerceIn(0.0, 1.0)
        return (1.0 - progress) * positions * paceStore.millisPerUnit(PaceKind.TEXT) / 1000.0
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
            mediaRepository
                .getItemResult(itemId, fields = FieldSets.BOOK_DETAIL)
                .onSuccess { dto ->
                    val book =
                        dto?.takeIf { it.type == BaseItemKind.BOOK }
                            ?.toAfinityBook(mediaRepository.getBaseUrl())
                    _uiState.update {
                        if (book == null) {
                            it.copy(isLoading = false, errorRes = R.string.item_error_not_found)
                        } else {
                            it.copy(book = book, isLoading = false, errorRes = null)
                        }
                    }
                    if (book != null) loadSeries(book)
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    val offline = bookMetadataStore.downloaded(itemId)?.book
                    if (offline != null) {
                        _uiState.update {
                            it.copy(book = offline, isLoading = false, errorRes = null)
                        }
                        return@onFailure
                    }
                    if (e !is NoActiveSessionException) Timber.e(e, "Failed to load book $itemId")
                    _uiState.update {
                        it.copy(isLoading = false, errorRes = R.string.error_load_content_generic)
                    }
                }
        }
    }

    private suspend fun loadSeries(book: AfinityBook) {
        val books = seriesResolver.series(book)
        val currentIndex = books.indexOfFirst { it.id == book.id }
        val others = books.filterNot { it.id == book.id }
        if (others.isEmpty()) return
        _uiState.update {
            it.copy(
                seriesBooks = others,
                seriesStartIndex = currentIndex.coerceIn(0, others.lastIndex),
            )
        }
    }

    fun startDownload(volumeId: String? = null) {
        viewModelScope.launch {
            downloadRepository
                .startDownload(itemId = itemId, sourceId = "", volumeId = volumeId)
                .onFailure { Timber.w(it, "Failed to start book download for $itemId") }
        }
    }

    fun pauseDownload() = itemDownloadDelegate.pauseDownload(viewModelScope, downloadInfo.value)

    fun resumeDownload() = itemDownloadDelegate.resumeDownload(viewModelScope, downloadInfo.value)

    fun cancelOrDeleteDownload() =
        itemDownloadDelegate.cancelDownload(viewModelScope, downloadInfo.value)

    fun toggleFavorite() {
        val book = uiState.value.book ?: return
        itemUserDataDelegate.toggleFavorite(
            scope = viewModelScope,
            item = book,
            updateOptimisticUI = {
                itemStore.applyPatch(book.id, UserDataPatch(favorite = !book.favorite))
            },
            revertUI = { itemStore.applyPatch(book.id, UserDataPatch(favorite = book.favorite)) },
        )
    }

    fun toggleRead() {
        val book = uiState.value.book ?: return
        val nowRead = !book.played
        val patch =
            UserDataPatch(
                played = nowRead,
                playbackPositionTicks = if (nowRead) 0L else book.playbackPositionTicks,
            )
        val revert =
            UserDataPatch(played = book.played, playbackPositionTicks = book.playbackPositionTicks)
        itemStore.applyPatch(book.id, patch)
        viewModelScope.launch {
            try {
                val success =
                    if (nowRead) userDataRepository.markWatched(book.id)
                    else userDataRepository.markUnwatched(book.id)
                if (success) {
                    val refreshed =
                        mediaRepository.refreshItemUserData(book.id, FieldSets.BOOK_GRID)
                    mediaChangeManager.publishKnownChange(
                        updatedItem = refreshed ?: book.withUserDataPatch(patch),
                        source = MediaChangeSource.MANUAL,
                        patch = patch,
                    )
                } else {
                    itemStore.applyPatch(book.id, revert)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle read state for ${book.id}")
                itemStore.applyPatch(book.id, revert)
            }
        }
    }
}
