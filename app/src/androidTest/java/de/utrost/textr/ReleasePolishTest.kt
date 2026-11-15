package de.utrost.textr

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ReleasePolishTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun intent(name: String? = null) = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (name != null) { action = Intent.ACTION_VIEW; data = Uri.parse("content://de.utrost.textr.test.documents/$name") }
    }
    @Before fun reset() {
        context.getSharedPreferences("reading", 0).edit().clear().commit()
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        DocumentRepository(context).apply { setSessionReferences(emptySet()); clear() }
    }
    @Test fun stalledReadCanBeCancelledWithoutRememberingOrResurrectingIt() {
        val activity = instrumentation.startActivitySync(intent("slow.txt")) as MainActivity
        try {
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.slow_reading))), 4000))
            click(By.desc(context.getString(R.string.cancel_loading)))
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.recent_files))), 2000))
            instrumentation.runOnMainSync {
                assertNull(activity.findViewById<View>(R.id.reader_loading))
                activity.startActivity(intent("sample.json"))
            }
            await {
                var ready = false
                instrumentation.runOnMainSync { ready = activity.findViewById<WebView>(R.id.reader)?.tag == true }
                ready
            }
            Thread.sleep(5200)
            assertTrue(device.hasObject(By.text("sample.json")))
            assertFalse(DocumentRepository(context).recentFiles().any { it.name == "slow.txt" })
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun openingHelpIsContextualAndDismissalSurvivesRecreation() {
        ActivityScenario.launch<MainActivity>(intent()).use { scenario ->
            scenario.onActivity { assertTrue(it.findViewById<View>(R.id.opening_hint).isShown) }
            click(By.desc(context.getString(R.string.opening_help)))
            assertTrue(device.wait(Until.hasObject(By.textContains("MIME")), 3000))
            device.pressBack()
            click(By.desc(context.getString(R.string.dismiss_hint)))
            scenario.recreate()
            scenario.onActivity { assertNull(it.findViewById<View>(R.id.opening_hint)) }
            click(By.desc(context.getString(R.string.more_options)))
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.opening_help))), 2000))
            device.pressBack()
        }
    }
    @Test fun wideHintNeverEnablesSidebarAndDismissalPersists() {
        val size = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        val density = Regex("Override density: (\\d+)").find(device.executeShellCommand("wm density"))?.groupValues?.get(1)
        try {
            device.executeShellCommand("wm size 1200x1600")
            device.executeShellCommand("wm density 240")
            ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
                ready(scenario)
                await { var shown = false; scenario.onActivity { shown = it.findViewById<View>(R.id.sidebar_hint).isShown }; shown }
                scenario.onActivity { assertFalse(it.findViewById<View>(R.id.file_sidebar).isShown) }
                click(By.desc(context.getString(R.string.dismiss_hint)))
                scenario.recreate(); ready(scenario)
                scenario.onActivity { assertFalse(it.findViewById<View>(R.id.sidebar_hint).isShown) }
                assertFalse(context.getSharedPreferences("reading", 0).getBoolean("fileSidebar", false))
            }
        } finally {
            device.executeShellCommand("wm size ${size ?: "reset"}")
            device.executeShellCommand("wm density ${density ?: "reset"}")
        }
    }
    @Test fun germanResourcesAndPreviewLabelsPreserveDocumentContents() {
        val german = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) })
        assertEquals("Datei öffnen", german.getString(R.string.open_file))
        assertTrue(german.getString(R.string.opening_help_text).contains("MIME-Typ"))
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        val labels = english.resources.getStringArray(R.array.preview_labels).zip(german.resources.getStringArray(R.array.preview_labels)).toMap()
        val source = TextDocument("example.md", "# Keep my title\n![photo](https://example.com/image)", FileKind.MARKDOWN, 60)
        val prepared = DocumentRenderer.prepare(source, translate = { labels[it] ?: it })
        assertEquals(source.text, prepared.source.text)
        assertTrue(prepared.preview!!.contains("Keep my title"))
        assertTrue(prepared.preview.contains("Bild ausgelassen"))
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(android.app.LocaleManager::class.java)
            val original = manager.applicationLocales
            try {
                manager.applicationLocales = LocaleList.forLanguageTags("de")
                ActivityScenario.launch<MainActivity>(intent()).use { scenario ->
                    assertTrue(device.wait(Until.hasObject(By.text("Datei öffnen")), 5000))
                    scenario.onActivity { assertEquals("Weitere Optionen", it.getString(R.string.more_options)) }
                }
            } finally { manager.applicationLocales = original }
        }
    }
    @Test fun csvSourceFallbackRemainsAccessibleAcrossPreviewChanges() {
        val uri = Uri.parse("content://de.utrost.textr.test.documents/accessibility-probe.csv")
        val csv = "Heading,Other\nCell target,Second cell\n"
        context.contentResolver.call(uri, "replace", uri.toString(), android.os.Bundle().apply { putString("text", csv) })
        ActivityScenario.launch<MainActivity>(intent("accessibility-probe.csv")).use { scenario ->
            repeat(3) { iteration ->
                ready(scenario)
                click(By.res(context.packageName, "display_mode")); click(By.text(context.getString(R.string.preview)))
                ready(scenario)
                // Diagnostic only: this must not be mistaken for a cell-navigation assertion.
                val visible = device.wait(Until.hasObject(By.textContains("Cell target")), 1500)
                android.util.Log.i("TextrCsvAccessibility", "iteration=$iteration cellExposed=$visible webview=${WebView.getCurrentWebViewPackage()?.versionName}")
                device.dumpWindowHierarchy(java.io.File(context.cacheDir, "csv-accessibility-$iteration.xml"))
                click(By.res(context.packageName, "display_mode")); click(By.text(context.getString(R.string.plain_text)))
                ready(scenario)
                assertTrue("Source remains in Android accessibility tree", device.wait(Until.hasObject(By.textContains("Cell target")), 5000))
                scenario.onActivity {
                    val source = androidx.lifecycle.ViewModelProvider(it)[ReaderSession::class.java].document!!.source
                    assertEquals(csv, source.text)
                    assertFalse(it.findViewById<WebView>(R.id.reader).settings.javaScriptEnabled)
                }
                scenario.recreate()
            }
        }
    }

    private fun click(selector: BySelector) {
        var node = device.wait(Until.findObject(selector), 4000)
        if (node == null) node = device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
        assertNotNull("Missing $selector", node); node!!.click(); device.waitForIdle()
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var ready = false; scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }; ready
    }
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < until) { if (condition()) return; Thread.sleep(100) }
        fail("Timed out")
    }
}
