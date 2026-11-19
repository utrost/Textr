package de.utrost.textr

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.view.View
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.Spinner
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoadmapFormatsDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repository get() = DocumentRepository(context)
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    @Before fun reset() {
        for (name in listOf("recent_files", "open_documents", "reading", "bookmarks")) context.getSharedPreferences(name, 0).edit().clear().commit()
    }
    private fun open(name: String, source: String, mime: String = "text/plain"): ActivityScenario<MainActivity> {
        context.contentResolver.call(uri(name), "replace", uri(name).toString(), Bundle().apply { putString("text", source) })
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java).setDataAndType(uri(name), mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    @Test fun formatsOpenReadOnlyWithOriginalBytesSearchAndManualPlainOverride() {
        val samples = listOf(".env.local" to "export VALUE='<script>🚀</script>'\r\n# fixture\r\n",
            "sample.properties" to "message=Grüße\\u0041\\\r\n continued\r\n",
            "Dockerfile.dev" to "FROM alpine\nRUN echo '<script>🚀</script>'\n",
            "source.ts" to "interface Item { value: string }; const x = `<script>🚀</script>`;\n",
            "component.tsx" to "const node = <script src='https://example.invalid/'>{`🚀`}</script>;\n")
        for ((name, source) in samples) open(name, source).use { scenario ->
            ready(scenario)
            scenario.onActivity {
                val prepared = ViewModelProvider(it)[ReaderSession::class.java].document!!
                assertEquals(source, prepared.source.text)
                assertTrue(DisplayMode.HIGHLIGHTED in prepared.modes)
                assertEquals(DisplayMode.HIGHLIGHTED, prepared.defaultMode)
                assertNull(prepared.preview)
                val web = it.findViewById<WebView>(R.id.reader)
                assertFalse(web.settings.javaScriptEnabled); assertFalse(web.settings.allowFileAccess)
                assertTrue(web.settings.blockNetworkLoads)
            }
            val searchDone = java.util.concurrent.CountDownLatch(1)
            val matches = java.util.concurrent.atomic.AtomicInteger()
            scenario.onActivity {
                it.findViewById<WebView>(R.id.reader).apply {
                    setFindListener { _, count, finished -> if (finished) { matches.set(count); searchDone.countDown() } }
                    findAllAsync(if (source.contains("script")) "script" else "message")
                }
            }
            assertTrue(searchDone.await(10, java.util.concurrent.TimeUnit.SECONDS)); assertTrue(matches.get() > 0)
            if (name == ".env.local") {
                scenario.onActivity { MainActivity::class.java.getDeclaredMethod("showFormats").apply { isAccessible = true }.invoke(it) }
                await { var visible = false; scenario.onActivity { visible = WindowInspector.getGlobalWindowViews().any { root -> findList(root)?.isLaidOut == true } }; visible }
                scenario.onActivity {
                    val list = WindowInspector.getGlobalWindowViews().firstNotNullOf(::findList)
                    list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1))
                }
                ready(scenario)
                assertEquals(FileKind.TEXT, repository.readingState(uri(name)).format)
            }
            val output = java.io.ByteArrayOutputStream()
            repository.copySource(uri(name), output, CancellationSignal())
            assertArrayEquals(source.toByteArray(), output.toByteArray())
            scenario.recreate(); ready(scenario)
            scenario.onActivity { assertEquals(source, ViewModelProvider(it)[ReaderSession::class.java].original!!.text) }
        }
        for (mime in listOf("application/typescript", "application/x-java-properties", "application/tsv")) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse("content://example.invalid/file")).setType(mime)
            assertTrue(context.packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY).any { it.activityInfo.packageName == context.packageName })
        }
    }
    @Test fun tsvSourceFirstDefaultsTabAndRetainsExplicitCommaHeaderAcrossReopenAndRecreation() {
        val source = "\"Name\tlabel\"\tValue\r\n\"First\r\nline\"\t42\r\n"
        open("sample.tsv", source).use { scenario ->
            ready(scenario)
            assertEquals(CsvOptions('\t', configured = false), repository.readingState(uri("sample.tsv")).csv)
            scenario.onActivity {
                val doc = ViewModelProvider(it)[ReaderSession::class.java].document!!
                assertTrue(doc.preview!!.contains("<td>42</td>")); assertEquals(DisplayMode.PLAIN, doc.defaultMode)
            }
            scenario.onActivity { MainActivity::class.java.getDeclaredMethod("showCsvOptions").apply { isAccessible = true }.invoke(it) }
            await { var ready = false; scenario.onActivity { ready = WindowInspector.getGlobalWindowViews().any { root -> root.findViewById<View>(R.id.csv_delimiter)?.isLaidOut == true } }; ready }
            scenario.onActivity {
                val root = WindowInspector.getGlobalWindowViews().first { root -> root.findViewById<View>(R.id.csv_delimiter) != null }
                root.findViewById<Spinner>(R.id.csv_delimiter).setSelection(0)
                root.findViewById<CheckBox>(R.id.csv_header).isChecked = true
                root.findViewById<View>(android.R.id.button1).performClick()
            }
            ready(scenario)
            val explicit = CsvOptions(',', true, true)
            assertEquals(explicit, repository.readingState(uri("sample.tsv")).csv)
            scenario.recreate(); ready(scenario)
            assertEquals(explicit, OpenDocuments(context).entries.single().reading.csv)
            assertEquals(source, repository.read(uri("sample.tsv"), null, CancellationSignal()).text)
        }
        open("sample.tsv", source).use { scenario -> ready(scenario); assertEquals(CsvOptions(',', true, true), repository.readingState(uri("sample.tsv")).csv) }
    }
    @Test fun csvOptionMigrationKeepsKnownManualChoicesAndDefaultsUnconfiguredTsv() {
        assertEquals(CsvOptions(), csvOptions(JSONObject()))
        assertEquals(CsvOptions(';', false, true), csvOptions(JSONObject("""{"delimiter":";"}""")))
        assertEquals(CsvOptions(',', false, true), csvOptions(JSONObject("""{"delimiter":",","format":"CSV"}""")))
        assertEquals(CsvOptions(), csvOptions(JSONObject("""{"delimiter":",","mode":"PLAIN"}""")))
        assertEquals(CsvOptions('\t', false, false), csvOptions(JSONObject("""{"delimiter":"\t","csvConfigured":false}""")))
    }
    private fun findList(view: View): android.widget.ListView? {
        if (view is android.widget.ListView) return view
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) findList(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await { var ready = false; scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }; ready }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) { if (condition()) return; Thread.sleep(50) }
        fail("Reader/control did not become ready")
    }
}
