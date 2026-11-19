package de.utrost.textr

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RoadmapEntryDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun open(name: String, source: String): ActivityScenario<MainActivity> {
        val uri = Uri.parse("content://de.utrost.textr.test.documents/$name")
        context.contentResolver.call(uri, "replace", uri.toString(), Bundle().apply { putString("text", source) })
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    @Test fun comparisonCapturesOriginalHtmlAndBomWithoutPuttingContentsInIntent() {
        val html = "<h1>Actual source</h1><script>omitted from reading view</script>"
        val captured = AtomicReference<Intent>()
        val monitor = capture(ComparisonActivity::class.java.name, captured)
        try {
            open("compare-original.html", "\uFEFF$html").use { scenario ->
                ready(scenario)
                scenario.onActivity {
                    val session = ViewModelProvider(it)[ReaderSession::class.java]
                    assertNotNull(session.document!!.convertedMarkdown)
                    MainActivity::class.java.getDeclaredMethod("startComparison").apply { isAccessible = true }.invoke(it)
                }
                await { captured.get() != null }
                val intent = captured.get(); assertFalse(intent.extras.toString().contains(html))
                val snapshot = ComparisonInbox.take(intent.getStringExtra("token"))!!
                assertEquals(html, snapshot.document.text); assertTrue(snapshot.document.hasBom)
                assertTrue(snapshot.reference.uri!!.endsWith("compare-original.html"))
                assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK, context.packageManager.getActivityInfo(intent.component!!, 0).launchMode)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun editActionRequiresNoticeAndHandsOffRawMarkdownWithSourceGuard() {
        val source = "# Raw source\r\n**not a preview buffer** 🚀"
        val captured = AtomicReference<Intent>()
        val monitor = capture(EditorActivity::class.java.name, captured)
        try {
            open("edit-original.md", source).use { scenario ->
                ready(scenario)
                scenario.onActivity { MainActivity::class.java.getDeclaredMethod("startEditor").apply { isAccessible = true }.invoke(it) }
                assertNull(captured.get())
                assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.editor_recovery_notice))), 5000))
                device.wait(Until.findObject(By.res("android", "button1")), 5000)!!.click()
                await { captured.get() != null }
                val intent = captured.get(); assertFalse(intent.extras.toString().contains(source))
                val snapshot = EditorInbox.take(intent.getStringExtra("token"))!!
                assertEquals(source, snapshot.document.text); assertTrue(snapshot.uri!!.endsWith("edit-original.md"))
                assertFalse(snapshot.bom)
                assertArrayEquals(source.toByteArray(), context.contentResolver.openInputStream(Uri.parse(snapshot.uri))!!.use { it.readBytes() })
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    private fun capture(name: String, result: AtomicReference<Intent>): Instrumentation.ActivityMonitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.component?.className != name) return null
            result.set(intent); return Instrumentation.ActivityResult(0, null)
        }
    }.also { instrumentation.addMonitor(it) }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await { var ready = false; scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }; ready }
    private fun await(condition: () -> Boolean) { val until = System.currentTimeMillis() + 30_000; while (System.currentTimeMillis() < until) { if (condition()) return; Thread.sleep(50) }; fail("Roadmap entry did not become ready") }
}
