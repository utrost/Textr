package de.utrost.textr

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ReaderUxTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val repository get() = DocumentRepository(context)
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun intent(name: String) = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Before fun reset() {
        repository.clear()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
    }

    @Test fun menusAndInstalledVersionAreReachableFromHomeAndReader() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertFalse(device.hasObject(By.desc("Back to files")))
            menu("About Textr")
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            assertNotNull(device.wait(Until.findObject(By.textContains("Textr $version")), 5000))
            assertTrue(device.hasObject(By.text("Source code")))
            assertTrue(device.hasObject(By.text("Open-source licenses")))
            click("Done")
        }
        ActivityScenario.launch<MainActivity>(intent("copy.txt")).use { scenario ->
            ready(scenario)
            scenario.onActivity { assertFalse(it.findViewById<TextView>(R.id.display_mode).isShown) }
            menu("About Textr")
            click("Done")
            ready(scenario)
            menu("Reading settings")
            click("Cancel")
            ready(scenario)
        }
    }

    @Test fun recentReopenAndNewActivityRestorePositionAndMode() {
        var position = 0
        ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
            ready(scenario)
            click("Highlighted"); click("Plain text"); ready(scenario)
            scenario.onActivity {
                it.findViewById<WebView>(R.id.reader).scrollTo(0, 1200)
                position = it.findViewById<WebView>(R.id.reader).scrollY
            }
            assertTrue(position > 0)
            click(By.desc("Back to files"))
            click("long.ini")
            ready(scenario)
            assertPosition(scenario, position)
        }
        ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
            ready(scenario)
            assertPosition(scenario, position)
            scenario.onActivity { assertEquals("Plain text", it.findViewById<TextView>(R.id.display_mode).text.toString()) }
        }
        repository.remove(uri("long.ini").toString())
        assertEquals(ReadingState(), repository.readingState(uri("long.ini")))
        ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
            ready(scenario)
            assertPosition(scenario, 0)
            scenario.onActivity { assertEquals("Highlighted", it.findViewById<TextView>(R.id.display_mode).text.toString()) }
        }
    }

    @Test fun queuedModeSelectionFromDestroyedActivityIsIgnored() {
        ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
            ready(scenario)
            lateinit var oldActivity: MainActivity
            lateinit var oldList: ListView
            lateinit var queuedClick: AdapterView.OnItemClickListener
            fun findList(view: View): ListView? {
                if (view is ListView) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) {
                    findList(view.getChildAt(index))?.let { return it }
                }
                return null
            }
            scenario.onActivity {
                oldActivity = it
                it.findViewById<View>(R.id.display_mode).performClick()
                oldList = WindowInspector.getGlobalWindowViews().firstNotNullOf(::findList)
                queuedClick = oldList.onItemClickListener!!
            }
            scenario.recreate()
            ready(scenario)
            scenario.onActivity {
                assertTrue(oldActivity.isDestroyed)
                // Android can deliver an already queued list click after activity teardown.
                queuedClick.onItemClick(oldList, View(it), 0, oldList.adapter.getItemId(0))
                assertEquals("Highlighted", it.findViewById<TextView>(R.id.display_mode).text.toString())
                assertEquals(true, it.findViewById<WebView>(R.id.reader).tag)
            }
        }
    }

    @Test fun overridePersistsAndAutomaticRestoresDetectedFormatWithoutChangingBytes() {
        val before = raw("override.conf")
        ActivityScenario.launch<MainActivity>(intent("override.conf")).use { scenario ->
            ready(scenario)
            override("JSON")
            ready(scenario)
            assertEquals(FileKind.JSON, repository.readingState(uri("override.conf")).format)
            scenario.onActivity {
                val session = androidx.lifecycle.ViewModelProvider(it)[ReaderSession::class.java]
                assertEquals(FileKind.TEXT, session.original!!.kind)
                assertEquals(FileKind.JSON, session.document!!.source.kind)
                assertEquals("Preview", it.findViewById<TextView>(R.id.display_mode).text.toString())
            }
        }
        ActivityScenario.launch<MainActivity>(intent("override.conf")).use { scenario ->
            ready(scenario)
            scenario.onActivity { assertEquals("Preview", it.findViewById<TextView>(R.id.display_mode).text.toString()) }
            override("Automatic")
            ready(scenario)
            scenario.onActivity { assertFalse(it.findViewById<TextView>(R.id.display_mode).isShown) }
            assertNull(repository.readingState(uri("override.conf")).format)
        }
        assertArrayEquals(before, raw("override.conf"))
    }

    @Test fun invalidJsonOverrideKeepsSourceReadable() {
        ActivityScenario.launch<MainActivity>(intent("copy.txt")).use { scenario ->
            ready(scenario)
            override("JSON"); ready(scenario)
            scenario.onActivity {
                val prepared = androidx.lifecycle.ViewModelProvider(it)[ReaderSession::class.java].document!!
                assertNull(prepared.preview)
                assertEquals("Copyable Textr selection", prepared.source.text)
                assertEquals("Highlighted", it.findViewById<TextView>(R.id.display_mode).text.toString())
            }
        }
    }

    @Test fun shareOriginalFromPreviewPreservesBomAndNewlinesAndGrantsReadOnly() {
        ActivityScenario.launch<MainActivity>(intent("override.conf")).use { scenario ->
            ready(scenario); override("JSON"); ready(scenario)
            val outgoing = captureShare { menu("Share file…") }
            assertEquals(Intent.ACTION_SEND, outgoing.action)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, outgoing.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            val shared = outgoing.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals(shared, outgoing.clipData!!.getItemAt(0).uri)
            assertEquals("override.conf", shared.lastPathSegment)
            assertArrayEquals(raw("override.conf"), context.contentResolver.openInputStream(shared)!!.use { it.readBytes() })
            val expected = MessageDigest.getInstance("SHA-256").digest(raw("override.conf")).joinToString("") { "%02x".format(it) }
            deliver(outgoing)
            assertEquals("$expected:writeDenied=true", receiverResult())
        }
    }

    @Test fun nativeSelectionAndIncomingTextShareThroughAndroid() {
        ActivityScenario.launch<MainActivity>(intent("copy.txt")).use { scenario ->
            ready(scenario)
            val outgoing = captureShare {
                device.wait(Until.findObject(By.text("Copyable Textr selection")), 10000)!!.longClick()
                var share = device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("Share", 2))), 2000)
                if (share == null) {
                    (device.findObject(By.res("android", "floating_toolbar_overflow_button"))
                        ?: device.findObject(By.desc("More options")))!!.click()
                    share = device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("Share", 2))), 5000)
                }
                assertNotNull("Native selected-text Share action", share)
                share!!.click()
            }
            val text = outgoing.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            assertTrue(text.isNotBlank() && "Copyable Textr selection".contains(text))
        }
        val incoming = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Shared without a file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(incoming).use { scenario ->
            ready(scenario)
            val outgoing = captureShare { menu("Share text…") }
            assertEquals("Shared without a file", outgoing.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(1, repository.recentFiles().size) // Only the earlier URI-backed fixture.
        }
    }

    @Test fun failedAndCanceledSharesLeaveNoPartialCopiesAndExpiredCopiesArePruned() {
        val shares = java.io.File(context.cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
        val source = repository.read(uri("copy.txt"), null, CancellationSignal())
        for ((name, signal) in listOf("missing.txt" to CancellationSignal(), "oversized.txt" to CancellationSignal(),
            "slow.txt" to CancellationSignal().apply { cancel() })) {
            try { ShareFiles(context).prepare(uri(name), source, signal); fail("Share should fail") }
            catch (_: java.io.FileNotFoundException) { }
            catch (_: android.os.OperationCanceledException) { }
            catch (error: DocumentException) { assertEquals(ReadFailure.TOO_LARGE, error.reason) }
            assertEquals(0, shares.listFiles()!!.size)
        }
        val old = java.io.File(shares, "expired").apply { mkdirs(); resolve("file").writeText("temporary"); setLastModified(1) }
        ShareFiles(context).prune()
        assertFalse(old.exists())
        repeat(ShareFiles.MAX_COPIES + 1) { ShareFiles(context).prepare(uri("copy.txt"), source, CancellationSignal()) }
        assertEquals(ShareFiles.MAX_COPIES, shares.listFiles()!!.size)
    }

    @Test fun providerDisplayNamesAndMimeSurviveSafeCachePaths() {
        val source = repository.read(uri("copy.txt"), null, CancellationSignal())
        val name = "名".repeat(100) + ".csv"
        val share = ShareFiles(context).prepare(uri("copy.txt"), source.copy(name = name), CancellationSignal())
        val target = share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("text/csv", share.type)
        context.contentResolver.query(target, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(name, it.getString(0))
        }
        assertArrayEquals(raw("copy.txt"), context.contentResolver.openInputStream(target)!!.use { it.readBytes() })
    }

    private fun raw(name: String) = context.contentResolver.openInputStream(uri(name))!!.use { it.readBytes() }
    private fun override(format: String) { menu("Document info"); click("Treat as…"); click(format) }
    private fun menu(label: String) { click(By.desc("More options")); click(label) }
    private fun click(label: String) = click(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(label), 2)))
    private fun click(selector: BySelector) {
        val target = device.wait(Until.findObject(selector), 5000)
        assertNotNull("Missing $selector", target)
        target!!.click(); device.waitForIdle()
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var ready = false
        scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }
        ready
    }
    private fun assertPosition(scenario: ActivityScenario<MainActivity>, position: Int) = await {
        var scroll = 0
        scenario.onActivity { scroll = it.findViewById<WebView>(R.id.reader).scrollY }
        kotlin.math.abs(scroll - position) <= 2
    }
    private fun captureShare(action: () -> Unit): Intent {
        val captured = AtomicReference<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                val inner = if (intent.action == Intent.ACTION_CHOOSER) intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) else intent
                if (inner?.action == Intent.ACTION_SEND) {
                    captured.set(inner)
                    return Instrumentation.ActivityResult(0, null)
                }
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        try { action(); await { captured.get() != null }; return captured.get() }
        finally { instrumentation.removeMonitor(monitor) }
    }
    private fun deliver(intent: Intent) {
        context.contentResolver.call(uri("copy.txt"), "reset-shared", "", null)
        context.startActivity(Intent(intent).setClassName("${context.packageName}.test", ShareReceiver::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await { receiverResult().isNotEmpty() }
    }
    private fun receiverResult() = context.contentResolver.call(uri("copy.txt"), "shared-result", "", null)!!.getString("result")!!
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(100) }
        fail("Reader UX condition timed out")
    }
}
