package de.utrost.textr

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.CancellationSignal
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineIntegrityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val uri = Uri.parse("content://de.utrost.textr.test.documents/hostile.md")

    @Test fun hostileDocumentCannotLoadResourcesOrLaunchUntappedLinks() {
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val repository = DocumentRepository(context)
        val before = repository.read(uri, null, CancellationSignal()).text
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitReader(scenario)
            val filter = IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("https"); addCategory(Intent.CATEGORY_BROWSABLE) }
            val monitor = instrumentation.addMonitor(filter, Instrumentation.ActivityResult(0, null), true)
            try {
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
                assertEquals("No automatic external launch", 0, monitor.hits)
                val link = device.wait(Until.findObject(By.text("Open external")), 5000)
                assertNotNull("Rendered link is reachable", link)
                link!!.click()
                val deadline = System.nanoTime() + 5_000_000_000L
                while (monitor.hits == 0 && System.nanoTime() < deadline) Thread.sleep(50)
                assertTrue("A deliberate tap opens the external handler", instrumentation.checkMonitorHit(monitor, 1))
            } finally { instrumentation.removeMonitor(monitor) }
        }
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
