package de.utrost.textr

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.webkit.WebView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HtmlExportDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val sourceUri = Uri.parse("content://de.utrost.textr.test.documents/export.html")
    private fun target(id: String) = DocumentsContract.buildDocumentUri("de.utrost.textr.test.outputs", id)
    private fun fixture(operation: String, id: String) = context.contentResolver.call(Uri.parse("content://de.utrost.textr.test.documents"), "output-fixture", id, Bundle().apply { putString("operation", operation) })
    private val html = "<h1>Export Grüße 🚀</h1><p>Private article fixture.</p><ul><li>Item</li></ul><pre><code>x &lt; y</code></pre><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table><a href='https://example.invalid/'>Link</a><img alt='Figure' src='https://example.invalid/a.png'><script>NEVER_EXECUTED</script>"
    @Before @After fun reset() {
        for (name in listOf("reading", "recent_files", "open_documents", "bookmarks", "output_operation")) context.getSharedPreferences(name, 0).edit().clear().commit()
        fixture("reset", "root")
    }
    private fun open(source: String = html): ActivityScenario<MainActivity> {
        context.contentResolver.call(sourceUri, "replace", sourceUri.toString(), Bundle().apply { putString("text", source) })
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW, sourceUri, context, MainActivity::class.java).setDataAndType(sourceUri, "text/html").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    @Test fun explicitPickerExportsActualMarkdownAndReopensWithoutChangingOriginal() {
        fixture("configure", "article.md")
        val monitor = monitor(Intent().setData(target("article.md")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        try {
            open().use { scenario ->
                ready(scenario)
                var converted = ""
                scenario.onActivity {
                    converted = ViewModelProvider(it)[ReaderSession::class.java].document!!.convertedMarkdown!!
                    MainActivity::class.java.getDeclaredMethod("exportHtml").apply { isAccessible = true }.invoke(it)
                }
                click(context.getString(R.string.choose_destination))
                await { var done = false; scenario.onActivity { done = ViewModelProvider(it)[OutputSession::class.java].status.result == OutputResult.SAVED }; done }
                assertTrue(monitor.hits > 0)
                val reopened = DocumentRepository(context).read(target("article.md"), "text/markdown", CancellationSignal())
                assertEquals(converted, reopened.text); assertEquals(FileKind.MARKDOWN, reopened.kind)
                val prepared = DocumentRenderer.prepare(reopened)
                assertEquals(listOf("Export Grüße 🚀"), prepared.headings.map { it.title })
                assertTrue(prepared.preview!!.contains("<table>")); assertFalse(converted.contains("NEVER_EXECUTED"))
                val output = java.io.ByteArrayOutputStream(); DocumentRepository(context).copySource(sourceUri, output, CancellationSignal())
                assertArrayEquals(html.toByteArray(), output.toByteArray())
                click(context.getString(R.string.done))
                scenario.onActivity { assertEquals(html, ViewModelProvider(it)[ReaderSession::class.java].original!!.text) }
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun pendingSnapshotSurvivesRotationWithoutSerializingContent() {
        fixture("configure", "rotated.md")
        open().use { scenario ->
            ready(scenario)
            var markdown = ""; var first: OutputSession? = null
            scenario.onActivity {
                markdown = ViewModelProvider(it)[ReaderSession::class.java].document!!.convertedMarkdown!!
                first = ViewModelProvider(it)[OutputSession::class.java]
                first!!.begin(OutputRequest("rotated.md", "text/markdown", markdown, sourceUri.toString()))
                val state = Bundle()
                MainActivity::class.java.getDeclaredMethod("onSaveInstanceState", Bundle::class.java).apply { isAccessible = true }.invoke(it, state)
                assertTrue(state.getBoolean("outputPending")); assertEquals("rotated.md", state.getString("outputName"))
                assertFalse(state.toString().contains("Private article fixture"))
            }
            scenario.recreate(); ready(scenario)
            scenario.onActivity { val retained = ViewModelProvider(it)[OutputSession::class.java]; assertSame(first, retained); retained.pickerResult(target("rotated.md")) }
            await { first!!.status.phase == OutputPhase.COMPLETE }
            assertEquals(OutputResult.SAVED, first!!.status.result)
            assertEquals(markdown, DocumentRepository(context).read(target("rotated.md"), null, CancellationSignal()).text)
        }
    }
    @Test fun sharedHtmlCanCancelAndOverBudgetConversionExplainsUnavailableExport() {
        val cancelled = monitor(null)
        try {
            val shared = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/html")
                .putExtra(Intent.EXTRA_HTML_TEXT, html).putExtra(Intent.EXTRA_TEXT, "Fallback").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<MainActivity>(shared).use { scenario ->
                ready(scenario)
                scenario.onActivity { MainActivity::class.java.getDeclaredMethod("exportHtml").apply { isAccessible = true }.invoke(it) }
                click(context.getString(R.string.choose_destination))
                await { var idle = false; scenario.onActivity { idle = ViewModelProvider(it)[OutputSession::class.java].status.phase == OutputPhase.IDLE }; idle }
                assertTrue(cancelled.hits > 0); assertTrue(DocumentRepository(context).recentFiles().isEmpty())
                scenario.onActivity { assertEquals(html, ViewModelProvider(it)[ReaderSession::class.java].original!!.text) }
            }
        } finally { instrumentation.removeMonitor(cancelled) }
        open("x".repeat(HtmlMarkdown.MAX_CHARS + 1)).use { scenario ->
            ready(scenario)
            scenario.onActivity { assertNull(ViewModelProvider(it)[ReaderSession::class.java].document!!.convertedMarkdown) }
            clickDescription(context.getString(R.string.more_options)); click(context.getString(R.string.html_export_unavailable))
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.html_export_unavailable_help))), 5_000))
        }
        val german = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) })
        assertEquals("Markdown speichern unter…", german.getString(R.string.html_export))
        assertTrue(german.getString(R.string.html_export_help).contains("UTF-8"))
        assertTrue(german.getString(R.string.output_partial, "fixture").contains("unvollständiges"))
    }
    private fun monitor(result: Intent?): Instrumentation.ActivityMonitor {
        val filter = IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("text/markdown") }
        return instrumentation.addMonitor(filter, Instrumentation.ActivityResult(if (result == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, result), true)
    }
    private fun click(text: String) { val node = device.wait(Until.findObject(By.text(text)), 5_000); assertNotNull(text, node); node!!.click() }
    private fun clickDescription(text: String) { val node = device.wait(Until.findObject(By.desc(text)), 5_000); assertNotNull(text, node); node!!.click() }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await { var ready = false; scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }; ready }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) { if (condition()) return; Thread.sleep(50) }
        fail("HTML export did not become ready")
    }
}
