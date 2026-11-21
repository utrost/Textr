package de.utrost.textr

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutputDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as Application
    private val models = mutableListOf<ViewModelStore>()
    private fun uri(id: String) = DocumentsContract.buildDocumentUri("de.utrost.textr.test.outputs", id)
    private fun fixture(operation: String, id: String, mode: String = "normal", bytes: ByteArray? = null): Bundle =
        context.contentResolver.call(Uri.parse("content://de.utrost.textr.test.documents"), "output-fixture", id,
            Bundle().apply { putString("operation", operation); putString("mode", mode); putByteArray("bytes", bytes) })!!
    private fun model(): OutputSession {
        val store = ViewModelStore().also { models.add(it) }
        return ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[OutputSession::class.java]
    }
    @Before fun reset() { context.getSharedPreferences("output_operation", 0).edit().clear().commit(); fixture("reset", "root") }
    @After fun cleanup() { instrumentation.runOnMainSync { models.forEach { it.clear() } }; fixture("reset", "root"); context.getSharedPreferences("output_operation", 0).edit().clear().commit() }
    @Test fun saveLocationChangesOnlyAfterSuccessfulOutputAndCanBeForgotten() {
        val location = SaveLocation(context)
        location.clear()
        val session = model()
        val request = OutputRequest("copy.md", "text/markdown", "# saved", null)
        fun hint(intent: Intent): Uri? = intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI)
        instrumentation.runOnMainSync {
            assertNull(hint(session.begin(request))); session.pickerResult(null)
        }
        fixture("configure", "location.md")
        instrumentation.runOnMainSync { session.begin(request); session.pickerResult(uri("location.md")) }
        await { session.status.phase == OutputPhase.COMPLETE }
        assertEquals(OutputResult.SAVED, session.status.result)
        instrumentation.runOnMainSync {
            session.acknowledge()
            assertEquals(uri("location.md"), hint(session.begin(request))); session.pickerResult(null)
        }
        fixture("configure", "denied-location.md", "denied")
        instrumentation.runOnMainSync { session.begin(request); session.pickerResult(uri("denied-location.md")) }
        await { session.status.phase == OutputPhase.COMPLETE }
        assertEquals(OutputResult.FAILED, session.status.result)
        assertEquals(uri("location.md"), hint(SaveLocation(context).apply(Intent())))
        location.clear(); assertNull(hint(location.apply(Intent())))
        assertFalse(context.contentResolver.persistedUriPermissions.any { it.uri == uri("location.md") && it.isWritePermission })
    }

    @Test fun successfulSaveReopensWithBomAndDoesNotWriteOrDeleteTheSource() {
        val source = "original\r\n🚀".toByteArray()
        fixture("configure", "source", bytes = source); fixture("configure", "copy.md")
        val session = model()
        lateinit var intent: Intent
        instrumentation.runOnMainSync {
            intent = session.begin(OutputRequest("copy.md", "text/markdown", "# Grüße\r\n\n🚀", uri("source").toString(), bom = true))
            session.pickerResult(uri("copy.md"))
        }
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action); assertEquals("text/markdown", intent.type)
        assertTrue(intent.categories!!.contains(Intent.CATEGORY_OPENABLE))
        assertEquals("copy.md", intent.getStringExtra(Intent.EXTRA_TITLE))
        await { session.status.phase == OutputPhase.COMPLETE }
        assertEquals(OutputResult.SAVED, session.status.result); assertFalse(session.status.partial)
        val reopened = DocumentRepository(context).read(uri("copy.md"), "text/markdown", android.os.CancellationSignal())
        assertEquals("# Grüße\r\n\n🚀", reopened.text)
        assertArrayEquals(source, fixture("inspect", "source").getByteArray("bytes"))
        assertFalse(context.contentResolver.persistedUriPermissions.any { it.uri == uri("copy.md") && it.isWritePermission })
        val restored = model()
        assertEquals(OutputResult.SAVED, restored.status.result)
        instrumentation.runOnMainSync { session.acknowledge(); restored.acknowledge() }
        assertFalse(context.getSharedPreferences("output_operation", 0).contains("target"))
    }
    @Test fun pickerCancellationAndSourceAliasesNeverOpenAnOutputDescriptor() {
        fixture("configure", "source", bytes = "original".toByteArray())
        val session = model()
        instrumentation.runOnMainSync { session.begin(OutputRequest("copy.txt", "text/plain", "edited", uri("source").toString())); session.pickerResult(null) }
        assertEquals(OutputPhase.IDLE, session.status.phase)
        for (target in listOf(uri("source"), DocumentsContract.buildDocumentUriUsingTree(DocumentsContract.buildTreeDocumentUri("de.utrost.textr.test.outputs", "root"), "source"))) {
            instrumentation.runOnMainSync { session.begin(OutputRequest("copy.txt", "text/plain", "edited", uri("source").toString())); session.pickerResult(target) }
            await { session.status.phase == OutputPhase.COMPLETE }
            assertEquals(OutputResult.REFUSED, session.status.result)
            assertEquals("original", fixture("inspect", "source").getByteArray("bytes")!!.toString(Charsets.UTF_8))
            instrumentation.runOnMainSync { session.acknowledge() }
        }
    }
    @Test fun deniedBrokenAndUnrepresentableWritesCleanNewDocumentsOrExplainPartialOutput() {
        val session = model()
        for ((id, mode, text, encoding) in listOf(
            listOf("denied.txt", "denied", "text", TextEncoding.UTF8),
            listOf("broken.txt", "broken", "text", TextEncoding.UTF8),
            listOf("kept.txt", "broken-keep", "text", TextEncoding.UTF8),
            listOf("encoding.txt", "normal", "🚀", TextEncoding.LATIN1))) {
            id as String; mode as String; text as String; encoding as TextEncoding
            fixture("configure", id, mode)
            instrumentation.runOnMainSync { session.begin(OutputRequest(id, "text/plain", text, null, encoding)); session.pickerResult(uri(id)) }
            await { session.status.phase == OutputPhase.COMPLETE }
            assertEquals(id, OutputResult.FAILED, session.status.result)
            val reason = when (mode) { "denied" -> OutputFailure.ACCESS; "normal" -> OutputFailure.ENCODING; else -> OutputFailure.UNKNOWN }
            assertEquals(reason, session.status.failure)
            assertEquals(reason, model().status.failure)
            assertEquals(mode.contains("keep"), session.status.partial)
            assertEquals(mode.contains("keep"), fixture("inspect", id).getBoolean("exists"))
            instrumentation.runOnMainSync { session.acknowledge() }
        }
    }
    @Test fun stalledPipeCancelsAndInterruptedMetadataNeverContainsContent() {
        fixture("configure", "stalled.txt", "stalled")
        val session = model()
        val privateText = "private fixture ".repeat(40_000)
        instrumentation.runOnMainSync { session.begin(OutputRequest("stalled.txt", "text/plain", privateText, null)); session.pickerResult(uri("stalled.txt")) }
        await { context.getSharedPreferences("output_operation", 0).contains("target") }
        val metadata = context.getSharedPreferences("output_operation", 0).all.toString()
        assertFalse(metadata.contains("private fixture"))
        val restarted = model()
        assertEquals(OutputResult.INTERRUPTED, restarted.status.result); assertTrue(restarted.status.partial)
        instrumentation.runOnMainSync { session.cancel() }
        await { session.status.phase == OutputPhase.COMPLETE }
        assertEquals(OutputResult.CANCELLED, session.status.result); assertFalse(session.status.partial)
        assertFalse(fixture("inspect", "stalled.txt").getBoolean("exists"))
    }
    @Test fun slowCleanupIsBoundedAndCannotDeleteAReusedSuccessfulTarget() {
        fixture("configure", "slow.txt", "slow-delete")
        val session = model()
        instrumentation.runOnMainSync {
            session.begin(OutputRequest("slow.txt", "text/plain", "🚀", null, TextEncoding.LATIN1))
            session.pickerResult(uri("slow.txt"))
        }
        await { session.status.phase == OutputPhase.COMPLETE }
        assertEquals(OutputResult.FAILED, session.status.result); assertTrue(session.status.partial)
        instrumentation.runOnMainSync {
            session.acknowledge()
            session.begin(OutputRequest("slow.txt", "text/plain", "new snapshot", null))
            session.pickerResult(uri("slow.txt"))
        }
        await { session.status.phase == OutputPhase.COMPLETE }
        assertEquals(OutputResult.REFUSED, session.status.result)
    }
    @Test fun processDeathWhilePickerOpenCleansReturnedEmptyDocumentWithoutWriting() {
        fixture("configure", "interrupted.md")
        val session = model()
        instrumentation.runOnMainSync { session.restorePending("interrupted.md", null); session.pickerResult(uri("interrupted.md")) }
        await { session.status.phase == OutputPhase.COMPLETE }
        assertEquals(OutputResult.INTERRUPTED, session.status.result)
        assertFalse(fixture("inspect", "interrupted.md").getBoolean("exists"))
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) { if (condition()) return; Thread.sleep(25) }
        fail("Output did not complete")
    }
}
