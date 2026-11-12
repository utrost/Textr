package de.utrost.textr

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityLayoutTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    @Test fun twoHundredPercentFontsKeepControlsReachableInPortraitAndLandscape() {
        val previousScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            device.setOrientationNatural()
            context.getSharedPreferences("reading", 0).edit().clear().commit()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://de.utrost.textr.test.documents/sample.md"), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                for (landscape in listOf(false, true)) {
                    if (landscape) device.setOrientationLeft()
                    ready(scenario)
                    scenario.onActivity {
                        assertTrue(it.resources.configuration.fontScale >= 1.99f)
                        checkButtons(it.window.decorView, it.resources.displayMetrics.density)
                        assertTrue("Reader has visible space", it.findViewById<WebView>(R.id.reader).height > 48 * it.resources.displayMetrics.density)
                    }
                    val find = device.wait(Until.findObject(By.desc("Find")), 5000)!!
                    find.click()
                    scenario.onActivity { activity ->
                        val query = activity.findViewById<EditText>(R.id.search_query)
                        query.setText("Textr")
                        (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                            .hideSoftInputFromWindow(query.windowToken, 0)
                    }
                    Thread.sleep(400)
                    scenario.onActivity {
                        checkButtons(it.window.decorView, it.resources.displayMetrics.density)
                        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, it.findViewById<TextView>(R.id.search_count).accessibilityLiveRegion)
                        assertTrue("Document remains visible with search", it.findViewById<WebView>(R.id.reader).height > 0)
                    }
                    device.findObject(By.desc("Close search")).click()
                }
                device.findObject(By.desc("Reading settings")).click()
                assertNotNull("Scrollable settings retain their confirmation action", device.wait(Until.findObject(By.res("android", "button1")), 5000))
                device.findObject(By.res("android", "button1")).click()
            }
        } finally {
            device.executeShellCommand("settings put system font_scale ${previousScale.takeIf { it.toFloatOrNull() != null } ?: "1.0"}")
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    private fun checkButtons(view: View, density: Float) {
        if (view is Button && view.isShown) {
            val visible = Rect()
            assertTrue("Visible button: ${view.text}", view.getGlobalVisibleRect(visible))
            assertTrue("Unclipped width: ${view.text}", visible.width() >= view.width - 1)
            assertTrue("Unclipped height: ${view.text}", visible.height() >= view.height - 1)
            assertTrue("48dp touch width: ${view.text}", view.width >= 48 * density - 1)
            assertTrue("48dp touch height: ${view.text}", view.height >= 48 * density - 1)
            assertFalse("Control has a spoken label", view.contentDescription.isNullOrBlank())
        }
        if (view is ViewGroup && view !is WebView) for (index in 0 until view.childCount) checkButtons(view.getChildAt(index), density)
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < deadline) {
            var ready = false
            scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Reader did not load")
    }
}
