package de.utrost.textr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.view.KeyEvent
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
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingExtrasDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun intent(name: String) = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    private fun replace(name: String, text: String) = context.contentResolver.call(uri(name), "replace", uri(name).toString(), Bundle().apply { putString("text", text) })
    private fun provider(method: String, name: String) = context.contentResolver.call(uri(name), method, uri(name).toString(), null)

    @Before @After fun reset() {
        Bookmarks(context).clear()
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        DocumentRepository(context).setSessionReferences(emptySet()); DocumentRepository(context).clear()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
    }

    @Test fun csvOptionsPersistAndOriginalBytesAreShared() {
        val name = "options.csv"
        val source = "\uFEFFName;Value\r\nTextr;\"one;two\"\r\n<script>literal</script>;3"
        replace(name, source)
        ActivityScenario.launch<MainActivity>(intent(name)).use { scenario ->
            ready(scenario)
            scenario.onActivity { assertEquals("Plain text", it.findViewById<TextView>(R.id.display_mode).text.toString()) }
            menu("CSV table options…")
            click(By.res(context.packageName, "csv_delimiter")); click("Semicolon (;)")
            click(By.res(context.packageName, "csv_header")); click("Preview")
            assertTrue(device.wait(Until.gone(By.text("CSV table options…")), 5000)); ready(scenario)
            assertVisible(scenario, "one;two")
            assertTrue(device.hasObject(By.text("<script>literal</script>")))
            scenario.onActivity {
                val prepared = ViewModelProvider(it)[ReaderSession::class.java].document!!
                assertTrue(prepared.preview!!.contains("<th scope=\"col\">Name</th>"))
                assertFalse(it.findViewById<WebView>(R.id.reader).settings.javaScriptEnabled)
            }
            val state = DocumentRepository(context).readingState(uri(name))
            assertEquals(CsvOptions(';', true), state.csv)
            scenario.onActivity { it.viewModelStore.clear() }; scenario.recreate(); ready(scenario)
            assertVisible(scenario, "one;two")
            val repo = DocumentRepository(context)
            val original = repo.read(uri(name), null, CancellationSignal())
            val shared = ShareFiles(context).prepare(uri(name), original, CancellationSignal())
            assertArrayEquals(source.toByteArray(), context.contentResolver.openInputStream(shared.clipData!!.getItemAt(0).uri)!!.use { it.readBytes() })
            click(By.res(context.packageName, "display_mode")); click("Plain text"); ready(scenario)
            scenario.onActivity { assertEquals(source.removePrefix("\uFEFF"), ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text) }
        }
    }

    @Test fun csvCellsSupportNativeSelectionAndCopy() {
        replace("selection.csv", "Copyable,Value\nTextr,42")
        ActivityScenario.launch<MainActivity>(intent("selection.csv")).use { scenario ->
            ready(scenario); menu("CSV table options…"); click("Preview"); ready(scenario)
            scenario.onActivity { (it.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).clearPrimaryClip() }
            val cell = device.wait(Until.findObject(By.text("Copyable")), 5000)
            if (cell == null) capture(scenario, "Copyable")
            assertNotNull(cell); cell!!.longClick(); click("Copy")
            await {
                var copied = false
                scenario.onActivity { copied = (it.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .primaryClip?.getItemAt(0)?.text?.toString()?.contains("Copyable") == true }; copied
            }
        }
    }

    @Test fun csvHeadersStayVisibleDuringVerticalAndHorizontalScrolling() {
        val source = (listOf((1..10).joinToString(",") { "Heading $it" }) +
            (1..60).map { row -> (1..10).joinToString(",") { col -> "Row $row cell $col" } }).joinToString("\n")
        replace("scrolling.csv", source)
        ActivityScenario.launch<MainActivity>(intent("scrolling.csv")).use { scenario ->
            ready(scenario); menu("CSV table options…")
            click(By.res(context.packageName, "csv_header")); click("Preview"); ready(scenario)
            val heading = device.wait(Until.findObject(By.text("Heading 1")), 5000)
            assertNotNull(heading)
            val headerBounds = heading!!.visibleBounds
            val top = headerBounds.top
            val before = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
            val web = android.graphics.Rect()
            scenario.onActivity { assertTrue(it.findViewById<WebView>(R.id.reader).getGlobalVisibleRect(web)) }
            device.swipe(web.centerX(), web.bottom - 120, web.centerX(), top + 160, 35)
            device.waitForIdle()
            // Chromium reports the original (offscreen) AX bounds for sticky cells.
            // Compare the painted header after a real swipe instead of those stale bounds.
            val after = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
            fun ink(pixel: Int): Boolean = android.graphics.Color.red(pixel) < 120 &&
                android.graphics.Color.green(pixel) < 120 && android.graphics.Color.blue(pixel) < 120
            var matched = 0; var expected = 0; var actual = 0
            for (y in headerBounds.top + 12 until headerBounds.bottom - 12) {
                for (x in headerBounds.left + 12 until headerBounds.right - 12) {
                    if (ink(after.getPixel(x, y))) actual++
                    if (ink(before.getPixel(x, y))) {
                        expected++
                        // Sticky positioning can shift rasterization by a subpixel.
                        if ((-2..2).any { dy -> (-2..2).any { dx -> ink(after.getPixel(x + dx, y + dy)) } }) matched++
                    }
                }
            }
            before.recycle(); after.recycle()
            assertTrue("Header strokes moved: $matched/$expected, after=$actual", expected > 100 &&
                matched.toDouble() / expected > 0.95 && actual.toDouble() / expected in 0.75..1.25)
            assertFalse(device.hasObject(By.text("Row 1 cell 1")))
            repeat(3) { device.swipe(web.right - 50, top + 60, web.left + 50, top + 60, 30) }
            device.waitForIdle()
            assertFalse(device.hasObject(By.text(java.util.regex.Pattern.compile("Row \\d+ cell 1"))))
            assertTrue(device.hasObject(By.text(java.util.regex.Pattern.compile("Row \\d+ cell (8|9|10)"))))
        }
    }

    @Test fun bookmarksOwnGrantsIndependentlyAndHaveBoundedMetadata() {
        val repo = DocumentRepository(context)
        val store = Bookmarks(context)
        val name = "bookmark-grant.txt"
        provider("grant", name)
        repo.retainReadAccess(uri(name), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        repo.remember(uri(name), name); repo.setSessionReferences(setOf(uri(name).toString()))
        val state = ReadingState(DisplayMode.PLAIN, 900, FileKind.CSV, TextEncoding.LATIN1, CsvOptions(';', true))
        repeat(Bookmarks.LIMIT) { assertTrue(store.add(uri(name).toString(), name, "Point $it", 0.5, "digest", state)) }
        assertFalse(store.add(uri(name).toString(), name, "Overflow", 0.5, "digest", state))
        repo.clear(); repo.setSessionReferences(emptySet())
        assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri == uri(name) })
        assertEquals(100, Bookmarks(context).entries().size)
        assertEquals(state.copy(scroll = 0), Bookmarks(context).entries().first().reading)
        val first = store.entries().first()
        store.rename(first.id, "Renamed"); assertEquals("Renamed", Bookmarks(context).entries().first().label)
        store.remove(first.id)
        assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri == uri(name) })
        store.clear()
        assertFalse(context.contentResolver.persistedUriPermissions.any { it.uri == uri(name) })
        assertEquals("[]", context.getSharedPreferences("bookmarks", 0).getString("entries", null))
    }

    @Test fun bookmarksNavigatePersistAndRecoverAfterChangedOrUnavailableFiles() {
        val name = "bookmark-reading.txt"
        val original = (1..400).joinToString("\n") { "PRIVATE CONTENT line $it" }
        replace(name, original)
        ActivityScenario.launch<MainActivity>(intent(name)).use { scenario ->
            ready(scenario)
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).scrollTo(0, 2000) }
            menu("Add bookmark…")
            device.findObject(By.res(context.packageName, "bookmark_name")).text = "Middle"
            click("Save")
            await { Bookmarks(context).entries().size == 1 }
            val bookmark = Bookmarks(context).entries().single()
            assertTrue(bookmark.fraction > 0)
            assertFalse(context.getSharedPreferences("bookmarks", 0).getString("entries", "")!!.contains("PRIVATE CONTENT"))
            click(By.desc("Back to files"))
            DocumentRepository(context).clear()
            menu("Bookmarks"); click(By.desc("Bookmark: Middle, in $name")); click("Open file"); ready(scenario)
            scenario.onActivity { assertTrue(it.findViewById<WebView>(R.id.reader).scrollY > 1500) }
            scenario.onActivity { it.viewModelStore.clear() }; scenario.recreate(); ready(scenario)
            click(By.desc("Back to files"))
            replace(name, "Changed short file")
            menu("Bookmarks"); click(By.desc("Bookmark: Middle, in $name")); click("Open file"); ready(scenario)
            scenario.onActivity {
                assertEquals("Changed short file", ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text)
                assertEquals(0, it.findViewById<WebView>(R.id.reader).scrollY)

            }
            await {
                var complete = false
                scenario.onActivity { complete = it.findViewById<TextView>(R.id.reading_progress).text.contains("100%") }; complete
            }
            click(By.desc("Back to files")); provider("missing", name)
            menu("Bookmarks"); click(By.desc("Bookmark: Middle, in $name")); click("Open file")
            assertTrue(device.wait(Until.hasObject(By.textContains("Your bookmark is kept")), 5000))
            assertEquals(1, Bookmarks(context).entries().size)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val canceled = monitorPicker(null)
            try {
                click("Open file again"); await { canceled.hits > 0 }
                assertEquals(bookmark.uri, Bookmarks(context).entries().single().uri)
            } finally { instrumentation.removeMonitor(canceled) }
            val failed = monitorPicker(Intent().setData(uri("missing.txt")))
            try {
                click("Open file again"); await { failed.hits > 0 }
                assertTrue(device.wait(Until.hasObject(By.textContains("Your bookmark is kept")), 5000))
                assertEquals(bookmark.uri, Bookmarks(context).entries().single().uri)
            } finally { instrumentation.removeMonitor(failed) }
            replace("reconnected.txt", original); provider("grant", "reconnected.txt")
            val selected = monitorPicker(Intent().setData(uri("reconnected.txt"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
            try {
                click("Open file again"); await { selected.hits > 0 }; awaitDocument(scenario, "reconnected.txt")
                assertEquals(uri("reconnected.txt").toString(), Bookmarks(context).entries().single().uri)
                scenario.onActivity { assertTrue(it.findViewById<WebView>(R.id.reader).scrollY > 1500) }
            } finally { instrumentation.removeMonitor(selected) }
        }
    }

    @Test fun keyboardShortcutsDispatchAndRespectInputDialogsAndUnavailableActions() {
        DocumentRepository(context).remember(uri("sample.json"), "sample.json")
        ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
            ready(scenario)
            device.pressKeyCode(KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON)
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "search_query")), 5000))
            scenario.onActivity { it.findViewById<EditText>(R.id.search_query).setText("kept") }
            device.pressKeyCode(KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON)
            assertFalse(device.hasObject(By.text("Go to line…")))
            scenario.onActivity { assertEquals("kept", it.findViewById<EditText>(R.id.search_query).text.toString()) }
            click(By.desc("Close search"))
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).requestFocus() }
            device.pressKeyCode(KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON)
            assertVisible(scenario, "Go to line…")
            device.pressKeyCode(KeyEvent.KEYCODE_O, KeyEvent.META_CTRL_ON)
            assertTrue(device.hasObject(By.text("Go to line…"))); click("Cancel")
            click(By.desc("Back to files")); click("sample.json"); awaitDocument(scenario, "sample.json")
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).requestFocus() }
            device.pressKeyCode(KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON)
            assertFalse(device.hasObject(By.text("Go to line…")))
            device.pressKeyCode(KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON)
            awaitDocument(scenario, "long.ini")
            device.pressKeyCode(KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            awaitDocument(scenario, "sample.json")
            scenario.onActivity {
                val groups = mutableListOf<android.view.KeyboardShortcutGroup>()
                it.onProvideKeyboardShortcuts(groups, null, -1)
                assertEquals(5, groups.last().items.size)
            }
            menu("About Textr"); click("Keyboard shortcuts")
            assertTrue(device.wait(Until.hasObject(By.textContains("Ctrl+Shift+Tab")), 5000))
            click("Done"); click("Done")
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).requestFocus() }
            val picker = monitorPicker(null)
            try {
                device.pressKeyCode(KeyEvent.KEYCODE_O, KeyEvent.META_CTRL_ON)
                await { picker.hits > 0 }
            } finally { InstrumentationRegistry.getInstrumentation().removeMonitor(picker) }
        }
    }

    @Test fun bookmarksAndCsvControlsRemainReachableAtLargeFonts() {
        val old = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            replace("large-font.csv", "a,b\n1,2")
            ActivityScenario.launch<MainActivity>(intent("large-font.csv")).use { scenario ->
                scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                await {
                    var configured = false
                    scenario.onActivity { configured = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && it.resources.configuration.fontScale >= 1.99f }; configured
                }
                ready(scenario); menu("CSV table options…"); click("Preview"); ready(scenario)
                scenario.onActivity { assertTrue(it.findViewById<WebView>(R.id.reader).height > 0) }
                click(By.res(context.packageName, "display_mode")); click("Plain text"); ready(scenario)
                menu("Add bookmark…"); click("Save")
                await { Bookmarks(context).entries().isNotEmpty() }
                menu("Bookmarks")
                assertTrue(device.wait(Until.hasObject(By.descContains("Bookmark:")), 5000))
                click("Done")
            }
        } finally { device.executeShellCommand("settings put system font_scale ${old.takeUnless { it == "null" } ?: "1.0"}") }
    }

    private fun monitorPicker(result: Intent?): android.app.Instrumentation.ActivityMonitor {
        val filter = android.content.IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*")
        }
        return InstrumentationRegistry.getInstrumentation().addMonitor(filter,
            android.app.Instrumentation.ActivityResult(if (result == null) android.app.Activity.RESULT_CANCELED else android.app.Activity.RESULT_OK, result), true)
    }

    private fun capture(scenario: ActivityScenario<MainActivity>, label: String) {
        val prefix = "extras-${label.replace(Regex("[^a-zA-Z]"), "")}" 
        device.dumpWindowHierarchy(java.io.File(context.cacheDir, "$prefix.xml"))
        device.takeScreenshot(java.io.File(context.cacheDir, "$prefix.png"))
        scenario.onActivity {
            val view = it.findViewById<WebView>(R.id.reader)
            android.util.Log.e("TextrExtrasTest", "$label mode=${it.findViewById<TextView>(R.id.display_mode)?.text} document=${ViewModelProvider(it)[ReaderSession::class.java].document?.source?.name} height=${view?.height} content=${view?.contentHeight}")
        }
    }
    private fun assertVisible(scenario: ActivityScenario<MainActivity>, label: String) {
        val found = device.wait(Until.hasObject(By.text(label)), 5000)
        if (!found) capture(scenario, label)
        assertTrue("Missing $label", found)
    }

    private fun awaitDocument(scenario: ActivityScenario<MainActivity>, name: String) = await {
        var match = false
        scenario.onActivity { match = ViewModelProvider(it)[ReaderSession::class.java].document?.source?.name == name && it.findViewById<WebView>(R.id.reader)?.tag == true }; match
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var value = false; scenario.onActivity { value = it.findViewById<WebView>(R.id.reader)?.tag == true }; value
    }
    private fun menu(text: String) { click(By.desc("More options")); click(text) }
    private fun click(text: String) = click(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(text), 2)))
    private fun click(selector: BySelector) {
        repeat(3) { attempt ->
            try {
                var node = device.wait(Until.findObject(selector), 3000)
                if (node == null) node = device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
                if (node == null) {
                    device.dumpWindowHierarchy(java.io.File(context.cacheDir, "extras-control.xml"))
                    device.takeScreenshot(java.io.File(context.cacheDir, "extras-control.png"))
                }
                assertNotNull("Missing $selector", node)
                node!!.click(); device.waitForIdle(); return
            } catch (error: StaleObjectException) { if (attempt == 2) throw error; device.waitForIdle() }
        }
    }
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < until) { if (condition()) return; Thread.sleep(100) }
        fail("Reader condition timed out")
    }
}
