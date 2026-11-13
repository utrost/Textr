package de.utrost.textr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RecentFilesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get() = DocumentRepository(context)
    private fun uri(index: Int) = Uri.parse("content://de.utrost.textr.test.documents/recent-$index.txt")
    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    private fun granted(uri: Uri) = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    private fun provider(method: String, uri: Uri) = context.contentResolver.call(uri, method, uri.toString(), null)

    @Before @After fun clear() { context.getSharedPreferences("open_documents", 0).edit().clear().commit(); repository.setSessionReferences(emptySet()); repository.clear() }

    @Test fun readingMetadataMigratesAndDisappearsWithHistoryWithoutResurrectingEntries() {
        val target = uri(300)
        // Simulate a real 0.1.0 history entry with no reading fields.
        context.getSharedPreferences("recent_files", 0).edit().putString("entries",
            """[{"uri":"$target","name":"old.conf","openedAt":1}]""").commit()
        assertEquals(ReadingState(), repository.readingState(target))
        val state = ReadingState(DisplayMode.HIGHLIGHTED, 1200, FileKind.INI)
        repository.saveReadingState(target, state)
        repository.remember(target, "renamed.conf")
        assertEquals(state, DocumentRepository(context).readingState(target))
        for (index in 301..330) repository.remember(uri(index), "Other")
        assertEquals(ReadingState(), repository.readingState(target))
        repository.saveReadingState(target, state)
        assertFalse(repository.recentFiles().any { it.uri == target.toString() })
        val remaining = uri(330)
        repository.saveReadingState(remaining, state)
        repository.clear()
        assertEquals(ReadingState(), DocumentRepository(context).readingState(remaining))
    }

    @Test fun historyLimitDeduplicationEvictionAndGrantCleanup() {
        val first = uri(0)
        provider("grant", first)
        repository.retainReadAccess(first, flags)
        assertTrue(granted(first))
        for (index in 0..30) repository.remember(uri(index), "File $index")
        assertEquals(30, repository.recentFiles().size)
        assertFalse(granted(first))
        repository.remember(uri(10), "Renamed")
        assertEquals(30, repository.recentFiles().size)
        assertEquals("Renamed", repository.recentFiles().first().name)
        assertEquals(1, repository.recentFiles().count { it.uri == uri(10).toString() })
        val persisted = JSONArray(context.getSharedPreferences("recent_files", Context.MODE_PRIVATE).getString("entries", "[]"))
        for (index in 0 until persisted.length()) {
            assertEquals(setOf("uri", "name", "openedAt"), persisted.getJSONObject(index).keys().asSequence().toSet())
        }
    }

    @Test fun retainedReferencesSurviveRepositoryRestartAndReleaseOnlyTheirOwnGrants() {
        val first = uri(100)
        val second = uri(101)
        for (uri in listOf(first, second)) {
            provider("grant", uri)
            repository.retainReadAccess(uri, flags)
            repository.remember(uri, uri.lastPathSegment!!)
            assertTrue(granted(uri))
        }
        val restarted = DocumentRepository(context)
        assertEquals(2, restarted.recentFiles().size)
        assertTrue(restarted.read(first, null, CancellationSignal()).text.contains("Textr fixture"))
        restarted.remove(first.toString())
        assertFalse(granted(first))
        assertTrue(granted(second))
        assertTrue(restarted.read(first, null, CancellationSignal()).text.contains("Textr fixture"))
        restarted.clear()
        assertFalse(granted(second))
        assertTrue(restarted.read(second, null, CancellationSignal()).text.contains("Textr fixture"))
    }

    @Test fun temporaryAndRevokedAccessDoNotPretendToBePersistent() {
        val temporary = uri(200)
        provider("temporary", temporary)
        repository.retainReadAccess(temporary, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertFalse(granted(temporary))
        val revoked = uri(201)
        provider("grant", revoked)
        repository.retainReadAccess(revoked, flags)
        repository.remember(revoked, "Revoked")
        provider("revoke", revoked)
        assertFalse(granted(revoked))
        try {
            repository.read(revoked, null, CancellationSignal())
            fail("Revoked document must fail")
        } catch (_: SecurityException) { }
        assertEquals("Revoked", repository.recentFiles().single().name)
    }

    @Test fun slowPipeCanBeCanceledBeforeProviderFinishesWriting() {
        val signal = CancellationSignal()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<Boolean> {
                try {
                    repository.read(Uri.parse("content://de.utrost.textr.test.documents/slow.txt"), null, signal)
                    false
                } catch (_: android.os.OperationCanceledException) { true }
            }
            Thread.sleep(200)
            signal.cancel()
            assertTrue(future.get(1, TimeUnit.SECONDS))
        } finally { executor.shutdownNow() }
    }

    @Test fun rejectedDocumentsDoNotRetainGrantsOrSuccessfulHistory() {
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        for (name in listOf("binary.txt", "oversized.txt")) {
            val target = Uri.parse("content://de.utrost.textr.test.documents/$name")
            provider("grant", target)
            val intent = Intent(Intent.ACTION_VIEW, target, context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or flags)
            androidx.test.core.app.ActivityScenario.launch<MainActivity>(intent).use {
                assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(
                    androidx.test.uiautomator.By.text(context.getString(R.string.cannot_open))), 10000))
                assertFalse(granted(target))
                assertFalse(repository.recentFiles().any { it.uri == target.toString() })
            }
        }
    }
}
