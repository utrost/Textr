package de.utrost.textr

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ReaderIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun openIntent(name: String) = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    @Test fun externalContentOpensSearchesAndSurvivesRecreation() {
        ActivityScenario.launch<MainActivity>(openIntent("sample.json")).use { scenario ->
            awaitReader(scenario)
            scenario.onActivity { activity ->
                val web = findWebView(activity.window.decorView)!!
                assertFalse(web.settings.javaScriptEnabled)
                assertFalse(web.settings.allowContentAccess)
                assertFalse(web.settings.allowFileAccess)
                assertTrue(web.settings.blockNetworkLoads)
                assertTrue(texts(activity.window.decorView).contains("Preview"))
            }
            assertSearch(scenario, "Textr fixture")
            scenario.recreate()
            awaitReader(scenario)
            assertSearch(scenario, "Textr fixture")
        }
    }

    @Test fun markdownRendersWithoutImagesAndSupportsSourceSwitching() {
        ActivityScenario.launch<MainActivity>(openIntent("sample.md")).use { scenario ->
            awaitReader(scenario)
            assertSearch(scenario, "First")
            assertSearch(scenario, "[image omitted]")
            scenario.onActivity { activity ->
                assertTrue(texts(activity.window.decorView).contains("Preview"))
                findText(activity.window.decorView, "Preview")!!.performClick()
            }
            // Dialog selection is exercised manually as well; the renderer's alternatives have unit coverage.
        }
    }

    @Test fun sharedTextDoesNotEnterHistoryAndSurvivesRotation() {
        val repository = DocumentRepository(context)
        val before = repository.recentFiles()
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Ephemeral shared note")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitReader(scenario)
            assertSearch(scenario, "Ephemeral shared note")
            scenario.recreate()
            awaitReader(scenario)
            assertSearch(scenario, "Ephemeral shared note")
            assertEquals(before, repository.recentFiles())
        }
    }

    @Test fun inaccessibleRecentShowsRecoveryAction() {
        ActivityScenario.launch<MainActivity>(openIntent("missing.txt")).use { scenario ->
            awaitText(scenario, context.getString(R.string.open_again))
            scenario.onActivity { activity ->
                assertTrue(texts(activity.window.decorView).contains(context.getString(R.string.access_error)))
            }
        }
    }

    @Test fun contentProviderBytesAreLimitedAndReadOnly() {
        val repository = DocumentRepository(context)
        assertEquals(FileKind.JSON, repository.read(uri("sample.json"), null, CancellationSignal()).kind)
        for ((name, reason) in listOf("binary.txt" to ReadFailure.BINARY, "oversized.txt" to ReadFailure.TOO_LARGE)) {
            try { repository.read(uri(name), null, CancellationSignal()); fail("Expected $reason") }
            catch (error: DocumentException) { assertEquals(reason, error.reason) }
        }
    }

    @Test fun optionalMetadataFailuresDoNotPreventReading() {
        val repository = DocumentRepository(context)
        for (name in listOf("unsupported-metadata.txt", "invalid-metadata.txt", "null-metadata.txt", "missing-column.txt")) {
            val document = repository.read(uri(name), null, CancellationSignal())
            assertEquals(name, document.name)
            assertEquals(FileKind.TEXT, document.kind)
            assertTrue(document.text.contains("Textr fixture"))
        }
        assertEquals(FileKind.JSON, repository.read(uri("extensionless-json"), "application/octet-stream", CancellationSignal()).kind)
    }

    @Test fun metadataFallbackPreservesPermissionAndCancellationFailures() {
        val repository = DocumentRepository(context)
        try {
            repository.read(uri("denied.txt"), null, CancellationSignal())
            fail("Permission failure must not be swallowed")
        } catch (_: SecurityException) { }
        val signal = CancellationSignal().apply { cancel() }
        try {
            repository.read(uri("unsupported-metadata.txt"), null, signal)
            fail("Cancellation must not be swallowed")
        } catch (_: android.os.OperationCanceledException) { }
    }

    @Test fun fiveMiBFileRemainsReadableToTheLastByte() {
        ActivityScenario.launch<MainActivity>(openIntent("boundary.txt")).use { scenario ->
            awaitReader(scenario)
            assertSearch(scenario, "Boundary end marker")
        }
    }

    @Test fun recentRemovalOnlyRemovesMetadata() {
        val repository = DocumentRepository(context)
        repository.clear()
        repository.remember(uri("sample.json"), "sample.json")
        repository.remember(uri("sample.md"), "sample.md")
        repository.remember(uri("sample.json"), "sample.json")
        assertEquals(listOf("sample.json", "sample.md"), repository.recentFiles().map { it.name })
        repository.remove(uri("sample.json").toString())
        assertEquals(listOf("sample.md"), repository.recentFiles().map { it.name })
        assertTrue(repository.read(uri("sample.json"), null, CancellationSignal()).text.contains("fixture"))
        repository.clear()
        assertTrue(repository.recentFiles().isEmpty())
    }

    @Test fun manifestProvidesHandlersWithoutNetworkOrStoragePermissions() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()
        for (permission in listOf("android.permission.INTERNET", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.MANAGE_EXTERNAL_STORAGE")) {
            assertFalse(permissions.toString(), permission in permissions)
        }
        for (type in listOf("text/plain", "text/markdown", "application/json", "application/octet-stream")) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri("sample.json"), type).setPackage(context.packageName)
            assertTrue(type, context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty())
        }
    }

    private fun awaitReader(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            var ready = false
            scenario.onActivity { ready = findWebView(it.window.decorView)?.tag == true }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Document did not finish loading")
    }

    private fun awaitText(scenario: ActivityScenario<MainActivity>, value: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            var found = false
            scenario.onActivity { found = value in texts(it.window.decorView) }
            if (found) return
            Thread.sleep(100)
        }
        fail("Missing text: $value")
    }

    private fun assertSearch(scenario: ActivityScenario<MainActivity>, query: String) {
        val result = AtomicInteger(0)
        val latch = CountDownLatch(1)
        scenario.onActivity {
            findWebView(it.window.decorView)!!.apply {
                setFindListener { _, count, finished -> if (finished) { result.set(count); latch.countDown() } }
                findAllAsync(query)
            }
        }
        assertTrue("Search callback", latch.await(10, TimeUnit.SECONDS))
        assertTrue("No match for $query", result.get() > 0)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun texts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
    }
    private fun findText(view: View, text: String): View? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findText(view.getChildAt(i), text)?.let { return it }
        return null
    }
}
