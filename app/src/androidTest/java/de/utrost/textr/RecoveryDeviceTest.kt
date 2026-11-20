package de.utrost.textr

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun intent(name: String) = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    private fun provider(action: String, name: String) { context.contentResolver.call(uri(name), action, uri(name).toString(), null) }
    @Before fun reset() {
        context.getSharedPreferences("reading", 0).edit().clear().commit()
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        Bookmarks(context).clear()
        DocumentRepository(context).clear()
    }
    @Test fun deniedAccessExplainsReselectionAndRetryUsesRestoredGrant() {
        provider("revoke", "retry-recovery.txt")
        try {
            ActivityScenario.launch<MainActivity>(intent("retry-recovery.txt")).use { scenario ->
                text(scenario, R.string.access_denied)
                scenario.recreate(); text(scenario, R.string.access_denied)
                provider("grant", "retry-recovery.txt")
                click(scenario, R.string.retry_open); ready(scenario, "retry-recovery.txt")
            }
        } finally { provider("grant", "retry-recovery.txt") }
    }
    @Test fun failedOpenCanReturnToOriginalSnapshotEvenWithoutItsGrant() {
        provider("grant", "long.ini")
        ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
            ready(scenario, "long.ini")
            var original = ""
            scenario.onActivity {
                original = ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text
                it.findViewById<WebView>(R.id.reader).scrollTo(0, 600)
            }
            choose(scenario, "missing.txt")
            text(scenario, R.string.file_unavailable)
            scenario.recreate(); text(scenario, R.string.file_unavailable)
            provider("revoke", "long.ini")
            try {
                click(scenario, R.string.return_to_document); ready(scenario, "long.ini")
                scenario.onActivity {
                    assertEquals(original, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text)
                    assertEquals(600, it.findViewById<WebView>(R.id.reader).scrollY)
                }
            } finally { provider("grant", "long.ini") }
        }
    }
    @Test fun cancellingSlowReplacementReturnsToReaderAndRejectsLateCompletion() {
        ActivityScenario.launch<MainActivity>(intent("copy.txt")).use { scenario ->
            ready(scenario, "copy.txt")
            choose(scenario, "slow.txt")
            click(scenario, R.string.cancel_loading); ready(scenario, "copy.txt")
            Thread.sleep(5200) // The fixture completes late; it must not replace the restored snapshot.
            ready(scenario, "copy.txt")
            assertFalse(DocumentRepository(context).recentFiles().any { it.uri == uri("slow.txt").toString() })
        }
    }
    @Test fun pickerCancellationAndDifferentUriKeepOriginalMetadataAndDraft() {
        val repository = DocumentRepository(context)
        repository.remember(uri("missing.txt"), "missing.txt")
        repository.saveReadingState(uri("missing.txt"), ReadingState(DisplayMode.PLAIN, 600, FileKind.INI))
        Bookmarks(context).add(uri("missing.txt").toString(), "missing.txt", "Kept", 0.5, "fixture", ReadingState())
        val before = Bookmarks(context).entries()
        val store = EditDraftStore(context)
        val existing = store.read() ?: EditDraft.new(TextDocument("kept.txt", "Private kept draft", FileKind.TEXT, 18, "UTF-8"), uri("missing.txt").toString())
        val created = if (!store.exists()) store.create(existing) else false
        try { ActivityScenario.launch<MainActivity>(intent("missing.txt")).use { scenario ->
            text(scenario, R.string.file_unavailable)
            var monitor = monitor(null)
            try {
                click(scenario, R.string.open_again); await { monitor.hits > 0 }
                text(scenario, R.string.file_unavailable)
                assertEquals(before, Bookmarks(context).entries())
                instrumentation.removeMonitor(monitor)
                monitor = monitor(Intent().setData(uri("sample.json")))
                click(scenario, R.string.open_again); ready(scenario, "sample.json")
                scenario.onActivity {
                    assertEquals(FileKind.JSON, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.kind)
                    assertEquals(0, it.findViewById<WebView>(R.id.reader).scrollY)
                }
                assertEquals(before, Bookmarks(context).entries())
                assertEquals(existing, store.read())
                assertEquals(600, repository.readingState(uri("missing.txt")).scroll)
            } finally { instrumentation.removeMonitor(monitor) }
        } } finally { if (created) store.delete(existing.id) }
    }
    private fun choose(scenario: ActivityScenario<MainActivity>, name: String) {
        val monitor = monitor(Intent().setData(uri(name)))
        try {
            scenario.onActivity {
                MainActivity::class.java.getDeclaredMethod("openPicker", Bookmark::class.java).apply { isAccessible = true }.invoke(it, null)
            }
            await { monitor.hits > 0 }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    private fun monitor(result: Intent?): Instrumentation.ActivityMonitor = instrumentation.addMonitor(
        IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") },
        Instrumentation.ActivityResult(if (result == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, result), true)
    private fun ready(scenario: ActivityScenario<MainActivity>, name: String) = await {
        var ready = false
        scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true && ViewModelProvider(it)[ReaderSession::class.java].document?.source?.name == name }
        ready
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun text(scenario: ActivityScenario<MainActivity>, label: Int) = await {
        var found = false
        scenario.onActivity { found = views(it.window.decorView).filterIsInstance<TextView>().any { v -> v.text.toString() == context.getString(label) } }
        found
    }
    private fun click(scenario: ActivityScenario<MainActivity>, label: Int) {
        text(scenario, label)
        scenario.onActivity { views(it.window.decorView).filterIsInstance<Button>().first { v -> v.text.toString() == context.getString(label) }.performClick() }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < deadline) { if (condition()) return; Thread.sleep(50) }
        fail("Recovery did not reach the expected state")
    }
}
