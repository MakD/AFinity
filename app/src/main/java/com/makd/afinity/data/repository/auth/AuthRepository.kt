package com.makd.afinity.data.repository.auth

import com.makd.afinity.data.models.auth.QuickConnectState
import com.makd.afinity.data.models.user.User
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.model.api.AuthenticationResult

interface AuthRepository {

    val currentUser: StateFlow<User?>
    val isAuthenticated: StateFlow<Boolean>

    suspend fun restoreAuthenticationState(): RestoreResult

    suspend fun hasValidSavedAuth(): Boolean

    suspend fun clearAllAuthData()

    suspend fun authenticateByName(
        serverUrl: String,
        username: String,
        password: String,
    ): AuthResult

    suspend fun authenticateWithQuickConnect(serverUrl: String, secret: String): AuthResult

    suspend fun logout()

    suspend fun initiateQuickConnect(serverUrl: String): QuickConnectState?

    suspend fun getQuickConnectState(serverUrl: String, secret: String): QuickConnectState?

    suspend fun getCurrentUser(): User?

    suspend fun getPublicUsers(serverUrl: String): List<User>

    sealed class AuthResult {
        data class Success(val authResult: AuthenticationResult) : AuthResult()

        data class Error(val message: String) : AuthResult()
    }

    sealed class RestoreResult {
        object Success : RestoreResult()

        data class Degraded(val reason: Throwable) : RestoreResult()

        object Failed : RestoreResult()
    }
}
