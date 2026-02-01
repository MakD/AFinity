package com.makd.afinity.ui.settings.servers

import android.content.Context
import android.webkit.CookieManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import com.makd.afinity.data.repository.server.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject

data class AddEditServerState(
    val serverId: String? = null,
    val serverUrl: String = "",
    val serverName: String = "",
    val isTestingConnection: Boolean = false,
    val connectionTestResult: ConnectionTestResult? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val requiresWebViewUrl: String? = null
)

sealed class ConnectionTestResult {
    data class Success(
        val serverInfo: ServerInfo
    ) : ConnectionTestResult()

    data class Error(
        val message: String
    ) : ConnectionTestResult()
}

data class ServerInfo(
    val id: String,
    val name: String,
    val version: String,
    val address: String
)

@HiltViewModel
class AddEditServerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverRepository: ServerRepository,
    private val databaseRepository: DatabaseRepository,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: String? =
        savedStateHandle.get<String>("serverId")?.takeIf { it != "null" }

    private val _state = MutableStateFlow(AddEditServerState(serverId = serverId))
    val state: StateFlow<AddEditServerState> = _state.asStateFlow()

    init {
        serverId?.let { loadServer(it) }
    }

    private fun loadServer(serverId: String) {
        viewModelScope.launch {
            try {
                val server = databaseRepository.getServer(serverId)
                if (server != null) {
                    _state.value = _state.value.copy(
                        serverUrl = server.address,
                        serverName = server.name
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading server")
                _state.value = _state.value.copy(
                    error = context.getString(R.string.error_load_server_failed_fmt, e.message)
                )
            }
        }
    }

    fun updateServerUrl(url: String) {
        _state.value = _state.value.copy(
            serverUrl = url,
            connectionTestResult = null
        )
    }

    fun updateServerName(name: String) {
        _state.value = _state.value.copy(serverName = name)
    }

    fun testConnection() {
        val url = _state.value.serverUrl.trim()
        if (url.isBlank()) {
            _state.value = _state.value.copy(
                connectionTestResult = ConnectionTestResult.Error(context.getString(R.string.error_enter_server_url))
            )
            return
        }

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isTestingConnection = true,
                    connectionTestResult = null,
                    error = null
                )

                when (val result = serverRepository.testServerConnection(url)) {
                    is JellyfinServerRepository.ServerConnectionResult.Success -> {
                        handleTestSuccess(result)
                    }

                    is JellyfinServerRepository.ServerConnectionResult.Error -> {
                        if (trySyncWebViewCookies(url)) {
                            val retryResult = serverRepository.testServerConnection(url)
                            if (retryResult is JellyfinServerRepository.ServerConnectionResult.Success) {
                                handleTestSuccess(retryResult)
                                return@launch
                            }
                        }

                        if (checkForProxyWall(url)) {
                            _state.value = _state.value.copy(
                                isTestingConnection = false,
                                requiresWebViewUrl = url
                            )
                        } else {
                            _state.value = _state.value.copy(
                                isTestingConnection = false,
                                connectionTestResult = ConnectionTestResult.Error(result.message)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error testing connection")
                _state.value = _state.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = ConnectionTestResult.Error(
                        context.getString(
                            R.string.error_connection_test_failed_fmt,
                            e.message ?: context.getString(R.string.error_unknown)
                        )
                    )
                )
            }
        }
    }

    private fun handleTestSuccess(result: JellyfinServerRepository.ServerConnectionResult.Success) {
        val serverInfo = ServerInfo(
            id = result.server.id,
            name = result.server.name,
            version = result.version,
            address = result.serverAddress
        )
        val currentName = _state.value.serverName
        _state.value = _state.value.copy(
            isTestingConnection = false,
            connectionTestResult = ConnectionTestResult.Success(serverInfo),
            serverName = currentName.ifBlank { serverInfo.name }
        )
    }

    private suspend fun trySyncWebViewCookies(url: String): Boolean {
        val cookies = CookieManager.getInstance().getCookie(url)
        if (!cookies.isNullOrBlank()) {
            Timber.d("AddEditServer: Found proxy cookies in CookieManager for: $url")
            securePreferencesRepository.saveAuthCookies(url, cookies)
            return true
        }
        return false
    }

    private suspend fun checkForProxyWall(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("$url/System/Info/Public").build()
                okHttpClient.newCall(request).execute().use { response ->
                    val isHtml = response.header("Content-Type", "")?.contains("text/html") == true
                    val code = response.code
                    code in 300..399 || code == 401 || code == 403 || (code == 200 && isHtml)
                }
            } catch (e: Exception) {
                Timber.e(e, "AddEditServer: checkForProxyWall exception")
                false
            }
        }
    }

    fun saveAuthCookies(cookies: String) {
        viewModelScope.launch {
            securePreferencesRepository.saveAuthCookies(_state.value.serverUrl, cookies)
            testConnection()
        }
    }

    fun onWebViewLaunched() {
        _state.value = _state.value.copy(requiresWebViewUrl = null)
    }

    fun saveServer() {
        val currentState = _state.value
        val url = currentState.serverUrl.trim()
        val name = currentState.serverName.trim()

        if (url.isBlank()) {
            _state.value = _state.value.copy(error = context.getString(R.string.error_url_required))
            return
        }

        val testResult = currentState.connectionTestResult
        if (testResult !is ConnectionTestResult.Success) {
            _state.value = _state.value.copy(
                error = context.getString(R.string.error_test_connection_first)
            )
            return
        }

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isSaving = true,
                    error = null
                )

                val serverInfo = testResult.serverInfo
                val server = Server(
                    id = currentState.serverId ?: serverInfo.id,
                    name = name.ifBlank { serverInfo.name },
                    version = serverInfo.version,
                    address = url
                )

                if (currentState.serverId != null) {
                    databaseRepository.updateServer(server)
                    Timber.d("Server updated: ${server.name}")
                } else {
                    databaseRepository.insertServer(server)
                    Timber.d("Server saved: ${server.name}")
                }

                _state.value = _state.value.copy(
                    isSaving = false,
                    saveSuccess = true
                )
            } catch (e: Exception) {
                Timber.e(e, "Error saving server")
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = context.getString(R.string.error_save_server_failed_fmt, e.message)
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}