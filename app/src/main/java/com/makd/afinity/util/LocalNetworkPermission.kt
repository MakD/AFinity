package com.makd.afinity.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNetworkPermission
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    private val _isGranted = MutableStateFlow(checkGranted())
    val isGranted: StateFlow<Boolean> = _isGranted.asStateFlow()

    val isRequired: Boolean
        get() = Build.VERSION.SDK_INT >= LOCAL_NETWORK_SDK

    fun refresh() {
        _isGranted.value = checkGranted()
    }

    fun isSatisfied(): Boolean = !isRequired || checkGranted()

    fun blocks(url: String): Boolean = isRequired && isLocalAddress(url) && !checkGranted()

    fun mayExplainFailure(urls: List<String>, onLocalNetwork: Boolean): Boolean =
        isRequired && !checkGranted() && (onLocalNetwork || urls.any { isLocalAddress(it) })

    private fun checkGranted(): Boolean {
        if (!isRequired) return true
        return ContextCompat.checkSelfPermission(context, PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK
        private const val LOCAL_NETWORK_SDK = 37
    }
}
