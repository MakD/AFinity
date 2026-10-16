package com.makd.afinity.ui.settings.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.player.mpv.MpvDeviceCapabilities
import com.makd.afinity.player.mpv.MpvDeviceSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val MIN_CHECK_DURATION_MS = 1600L

@HiltViewModel
class MpvDeviceCheckViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val capabilities: StateFlow<MpvDeviceCapabilities?> = flow {
        emit(MpvDeviceSupport.capabilities(context))
    }
        .catch { e -> Timber.e(e, "Failed to read MPV device capabilities") }
        .flowOn(Dispatchers.IO)
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = null)

    val vulkanVerified: StateFlow<Boolean> =
        combine(
                capabilities.filterNotNull(),
                preferencesRepository.getMpvDeviceCheckSignatureFlow(),
            ) { current, saved ->
                MpvDeviceSupport.isVulkanVerified(current, saved)
            }
            .catch { e -> Timber.e(e, "Failed to read MPV device check") }
            .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = false)

    val checkPending: StateFlow<Boolean> =
        combine(
                capabilities.filterNotNull(),
                preferencesRepository.getMpvDeviceCheckSignatureFlow(),
            ) { current, saved ->
                saved != MpvDeviceSupport.signature(current)
            }
            .catch { e -> Timber.e(e, "Failed to read MPV device check") }
            .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = false)

    private val _check = MutableStateFlow(MpvDeviceCheck())
    val check: StateFlow<MpvDeviceCheck> = _check.asStateFlow()
    private var checkJob: Job? = null

    fun startCheck() {
        checkJob?.cancel()
        _check.value = MpvDeviceCheck(status = MpvDeviceCheckStatus.CHECKING)
        checkJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val result =
                try {
                    withContext(Dispatchers.IO) { MpvDeviceSupport.capabilities(context) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to check MPV device capabilities")
                    null
                }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < MIN_CHECK_DURATION_MS) {
                delay((MIN_CHECK_DURATION_MS - elapsed).milliseconds)
            }
            _check.value = MpvDeviceCheck(status = result.toCheckStatus(), capabilities = result)
        }
    }

    fun finishCheck() {
        checkJob?.cancel()
        checkJob = null
        val check = _check.value
        _check.value = MpvDeviceCheck()
        val result = check.capabilities
        val completed =
            check.status != MpvDeviceCheckStatus.IDLE &&
                check.status != MpvDeviceCheckStatus.CHECKING
        if (!completed || result == null) return
        viewModelScope.launch {
            try {
                preferencesRepository.setMpvDeviceCheckSignature(MpvDeviceSupport.signature(result))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to save MPV device check")
            }
        }
    }
}
