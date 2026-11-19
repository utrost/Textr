package de.utrost.textr

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.view.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineIntegrityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val uri = Uri.parse("content://de.utrost.textr.test.documents/hostile.md")

    @Before fun resetReaderMetadata() {
        for (name in listOf("reading", "recent_files", "open_documents", "bookmarks"))
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun hostileDocumentCannotLoadResourcesOrLaunchUntappedLinks() {
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val repository = DocumentRepository(context)
        val before = repository.read(uri, null, CancellationSignal()).text
        val launches = CopyOnWriteArrayList<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW || !intent.hasCategory(Intent.CATEGORY_BROWSABLE)) return null
                launches.add(Intent(intent))
                return Instrumentation.ActivityResult(0, null)
            }
        }
        // Monitor the complete launch/render lifecycle, not only the loaded page.
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                awaitReader(scenario)
                scenario.onActivity { activity ->
                    val web = activity.findViewById<WebView>(R.id.reader)
                    assertFalse(web.settings.javaScriptEnabled)
                    assertFalse(web.settings.safeBrowsingEnabled)
                    assertFalse(web.settings.allowFileAccess)
                    assertFalse(web.settings.allowContentAccess)
                    assertFalse(web.settings.domStorageEnabled)
                    assertFalse(web.settings.loadsImagesAutomatically)
                    assertTrue(web.settings.blockNetworkLoads)
                    for (url in listOf("https://example.com/remote", "http://example.com/remote", "file:///data/local/tmp/private", "content://private/file", "data:text/html,test")) {
                        val request = Request(Uri.parse(url), false, false)
                        assertEquals(0, web.webViewClient.shouldInterceptRequest(web, request)!!.data.use { it.readBytes().size })
                        assertTrue(web.webViewClient.shouldOverrideUrlLoading(web, request))
                    }
                }
                Thread.sleep(300)
                assertEquals("No automatic external launch", 0, launches.size)
                // This is a gesture/security test, independent of WebView 133's
                // intermittently empty Android accessibility subtree (#59).
                // Tab and Enter exercise a real browser user activation without JS.
                var x = 0; var y = 0
                scenario.onActivity { activity ->
                    assertEquals("Preview", activity.findViewById<android.widget.Button>(R.id.display_mode).text.toString())
                    val web = activity.findViewById<WebView>(R.id.reader)
                    web.pageUp(true)
                    val location = IntArray(2); web.getLocationOnScreen(location)
                    x = location[0] + web.width / 2
                    y = location[1] + web.height - (24 * activity.resources.displayMetrics.density).toInt()
                }
                // A blank-area touch clears prior DOM focus and establishes touch
                // mode before Tab enters the document's first link.
                device.click(x, y)
                assertEquals("A blank-area tap cannot launch a link", 0, launches.size)
                device.pressKeyCode(KeyEvent.KEYCODE_TAB)
                assertEquals("Focus navigation alone cannot launch a link", 0, launches.size)
                device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
                val deadline = System.nanoTime() + 5_000_000_000L
                while (launches.isEmpty() && System.nanoTime() < deadline) Thread.sleep(50)
                assertEquals("One deliberate keyboard activation opens the external handler", 1, launches.size)
                assertEquals("https://example.com/textr-validation", launches.single().data.toString())
            }
        } finally { instrumentation.removeMonitor(monitor) }
        repository.remove(uri.toString())
        repository.clear()
        assertEquals("Viewing and history operations must preserve source bytes", before, repository.read(uri, null, CancellationSignal()).text)
    }

    @Test fun sharedContentIsNotWrittenIntoAppFilesOrPreferences() {
        val marker = "TEXTR_PRIVATE_CONTENT_5f14c0e3"
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, marker).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitReader(scenario)
            scenario.recreate()
            awaitReader(scenario)
        }
        // Include WebView/cache and preferences, not just the app's own files directory.
        val markerBytes = marker.toByteArray()
        context.dataDir.walkTopDown().filter { it.isFile && it.length() < 20 * 1024 * 1024 }.forEach { file ->
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@forEach
            assertFalse("Document persisted in ${file.relativeTo(context.dataDir)}", bytes.toString(Charsets.ISO_8859_1).contains(markerBytes.toString(Charsets.ISO_8859_1)))
        }
        assertFalse(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        val metadata = context.packageManager.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA).metaData
        assertTrue(metadata.getBoolean("android.webkit.WebView.MetricsOptOut"))
    }

    private fun awaitReader(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < deadline) {
            var ready = false
            scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Reader did not load")
    }
    private class Request(private val uri: Uri, private val gesture: Boolean, private val main: Boolean) : WebResourceRequest {
        override fun getUrl() = uri
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = gesture
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }
}
