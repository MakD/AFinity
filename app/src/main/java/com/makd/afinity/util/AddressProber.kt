package com.makd.afinity.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val PREFERRED_GRACE_MS = 500L

suspend fun probeAddresses(
    addresses: List<String>,
    preferLocal: Boolean,
    logTag: String,
    validator: suspend (String) -> Boolean,
): String? {
    if (addresses.isEmpty()) return null

    val (localAddresses, externalAddresses) = addresses.partition { isLocalAddress(it) }
    val orderedAddresses =
        if (preferLocal) localAddresses + externalAddresses else externalAddresses + localAddresses

    Timber.d(
        "$logTag: Resolving address, preferLocal=$preferLocal, " +
            "addresses=${orderedAddresses.map { "${it}[${if (isLocalAddress(it)) "local" else "ext"}]" }}"
    )

    val startTime = System.currentTimeMillis()

    return coroutineScope {
        val results = Channel<Pair<String, Boolean>>(orderedAddresses.size)
        val jobs = orderedAddresses.map { address ->
            launch {
                val tag = if (isLocalAddress(address)) "local" else "ext"
                val probeStart = System.currentTimeMillis()
                val success = validator(address)
                val elapsed = System.currentTimeMillis() - probeStart
                Timber.d(
                    "$logTag: Probe $address [$tag] → ${if (success) "OK" else "FAIL"} (${elapsed}ms)"
                )
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
            if (preferLocal && isLocalAddress(address)) {
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
            val tag = if (isLocalAddress(resolved)) "local" else "ext"
            Timber.d("$logTag: Resolved → $resolved [$tag] (${totalElapsed}ms)")
        } else {
            Timber.w("$logTag: All ${orderedAddresses.size} addresses failed (${totalElapsed}ms)")
        }
        resolved
    }
}