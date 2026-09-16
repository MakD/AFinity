package com.makd.afinity.util

import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.Constraints
import androidx.work.NetworkType

fun serverNetworkRequest(unmeteredOnly: Boolean = false): NetworkRequest =
    NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .apply {
            if (unmeteredOnly) addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        .build()

fun Constraints.Builder.requireServerNetwork(unmeteredOnly: Boolean = false): Constraints.Builder =
    setRequiredNetworkRequest(
        serverNetworkRequest(unmeteredOnly),
        if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
    )
