package com.makd.afinity.ui.admin.libraries

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ServerFolder
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.ui.admin.config.ChoiceOptionsLoader
import com.makd.afinity.ui.admin.config.ConfigFormEditor
import com.makd.afinity.ui.admin.config.LoadedConfigForm
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.jellyfin.sdk.model.api.CollectionTypeOptions

enum class NewLibraryType(
    val collectionType: CollectionTypeOptions?,
    @param:StringRes val label: Int,
) {
    MOVIES(CollectionTypeOptions.MOVIES, R.string.library_type_movies),
    SHOWS(CollectionTypeOptions.TVSHOWS, R.string.library_type_shows),
    MUSIC(CollectionTypeOptions.MUSIC, R.string.library_type_music),
    BOOKS(CollectionTypeOptions.BOOKS, R.string.library_type_books),
    HOME_VIDEOS(CollectionTypeOptions.HOMEVIDEOS, R.string.library_type_home_videos),
    MUSIC_VIDEOS(CollectionTypeOptions.MUSICVIDEOS, R.string.library_type_music_videos),
    MIXED(null, R.string.library_type_mixed),
}

data class AddLibraryUiState(
    val name: String = "",
    val type: NewLibraryType = NewLibraryType.MOVIES,
    val paths: List<String> = emptyList(),
    val existingNames: Set<String> = emptySet(),
    val isCreating: Boolean = false,
) {
    val isDuplicateName: Boolean
        get() = name.trim().lowercase() in existingNames

    val hasInput: Boolean
        get() = name.isNotBlank() || paths.isNotEmpty()

    val isComplete: Boolean
        get() = name.isNotBlank() && !isDuplicateName && paths.isNotEmpty()
}

@HiltViewModel
class AddLibraryViewModel @Inject constructor(private val adminRepository: ServerAdminRepository) :
    ViewModel() {

    private val optionsLoader = ChoiceOptionsLoader(adminRepository)

    private val _state = MutableStateFlow(AddLibraryUiState())
    val state: StateFlow<AddLibraryUiState> = _state.asStateFlow()

    private val _editor = MutableStateFlow(newEditor(NewLibraryType.MOVIES))
    val editor: StateFlow<ConfigFormEditor> = _editor.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    private val _created = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val created: SharedFlow<Unit> = _created.asSharedFlow()

    init {
        viewModelScope.launch {
            adminRepository.getLibraries().onSuccess { libraries ->
                val names = libraries.mapNotNull { it.name?.trim()?.lowercase() }.toSet()
                _state.update { it.copy(existingNames = names) }
            }
        }
    }

    suspend fun browseFolders(path: String?): Result<List<ServerFolder>> =
        adminRepository.getServerFolders(path)

    fun setName(name: String) {
        _state.update { it.copy(name = name) }
    }

    fun setType(type: NewLibraryType) {
        if (type == _state.value.type) return
        _state.update { it.copy(type = type) }
        _editor.value = newEditor(type)
    }

    fun addPath(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        _state.update { state ->
            if (trimmed in state.paths) state else state.copy(paths = state.paths + trimmed)
        }
    }

    fun removePath(path: String) {
        _state.update { it.copy(paths = it.paths - path) }
    }

    fun create() {
        val current = _state.value
        val form = _editor.value.state.value
        val defaults = form.document ?: return
        if (current.isCreating || !current.isComplete) return
        if (form.invalidKeys.isNotEmpty() || form.conflictKeys.isNotEmpty()) return
        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                val result =
                    adminRepository.createLibrary(
                        name = current.name,
                        collectionType = current.type.collectionType,
                        paths = current.paths,
                        options = JsonObject(defaults + form.changes),
                    )
                if (result.isSuccess) _created.tryEmit(Unit) else _commandError.tryEmit(Unit)
            } finally {
                _state.update { it.copy(isCreating = false) }
            }
        }
    }

    private fun newEditor(type: NewLibraryType): ConfigFormEditor =
        ConfigFormEditor(
            scope = viewModelScope,
            loadForm = {
                val spec = libraryOptionsForm(type.collectionType?.serialName)
                Result.success(
                    LoadedConfigForm(
                        spec = optionsLoader.resolve(spec),
                        document = newLibraryDefaults(),
                    )
                )
            },
            saveChanges = { Result.success(Unit) },
        )
}
