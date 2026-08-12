package com.makd.afinity.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val PREFERRED_GRACE_MS = 500L
private const val LOCALITY_BUDGET_MS = 500L

private suspend fun resolveOnLink(
    addresses: List<String>,
    networkLocality: NetworkLocality,
    logTag: String,
): List<String> {
    val resolved =
        withTimeoutOrNull(LOCALITY_BUDGET_MS) {
            coroutineScope {
                addresses
                    .map { address ->
                        async { address to (networkLocality.resolve(address) == Locality.ON_LINK) }
                    }
                    .awaitAll()
            }
        }
    if (resolved == null) {
        Timber.d("$logTag: Locality resolution exceeded ${LOCALITY_BUDGET_MS}ms, using shape only")
        return emptyList()
    }
    return resolved.filter { it.second }.map { it.first }
}

suspend fun probeAddresses(
    addresses: List<String>,
    preferLocal: Boolean,
    logTag: String,
    networkLocality: NetworkLocality? = null,
    validator: suspend (String) -> Boolean,
): String? {
    if (addresses.isEmpty()) return null

    val addressesByShape = addresses.filter { isLocalAddress(it) }
    val localAddresses =
        if (
            addresses.size < 2 ||
                addressesByShape.isNotEmpty() ||
                !preferLocal ||
                networkLocality == null
        ) {
            addressesByShape
        } else {
            resolveOnLink(addresses, networkLocality, logTag).also {
                if (it.isNotEmpty()) {
                    Timber.d("$logTag: Locality resolved on-link addresses: $it")
                }
            }
        }
    val localSet = localAddresses.toSet()
    val externalAddresses = addresses.filterNot { it in localSet }
    val orderedAddresses =
        if (preferLocal) localAddresses + externalAddresses else externalAddresses + localAddresses

    Timber.d(
        "$logTag: Resolving address, preferLocal=$preferLocal, " +
            "addresses=${orderedAddresses.map { "${it}[${if (it in localSet) "local" else "ext"}]" }}"
    )

    val startTime = System.currentTimeMillis()

    return coroutineScope {
        val results = Channel<Pair<String, Boolean>>(orderedAddresses.size)
        val jobs = orderedAddresses.map { address ->
            launch {
                val tag = if (address in localSet) "local" else "ext"
                val probeStart = System.currentTimeMillis()
                val success = validator(address)
                val elapsed = System.currentTimeMillis() - probeStart
                val outcome =
                    when {
                        success -> "OK"
                        !isActive -> "ABANDONED"
                        else -> "FAIL"
                    }
                Timber.d("$logTag: Probe $address [$tag] → $outcome (${elapsed}ms)")
                results.send(address to success)
            }
        }

        var pendingPreferred = if (preferLocal) localAddresses.size else 0
        var fallbackWinner: String? = null
        var graceDeadline = 0L
        var winner: String? = null
        var received = 0

        while (received < orderedAddresses.size) {
            val result =
                if (fallbackWinner != null) {
                    withTimeoutOrNull(graceDeadline - System.currentTimeMillis()) {
                        results.receive()
                    }
                } else {
                    results.receive()
                }
            if (result == null) break
            received++
            val (address, success) = result
            if (preferLocal && address in localSet) {
                pendingPreferred--
                if (success) {
                    winner = address
                    break
                }
                if (pendingPreferred == 0 && fallbackWinner != null) break
            } else if (success) {
                if (pendingPreferred == 0) {
                    winner = address
                    break
                }
                if (fallbackWinner == null) {
                    fallbackWinner = address
                    graceDeadline = System.currentTimeMillis() + PREFERRED_GRACE_MS
                }
            }
        }

        val resolved = winner ?: fallbackWinner
        jobs.forEach { it.cancel() }

        val totalElapsed = System.currentTimeMillis() - startTime
        if (resolved != null) {
            val tag = if (resolved in localSet) "local" else "ext"
            Timber.d("$logTag: Resolved → $resolved [$tag] (${totalElapsed}ms)")
        } else {
            Timber.w("$logTag: All ${orderedAddresses.size} addresses failed (${totalElapsed}ms)")
        }
        resolved
    }
}