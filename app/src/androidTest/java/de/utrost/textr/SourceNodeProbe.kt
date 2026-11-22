package de.utrost.textr

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2

/** Refresh the automation cache without altering source-exposure or gesture assertions. */
internal object SourceNodeProbe {
    fun find(selector: BySelector, timeoutMillis: Long, diagnostic: String): UiObject2? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(Configurator.getInstance().uiAutomationFlags)
        val device = UiDevice.getInstance(instrumentation)
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            if (Build.VERSION.SDK_INT >= 33) automation.clearCache()
            automation.rootInActiveWindow?.let { root ->
                root.refresh()
                @Suppress("DEPRECATION") root.recycle()
            }
            device.findObject(selector)?.let { return it }
            Thread.sleep(100)
        }
        val cache = instrumentation.targetContext.cacheDir
        device.dumpWindowHierarchy(java.io.File(cache, "$diagnostic.xml"))
        device.takeScreenshot(java.io.File(cache, "$diagnostic.png"))
        return null
    }
}
