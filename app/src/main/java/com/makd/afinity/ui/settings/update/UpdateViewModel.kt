package com.makd.afinity.ui.settings.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.BuildConfig
import com.makd.afinity.R
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.updater.UpdateManager
import com.makd.afinity.data.updater.UpdateScheduler
import com.makd.afinity.data.updater.models.GitHubRelease
import com.makd.afinity.data.updater.models.UpdateCheckFrequency
import com.makd.afinity.data.updater.models.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateManager: UpdateManager,
    private val updateScheduler: UpdateScheduler,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    val updateState: StateFlow<UpdateState> = updateManager.updateState

    init {
        loadUpdatePreferences()
    }

    private fun loadUpdatePreferences() {
        viewModelScope.launch {
            preferencesRepository.getUpdateCheckFrequencyFlow().collect { hours ->
                _uiState.value = _uiState.value.copy(
                    checkFrequency = UpdateCheckFrequency.fromHours(hours)
                )
            }
        }

        viewModelScope.launch {
            val lastCheck = preferencesRepository.getLastUpdateCheck()
            _uiState.value = _uiState.value.copy(
                lastCheckTime = if (lastCheck > 0) formatTimestamp(lastCheck) else context.getString(
                    R.string.update_never_checked)
            )
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val release = updateManager.checkForUpdates()
                val lastCheck = preferencesRepository.getLastUpdateCheck()
                _uiState.value = _uiState.value.copy(
                    lastCheckTime = formatTimestamp(lastCheck)
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for updates")
            }
        }
    }

    fun setCheckFrequency(frequency: UpdateCheckFrequency) {
        viewModelScope.launch {
            preferencesRepository.setUpdateCheckFrequency(frequency.hours)
            updateScheduler.scheduleUpdateChecks(frequency)
            _uiState.value = _uiState.value.copy(checkFrequency = frequency)
            Timber.d("Update check frequency set to: ${frequency.displayName}")
        }
    }

    fun downloadUpdate(release: GitHubRelease) {
        updateManager.downloadUpdate(release)
    }

    fun installUpdate(file: java.io.File) {
        updateManager.installUpdate(file)
    }

    fun dismissUpdate() {
        updateManager.resetState()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val checkFrequency: UpdateCheckFrequency = UpdateCheckFrequency.ON_APP_OPEN,
    val lastCheckTime: String = ""
)