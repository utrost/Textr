package de.utrost.textr

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RenderResilienceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun intent(name: String) = Intent(Intent.ACTION_VIEW,
        Uri.parse("content://de.utrost.textr.test.documents/$name"), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Test fun terminatedRendererOffersWorkingPlainTextRecovery() {
        ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
            ready(scenario)
            scenario.onActivity {
                val renderer = it.findViewById<WebView>(R.id.reader).webViewRenderProcess
                assertNotNull("Multiprocess WebView renderer", renderer)
                assertTrue(renderer!!.terminate())
            }
            val retry = device.wait(Until.findObject(By.text(context.getString(R.string.retry_plain))), 10000)
            assertNotNull("Renderer failure offers recovery", retry)
            retry!!.click()
            ready(scenario)
            scenario.onActivity {
                assertEquals("Plain text", it.findViewById<android.widget.TextView>(R.id.display_mode).text.toString())
            }
            find(scenario, "Textr fixture")
        }
    }

    @Test fun replacingSlowLoadCannotDisplayStaleDocumentOrRememberIt() {
        val repository = DocumentRepository(context)
        repository.clear()
        val activity = instrumentation.startActivitySync(intent("slow.txt")) as MainActivity
        try {
            instrumentation.runOnMainSync { activity.startActivity(intent("sample.json")) }
            assertTrue(device.wait(Until.hasObject(By.text("sample.json")), 5000))
            Thread.sleep(5200) // Let the canceled provider finish and attempt its stale write.
            assertTrue(device.hasObject(By.text("sample.json")))
            assertFalse(repository.recentFiles().any { it.name == "slow.txt" })
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun pathologicalFixturesRemainSearchableAndRecordOpeningTimes() {
        val selected = InstrumentationRegistry.getArguments().getString("fixture")
        for (name in listOf("boundary.txt", "large-markdown.md", "deep.json", "tokens.json", "tables.md").filter { selected == null || it == selected }) {
            val peak = java.util.concurrent.atomic.AtomicInteger()
            val sampler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
            sampler.scheduleAtFixedRate({
                val info = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
                peak.accumulateAndGet(info.totalPss) { left, right -> maxOf(left, right) }
            }, 0, 200, TimeUnit.MILLISECONDS)
            val started = SystemClock.elapsedRealtime()
            try { ActivityScenario.launch<MainActivity>(intent(name)).use { scenario ->
                ready(scenario)
                val opened = SystemClock.elapsedRealtime()
                find(scenario, if (name == "boundary.txt") "Boundary end marker" else "Fixture end marker")
                val memory = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
                Log.i("TextrBenchmark", "$name openMs=${opened-started} searchMs=${SystemClock.elapsedRealtime()-opened} appPssKiB=${memory.totalPss} peakAppPssKiB=${peak.get()}")
                scenario.onActivity { activity ->
                    val document = androidx.lifecycle.ViewModelProvider(activity)[ReaderSession::class.java].document!!
                    if (name in setOf("large-markdown.md", "deep.json", "tables.md")) assertNull(document.preview)
                }
            } } finally { sampler.shutdownNow() }
        }
    }

    private fun find(scenario: ActivityScenario<MainActivity>, query: String) {
        val latch = CountDownLatch(1)
        var matches = 0
        scenario.onActivity {
            it.findViewById<WebView>(R.id.reader).apply {
                setFindListener { _, total, done -> if (done) { matches = total; latch.countDown() } }
                findAllAsync(query)
            }
        }
        assertTrue("Search completed", latch.await(20, TimeUnit.SECONDS))
        assertTrue("Full source remains searchable: $query", matches > 0)
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < deadline) {
            var ready = false
            scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Document did not render within 30 seconds")
    }
}
