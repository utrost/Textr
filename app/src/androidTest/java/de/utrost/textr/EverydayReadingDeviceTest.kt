package de.utrost.textr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.webkit.WebView
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
import java.nio.charset.Charset

@RunWith(AndroidJUnit4::class)
class EverydayReadingDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun intent(name: String) = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    private fun replace(name: String, text: String) = context.contentResolver.call(uri(name), "replace", uri(name).toString(), Bundle().apply { putString("text", text) })

    @Before fun reset() {
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        DocumentRepository(context).setSessionReferences(emptySet()); DocumentRepository(context).clear()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
    }

    @Test fun sourceSearchOptionsResultsAndJumpWorkFromPreview() {
        val source = "# Header\nAlpha alpha alphabet alpha_beta\n" + "filler\n".repeat(80) + "alpha final\n"
        replace("source-search.md", source)
        ActivityScenario.launch<MainActivity>(intent("source-search.md")).use { scenario ->
            ready(scenario)
            click(By.desc("Find")); click(By.desc("Search source…"))
            val field = device.wait(Until.findObject(By.res(context.packageName, "source_query")), 5000)
            assertNotNull("Source search query", field)
            field!!.text = "alpha"
            click(By.res(context.packageName, "match_case")); click(By.res(context.packageName, "whole_word"))
            assertTrue(device.wait(Until.hasObject(By.text("2 matches in source")), 10000))
            click(By.text("Line 83")); ready(scenario)
            await {
                var reached = false
                scenario.onActivity {
                    val web = it.findViewById<WebView>(R.id.reader)
                    reached = web.url?.endsWith("#source-match") == true && web.scrollY > 0
                }; reached
            }
            scenario.onActivity {
                assertEquals("Plain text", it.findViewById<TextView>(R.id.display_mode).text.toString())
                assertEquals(source, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text)
                assertFalse(it.findViewById<WebView>(R.id.reader).settings.javaScriptEnabled)
            }
            scenario.recreate(); ready(scenario)
            click(By.desc("Find")); click(By.desc("Search source…"))
            val restored = device.wait(Until.findObject(By.res(context.packageName, "source_query")), 5000)
            assertNotNull(restored)
            assertEquals("alpha", restored!!.text)
            assertTrue(device.findObject(By.res(context.packageName, "match_case")).isChecked)
            assertTrue(device.findObject(By.res(context.packageName, "whole_word")).isChecked)
            click("Done")
            assertFalse(context.getSharedPreferences("open_documents", 0).getString("entries", "")!!.contains("alpha"))
        }
    }

    @Test fun encodingRecoveryPersistsThroughReloadSwitchingAndProcessStateAndSharesOriginalBytes() {
        val name = "legacy-1252.txt"
        context.contentResolver.call(uri(name), "grant", uri(name).toString(), null)
        val expected = "Café costs €5 — résumé\r\nSecond line"
        val repository = DocumentRepository(context)
        repository.remember(uri("copy.txt"), "copy.txt")
        ActivityScenario.launch<MainActivity>(intent(name)).use { scenario ->
            click("Reopen with encoding…"); click("windows-1252"); ready(scenario)
            scenario.onActivity { assertEquals(expected, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text) }
            assertEquals(TextEncoding.WINDOWS1252, repository.readingState(uri(name)).encoding)
            assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri == uri(name) })
            menu("Document info")
            assertTrue(device.wait(Until.hasObject(By.textContains("Encoding: windows-1252")), 5000))
            assertTrue(device.hasObject(By.textContains("Lines: 2\nLine endings: CRLF")))
            click("Done"); menu("Reload"); ready(scenario)
            click(By.desc("Back to files")); click("copy.txt"); ready(scenario)
            menu("Open documents"); click(name); ready(scenario)
            scenario.onActivity { it.viewModelStore.clear() }; scenario.recreate(); ready(scenario)
            scenario.onActivity { assertEquals(expected, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text) }
            val decoded = repository.read(uri(name), null, CancellationSignal(), TextEncoding.WINDOWS1252)
            val shared = ShareFiles(context).prepare(uri(name), decoded, CancellationSignal())
            assertArrayEquals(expected.toByteArray(Charset.forName("windows-1252")),
                context.contentResolver.openInputStream(shared.clipData!!.getItemAt(0).uri)!!.use { it.readBytes() })
            menu("Document info"); click("Reopen with encoding…"); click("Automatic")
            assertTrue(device.wait(Until.hasObject(By.text("Cannot open this file")), 5000))
            // A failed choice must not replace the last successful reading preference.
            assertEquals(TextEncoding.WINDOWS1252, repository.readingState(uri(name)).encoding)
            click("Reopen with encoding…"); click("windows-1252"); ready(scenario)
        }
    }

    @Test fun bomlessUtf16CanBeRecoveredFromBinaryMessage() {
        ActivityScenario.launch<MainActivity>(intent("bomless-utf16.txt")).use { scenario ->
            click("Reopen with encoding…"); click("UTF-16LE"); ready(scenario)
            scenario.onActivity { assertEquals("Hello 🌍\r\nGrüße", ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text) }
        }
    }

    @Test fun activeDocumentDirectFormatAndOfflineHelpAreDiscoverable() {
        ActivityScenario.launch<MainActivity>(intent("copy.txt")).use { scenario ->
            ready(scenario)
            click(By.desc("Open documents (1)"))
            assertTrue(device.wait(Until.hasObject(By.desc("Current document: copy.txt")), 5000))
            click("Done")
            menu("Treat as…"); click("JSON"); ready(scenario)
            scenario.onActivity { assertEquals(FileKind.JSON, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.kind) }
            menu("About Textr"); click("Opening files and defaults")
            assertTrue(device.wait(Until.hasObject(By.textContains("reported file type (MIME type)")), 5000))
            click("Done")
            assertTrue(device.wait(Until.gone(By.textContains("reported file type (MIME type)")), 5000))
            click("Done")
            assertTrue(device.wait(Until.gone(By.text("About Textr")), 5000))
            click(By.desc("Back to files"))
            menu("Opening files and defaults")
            assertTrue(device.hasObject(By.textContains("Clearing history does not delete your files")))
        }
    }

    @Test fun wideScreenTabsSwitchDocumentsAndKeepStableOrder() {
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        val oldDensity = Regex("Override density: (\\d+)").find(device.executeShellCommand("wm density"))?.groupValues?.get(1)
        val oldFont = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("wm size 1200x1920"); device.executeShellCommand("wm density 240")
            device.executeShellCommand("settings put system font_scale 1.0")
            device.waitForIdle()
            DocumentRepository(context).remember(uri("copy.txt"), "copy.txt")
            ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
                ready(scenario)
                click(By.desc("Back to files")); click("copy.txt"); ready(scenario)
                assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "document_strip")), 5000))
                assertTrue(device.hasObject(By.desc("Current document: copy.txt")))
                val before = device.findObject(By.desc("Switch to sample.json")).visibleBounds.left
                click(By.desc("Switch to sample.json")); ready(scenario)
                assertTrue(device.hasObject(By.desc("Current document: sample.json")))
                assertEquals(before, device.findObject(By.desc("Current document: sample.json")).visibleBounds.left)
                scenario.recreate(); ready(scenario)
                click(By.desc("Switch to copy.txt")); ready(scenario)
                click(By.desc("Open documents (2)")); click(By.desc("Close copy.txt")); click("Done")
                assertFalse(OpenDocuments(context).entries.any { it.name == "copy.txt" })
            }
        } finally {
            device.executeShellCommand("wm size ${oldSize ?: "reset"}")
            device.executeShellCommand("wm density ${oldDensity ?: "reset"}")
            device.executeShellCommand("settings put system font_scale ${oldFont.takeUnless { it == "null" } ?: "1.0"}")
            device.waitForIdle()
        }
    }

    @Test fun sourceSearchRemainsReachableAtLargeFontsInLandscape() {
        val oldFont = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            ActivityScenario.launch<MainActivity>(intent("repeated.txt")).use { scenario ->
                scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                await {
                    var configured = false
                    scenario.onActivity { configured = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && it.resources.configuration.fontScale >= 1.99f }
                    configured
                }
                ready(scenario)
                click(By.desc("Find"))
                scenario.onActivity {
                    val field = it.findViewById<android.widget.EditText>(R.id.search_query)
                    field.setText("needle")
                    (it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
                }
                click(By.desc("Search source…"))
                assertTrue(device.wait(Until.hasObject(By.text("3 matches in source")), 10000))
                click(By.res(context.packageName, "match_case"))
                click(By.res(context.packageName, "whole_word"))
                assertTrue(device.wait(Until.hasObject(By.text("3 matches in source")), 10000))
                click(By.text("Line 3")); ready(scenario)
                scenario.onActivity { assertTrue(it.findViewById<WebView>(R.id.reader).height > 0) }
            }
        } finally {
            device.executeShellCommand("settings put system font_scale ${oldFont.takeUnless { it == "null" } ?: "1.0"}")
            device.setOrientationNatural(); device.unfreezeRotation()
        }
    }

    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var ready = false; scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }; ready
    }
    private fun menu(label: String) { click(By.desc("More options")); click(label) }
    private fun click(text: String) = click(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(text), 2)))
    private fun click(selector: BySelector) {
        repeat(3) { attempt ->
            try {
                var node = device.wait(Until.findObject(selector), 3000)
                if (node == null) {
                    val list = device.findObject(By.res(context.packageName, "source_results"))
                        ?: device.findObject(By.scrollable(true))
                    node = list?.scrollUntil(Direction.DOWN, Until.findObject(selector))
                }
                if (node == null) {
                    device.dumpWindowHierarchy(java.io.File(context.cacheDir, "reader-test-failure.xml"))
                    device.takeScreenshot(java.io.File(context.cacheDir, "reader-test-failure.png"))
                }
                assertNotNull("Missing $selector", node)
                node!!.click(); device.waitForIdle()
                return
            } catch (error: StaleObjectException) {
                // Native menus/dialogs can replace their accessibility nodes during
                // animation. Reacquire the same control; do not weaken assertions.
                if (attempt == 2) throw error
                device.waitForIdle()
            }
        }
    }
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < until) { if (condition()) return; Thread.sleep(100) }
        fail("Reader condition timed out")
    }
}
