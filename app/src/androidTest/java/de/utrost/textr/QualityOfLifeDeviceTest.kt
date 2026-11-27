package de.utrost.textr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import android.widget.*
import androidx.lifecycle.Lifecycle
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
class QualityOfLifeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val repository get() = DocumentRepository(context)
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun launch(name: String, text: String): ActivityScenario<MainActivity> {
        context.contentResolver.call(uri(name), "replace", uri(name).toString(), Bundle().apply { putString("text", text) })
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    @Before fun reset() {
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
        repository.setSessionReferences(emptySet()); repository.clear()
    }
    @Test fun recentFiltersCombineAndSurviveRecreation() {
        repository.remember(uri("README"), "README", FileKind.MARKDOWN)
        repository.remember(uri("Alpha.md"), "Alpha.md"); repository.remember(uri("Alpha.txt"), "Alpha.txt"); repository.remember(uri("Beta.md"), "Beta.md")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.findViewById<EditText>(R.id.recent_query).setText("alpha")
                it.findViewById<Spinner>(R.id.recent_format).setSelection(FileKind.entries.indexOf(FileKind.MARKDOWN) + 1)
            }
            await { device.hasObject(By.text("Alpha.md")) && !device.hasObject(By.text("Alpha.txt")) && !device.hasObject(By.text("Beta.md")) }
            scenario.recreate()
            scenario.onActivity { assertEquals("alpha", it.findViewById<EditText>(R.id.recent_query).text.toString()) }
            scenario.onActivity { it.findViewById<EditText>(R.id.recent_query).setText("absent") }
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.recent_no_matches))), 5000))
            click(R.string.clear_filters)
            assertTrue(device.wait(Until.hasObject(By.text("Alpha.txt")), 5000))
            scenario.onActivity {
                it.findViewById<EditText>(R.id.recent_query).setText("README")
                it.findViewById<Spinner>(R.id.recent_format).setSelection(FileKind.entries.indexOf(FileKind.MARKDOWN) + 1)
            }
            assertTrue(device.wait(Until.hasObject(By.text("README")), 5000))
            assertEquals(FileKind.MARKDOWN, DocumentRepository(context).recentFiles().single { it.name == "README" }.knownFormat)
        }
    }
    @Test fun previousPositionSurvivesRecreationAndChangedReloadInvalidatesIt() {
        val text = (1..400).joinToString("\n") { "Line $it: unique content" }
        launch("navigation.txt", text).use { scenario ->
            ready(scenario)
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).scrollTo(0, 650) }
            val before = scroll(scenario)
            menu(R.string.go_to_line)
            device.findObject(By.clazz(EditText::class.java)).text = "300"
            click(R.string.go)
            ready(scenario); await { scroll(scenario) > before + 1000 }
            val destination = scroll(scenario)
            scenario.recreate(); ready(scenario)
            menu(R.string.previous_position); ready(scenario)
            await { kotlin.math.abs(scroll(scenario) - before) <= 3 }
            scenario.onActivity { assertFalse(ViewModelProvider(it)[ReaderSession::class.java].navigation.hasPrevious(uri("navigation.txt").toString())) }
            menu(R.string.next_position)
            await { kotlin.math.abs(scroll(scenario) - destination) <= 3 }
            menu(R.string.previous_position)
            await { kotlin.math.abs(scroll(scenario) - before) <= 3 }
            context.contentResolver.call(uri("navigation.txt"), "replace", uri("navigation.txt").toString(), Bundle().apply { putString("text", "changed") })
            menu(R.string.reload); ready(scenario)
            scenario.onActivity {
                val navigation = ViewModelProvider(it)[ReaderSession::class.java].navigation
                assertFalse(navigation.hasPrevious(uri("navigation.txt").toString()))
                assertFalse(navigation.hasNext(uri("navigation.txt").toString()))
            }
        }
    }
    @Test fun homeContinuesAtTheSavedPositionDespitePinnedOlderFile() {
        val first = uri("older.txt")
        repository.remember(first, "older.txt")
        assertTrue(repository.togglePin(first.toString()))
        val text = (1..400).joinToString("\n") { "Line $it" }
        launch("resume.txt", text).use { scenario ->
            ready(scenario)
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).scrollTo(0, 650) }
            val before = scroll(scenario)
            assertTrue(before > 0)
            device.findObject(By.desc(context.getString(R.string.back_to_files))).click()
            await { device.hasObject(By.res(context.packageName, "continue_reading").textContains("resume.txt")) }
            val current = repository.recentFiles().single { it.name == "resume.txt" }
            assertTrue((current.progress ?: -1) in 1..99)
            scenario.onActivity { it.findViewById<Button>(R.id.continue_reading).performClick() }
            ready(scenario)
            await { kotlin.math.abs(scroll(scenario) - before) <= 3 }
        }
    }

    @Test fun readerAddsCurrentFileToExistingAndNewCollections() {
        val store = ReadingCollections(context)
        val existing = store.create("Reader shortcuts QA")!!
        try {
            launch("collect.txt", "Reference-only fixture").use { scenario ->
                ready(scenario)
                menu(R.string.add_to_collection)
                device.wait(Until.findObject(By.text("Reader shortcuts QA")), 5000).click()
                await { store.entries().first { it.id == existing }.members.singleOrNull()?.uri == uri("collect.txt").toString() }
                menu(R.string.add_to_collection)
                device.wait(Until.findObject(By.res("android", "button3")), 5000).click()
                device.wait(Until.findObject(By.clazz(EditText::class.java)), 5000).text = "Reader shortcuts QA new"
                device.wait(Until.findObject(By.res("android", "button1")), 5000).click()
                await { store.entries().firstOrNull { it.name == "Reader shortcuts QA new" }?.members?.singleOrNull()?.uri == uri("collect.txt").toString() }
            }
        } finally {
            store.delete(existing)
            store.entries().firstOrNull { it.name == "Reader shortcuts QA new" }?.let { store.delete(it.id) }
        }
    }

    @Test fun readerWrapTogglePersistsWithoutChangingGlobalTypography() {
        val preferences = context.getSharedPreferences("reading", 0)
        preferences.edit().putInt("fontSize", 20).commit()
        launch("wrap.txt", (1..400).joinToString("\n") { "Line $it" }).use { scenario ->
            ready(scenario)
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).scrollTo(0, 650) }
            val before = scroll(scenario)
            menu(R.string.disable_line_wrap)
            await { repository.readingState(uri("wrap.txt")).profile?.wrap == false }
            ready(scenario)
            assertEquals(before, scroll(scenario))
            scenario.recreate()
            ready(scenario)
            menu(R.string.enable_line_wrap)
            await { repository.readingState(uri("wrap.txt")).profile?.wrap == true }
            assertTrue(repository.readingState(uri("wrap.txt")).profile!!.inheritTypography)
            assertEquals(20, preferences.getInt("fontSize", 16))
            assertTrue(preferences.getBoolean("wrap", true))
        }
    }

    @Test fun copyMarkdownUsesConversionAndCopyAllStillUsesHtml() {
        val source = "<h1>Heading</h1><p>Grüße &amp; 🚀 <strong>bold</strong></p>"
        launch("copy.html", source).use { scenario ->
            ready(scenario); menu(R.string.copy_markdown)
            scenario.onActivity {
                val text = it.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
                assertEquals(HtmlMarkdown.convert(source), text); assertFalse(text.contains("<h1>"))
            }
            menu(R.string.copy_all)
            scenario.onActivity { assertEquals(source, it.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()) }
        }
    }
    @Test fun closedDocumentsAreSessionOnlyBoundedAndNeverReplaceNewerOpenEntries() {
        val docs = OpenDocuments(context)
        for (i in 0..6) {
            val entry = docs.opened("content://fixture/$i", TextDocument("$i.txt", "text", FileKind.TEXT, 4))
            entry.query = "private $i"; entry.searching = true; entry.reading = ReadingState(DisplayMode.PLAIN, i * 100)
            docs.close(entry.key)
        }
        assertFalse(OpenDocuments(context).hasClosed)
        val last = docs.reopen()!!; assertEquals("private 6", last.query); assertEquals(600, last.reading.scroll)
        docs.close(last.key)
        val newer = docs.opened(last.uri, TextDocument("new.txt", "new", FileKind.TEXT, 3))
        assertSame(newer, docs.reopen()); assertEquals(1, docs.entries.size)
        assertEquals(listOf("5.txt", "4.txt", "3.txt", "2.txt"), generateSequence { docs.reopen() }.map { it.name }.toList())
        docs.close(newer.key); docs.clearClosed(); assertNull(docs.reopen())
        assertFalse(context.getSharedPreferences("open_documents", 0).all.toString().contains("private"))
    }
    @Test fun keepAwakeAppliesOnlyToForegroundReadyReader() {
        context.getSharedPreferences("reading", 0).edit().putBoolean("keepAwake", true).commit()
        launch("awake.txt", "content").use { scenario ->
            ready(scenario)
            fun awake(expected: Boolean) = scenario.onActivity { assertEquals(expected, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) }
            awake(true); scenario.moveToState(Lifecycle.State.STARTED); awake(false)
            scenario.moveToState(Lifecycle.State.RESUMED); awake(true)
            scenario.recreate(); ready(scenario); awake(true)
            device.findObject(By.desc(context.getString(R.string.back_to_files))).click(); device.waitForIdle(); awake(false)
        }
    }
    @Test fun nativeSelectionSearchTransfersExactSelectionWithoutChangingClipboard() {
        launch("selection.txt", "needle needle").use { scenario ->
            ready(scenario)
            scenario.onActivity { it.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("sentinel", "unchanged")) }
            val word = device.wait(Until.findObject(By.textContains("needle needle")), 5000)
            if (word != null) word.longClick() else {
                // Some WebViews omit source accessibility nodes (#59). Exercise the
                // native selection gesture at the first word without requiring that bridge.
                var x = 0; var y = 0
                scenario.onActivity {
                    val web = it.findViewById<WebView>(R.id.reader)
                    val location = IntArray(2); web.getLocationOnScreen(location)
                    val density = it.resources.displayMetrics.density
                    x = location[0] + (32 * density).toInt(); y = location[1] + (32 * density).toInt()
                }
                device.swipe(x, y, x, y, 100)
            }
            device.waitForIdle()
            val action = By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(context.getString(R.string.find_selected)), 2))
            if (!device.wait(Until.hasObject(action), 2000)) (device.findObject(By.res("android", "overflow")) ?: device.findObject(By.res("android", "floating_toolbar_overflow_button")))?.click()
            val item = device.wait(Until.findObject(action), 5000)
            if (item == null) { device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null), "selection.xml")); device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "selection.png")) }
            assertNotNull("Native Find selected text action", item); item!!.click()
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "source_query")), 5000))
            assertEquals("needle", device.findObject(By.res(context.packageName, "source_query")).text)
            scenario.onActivity { assertEquals("unchanged", it.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()) }
        }
    }
    @Test fun closedReaderReopensAfterRecreationWithPositionAndSourceQuery() {
        launch("reopen.txt", "reference text\n".repeat(400)).use { scenario ->
            ready(scenario)
            scenario.onActivity {
                it.findViewById<WebView>(R.id.reader).scrollTo(0, 750)
                it.findSelectedText("reference")
            }
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "source_query")), 5000))
            click(R.string.done)
            val position = scroll(scenario)
            device.findObject(By.desc(context.getString(R.string.documents_count, 1))).click()
            device.wait(Until.findObject(By.desc(context.getString(R.string.close_named, "reopen.txt"))), 5000)!!.click()
            device.waitForIdle(); scenario.recreate()
            menu(R.string.reopen_closed); ready(scenario)
            await { kotlin.math.abs(scroll(scenario) - position) <= 3 }
            scenario.onActivity {
                val entry = ViewModelProvider(it)[ReaderSession::class.java].openDocuments!!.entries.single()
                assertEquals("reference", entry.sourceSearch.query)
            }
        }
    }
    @Test fun rejectedSelectionsPreserveQueryAndValidUnicodeKeepsOptions() {
        launch("unicode.md", "# Title\nGrüße 🚀\nsecond line").use { scenario ->
            ready(scenario)
            scenario.onActivity { it.findSelectedText("Grüße 🚀\nsecond line") }
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "source_query")), 5000))
            device.findObject(By.res(context.packageName, "match_case")).click()
            assertTrue("Match case checked after its native click", device.wait(Until.hasObject(By.res(context.packageName, "match_case").checked(true)), 5000))
            device.findObject(By.res(context.packageName, "whole_word")).click()
            assertTrue("Whole word checked after its native click", device.wait(Until.hasObject(By.res(context.packageName, "whole_word").checked(true)), 5000))
            click(R.string.done)
            scenario.onActivity {
                it.findSelectedText(" \n "); it.findSelectedText("x".repeat(SourceSearch.MAX_QUERY + 1))
                val entry = ViewModelProvider(it)[ReaderSession::class.java].openDocuments!!.entries.single()
                assertEquals("Grüße 🚀\nsecond line", entry.sourceSearch.query)
                assertTrue("Retained match case: ${entry.sourceSearch}", entry.sourceSearch.matchCase); assertTrue("Retained whole word: ${entry.sourceSearch}", entry.sourceSearch.wholeWord)
                it.findSelectedText("Title")
            }
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "source_query")), 5000))
            assertEquals("Title", device.findObject(By.res(context.packageName, "source_query")).text)
            assertTrue(device.findObject(By.res(context.packageName, "match_case")).isChecked)
            assertTrue(device.findObject(By.res(context.packageName, "whole_word")).isChecked)
            scenario.recreate(); ready(scenario)
            scenario.onActivity { assertEquals("Title", ViewModelProvider(it)[ReaderSession::class.java].openDocuments!!.entries.single().sourceSearch.query) }
        }
    }
    @Test fun oversizedMarkdownCopyKeepsClipboardAndOffersExport() {
        val html = "<p>" + "*".repeat(140_000) + "</p>"
        launch("large-copy.html", html).use { scenario ->
            ready(scenario)
            scenario.onActivity { it.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("sentinel", "unchanged")) }
            menu(R.string.copy_markdown)
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.copy_markdown_limit))), 5000))
            scenario.onActivity { assertEquals("unchanged", it.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()) }
        }
    }

    @Test fun returningFromJumpRestoresActiveFindQueryAndExactPosition() {
        launch("find-history.txt", "reference text\n".repeat(400)).use { scenario ->
            ready(scenario)
            device.findObject(By.desc(context.getString(R.string.find))).click()
            scenario.onActivity { it.findViewById<EditText>(R.id.search_query).setText("reference") }
            await { device.hasObject(By.res(context.packageName, "search_count").textContains("400")) }
            scenario.onActivity {
                val field = it.findViewById<EditText>(R.id.search_query)
                field.clearFocus()
                it.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
            }
            device.waitForIdle()
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).scrollTo(0, 650) }
            val before = scroll(scenario)
            menu(R.string.go_to_line)
            device.findObject(By.clazz(EditText::class.java)).text = "300"
            click(R.string.go); ready(scenario)
            await { scroll(scenario) > before + 1000 }
            menu(R.string.previous_position); ready(scenario)
            try { await { device.hasObject(By.res(context.packageName, "search_count").textContains("400")) && kotlin.math.abs(scroll(scenario) - before) <= 3 } }
            catch (error: AssertionError) {
                var detail = ""
                scenario.onActivity { detail = "expected=$before actual=${scroll(scenario)} query=${it.findViewById<EditText>(R.id.search_query).text} count=${it.findViewById<TextView>(R.id.search_count).text}" }
                throw AssertionError(detail, error)
            }
            scenario.onActivity { assertEquals("reference", it.findViewById<EditText>(R.id.search_query).text.toString()) }
            device.waitForIdle(); assertTrue(kotlin.math.abs(scroll(scenario) - before) <= 3)
            scenario.recreate(); ready(scenario)
            await { kotlin.math.abs(scroll(scenario) - before) <= 3 }
            device.waitForIdle(); assertTrue(kotlin.math.abs(scroll(scenario) - before) <= 3)
        }
    }

    private fun scroll(scenario: ActivityScenario<MainActivity>): Int { var value = 0; scenario.onActivity { value = it.findViewById<WebView>(R.id.reader).scrollY }; return value }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await { var ready = false; scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }; ready }
    private fun menu(label: Int) {
        device.findObject(By.desc(context.getString(R.string.more_options))).click()
        val selector = By.text(context.getString(label))
        assertNotNull(device.wait(Until.findObject(By.clazz(ListView::class.java)), 5000))
        repeat(12) {
            device.findObject(selector)?.let { it.click(); device.waitForIdle(); return }
            device.findObject(By.clazz(ListView::class.java))?.scroll(Direction.DOWN, 0.65f)
        }
        fail("Missing menu ${context.getString(label)}")
    }
    private fun click(label: Int) {
        val item = device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(context.getString(label)), 2))), 5000)
        assertNotNull(context.getString(label), item); item!!.click(); device.waitForIdle()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(100) }
        fail("Quality of life condition timed out")
    }
}
