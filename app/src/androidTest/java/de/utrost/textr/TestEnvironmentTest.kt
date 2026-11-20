package de.utrost.textr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestEnvironmentTest {
    @Test fun recordEffectiveRuntimeBudget() {
        val runtime = Runtime.getRuntime()
        android.util.Log.i("TextrTestRuntime", "Effective ART max heap: ${runtime.maxMemory() / (1024 * 1024)} MiB; CPUs: ${runtime.availableProcessors()}")
        val minimum = InstrumentationRegistry.getArguments().getString("minimumHeapMiB")?.toLongOrNull()
        if (minimum != null) assertTrue("CI emulator effective heap must be at least $minimum MiB", runtime.maxMemory() >= minimum * 1024 * 1024)
    }
}
