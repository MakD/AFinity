package com.makd.afinity.ui.admin.users

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class AdminUserDetail(
    val id: UUID,
    val name: String,
    val isAdmin: Boolean,
    val isDisabled: Boolean,
    val lastActivity: LocalDateTime?,
    val isSelf: Boolean,
    val isOnlyAdmin: Boolean,
    val imageUrl: String?,
) {
    val canDisable: Boolean
        get() = !isAdmin

    val canDelete: Boolean
        get() = !isSelf && !isOnlyAdmin
}

data class UserDetailUiState(
    val user: AdminUserDetail? = null,
    val failed: Boolean = false,
    val missing: Boolean = false,
    val isBusy: Boolean = false,
)

@HiltViewModel
class UserDetailViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    private val sessionManager: SessionManager,
    private val imageUrlBuilder: JellyfinImageUrlBuilder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: UUID? =
        savedStateHandle.get<String>("userId")?.let { raw ->
            runCatching { UUID.fromString(raw) }.getOrNull()
        }

    private val _state = MutableStateFlow(UserDetailUiState())
    val state: StateFlow<UserDetailUiState> = _state.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch { fetch() }
    }

    fun rename(name: String) {
        val id = userId ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == _state.value.user?.name) return
        runCommand { adminRepository.renameUser(id, trimmed) }
    }

    fun setPassword(newPassword: String, currentPassword: String?) {
        val id = userId ?: return
        if (newPassword.isEmpty()) return
        runCommand { adminRepository.setUserPassword(id, newPassword, currentPassword) }
    }

    fun setDisabled(disabled: Boolean) {
        val id = userId ?: return
        val user = _state.value.user ?: return
        if (disabled && !user.canDisable) return
        runCommand { adminRepository.setUserDisabled(id, disabled) }
    }

    fun delete() {
        val id = userId ?: return
        val user = _state.value.user ?: return
        if (!user.canDelete || _state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                if (adminRepository.deleteUser(id).isSuccess) {
                    _deleted.tryEmit(Unit)
                } else {
                    _commandError.tryEmit(Unit)
                    fetch()
                }
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun runCommand(block: suspend () -> Result<Unit>) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                if (block().isFailure) _commandError.tryEmit(Unit)
                fetch()
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun fetch() {
        adminRepository
            .getUsers()
            .onSuccess { users ->
                val user = users.firstOrNull { it.id == userId }
                val adminCount = users.count { it.policy?.isAdministrator == true }
                val session = sessionManager.currentSession.value
                val detail = user?.let {
                    val isAdmin = it.policy?.isAdministrator == true
                    val baseUrl = session?.serverUrl
                    val imageTag = it.primaryImageTag
                    AdminUserDetail(
                        id = it.id,
                        name = it.name.orEmpty(),
                        isAdmin = isAdmin,
                        isDisabled = it.policy?.isDisabled == true,
                        lastActivity = it.lastActivityDate,
                        isSelf = it.id == session?.userId,
                        isOnlyAdmin = isAdmin && adminCount == 1,
                        imageUrl =
                            if (baseUrl != null && imageTag != null) {
                                imageUrlBuilder.buildUserPrimaryImageUrl(
                                    baseUrl = baseUrl,
                                    userId = it.id.toString(),
                                    tag = imageTag,
                                    maxWidth = USER_AVATAR_MAX_WIDTH,
                                )
                            } else {
                                null
                            },
                    )
                }
                _state.update { it.copy(user = detail, failed = false, missing = detail == null) }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load user")
                _state.update { it.copy(failed = it.user == null) }
            }
    }
}
