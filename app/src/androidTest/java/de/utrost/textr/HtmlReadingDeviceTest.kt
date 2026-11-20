package de.utrost.textr

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
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
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HtmlReadingDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val uri = Uri.parse("content://de.utrost.textr.test.documents/html-reader-fixture.html")
    private val source = "<html><head><script>RUN_SCRIPT_SENTINEL</script></head><body>\r\n" +
        "<h1>Saved article</h1><p>Converted reading text &amp; café.</p>\r\n" +
        "<h2>Second section</h2><ul><li>A list item</li></ul>" +
        "<table><tr><th>Name</th><th>Value</th></tr><tr><td>Alpha</td><td>1</td></tr></table>" +
        "<img src='https://example.invalid/tracker.png'></body></html>\r\n"

    @Before fun reset() {
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        DocumentRepository(context).setSessionReferences(emptySet())
        DocumentRepository(context).clear()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
    }

    private fun replace(text: String) {
        context.contentResolver.call(uri, "replace", uri.toString(), Bundle().apply { putString("text", text) })
    }
    private fun open() = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/html").setClass(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    @Test fun localHtmlReadingViewSearchOutlineAndModeSurviveRecreationWithoutChangingOriginalBytes() {
        replace(source)
        ActivityScenario.launch<MainActivity>(open()).use { scenario ->
            ready(scenario)
            scenario.onActivity {
                assertEquals(context.getString(R.string.highlighted), it.findViewById<TextView>(R.id.display_mode).text.toString())
                assertEquals(source, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text)
            }
            selectReadingView(scenario)
            clickDescription(context.getString(R.string.find))
            scenario.onActivity { it.findViewById<EditText>(R.id.search_query).setText("Converted reading text") }
            await { text(scenario, R.id.search_count) == "1 / 1" }
            scenario.onActivity { it.findViewById<EditText>(R.id.search_query).setText("RUN_SCRIPT_SENTINEL") }
            await { text(scenario, R.id.search_count) == context.getString(R.string.no_matches) }
            clickDescription(context.getString(R.string.close_search))
            clickDescription(context.getString(R.string.more_options)); click(context.getString(R.string.outline))
            val heading = device.wait(Until.findObject(By.textContains("Second section")), 5000)
            assertNotNull("Converted heading is in native outline", heading); heading!!.click(); ready(scenario)
            scenario.recreate(); ready(scenario)
            scenario.onActivity {
                val prepared = ViewModelProvider(it)[ReaderSession::class.java].document!!
                assertEquals(source, prepared.source.text)
                assertEquals(context.getString(R.string.html_reading_view), it.findViewById<TextView>(R.id.display_mode).text.toString())
                assertEquals(2, prepared.headings.size)
                assertSecurity(it.findViewById(R.id.reader))
            }
            val original = DocumentRepository(context).read(uri, "text/html", CancellationSignal())
            val shared = ShareFiles(context).prepare(uri, original, CancellationSignal())
            assertEquals("text/html", shared.type)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, shared.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            val copy = shared.clipData!!.getItemAt(0).uri
            assertArrayEquals(source.toByteArray(), context.contentResolver.openInputStream(copy)!!.use { it.readBytes() })
            assertArrayEquals(source.toByteArray(), context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        }
    }

    @Test fun htmlTextSharesAreRecognizedButPlainUrlSharesRemainText() {
        val cases = listOf(
            Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_TEXT, source),
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Browser fallback").putExtra(Intent.EXTRA_HTML_TEXT, source),
            Intent(Intent.ACTION_SEND).setType("application/xhtml+xml").putExtra(Intent.EXTRA_TEXT, source),
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://example.invalid/article")
        )
        cases.forEachIndexed { index, intent ->
            ActivityScenario.launch<MainActivity>(intent.setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                ready(scenario)
                scenario.onActivity {
                    val doc = ViewModelProvider(it)[ReaderSession::class.java].document!!
                    assertEquals(if (index < 3) FileKind.HTML else FileKind.TEXT, doc.source.kind)
                    assertEquals(if (index < 3) source else "https://example.invalid/article", doc.source.text)
                    assertSecurity(it.findViewById(R.id.reader))
                }
                if (index < 3) { selectReadingView(scenario); scenario.recreate(); ready(scenario) }
            }
            assertTrue("Shared contents are not recent-file history", DocumentRepository(context).recentFiles().isEmpty())
        }
    }

    @Test fun oversizedHtmlRetainsCompleteSourceAndLocalizedLimitExplanation() {
        val over = "<p>" + "x".repeat(HtmlMarkdown.MAX_CHARS) + "</p>"
        replace(over)
        ActivityScenario.launch<MainActivity>(open()).use { scenario ->
            ready(scenario)
            scenario.onActivity {
                val doc = ViewModelProvider(it)[ReaderSession::class.java].document!!
                assertEquals(over, doc.source.text)
                assertNull(doc.preview); assertEquals(PreviewNotice.HTML_LIMIT, doc.notice)
                assertFalse(DisplayMode.PREVIEW in doc.modes)
                assertSecurity(it.findViewById(R.id.reader))
            }
        }
        val german = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) })
        assertEquals("Markdown-Leseansicht", german.getString(R.string.html_reading_view))
        assertTrue(german.getString(R.string.html_limit).contains("256 Ki"))
    }

    private fun assertSecurity(web: WebView) {
        assertFalse(web.settings.javaScriptEnabled); assertTrue(web.settings.blockNetworkLoads)
        assertFalse(web.settings.allowContentAccess); assertFalse(web.settings.allowFileAccess)
        assertFalse(web.settings.loadsImagesAutomatically); assertFalse(web.settings.domStorageEnabled)
    }
    private fun selectReadingView(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.findViewById<TextView>(R.id.display_mode).performClick() }
        click(context.getString(R.string.html_reading_view)); ready(scenario)
    }
    private fun text(scenario: ActivityScenario<MainActivity>, id: Int): String {
        var result = ""
        scenario.onActivity { result = it.findViewById<TextView>(id).text.toString() }
        return result
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var value = false
        scenario.onActivity { value = it.findViewById<WebView>(R.id.reader)?.tag == true }; value
    }
    private fun click(value: String) {
        val node = device.wait(Until.findObject(By.text(value)), 5000)
        assertNotNull(value, node); node!!.click(); device.waitForIdle()
    }
    private fun clickDescription(value: String) {
        val node = device.wait(Until.findObject(By.desc(value)), 5000)
        assertNotNull(value, node); node!!.click(); device.waitForIdle()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(100) }
        fail("HTML reader condition timed out")
    }
}
