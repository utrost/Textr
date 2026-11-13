package de.utrost.textr

import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdditionalFormatsDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun replace(name: String, text: String) = context.contentResolver.call(uri(name), "replace", uri(name).toString(), Bundle().apply { putString("text", text) })
    private val calendar = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nSUMMARY:Board meeting\r\nDTSTART;TZID=Europe/Berlin:20261113T100000\r\nDTEND;TZID=Europe/Berlin:20261113T110000\r\nLOCATION:Main office\r\nDESCRIPTION:Review the roadmap\\nBring notes\r\nRRULE:FREQ=WEEKLY;COUNT=4\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
    private data class Example(val name: String, val mime: String, val kind: FileKind, val text: String)
    private val examples get() = listOf(
        Example("Example.JAVA", "application/x-java-source", FileKind.JAVA, "public class Example { /* read only */ String name = \"Textr\"; }"),
        Example("example.mjs", "application/javascript", FileKind.JAVASCRIPT, "// source only\nexport const message = `Hello Textr`;"),
        Example("theme.CSS", "text/css", FileKind.CSS, ".reader { color: #245c50; padding: 20px; }"),
        Example("page.html", "application/xhtml+xml", FileKind.HTML, "<script>document.body.textContent='EXECUTED'</script><img src='https://example.com/tracker'>"),
        Example("example.kt", "text/x-kotlin", FileKind.KOTLIN, "val message = \"Textr\""),
        Example("example.py", "application/x-python", FileKind.PYTHON, "def greet():\n    return 'Textr'"),
        Example("example.sh", "application/x-sh", FileKind.SHELL, "#!/bin/sh\necho 'Textr'"),
        Example("example.sql", "application/sql", FileKind.SQL, "SELECT name FROM documents;"),
        Example("example.patch", "text/x-patch", FileKind.DIFF, "--- a\n+++ b\n-old\n+new"),
        Example("meeting.ICS", "text/calendar", FileKind.ICALENDAR, calendar)
    )

    @Before fun reset() {
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        DocumentRepository(context).setSessionReferences(emptySet())
        DocumentRepository(context).clear()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
    }

    @Test fun viewAndSendResolveNewFormatsAndRenderWithoutExecutingContent() {
        for (example in examples) {
            replace(example.name, example.text)
            for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_SEND)) {
                val intent = Intent(action).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (action == Intent.ACTION_VIEW) intent.setDataAndType(uri(example.name), example.mime)
                else intent.setType(example.mime).putExtra(Intent.EXTRA_STREAM, uri(example.name))
                assertTrue("No handler for $action ${example.mime}", context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty())
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    ready(scenario)
                    scenario.onActivity {
                        val doc = ViewModelProvider(it)[ReaderSession::class.java].document!!
                        assertEquals(example.kind, doc.source.kind); assertEquals(example.text, doc.source.text)
                        if (example.kind == FileKind.ICALENDAR) assertNotNull(doc.preview) else assertNull(doc.preview)
                        assertEquals(if (example.kind == FileKind.ICALENDAR) "Preview" else "Highlighted", it.findViewById<TextView>(R.id.display_mode).text.toString())
                        val web = it.findViewById<WebView>(R.id.reader)
                        assertFalse(web.settings.javaScriptEnabled); assertTrue(web.settings.blockNetworkLoads)
                        assertFalse(web.settings.allowContentAccess); assertFalse(web.settings.allowFileAccess)
                    }
                }
            }
        }
        for (mime in listOf("application/x-javascript", "application/ecmascript", "application/x-shellscript")) {
            assertTrue(context.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW).setPackage(context.packageName)
                .setDataAndType(uri("example"), mime), PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty())
        }
    }

    @Test fun calendarPreviewSearchSourceAndManualFormatSurviveRecreation() {
        replace("calendar.conf", calendar)
        val intent = Intent(Intent.ACTION_VIEW, uri("calendar.conf"), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            ready(scenario)
            clickDescription("More options"); click("Document info")
            val treatAs = device.wait(Until.findObject(By.res("android", "button3")), 5000)
            assertNotNull("Document info format action", treatAs)
            treatAs!!.click(); device.waitForIdle()
            UiScrollable(UiSelector().className("android.widget.ListView")).scrollIntoView(UiSelector().text("iCalendar"))
            click("iCalendar")
            await {
                var preview = false
                scenario.onActivity { preview = ViewModelProvider(it)[ReaderSession::class.java].document?.source?.kind == FileKind.ICALENDAR && it.findViewById<WebView>(R.id.reader)?.tag == true }
                preview
            }
            clickDescription("Find")
            scenario.onActivity { it.findViewById<EditText>(R.id.search_query).setText("Board meeting") }
            await { var count = ""; scenario.onActivity { count = it.findViewById<TextView>(R.id.search_count).text.toString() }; count == "1 / 1" }
            clickDescription("Close search")
            click("Preview"); click("Plain text"); ready(scenario)
            scenario.onActivity { assertEquals(calendar, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text) }
            scenario.recreate(); ready(scenario)
            scenario.onActivity {
                assertEquals("Plain text", it.findViewById<TextView>(R.id.display_mode).text.toString())
                assertEquals(FileKind.ICALENDAR, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.kind)
            }
        }
    }

    @Test fun newFormatSharesPreserveBytesAndUsefulMimeTypes() {
        val expected = mapOf(FileKind.JAVA to "text/x-java-source", FileKind.JAVASCRIPT to "text/javascript", FileKind.CSS to "text/css",
            FileKind.HTML to "text/html", FileKind.KOTLIN to "text/x-kotlin", FileKind.PYTHON to "text/x-python",
            FileKind.SHELL to "text/x-shellscript", FileKind.SQL to "application/sql", FileKind.DIFF to "text/x-diff", FileKind.ICALENDAR to "text/calendar")
        for (example in examples) {
            replace(example.name, example.text)
            val source = DocumentRepository(context).read(uri(example.name), null, CancellationSignal())
            val intent = ShareFiles(context).prepare(uri(example.name), source, CancellationSignal())
            assertEquals(expected[example.kind], intent.type)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            val copy = intent.clipData!!.getItemAt(0).uri
            assertArrayEquals(example.text.toByteArray(), context.contentResolver.openInputStream(copy)!!.use { it.readBytes() })
        }
    }

    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var ready = false; scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }; ready
    }
    private fun click(text: String) {
        val node = device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(text), java.util.regex.Pattern.CASE_INSENSITIVE))), 5000)
        assertNotNull(text, node); node!!.click(); device.waitForIdle()
    }
    private fun clickDescription(text: String) { val node = device.wait(Until.findObject(By.desc(text)), 5000); assertNotNull(text, node); node!!.click(); device.waitForIdle() }
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < until) { if (condition()) return; Thread.sleep(100) }
        fail("Additional format condition timed out")
    }
}
