package com.makd.afinity.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

class StartupBenchmark {

    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() =
        startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = mode,
            startupMode = StartupMode.COLD,
            iterations = 10,
        ) {
            pressHome()
            startActivityAndWait()
        }

    private companion object {
        const val TARGET_PACKAGE = "com.makd.afinity"
    }
}
