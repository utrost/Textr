package de.utrost.textr

import android.content.Context
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
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BacklogTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val repository get() = DocumentRepository(context)
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun intent(name: String) = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    private fun replace(name: String, text: String) = context.contentResolver.call(uri(name), "replace", uri(name).toString(), Bundle().apply { putString("text", text) })
    private fun grant(name: String) {
        context.contentResolver.call(uri(name), "grant", uri(name).toString(), null)
        repository.retainReadAccess(uri(name), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
    private fun granted(name: String) = context.contentResolver.persistedUriPermissions.any { it.uri == uri(name) && it.isReadPermission }

    @Before fun reset() {
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        repository.setSessionReferences(emptySet())
        repository.clear()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
    }

    @Test fun reloadReflectsChangedShortenedAndRevokedProviderFiles() {
        replace("mutable.ini", "[section]\n" + "long = original\n".repeat(300))
        ActivityScenario.launch<MainActivity>(intent("mutable.ini")).use { scenario ->
            ready(scenario)
            click("Highlighted"); click("Plain text")
            await { mode(scenario) == "Plain text" }; ready(scenario)
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).scrollTo(0, 1200) }
            awaitScroll(scenario, 1200)
            val position = scroll(scenario)
            replace("mutable.ini", "[section]\n" + "long = modified\n".repeat(300))
            menu("Reload")
            await { source(scenario)?.contains("modified") == true }; ready(scenario)
            assertEquals("Plain text", mode(scenario))
            awaitScroll(scenario, position)
            replace("mutable.ini", "short = changed")
            menu("Reload")
            await { source(scenario) == "short = changed" }; ready(scenario)
            assertTrue("Shortened content clamps scroll (allow CSS pixel rounding)", scroll(scenario) <= 4)
            context.contentResolver.call(uri("mutable.ini"), "missing", uri("mutable.ini").toString(), null)
            menu("Reload")
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.access_error))), 10000))
            replace("mutable.ini", "restored = file")
            click(By.desc("Back to files")); click("mutable.ini"); ready(scenario)
            context.contentResolver.call(uri("mutable.ini"), "revoke", uri("mutable.ini").toString(), null)
            menu("Reload")
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.access_error))), 10000))
        }
    }

    @Test fun sourceNumbersDoNotEnterClipboardAndGoToLineUsesLogicalLines() {
        replace("numbered.txt", "Alpha source line\nBeta source line\nGamma source line")
        context.getSharedPreferences("reading", 0).edit().putBoolean("lineNumbers", true).commit()
        ActivityScenario.launch<MainActivity>(intent("numbered.txt")).use { scenario ->
            ready(scenario)
            // WebView versions split source accessibility nodes differently around
            // hidden line markers. Select the displayed first word using a real gesture.
            var x = 0
            var y = 0
            scenario.onActivity {
                val view = it.findViewById<WebView>(R.id.reader)
                val location = IntArray(2).also(view::getLocationOnScreen)
                x = location[0] + (110 * it.resources.displayMetrics.density).toInt()
                y = location[1] + (32 * it.resources.displayMetrics.density).toInt()
                (it.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).clearPrimaryClip()
            }
            device.waitForIdle()
            device.swipe(x, y, x, y, 100)
            clickSelection("Select all"); clickSelection("Copy")
            await {
                var value = ""
                scenario.onActivity { value = (it.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip?.getItemAt(0)?.text.toString() }
                value == "Alpha source line\nBeta source line\nGamma source line"
            }
        }
        replace("wrapped.txt", (1..300).joinToString("\n") { "$it " + "wrapped words ".repeat(8) })
        ActivityScenario.launch<MainActivity>(intent("wrapped.txt")).use { scenario ->
            ready(scenario)
            menu("Go to line…")
            device.wait(Until.findObject(By.desc("Line number")), 5000)!!.text = "200"
            click("Go")
            await { var url = ""; scenario.onActivity { url = it.findViewById<WebView>(R.id.reader)?.url.orEmpty() }; url.endsWith("#line-200") }
            await { scroll(scenario) > 1000 }
            scenario.onActivity { assertFalse(it.findViewById<WebView>(R.id.reader).settings.javaScriptEnabled) }
        }
    }

    @Test fun outlineJumpsToDuplicateAndEmptyHeadingsWithoutScripts() {
        replace("outline.md", "# Same\n\n" + "Paragraph text.\n\n".repeat(80) + "## Same\n\n### \n")
        ActivityScenario.launch<MainActivity>(intent("outline.md")).use { scenario ->
            ready(scenario)
            menu("Heading outline")
            assertNotNull(device.wait(Until.findObject(By.desc("Heading level 3, Untitled heading")), 5000))
            click("    Untitled heading")
            await { scroll(scenario) > 1000 }
            scenario.onActivity {
                val view = it.findViewById<WebView>(R.id.reader)
                assertTrue(view.url!!.endsWith("#heading-3"))
                assertFalse(view.settings.javaScriptEnabled)
                assertTrue(view.settings.blockNetworkLoads)
            }
        }
    }

    @Test fun switchingRestoresQueryModePositionAndDeduplicates() {
        repository.remember(uri("repeated.txt"), "repeated.txt")
        ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
            ready(scenario)
            click("Highlighted"); click("Plain text")
            await { mode(scenario) == "Plain text" }; ready(scenario)
            click(By.desc("Find"))
            scenario.onActivity { it.findViewById<EditText>(R.id.search_query).setText("line100") }
            await { var count = ""; scenario.onActivity { count = it.findViewById<TextView>(R.id.search_count).text.toString() }; count == "1 / 1" }
            click(By.desc("Back to files")); click("repeated.txt"); ready(scenario)
            menu("Open documents"); click("long.ini"); ready(scenario)
            assertEquals("Plain text", mode(scenario))
            scenario.onActivity {
                assertEquals("line100", it.findViewById<EditText>(R.id.search_query).text.toString())
                assertTrue(it.findViewById<EditText>(R.id.search_query).isShown)
                assertEquals(2, ViewModelProvider(it)[ReaderSession::class.java].openDocuments!!.entries.size)
            }
            click(By.desc("Close search"))
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).scrollTo(0, 1700) }
            awaitScroll(scenario, 1700)
            val position = scroll(scenario)
            menu("Open documents"); click("repeated.txt"); ready(scenario)
            menu("Open documents"); click("long.ini"); ready(scenario)
            awaitScroll(scenario, position)
            scenario.recreate(); ready(scenario)
            assertEquals("Plain text", mode(scenario))
            menu("Open documents"); click(By.desc("Close long.ini")); click("Done")
            assertFalse(OpenDocuments(context).entries.any { it.uri == uri("long.ini").toString() })
            assertTrue(repository.recentFiles().any { it.uri == uri("long.ini").toString() })
        }
    }

    @Test fun sessionLimitPersistenceAndGrantOwnershipExcludeContentsAndSearch() {
        val sessions = OpenDocuments(context)
        grant("session-0.txt")
        val first = sessions.opened(uri("session-0.txt").toString(), TextDocument("First", "PRIVATE CONTENT", FileKind.TEXT, 15))
        first.query = "PRIVATE QUERY"; first.reading = ReadingState(DisplayMode.PLAIN, 800, FileKind.TEXT)
        sessions.save()
        repository.remember(uri("session-0.txt"), "First")
        repository.clear()
        assertTrue(granted("session-0.txt"))
        for (i in 1..5) sessions.opened(uri("session-$i.txt").toString(), TextDocument("File $i", "PRIVATE CONTENT", FileKind.TEXT, 15))
        assertEquals(5, sessions.entries.size)
        assertFalse(granted("session-0.txt"))
        sessions.opened(uri("session-3.txt").toString(), TextDocument("File 3", "PRIVATE CONTENT", FileKind.TEXT, 15))
        assertEquals(5, sessions.entries.size)
        sessions.entries.last().reading = ReadingState(DisplayMode.PLAIN, 800, FileKind.TEXT)
        sessions.entries.last().query = "PRIVATE QUERY"
        sessions.save()
        val stored = context.getSharedPreferences("open_documents", 0).getString("entries", "")!!
        assertFalse(stored.contains("PRIVATE"))
        val restored = OpenDocuments(context)
        assertEquals(5, restored.entries.size)
        assertEquals(800, restored.entries.last().reading.scroll)
        assertEquals("", restored.entries.last().query)
        sessions.opened(null, TextDocument("Shared", "PRIVATE CONTENT", FileKind.TEXT, 15))
        sessions.save()
        assertEquals(4, OpenDocuments(context).entries.size)
    }

    @Test fun pinsUndoAndExpiryRespectLimitsAndGrants() {
        for (i in 0..10) {
            repository.remember(uri("pin-$i.txt"), "Pin $i")
            assertEquals(i < 10, repository.togglePin(uri("pin-$i.txt").toString()))
        }
        for (i in 11..50) repository.remember(uri("pin-$i.txt"), "Pin $i")
        assertEquals(30, repository.recentFiles().size)
        assertEquals(10, repository.recentFiles().count { it.pinned })
        grant("pin-0.txt")
        repository.removeWithUndo(uri("pin-0.txt").toString())
        assertTrue(granted("pin-0.txt")); assertTrue(repository.undoRemoval())
        assertTrue(repository.recentFiles().any { it.uri == uri("pin-0.txt").toString() && it.pinned })
        repository.removeWithUndo(uri("pin-0.txt").toString())
        repository.remember(uri("new-pin.txt"), "New pin")
        assertTrue(repository.togglePin(uri("new-pin.txt").toString()))
        assertTrue(repository.undoRemoval())
        assertEquals(30, repository.recentFiles().size)
        assertEquals(10, repository.recentFiles().count { it.pinned })
        assertTrue(repository.recentFiles().any { it.uri == uri("pin-0.txt").toString() && !it.pinned })
        repository.removeWithUndo(uri("pin-0.txt").toString(), now = 0)
        assertFalse(repository.undoAvailable()); assertFalse(granted("pin-0.txt"))
        assertFalse(repository.undoRemoval())
    }

    @Test fun homePinAndUndoControlsOperateOnReferencesOnly() {
        repository.remember(uri("copy.txt"), "copy.txt")
        ActivityScenario.launch(MainActivity::class.java).use {
            click(By.desc("Pin copy.txt"))
            assertTrue(repository.recentFiles().single().pinned)
            click(By.desc("Remove copy.txt from recent files"))
            assertTrue(repository.recentFiles().isEmpty())
            click("Undo removal")
            assertTrue(repository.recentFiles().single().pinned)
            assertEquals("Copyable Textr selection", repository.read(uri("copy.txt"), null, android.os.CancellationSignal()).text)
        }
    }

    @Test fun openWithUsesAnotherAppAndGrantsOnlyReadAccessToOriginalBytes() {
        context.contentResolver.call(uri("sample.json"), "reset-shared", "", null)
        ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
            ready(scenario)
            menu("Open with…")
            assertTrue(device.wait(Until.hasObject(By.text("Open a read-only copy with…")), 5000))
            assertFalse(device.hasObject(By.text("Textr")))
            click("Textr test receiver")
            await {
                val result = context.contentResolver.call(uri("sample.json"), "shared-result", "", null)!!.getString("result")!!
                result == "384742851e060d655fa8735fa44816377e3cfb928e086ef0a2345b738fbe5651:writeDenied=true"
            }
            ready(scenario)
        }
    }

    @Test fun openWithWithoutAnotherHandlerKeepsTheReader() {
        fun enable(value: Boolean) = context.contentResolver.call(uri("sample.json"), "receiver-enabled", value.toString(), null)!!.getInt("state")
        try {
            assertEquals(android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, enable(false))
            val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://${context.packageName}.shared/probe"), "application/json")
            val otherViewers = context.packageManager.queryIntentActivities(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo.packageName != context.packageName }
            org.junit.Assume.assumeTrue("This case requires no installed third-party JSON viewer", otherViewers.isEmpty())
            ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
                ready(scenario)
                menu("Open with…")
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.no_file_handler))), 5000))
                ready(scenario)
                assertTrue(source(scenario)!!.contains("Textr fixture"))
            }
        } finally { enable(true) }
    }

    private fun mode(scenario: ActivityScenario<MainActivity>): String { var value = ""; scenario.onActivity { value = it.findViewById<TextView>(R.id.display_mode).text.toString() }; return value }
    private fun source(scenario: ActivityScenario<MainActivity>): String? { var value: String? = null; scenario.onActivity { value = ViewModelProvider(it)[ReaderSession::class.java].original?.text }; return value }
    private fun scroll(scenario: ActivityScenario<MainActivity>): Int { var value = 0; scenario.onActivity { value = it.findViewById<WebView>(R.id.reader)?.scrollY ?: 0 }; return value }
    private fun awaitScroll(scenario: ActivityScenario<MainActivity>, expected: Int) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < deadline) {
            if (kotlin.math.abs(expected - scroll(scenario)) <= 2) return
            Thread.sleep(100)
        }
        var state = "No reader"
        scenario.onActivity { it.findViewById<WebView>(R.id.reader)?.let { view ->
            state = "scroll=${view.scrollY}, contentHeight=${view.contentHeight}, height=${view.height}, ready=${view.tag}"
        } }
        fail("Expected scroll $expected: $state")
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await { var value = false; scenario.onActivity { value = it.findViewById<WebView>(R.id.reader)?.tag == true }; value }
    private fun menu(label: String) { click(By.desc("More options")); click(label) }
    private fun click(label: String) = click(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(label), 2)))
    private fun click(selector: BySelector) { val item = device.wait(Until.findObject(selector), 5000); assertNotNull("Missing $selector", item); item!!.click(); device.waitForIdle() }
    private fun clickSelection(label: String) {
        val selector = By.text(java.util.regex.Pattern.compile(label, 2))
        if (!device.wait(Until.hasObject(selector), 2000)) {
            (device.findObject(By.res("android", "floating_toolbar_overflow_button")) ?: device.findObject(By.desc("More options")))?.click()
        }
        click(selector)
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(100) }
        fail("Backlog condition timed out")
    }
}
