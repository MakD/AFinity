package com.makd.afinity.ui.admin.lyrics

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.LyricsNotSavedException
import com.makd.afinity.data.models.admin.LyricsTarget
import com.makd.afinity.data.models.admin.RemoteLyrics
import com.makd.afinity.data.repository.admin.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private val LYRIC_EXTENSIONS = setOf("lrc", "txt")
private const val MAX_LYRIC_BYTES = 1_048_576L
private const val BYTE_ORDER_MARK = '﻿'
private const val READ_BUFFER_BYTES = 8_192

private fun InputStream.readAtMost(limit: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(READ_BUFFER_BYTES)
    while (output.size() < limit) {
        val read = read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

enum class EditLyricsMessage(@param:StringRes val textRes: Int) {
    SAVED(R.string.lyrics_saved),
    SAVED_NOT_SHOWN(R.string.lyrics_saved_not_shown),
    DELETED(R.string.lyrics_deleted),
    SAVE_FAILED(R.string.lyrics_save_failed),
    DELETE_FAILED(R.string.lyrics_delete_failed),
    UNSUPPORTED_FILE(R.string.lyrics_unsupported_file),
    FILE_TOO_LARGE(R.string.lyrics_file_too_large),
    FILE_EMPTY(R.string.lyrics_file_empty),
    FILE_UNREADABLE(R.string.lyrics_file_unreadable),
}

sealed interface PendingLyrics {
    data class Remote(val result: RemoteLyrics) : PendingLyrics

    data class Upload(val fileName: String, val text: String) : PendingLyrics
}

data class EditLyricsUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val target: LyricsTarget? = null,
    val searching: Boolean = false,
    val hasSearched: Boolean = false,
    val searchFailed: Boolean = false,
    val results: List<RemoteLyrics> = emptyList(),
    val working: Boolean = false,
    val pending: PendingLyrics? = null,
    val confirmingDelete: Boolean = false,
    val message: EditLyricsMessage? = null,
)

@HiltViewModel
class EditLyricsViewModel
@Inject
constructor(
    private val adminRepository: AdminRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(EditLyricsUiState())
    val uiState: StateFlow<EditLyricsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        load(showLoading = true)
    }

    fun retryLoad() = load(showLoading = true)

    private fun load(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(loading = true, loadFailed = false) }
            adminRepository
                .getLyricsTarget(itemId)
                .onSuccess { target ->
                    _uiState.update {
                        it.copy(loading = false, loadFailed = false, target = target)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(loading = false, loadFailed = it.target == null) }
                }
        }
    }

    fun search() {
        if (_uiState.value.searching) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    searching = true,
                    hasSearched = true,
                    searchFailed = false,
                    results = emptyList(),
                )
            }
            adminRepository
                .searchRemoteLyrics(itemId)
                .onSuccess { results ->
                    _uiState.update { it.copy(searching = false, results = results) }
                }
                .onFailure { _uiState.update { it.copy(searching = false, searchFailed = true) } }
        }
    }

    fun useRemote(result: RemoteLyrics) = request(PendingLyrics.Remote(result))

    fun uploadFile(uri: Uri) {
        if (_uiState.value.working) return
        viewModelScope.launch {
            when (val read = readLyricFile(uri)) {
                is LyricFileRead.Ok -> request(PendingLyrics.Upload(read.fileName, read.text))
                is LyricFileRead.Rejected -> _uiState.update { it.copy(message = read.message) }
            }
        }
    }

    private fun request(pending: PendingLyrics) {
        val state = _uiState.value
        if (state.working) return
        if (state.target?.lyrics == null) apply(pending)
        else _uiState.update { it.copy(pending = pending) }
    }

    fun confirmPending() {
        _uiState.value.pending?.let(::apply)
    }

    fun dismissPending() {
        if (!_uiState.value.working) _uiState.update { it.copy(pending = null) }
    }

    private fun apply(pending: PendingLyrics) {
        _uiState.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val outcome =
                when (pending) {
                    is PendingLyrics.Remote ->
                        adminRepository.replaceWithRemoteLyrics(itemId, pending.result.id)
                    is PendingLyrics.Upload ->
                        adminRepository.replaceWithUploadedLyrics(
                            itemId,
                            pending.fileName,
                            pending.text,
                        )
                }
            val message =
                when {
                    outcome.isSuccess -> EditLyricsMessage.SAVED
                    outcome.exceptionOrNull() is LyricsNotSavedException ->
                        EditLyricsMessage.SAVED_NOT_SHOWN
                    else -> EditLyricsMessage.SAVE_FAILED
                }
            _uiState.update { it.copy(working = false, pending = null, message = message) }
            load(showLoading = false)
        }
    }

    fun requestDelete() {
        if (!_uiState.value.working) _uiState.update { it.copy(confirmingDelete = true) }
    }

    fun dismissDelete() {
        if (!_uiState.value.working) _uiState.update { it.copy(confirmingDelete = false) }
    }

    fun confirmDelete() {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val outcome = adminRepository.deleteLyrics(itemId)
            _uiState.update {
                it.copy(
                    working = false,
                    confirmingDelete = false,
                    message =
                        if (outcome.isSuccess) EditLyricsMessage.DELETED
                        else EditLyricsMessage.DELETE_FAILED,
                )
            }
            load(showLoading = false)
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private sealed interface LyricFileRead {
        data class Ok(val fileName: String, val text: String) : LyricFileRead

        data class Rejected(val message: EditLyricsMessage) : LyricFileRead
    }

    private suspend fun readLyricFile(uri: Uri): LyricFileRead =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                var displayName: String? = null
                var size: Long? = null
                resolver
                    .query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null,
                        null,
                        null,
                    )
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            displayName = cursor.getString(0)
                            size = if (cursor.isNull(1)) null else cursor.getLong(1)
                        }
                    }
                val extension = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
                if (extension !in LYRIC_EXTENSIONS) {
                    return@withContext LyricFileRead.Rejected(EditLyricsMessage.UNSUPPORTED_FILE)
                }
                if ((size ?: 0L) > MAX_LYRIC_BYTES) {
                    return@withContext LyricFileRead.Rejected(EditLyricsMessage.FILE_TOO_LARGE)
                }
                val bytes =
                    resolver.openInputStream(uri)?.use { input ->
                        input.readAtMost(MAX_LYRIC_BYTES + 1)
                    }
                        ?: return@withContext LyricFileRead.Rejected(
                            EditLyricsMessage.FILE_UNREADABLE
                        )
                if (bytes.size > MAX_LYRIC_BYTES) {
                    return@withContext LyricFileRead.Rejected(EditLyricsMessage.FILE_TOO_LARGE)
                }
                val text = bytes.toString(Charsets.UTF_8).trimStart(BYTE_ORDER_MARK)
                if (text.isBlank()) {
                    return@withContext LyricFileRead.Rejected(EditLyricsMessage.FILE_EMPTY)
                }
                LyricFileRead.Ok(fileName = "lyrics.$extension", text = text)
            } catch (e: IOException) {
                Timber.e(e, "Failed to read lyric file")
                LyricFileRead.Rejected(EditLyricsMessage.FILE_UNREADABLE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error reading lyric file")
                LyricFileRead.Rejected(EditLyricsMessage.FILE_UNREADABLE)
            }
        }
}
