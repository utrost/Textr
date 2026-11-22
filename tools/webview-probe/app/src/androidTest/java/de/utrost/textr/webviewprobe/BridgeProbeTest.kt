package de.utrost.textr.webviewprobe

import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Every sample is an independent transition; any missing node fails the complete probe. */
@RunWith(AndroidJUnit4::class)
class BridgeProbeTest {
    @Test fun sourceTableAndRecreationExposeCurrentDocument() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val automation = instrumentation.getUiAutomation(0)
        val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        val enabledDeadline = System.nanoTime() + 10_000_000_000L
        while (!manager.isEnabled && System.nanoTime() < enabledDeadline) Thread.sleep(50)
        assertTrue("Accessibility enabled before first WebView", manager.isEnabled)
        instrumentation.waitForIdleSync()
        val iterations = InstrumentationRegistry.getArguments().getString("iterations")?.toInt() ?: 30
        require(iterations in 1..100)
        val failures = mutableListOf<String>()
        var observations = 0
        android.util.Log.i("WebViewProbe", "provider=${WebView.getCurrentWebViewPackage()?.versionName}; api=${Build.VERSION.SDK_INT}; iterations=$iterations")
        for (sample in 0 until iterations) {
            val intent = Intent(context, ProbeActivity::class.java).putExtra("sample", sample).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<ProbeActivity>(intent).use { scenario ->
                fun check(phase: String) {
                    val readyDeadline = System.nanoTime() + 30_000_000_000L
                    var expected = ""
                    while (System.nanoTime() < readyDeadline) {
                        var ready = false
                        scenario.onActivity { expected = it.marker; ready = it.reader.tag == true; assertFalse(it.reader.settings.javaScriptEnabled) }
                        if (ready) break
                        Thread.sleep(50)
                    }
                    var ready = false
                    scenario.onActivity { ready = it.reader.tag == true }
                    assertTrue("Page ready: $sample/$phase", ready)
                    var visited = 0
                    fun exposed(node: AccessibilityNodeInfo?): Boolean {
                        if (node == null) return false
                        try {
                            if (++visited > 2000 || !node.refresh()) return false
                            if (node.isVisibleToUser && node.text?.toString() == expected) return true
                            for (index in 0 until node.childCount) if (exposed(node.getChild(index))) return true
                            return false
                        } finally { @Suppress("DEPRECATION") node.recycle() }
                    }
                    var found = false
                    val deadline = System.nanoTime() + 5_000_000_000L
                    while (!found && System.nanoTime() < deadline) {
                        if (Build.VERSION.SDK_INT >= 33) automation.clearCache()
                        visited = 0; found = exposed(automation.rootInActiveWindow)
                        if (!found) Thread.sleep(100)
                    }
                    observations++
                    android.util.Log.i("WebViewProbe", "$sample/$phase expected=$expected exposed=$found")
                    if (!found) {
                        failures.add("$sample/$phase:$expected")
                        if (failures.size <= 5) {
                            val prefix = "probe-$sample-$phase"
                            device.dumpWindowHierarchy(java.io.File(context.cacheDir, "$prefix.xml"))
                            device.takeScreenshot(java.io.File(context.cacheDir, "$prefix.png"))
                        }
                    }
                }
                check("source")
                scenario.onActivity { it.showTable(true) }; check("table")
                scenario.recreate(); check("recreated-table")
                scenario.onActivity { it.showTable(false) }; check("returned-source")
            }
        }
        android.util.Log.i("WebViewProbe", "observations=$observations missing=${failures.size}")
        assertTrue("Missing nodes in $observations independent observations: $failures", failures.isEmpty())
    }
}
