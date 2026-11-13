package de.utrost.textr

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderControlsTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun intent(name: String) = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    @Before fun resetPreferences() {
        context.getSharedPreferences("reading", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        DocumentRepository(context).setSessionReferences(emptySet())
        DocumentRepository(context).clear()
    }

    @Test fun actualModeSelectionsAndInvalidJsonFallback() {
        ActivityScenario.launch<MainActivity>(intent("sample.md")).use { scenario ->
            ready(scenario)
            click("Preview")
            click("Plain text")
            ready(scenario)
            assertMode(scenario, "Plain text")
            search(scenario, "# Textr fixture", "1 / 1")
            click("Plain text")
            click("Highlighted")
            ready(scenario)
            assertMode(scenario, "Highlighted")
            click("Highlighted")
            click("Preview")
            ready(scenario)
            search(scenario, "# Textr fixture", "No matches")
        }
        ActivityScenario.launch<MainActivity>(intent("invalid.json")).use { scenario ->
            ready(scenario)
            assertMode(scenario, "Highlighted")
            search(scenario, "not valid JSON", "1 / 1")
        }
    }

    @Test fun recreationDuringLoadingPreservesAutomaticModeSelection() {
        ActivityScenario.launch<MainActivity>(intent("slow.json")).use { scenario ->
            scenario.recreate()
            ready(scenario)
            assertMode(scenario, "Preview")
            search(scenario, "Textr fixture", "1 / 1")
        }
    }

    @Test fun searchNavigationClosingAndRecreationPreserveState() {
        ActivityScenario.launch<MainActivity>(intent("repeated.txt")).use { scenario ->
            ready(scenario)
            search(scenario, "needle", "1 / 3")
            clickDescription("Next match")
            await { count(scenario) == "2 / 3" }
            clickDescription("Previous match")
            await { count(scenario) == "1 / 3" }
            scenario.recreate()
            ready(scenario)
            await { count(scenario) == "1 / 3" }
            scenario.onActivity { assertEquals("needle", it.findViewById<EditText>(R.id.search_query).text.toString()) }
            clickDescription("Close search")
            scenario.onActivity { assertFalse(it.findViewById<EditText>(R.id.search_query).isShown) }
            search(scenario, "absent term", "No matches")
            scenario.onActivity { it.findViewById<EditText>(R.id.search_query).setText("") }
            await { count(scenario).isEmpty() }
        }
    }

    @Test fun settingsApplyAndSurviveActivityRestart() {
        ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
            ready(scenario)
            clickDescription("More options")
            click("Reading settings")
            val slider = device.wait(Until.findObject(By.res(context.packageName, "font_size")), 5000)!!
            device.click(slider.visibleBounds.left + slider.visibleBounds.width() * 3 / 4, slider.visibleBounds.centerY())
            device.findObject(By.res(context.packageName, "line_wrap")).click()
            device.findObject(By.res(context.packageName, "theme_choice")).click()
            click("Dark")
            click("Done")
            ready(scenario)
            val prefs = context.getSharedPreferences("reading", Context.MODE_PRIVATE)
            assertEquals("dark", prefs.getString("theme", null))
            assertFalse(prefs.getBoolean("wrap", true))
            assertTrue(prefs.getInt("fontSize", 0) > 16)
            scenario.onActivity {
                assertEquals(prefs.getInt("fontSize", 0), it.findViewById<WebView>(R.id.reader).settings.defaultFontSize)
            }
        }
        ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
            ready(scenario)
            scenario.onActivity {
                assertEquals(context.getSharedPreferences("reading", Context.MODE_PRIVATE).getInt("fontSize", 0),
                    it.findViewById<WebView>(R.id.reader).settings.defaultFontSize)
            }
        }
    }

    @Test fun cancelingPickerPreservesReader() {
        ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
            ready(scenario)
            clickDescription("More options")
            click("Open file")
            assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 5000) ||
                device.wait(Until.hasObject(By.pkg("com.android.documentsui")), 5000))
            var returned = false
            for (attempt in 0 until 12) {
                if (device.currentPackageName == context.packageName) { returned = true; break }
                device.pressBack()
                if (device.wait(Until.hasObject(By.pkg(context.packageName)), 2000)) { returned = true; break }
            }
            assertTrue("Picker cancellation returns to the reader", returned)
            ready(scenario)
            search(scenario, "Textr fixture", "1 / 1")
        }
    }

    @Test fun longPressCopiesTextWithoutChangingTheSource() {
        val repository = DocumentRepository(context)
        val before = repository.read(uri("copy.txt"), null, android.os.CancellationSignal()).text
        ActivityScenario.launch<MainActivity>(intent("copy.txt")).use { scenario ->
            ready(scenario)
            scenario.onActivity {
                (it.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).clearPrimaryClip()
            }
            val text = device.wait(Until.findObject(By.text("Copyable Textr selection")), 5000)
            assertNotNull(text)
            text!!.longClick()
            click("Copy")
            var copied = ""
            await {
                scenario.onActivity {
                    val clipboard = it.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(it)?.toString().orEmpty()
                }
                copied.isNotBlank()
            }
            assertTrue("Copied text comes from the original source", before.contains(copied))
        }
        assertEquals(before, repository.read(uri("copy.txt"), null, android.os.CancellationSignal()).text)
    }

    @Test fun wrappedChunksPreserveCrossBoundarySearchAndFullCopy() {
        val source = DocumentRepository(context).read(uri("copy-long.txt"), null, android.os.CancellationSignal()).text
        ActivityScenario.launch<MainActivity>(intent("copy-long.txt")).use { scenario ->
            ready(scenario)
            search(scenario, "CrossBoundaryMarker", "1 / 1")
            clickDescription("Close search")
            scenario.onActivity {
                it.findViewById<WebView>(R.id.reader).scrollTo(0, 0)
                (it.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).clearPrimaryClip()
            }
            device.waitForIdle()
            var x = 0; var y = 0
            scenario.onActivity {
                val reader = it.findViewById<WebView>(R.id.reader)
                val location = IntArray(2).also(reader::getLocationOnScreen)
                x = location[0] + (35 * it.resources.displayMetrics.density).toInt()
                y = location[1] + (32 * it.resources.displayMetrics.density).toInt()
            }
            device.swipe(x, y, x, y, 100)
            click("Select all")
            click("Copy")
            await {
                var copied = ""
                scenario.onActivity {
                    copied = (it.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .primaryClip?.getItemAt(0)?.coerceToText(it)?.toString().orEmpty()
                }
                copied == source
            }
        }
    }

    @Test fun readingPositionAndModeSurviveRecreationAndUriReload() {
        ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
            ready(scenario)
            click("Highlighted")
            click("Plain text")
            assertMode(scenario, "Plain text")
            ready(scenario)
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).scrollTo(0, 1200) }
            var originalScroll = 0
            scenario.onActivity { originalScroll = it.findViewById<WebView>(R.id.reader).scrollY }
            assertTrue(originalScroll > 0)
            scenario.recreate()
            ready(scenario)
            assertMode(scenario, "Plain text")
            await {
                var scroll = 0
                scenario.onActivity { scroll = it.findViewById<WebView>(R.id.reader).scrollY }
                kotlin.math.abs(scroll - originalScroll) <= 2
            }
            // Simulate losing the in-memory ViewModel; restoration must reopen its URI.
            scenario.onActivity { it.viewModelStore.clear() }
            scenario.recreate()
            ready(scenario)
            assertMode(scenario, "Plain text")
            search(scenario, "Final line marker", "1 / 1")
        }
    }

    @Test fun incomingFileReplacesTheActiveReader() {
        // ActivityScenario filters lifecycle events by the initial intent. A genuine
        // onNewIntent changes that intent, so use Instrumentation for this case.
        val activity = instrumentation.startActivitySync(intent("sample.json")) as MainActivity
        try {
            await { device.hasObject(By.text("sample.json")) }
            instrumentation.runOnMainSync { activity.startActivity(intent("repeated.txt")) }
            await { device.hasObject(By.text("repeated.txt")) }
            instrumentation.runOnMainSync {
                assertFalse(activity.isDestroyed)
                assertFalse(activity.findViewById<EditText>(R.id.search_query).isShown)
                assertEquals("repeated.txt", androidx.lifecycle.ViewModelProvider(activity)[ReaderSession::class.java].document!!.source.name)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun clipDataOnlyAttachmentAndUppercaseNameOpen() {
        val attachment = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND)
            .setType("application/octet-stream")
            .apply { clipData = ClipData.newRawUri("attachment", uri("UPPER.JSON")) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<MainActivity>(attachment).use { scenario ->
            ready(scenario)
            assertMode(scenario, "Preview")
            search(scenario, "Textr fixture", "1 / 1")
        }
    }

    private fun search(scenario: ActivityScenario<MainActivity>, query: String, expected: String) {
        var shown = false
        scenario.onActivity { shown = it.findViewById<EditText>(R.id.search_query).isShown }
        if (!shown) clickDescription("Find")
        scenario.onActivity { it.findViewById<EditText>(R.id.search_query).setText(query) }
        await { count(scenario) == expected }
        // Dismiss IME without closing the search controls.
        scenario.onActivity {
            (it.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(it.findViewById<EditText>(R.id.search_query).windowToken, 0)
        }
    }

    private fun count(scenario: ActivityScenario<MainActivity>): String {
        var result = ""
        scenario.onActivity { result = it.findViewById<TextView>(R.id.search_count).text.toString() }
        return result
    }
    private fun assertMode(scenario: ActivityScenario<MainActivity>, expected: String) {
        await {
            var actual = ""
            scenario.onActivity { actual = it.findViewById<TextView>(R.id.display_mode).text.toString() }
            actual == expected
        }
    }
    private fun ready(scenario: ActivityScenario<MainActivity>, name: String? = null) {
        await {
            var result = false
            scenario.onActivity { result = it.findViewById<WebView>(R.id.reader)?.tag == true }
            result && (name == null || device.hasObject(By.text(name)))
        }
    }
    private fun click(text: String) {
        val selector = By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(text), java.util.regex.Pattern.CASE_INSENSITIVE))
        repeat(3) { attempt ->
            var item = device.wait(Until.findObject(selector), 5000)
            if (item == null && text == "Select all") {
                // Android's floating selection toolbar moves this action into overflow
                // on narrow displays and some WebView/platform versions.
                val overflow = device.findObject(By.res("android", "floating_toolbar_overflow_button"))
                    ?: device.findObject(By.desc("More options"))
                overflow?.click()
                item = device.wait(Until.findObject(selector), 5000)
            }
            assertNotNull("Missing control: $text", item)
            try {
                item!!.click()
                device.waitForIdle()
                return
            } catch (error: androidx.test.uiautomator.StaleObjectException) {
                // Floating toolbars may replace their nodes during selection animation.
                if (attempt == 2) throw error
                device.waitForIdle()
            }
        }
    }
    private fun clickDescription(description: String) {
        val item = device.wait(Until.findObject(By.desc(description)), 5000)
        assertNotNull("Missing control: $description", item)
        item!!.click()
        device.waitForIdle()
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < deadline) {
            if (predicate()) return
            Thread.sleep(100)
        }
        fail("Reader condition timed out")
    }
}
