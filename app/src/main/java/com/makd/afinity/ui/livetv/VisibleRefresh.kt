package com.makd.afinity.ui.livetv

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VisibleRefresh(
    private val scope: CoroutineScope,
    private val interval: Duration,
    private val action: suspend () -> Unit,
) {
    private var job: Job? = null
    private var lastRunAt = 0L

    fun setVisible(visible: Boolean) {
        job?.cancel()
        job =
            if (visible) {
                scope.launch {
                    while (isActive) {
                        val waitMs =
                            interval.inWholeMilliseconds - (System.currentTimeMillis() - lastRunAt)
                        if (waitMs > 0) delay(waitMs.milliseconds)
                        lastRunAt = System.currentTimeMillis()
                        action()
                    }
                }
            } else {
                null
            }
    }

    fun invalidate() {
        lastRunAt = 0L
        if (job != null) setVisible(true)
    }
}
