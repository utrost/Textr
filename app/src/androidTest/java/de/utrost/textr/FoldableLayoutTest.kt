package de.utrost.textr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoldableLayoutTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun intent(name: String) = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    @Before fun reset() {
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        DocumentRepository(context).setSessionReferences(emptySet())
        DocumentRepository(context).clear()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
    }

    @Test fun sidebarIsOptInAndSupportsRecentSwitchAndClose() = display {
        DocumentRepository(context).remember(uri("copy.txt"), "copy.txt")
        ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
            ready(scenario)
            sidebar(scenario, false)
            click(By.desc("More options")); click(By.text("Reading settings"))
            click(By.res(context.packageName, "sidebar_setting")); click(By.res("android", "button1"))
            ready(scenario); sidebar(scenario, true)
            assertTrue(context.getSharedPreferences("reading", 0).getBoolean("fileSidebar", false))
            click(By.desc("Open recent file: copy.txt")); ready(scenario)
            click(By.desc("Switch to sample.json")); ready(scenario)
            scenario.onActivity {
                assertEquals("sample.json", ViewModelProvider(it)[ReaderSession::class.java].document!!.source.name)
                assertEquals(View.GONE, it.findViewById<View>(R.id.document_strip).visibility)
                val reader = it.findViewById<WebView>(R.id.reader)
                val files = it.findViewById<View>(R.id.file_sidebar)
                val readerPosition = IntArray(2); val filesPosition = IntArray(2)
                reader.getLocationOnScreen(readerPosition); files.getLocationOnScreen(filesPosition)
                assertTrue(readerPosition[0] >= filesPosition[0] + files.width)
                assertTrue(reader.width / it.resources.displayMetrics.density >= 480)
                assertFalse(reader.settings.javaScriptEnabled)
            }
            click(By.desc("Close copy.txt"))
            // Input injection can return before the Android 10 click handler finishes.
            // Wait for completion before inspecting state; constructing OpenDocuments
            // here would itself save a potentially stale snapshot back to preferences.
            val closed = device.wait(Until.hasObject(By.desc("Open recent file: copy.txt")), 10000)
            if (!closed) {
                device.dumpWindowHierarchy(java.io.File(context.cacheDir, "foldable-close.xml"))
                device.takeScreenshot(java.io.File(context.cacheDir, "foldable-close.png"))
            }
            assertTrue("Closed document should return to sidebar recents", closed)
            scenario.onActivity {
                assertFalse(ViewModelProvider(it)[ReaderSession::class.java].openDocuments!!.entries.any { entry -> entry.name == "copy.txt" })
            }
            assertFalse(context.getSharedPreferences("open_documents", 0).getString("entries", "")!!.contains("copy.txt"))
            scenario.recreate(); ready(scenario); sidebar(scenario, true)
            // Switching the option off restores tabs without closing documents.
            click(By.desc("Open recent file: copy.txt")); ready(scenario)
            click(By.desc("More options")); click(By.text("Reading settings"))
            click(By.res(context.packageName, "sidebar_setting")); click(By.res("android", "button1"))
            ready(scenario); sidebar(scenario, false)
            scenario.onActivity { assertEquals(View.VISIBLE, it.findViewById<View>(R.id.document_strip).visibility) }
            scenario.onActivity { assertEquals(2, ViewModelProvider(it)[ReaderSession::class.java].openDocuments!!.entries.size) }
            assertFalse(context.getSharedPreferences("reading", 0).getBoolean("fileSidebar", true))
        }
    }

    @Test fun windowTransitionsPreserveDocumentModePositionAndFind() = display {
        context.getSharedPreferences("reading", 0).edit().putBoolean("fileSidebar", true).commit()
        val text = "{\n" + (1..300).joinToString(",\n") { "  \"entry$it\": \"needle\"" } + "\n}"
        context.contentResolver.call(uri("fold.json"), "replace", uri("fold.json").toString(), Bundle().apply { putString("text", text) })
        ActivityScenario.launch<MainActivity>(intent("fold.json")).use { scenario ->
            ready(scenario); sidebar(scenario, true)
            click(By.res(context.packageName, "display_mode")); click(By.text("Plain text")); ready(scenario)
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).scrollTo(0, 600) }
            resize(scenario, "600x1200", 400)
            sidebar(scenario, false)
            scenario.onActivity {
                assertEquals("Plain text", it.findViewById<TextView>(R.id.display_mode).text.toString())
                assertEquals(600, it.findViewById<WebView>(R.id.reader).scrollY)
                assertEquals(text, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text)
            }
            click(By.desc("Find"))
            scenario.onActivity {
                val field = it.findViewById<EditText>(R.id.search_query)
                field.setText("needle")
                (it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(field.windowToken, 0)
            }
            resize(scenario, "1200x1600", 800)
            sidebar(scenario, true)
            scenario.onActivity {
                assertEquals("needle", it.findViewById<EditText>(R.id.search_query).text.toString())
                assertTrue(it.findViewById<EditText>(R.id.search_query).isShown)
                assertEquals("Plain text", it.findViewById<TextView>(R.id.display_mode).text.toString())
                assertEquals(text, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text)
            }
            click(By.desc("Close search"))
            click(By.desc("Close fold.json"))
            assertTrue(device.wait(Until.hasObject(By.text("Recent files")), 5000))
            sidebar(scenario, false)
            await {
                var empty = false
                scenario.onActivity { empty = ViewModelProvider(it)[ReaderSession::class.java].openDocuments!!.entries.isEmpty() }
                empty
            }
            assertEquals("[]", context.getSharedPreferences("open_documents", 0).getString("entries", ""))
        }
    }

    @Test fun largeTextAndShortWindowsKeepSinglePane() = display {
        context.getSharedPreferences("reading", 0).edit().putBoolean("fileSidebar", true).commit()
        ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
            ready(scenario); sidebar(scenario, true)
            device.executeShellCommand("settings put system font_scale 2.0")
            await {
                var large = false
                scenario.onActivity { large = it.resources.configuration.fontScale >= 1.99f }
                large
            }
            ready(scenario); sidebar(scenario, false)
            scenario.onActivity { assertTrue(it.findViewById<WebView>(R.id.reader).width / it.resources.displayMetrics.density >= 790) }
            device.executeShellCommand("settings put system font_scale 1.0")
            await {
                var normal = false
                scenario.onActivity { normal = it.resources.configuration.fontScale <= 1.01f }
                normal
            }
            ready(scenario); sidebar(scenario, true)
            resize(scenario, "1200x600", 800)
            sidebar(scenario, false)
        }
    }

    private fun display(test: () -> Unit) {
        val size = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        val density = Regex("Override density: (\\d+)").find(device.executeShellCommand("wm density"))?.groupValues?.get(1)
        val font = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("wm size 1200x1600")
            device.executeShellCommand("wm density 240")
            device.executeShellCommand("settings put system font_scale 1.0")
            device.waitForIdle()
            test()
        } finally {
            device.executeShellCommand("wm size ${size ?: "reset"}")
            device.executeShellCommand("wm density ${density ?: "reset"}")
            device.executeShellCommand("settings put system font_scale ${font.takeUnless { it == "null" } ?: "1.0"}")
            device.waitForIdle()
        }
    }
    private fun resize(scenario: ActivityScenario<MainActivity>, size: String, width: Int) {
        var previous: MainActivity? = null
        scenario.onActivity { previous = it }
        device.executeShellCommand("wm size $size")
        await {
            var changed = false
            scenario.onActivity { changed = it !== previous && it.resources.configuration.screenWidthDp in (width - 10)..(width + 10) }
            changed
        }
        ready(scenario)
    }
    private fun sidebar(scenario: ActivityScenario<MainActivity>, visible: Boolean) = await {
        var matches = false
        scenario.onActivity { matches = (it.findViewById<View>(R.id.file_sidebar).visibility == View.VISIBLE) == visible }
        matches
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var ready = false
        scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }
        ready
    }
    private fun click(selector: BySelector) {
        var node = device.wait(Until.findObject(selector), 3000)
        if (node == null) node = device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
        assertNotNull("Missing $selector", node)
        node!!.click(); device.waitForIdle()
    }
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < until) { if (condition()) return; Thread.sleep(100) }
        fail("Foldable layout condition timed out")
    }
}
