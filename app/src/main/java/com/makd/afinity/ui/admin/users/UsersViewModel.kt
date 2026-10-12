package com.makd.afinity.ui.admin.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.util.JellyfinImageUrlBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val STOP_TIMEOUT_MS = 5_000L

data class AdminUserRow(
    val id: UUID,
    val name: String,
    val isAdmin: Boolean,
    val isDisabled: Boolean,
    val lastActivity: LocalDateTime?,
    val imageUrl: String?,
)

data class UsersUiState(
    val users: List<AdminUserRow>? = null,
    val failed: Boolean = false,
    val isBusy: Boolean = false,
) {
    val adminCount: Int
        get() = users.orEmpty().count { it.isAdmin }

    val disabledCount: Int
        get() = users.orEmpty().count { it.isDisabled }
}

@HiltViewModel
class UsersViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    private val sessionManager: SessionManager,
    private val imageUrlBuilder: JellyfinImageUrlBuilder,
) : ViewModel() {

    private val _state = MutableStateFlow(UsersUiState())
    val state: StateFlow<UsersUiState> = _state.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    private val _userCreated = MutableSharedFlow<UUID>(extraBufferCapacity = 1)
    val userCreated: SharedFlow<UUID> = _userCreated.asSharedFlow()

    val playingUserIds: StateFlow<Set<UUID>> =
        adminRepository.sessions
            .map { result ->
                result
                    ?.getOrNull()
                    .orEmpty()
                    .filter { it.nowPlayingItem != null }
                    .map { it.userId }
                    .toSet()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch { fetch() }
    }

    fun create(name: String, password: String) {
        if (name.isBlank() || _state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                adminRepository
                    .createUser(name, password)
                    .onSuccess { id ->
                        fetch()
                        _userCreated.tryEmit(id)
                    }
                    .onFailure { _commandError.tryEmit(Unit) }
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun fetch() {
        adminRepository
            .getUsers()
            .onSuccess { users ->
                val baseUrl = sessionManager.currentSession.value?.serverUrl
                val rows =
                    users
                        .map { user ->
                            val imageTag = user.primaryImageTag
                            AdminUserRow(
                                id = user.id,
                                name = user.name.orEmpty(),
                                isAdmin = user.policy?.isAdministrator == true,
                                isDisabled = user.policy?.isDisabled == true,
                                lastActivity = user.lastActivityDate,
                                imageUrl =
                                    if (baseUrl != null && imageTag != null) {
                                        imageUrlBuilder.buildUserPrimaryImageUrl(
                                            baseUrl = baseUrl,
                                            userId = user.id.toString(),
                                            tag = imageTag,
                                            maxWidth = USER_AVATAR_MAX_WIDTH,
                                        )
                                    } else {
                                        null
                                    },
                            )
                        }
                        .sortedBy { it.name.lowercase() }
                _state.update { it.copy(users = rows, failed = false) }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load users")
                _state.update { it.copy(failed = it.users == null) }
            }
    }
}
