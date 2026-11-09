package com.makd.afinity.data.repository.jellyseerr

import com.makd.afinity.data.repository.SecurePreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SeerrImageProxy
@Inject
constructor(private val securePreferencesRepository: SecurePreferencesRepository) {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
    }

    fun rewrite(url: String): String {
        if (!_enabled.value || !url.startsWith(TMDB_IMAGE_HOST)) return url
        val base =
            securePreferencesRepository.getCachedJellyseerrServerUrl()?.trimEnd('/')?.takeIf {
                it.isNotBlank()
            } ?: return url
        return "$base/imageproxy/tmdb/${url.removePrefix(TMDB_IMAGE_HOST)}"
    }

    private companion object {
        const val TMDB_IMAGE_HOST = "https://image.tmdb.org/"
    }
}
