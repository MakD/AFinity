package com.makd.afinity.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val PREFERRED_GRACE_MS = 500L
private const val LOCALITY_BUDGET_MS = 500L

sealed interface ProbeResult {
    data class Success(val address: String) : ProbeResult

    data object AllFailed : ProbeResult

    data object NoRoute : ProbeResult
}

private suspend fun resolveLocalities(
    addresses: List<String>,
    networkLocality: NetworkLocality,
    logTag: String,
): Map<String, Locality>? {
    val resolved =
        withTimeoutOrNull(LOCALITY_BUDGET_MS) {
            coroutineScope {
                addresses
                    .map { address -> async { address to networkLocality.resolve(address) } }
                    .awaitAll()
                    .toMap()
            }
        }
    if (resolved == null) {
        Timber.d("$logTag: Locality resolution exceeded ${LOCALITY_BUDGET_MS}ms, using shape only")
    }
    return resolved
}

suspend fun probeAddresses(
    addresses: List<String>,
    preferLocal: Boolean,
    logTag: String,
    networkLocality: NetworkLocality? = null,
    rememberedAddress: String? = null,
    validator: suspend (String) -> Boolean,
): ProbeResult {
    if (addresses.isEmpty()) return ProbeResult.AllFailed

    val localities =
        if (networkLocality != null) resolveLocalities(addresses, networkLocality, logTag) else null

    val unroutable =
        localities
            ?.filter { (address, locality) ->
                locality == Locality.PUBLIC && isLocalAddress(address)
            }
            ?.keys
            .orEmpty()

    val candidates = addresses.filterNot { it in unroutable }
    if (candidates.isEmpty()) {
        Timber.w("$logTag: No candidate is reachable from this network, skipped $addresses")
        return ProbeResult.NoRoute
    }
    if (unroutable.isNotEmpty()) {
        Timber.d("$logTag: Skipping addresses with no route from this network: $unroutable")
    }

    val localSet =
        if (localities != null) {
            candidates.filter { localities[it] == Locality.ON_LINK }.toSet()
        } else {
            candidates.filter { isLocalAddress(it) }.toSet()
        }
    val preferLocalNow = preferLocal && localSet.isNotEmpty()

    val localAddresses = candidates.filter { it in localSet }
    val externalAddresses = candidates.filterNot { it in localSet }
    val byLocality =
        if (preferLocalNow) localAddresses + externalAddresses
        else externalAddresses + localAddresses
    val orderedAddresses =
        if (rememberedAddress != null && rememberedAddress in byLocality) {
            Timber.d("$logTag: Trying remembered address first: $rememberedAddress")
            listOf(rememberedAddress) + byLocality.filterNot { it == rememberedAddress }
        } else {
            byLocality
        }

    Timber.d(
        "$logTag: Resolving address, preferLocal=$preferLocalNow, " +
            "addresses=${orderedAddresses.map { "${it}[${if (it in localSet) "local" else "ext"}]" }}"
    )

    val startTime = System.currentTimeMillis()

    val probeScope =
        CoroutineScope(currentCoroutineContext() + SupervisorJob(currentCoroutineContext()[Job]))

    try {
        val results = Channel<Pair<String, Boolean>>(orderedAddresses.size)
        orderedAddresses.forEach { address ->
            probeScope.launch {
                val tag = if (address in localSet) "local" else "ext"
                val probeStart = System.currentTimeMillis()
                val success =
                    try {
                        validator(address)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "$logTag: Probe $address [$tag] threw")
                        false
                    }
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

        var pendingPreferred = if (preferLocalNow) localAddresses.size else 0
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
            if (preferLocalNow && address in localSet) {
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

        val totalElapsed = System.currentTimeMillis() - startTime
        if (resolved != null) {
            val tag = if (resolved in localSet) "local" else "ext"
            Timber.d("$logTag: Resolved → $resolved [$tag] (${totalElapsed}ms)")
            return ProbeResult.Success(resolved)
        }
        Timber.w("$logTag: All ${orderedAddresses.size} addresses failed (${totalElapsed}ms)")
        return ProbeResult.AllFailed
    } finally {
        probeScope.cancel()
    }
}
