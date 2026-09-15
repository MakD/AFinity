package com.makd.afinity.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {

    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle(IDLE_TIMEOUT_MS)
        }

    private companion object {
        const val TARGET_PACKAGE = "com.makd.afinity"
        const val IDLE_TIMEOUT_MS = 3_000L
    }
}
