package de.utrost.textr

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
import java.io.File

@RunWith(AndroidJUnit4::class)
class EditorDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val sessions = linkedSetOf<EditorSession>()
    private val source = "# Private copy Grüße 🚀\r\ncat cat_ CAT\r\nend"
    private val sourceUri = Uri.parse("content://de.utrost.textr.test.documents/editor.md")
    private val store get() = EditDraftStore(context)
    private fun fixture(operation: String, id: String, behavior: String = "normal") = context.contentResolver.call(Uri.parse("content://de.utrost.textr.test.documents"), "output-fixture", id, Bundle().apply { putString("operation", operation); putString("mode", behavior) })
    private fun target(id: String) = DocumentsContract.buildDocumentUri("de.utrost.textr.test.outputs", id)
    @Before @After fun reset() {
        // Closing an editor intentionally finishes its queued private checkpoints.
        // Wait for those writers before deleting fixtures between independent tests.
        for (session in sessions) {
            val executor = EditorSession::class.java.getDeclaredField("worker").apply { isAccessible = true }.get(session) as java.util.concurrent.ExecutorService
            assertTrue("Editor checkpoint worker closed", executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
        }
        sessions.clear()
        File(context.noBackupFilesDir, "edits").deleteRecursively()
        context.getSharedPreferences("output_operation", 0).edit().clear().commit()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
        fixture("reset", "root")
    }
    private fun launch(text: String = source, name: String = "editor.md", encoding: String = "UTF-8", bom: Boolean = false): ActivityScenario<EditorActivity> {
        context.contentResolver.call(sourceUri, "replace", sourceUri.toString(), Bundle().apply { putString("text", text) })
        val token = EditorInbox.put(EditorSnapshot(TextDocument(name, text, TextDocuments.kind(name, null), text.toByteArray().size, encoding), sourceUri.toString(), bom))!!
        return ActivityScenario.launch(Intent(context, EditorActivity::class.java).putExtra("token", token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun model(activity: EditorActivity) = ViewModelProvider(activity)[EditorSession::class.java].also { sessions.add(it) }
    private fun ready(scenario: ActivityScenario<EditorActivity>) = await { var ready = false; scenario.onActivity { ready = model(it).buffer != null && !model(it).loading }; ready }
    private fun editor(scenario: ActivityScenario<EditorActivity>, action: (EditText) -> Unit) = scenario.onActivity { action(it.findViewById(R.id.editor_text)) }
    private fun checkpoint(text: String) = await { try { store.read()?.text == text } catch (_: Exception) { false } }
    @Test fun nativeEditsUndoRedoNewlinesAndRotationDoNotSerializeContents() {
        launch().use { scenario ->
            ready(scenario)
            editor(scenario) { assertEquals(source, it.text.toString()); assertFalse(it.isSaveEnabled); it.setSelection(it.length()); it.text.append("\nnew") }
            val expected = source + "\r\nnew"
            checkpoint(expected)
            scenario.onActivity {
                assertTrue(model(it).dirty); assertEquals(expected, model(it).buffer!!.text)
                it.findViewById<Button>(R.id.editor_undo).performClick(); assertEquals(source, model(it).buffer!!.text)
                it.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Y, 0, KeyEvent.META_CTRL_ON))
                assertEquals(expected, model(it).buffer!!.text)
                val state = Bundle(); EditorActivity::class.java.getDeclaredMethod("onSaveInstanceState", Bundle::class.java).apply { isAccessible = true }.invoke(it, state)
                assertFalse(state.toString().contains("Private copy")); assertFalse(state.toString().contains(expected))
            }
            scenario.recreate(); ready(scenario)
            editor(scenario) { assertEquals(expected, it.text.toString()) }
            assertArrayEquals(source.toByteArray(), context.contentResolver.openInputStream(sourceUri)!!.use { it.readBytes() })
            assertTrue(File(context.noBackupFilesDir, "edits/current.json").isFile)
        }
    }
    @Test fun dirtyBackCanCancelThenDiscardWithoutDeletingOriginal() {
        launch().use { scenario ->
            ready(scenario); editor(scenario) { it.text.append("!") }; checkpoint(source + "!")
            scenario.onActivity { it.findViewById<Button>(R.id.editor_back).performClick() }
            click(R.string.cancel)
            scenario.onActivity { assertTrue(model(it).dirty); assertFalse(it.isFinishing) }
            scenario.onActivity { it.findViewById<Button>(R.id.editor_back).performClick() }
            click(R.string.editor_discard)
            await { store.read() == null }
            assertArrayEquals(source.toByteArray(), context.contentResolver.openInputStream(sourceUri)!!.use { it.readBytes() })
        }
    }
    @Test fun recoveryAfterModelLossNeedsNoOriginalGrantAndDoesNotEvictExistingDraft() {
        var saved: EditDraft? = null
        launch().use { scenario ->
            ready(scenario); editor(scenario) { it.text.append(" recovered"); it.setSelection(4, 9) }; checkpoint(source + " recovered"); saved = store.read()
        }
        await { store.read()?.selectionStart == 4 && store.read()?.selectionEnd == 9 }
        context.contentResolver.call(sourceUri, "missing", sourceUri.toString(), null)
        var fresh: EditorSession? = null
        instrumentation.runOnMainSync { fresh = EditorSession(context.applicationContext as Application); fresh!!.initialize(null) }
        await { !fresh!!.loading }
        assertEquals(saved!!.text, fresh!!.buffer!!.text); assertTrue(fresh!!.dirty); assertEquals(saved!!.id, fresh!!.draft!!.id)
        assertEquals(EditSelection(4, 9), fresh!!.buffer!!.selection)
        clear(fresh!!)
        launch("different file").use { scenario ->
            await { var blocked = false; scenario.onActivity { blocked = model(it).blockedByExisting }; blocked }
            scenario.onActivity { assertNull(model(it).buffer); it.findViewById<Button>(R.id.editor_back).performClick() }
            await { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(saved!!.id, store.read()!!.id)
        }
        launch("different file").use { scenario ->
            await { var blocked = false; scenario.onActivity { blocked = model(it).blockedByExisting }; blocked }
            scenario.onActivity { assertNull(model(it).buffer); it.findViewById<Button>(R.id.editor_resume).performClick() }
            ready(scenario); editor(scenario) { assertEquals(saved!!.text, it.text.toString()) }
            assertEquals(saved!!.id, store.read()!!.id)
        }
    }
    @Test fun failedCheckpointKeepsPreviousAtomicCopyAndShowsRecoveryWarning() {
        launch().use { scenario ->
            ready(scenario); checkpoint(source)
            val directory = File(context.noBackupFilesDir, "edits")
            val file = File(directory, "current.json")
            assertTrue(file.setWritable(false, false)); assertTrue(directory.setWritable(false, false))
            try {
                editor(scenario) { it.text.append(" unsaved") }
                await { var failed = false; scenario.onActivity { failed = model(it).recoveryFailed }; failed }
                assertEquals(source, store.read()!!.text)
                scenario.onActivity { assertTrue(it.findViewById<TextView>(R.id.editor_status).text.contains(it.getString(R.string.editor_recovery_failed))) }
            } finally { directory.setWritable(true, true); file.setWritable(true, true) }
            scenario.onActivity { it.findViewById<Button>(R.id.retry_recovery).performClick() }
            checkpoint(source + " unsaved")
            scenario.onActivity { assertFalse(model(it).recoveryFailed); assertTrue(model(it).dirty) }
        }
    }
    @Test fun unreadableRecoveryCanBeRetriedWithoutDiscardingIt() {
        val draft = EditDraft.new(TextDocument("recovery.md", source, FileKind.MARKDOWN, source.toByteArray().size, "UTF-8"), sourceUri.toString())
        assertTrue(store.create(draft))
        val file = File(context.noBackupFilesDir, "edits/current.json")
        val bytes = file.readBytes()
        file.writeText("interrupted fixture")
        try {
            ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
                await { var failed = false; scenario.onActivity { failed = model(it).unavailable && model(it).recoveryFailed }; failed }
                scenario.onActivity { assertTrue(it.findViewById<TextView>(R.id.editor_status).text.contains(it.getString(R.string.editor_recovery_unreadable))) }
                assertEquals("interrupted fixture", file.readText())
                file.writeBytes(bytes)
                scenario.onActivity { it.findViewById<Button>(R.id.retry_recovery).performClick() }
                ready(scenario)
                editor(scenario) { assertEquals(source, it.text.toString()) }
                assertEquals(draft.id, store.read()!!.id)
            }
        } finally { file.writeBytes(bytes) }
    }

    @Test fun realSavePickerPreservesBomEncodingNewlinesAndDeletesOnlyConfirmedRecovery() {
        fixture("configure", "edited.md")
        val monitor = monitor(target("edited.md"))
        try {
            launch(encoding = "UTF-16LE", bom = true).use { scenario ->
                ready(scenario); editor(scenario) { it.text.append("!") }; checkpoint(source + "!")
                scenario.onActivity { it.findViewById<Button>(R.id.editor_save).performClick() }
                click(R.string.choose_destination)
                await { var saved = false; scenario.onActivity { saved = ViewModelProvider(it)[OutputSession::class.java].status.result == OutputResult.SAVED && !model(it).loading && !model(it).dirty }; saved }
                assertTrue(monitor.hits > 0); assertNull(store.read())
                assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.editor_open_saved))), 5000))
                assertEquals(source + "!", DocumentRepository(context).read(target("edited.md"), null, android.os.CancellationSignal()).text)
                val bytes = context.contentResolver.openInputStream(target("edited.md"))!!.use { it.readBytes() }
                assertArrayEquals(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + (source + "!").toByteArray(Charsets.UTF_16LE), bytes)
                click(R.string.done)
                editor(scenario) { it.text.append(" again") }; checkpoint(source + "! again")
                assertTrue(store.read()!!.revision > 0)
                assertArrayEquals(source.toByteArray(), context.contentResolver.openInputStream(sourceUri)!!.use { it.readBytes() })
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun cancelledAndDeniedOutputRetainDirtyRecovery() {
        var monitor = monitor(null)
        try {
            launch().use { scenario ->
                ready(scenario); editor(scenario) { it.text.append("!") }; checkpoint(source + "!")
                scenario.onActivity { it.findViewById<Button>(R.id.editor_save).performClick() }; click(R.string.choose_destination)
                await { var idle = false; scenario.onActivity { idle = !ViewModelProvider(it)[OutputSession::class.java].busy }; idle }
                assertEquals(source + "!", store.read()!!.text)
                instrumentation.removeMonitor(monitor); fixture("configure", "denied.md", "denied"); monitor = monitor(target("denied.md"))
                scenario.onActivity { it.findViewById<Button>(R.id.editor_save).performClick() }; click(R.string.choose_destination)
                await { var failed = false; scenario.onActivity { failed = ViewModelProvider(it)[OutputSession::class.java].status.result == OutputResult.FAILED }; failed }
                assertEquals(source + "!", store.read()!!.text)
                scenario.onActivity { assertTrue(model(it).dirty) }
                scenario.recreate()
                assertNotNull(device.wait(Until.findObject(By.textContains("Permission to save")), 5000))
                instrumentation.removeMonitor(monitor)
                fixture("configure", "retry.md"); monitor = monitor(target("retry.md"))
                val retry = device.wait(Until.findObject(By.clazz(Button::class.java).text(context.getString(R.string.save_copy))), 5000)
                assertNotNull("Failure dialog offers Save a new copy", retry)
                retry!!.click(); click(R.string.choose_destination)
                await { var saved = false; scenario.onActivity { saved = !model(it).dirty && !model(it).loading }; saved }
                assertNull(store.read())
                assertEquals(source + "!", DocumentRepository(context).read(target("retry.md"), null, android.os.CancellationSignal()).text)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun literalReplaceAllIsOneUndoStepWithAccessibleNativeControls() {
        launch().use { scenario ->
            ready(scenario)
            scenario.onActivity { it.findViewById<Button>(R.id.editor_find).performClick() }
            await { roots().any { it.findViewById<View>(R.id.editor_query) != null } }
            scenario.onActivity {
                val root = roots().first { it.findViewById<View>(R.id.editor_query) != null }
                root.findViewById<EditText>(R.id.editor_query).setText("cat")
                val replacement = root.findViewById<EditText>(R.id.editor_replacement)
                replacement.setText("x".repeat(EditBuffer.MAX_REPLACEMENT + 1)); assertEquals("", replacement.text.toString())
                replacement.setText("dog")
                root.findViewById<Button>(R.id.editor_replace_all).performClick()
                assertEquals(source.replace("cat", "dog").replace("CAT", "dog"), model(it).buffer!!.text)
                assertTrue(root.findViewById<TextView>(R.id.editor_match_count).text.contains("3"))
            }
            click(R.string.done)
            scenario.onActivity { it.findViewById<Button>(R.id.editor_undo).performClick(); assertEquals(source, model(it).buffer!!.text); it.findViewById<Button>(R.id.editor_find).performClick() }
            val first = source.indexOf("cat")
            val changed = source.replaceRange(first, first + 3, "catcat")
            scenario.onActivity {
                val root = roots().first { root -> root.findViewById<View>(R.id.editor_query) != null }
                root.findViewById<EditText>(R.id.editor_replacement).setText("catcat")
                nativeButton(root, R.string.next_match).performClick(); nativeButton(root, R.string.editor_replace).performClick()
                assertEquals(changed, model(it).buffer!!.text)
                assertEquals(source.indexOf("cat_") + 3, model(it).buffer!!.selection.start)
            }
            click(R.string.done)
            scenario.onActivity {
                it.findViewById<Button>(R.id.editor_undo).performClick(); assertEquals(source, model(it).buffer!!.text)
                it.findViewById<Button>(R.id.editor_redo).performClick(); assertEquals(changed, model(it).buffer!!.text)
            }
            val german = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.GERMAN) })
            assertEquals("Kopie bearbeiten…", german.getString(R.string.editor_edit_copy))
        }
    }
    @Test fun explicitLegacyConversionFailsWithoutLosingRecoveryAndStaleSaveCannotClearIt() {
        fixture("configure", "legacy.md")
        val monitor = monitor(target("legacy.md"))
        try {
            launch().use { scenario ->
                ready(scenario); editor(scenario) { it.text.append("!") }; checkpoint(source + "!")
                scenario.onActivity {
                    val value = model(it).draft!!
                    model(it).saved(OutputStatus(OutputPhase.COMPLETE, result = OutputResult.SAVED, draftId = value.id, revision = value.revision - 1)) { assertFalse(it) }
                    assertTrue(store.read() != null)
                    it.findViewById<Button>(R.id.editor_save).performClick()
                    roots().first { root -> root.findViewById<View>(R.id.editor_encoding) != null }
                        .findViewById<android.widget.Spinner>(R.id.editor_encoding).setSelection(TextEncoding.entries.indexOf(TextEncoding.WINDOWS1252))
                }
                click(R.string.choose_destination)
                assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.editor_conversion_title))), 5000))
                click(R.string.done)
                await { var failed = false; scenario.onActivity { failed = ViewModelProvider(it)[OutputSession::class.java].status.result == OutputResult.FAILED }; failed }
                checkpoint(source + "!"); assertEquals(TextEncoding.WINDOWS1252, store.read()!!.encoding)
                scenario.onActivity { assertTrue(model(it).dirty) }
            }
        } finally { instrumentation.removeMonitor(monitor) }
        assertNull(EditorInbox.put(EditorSnapshot(TextDocument("page.html", "<p>x</p>", FileKind.HTML, 8), null, false)))
        assertNull(EditorInbox.put(EditorSnapshot(TextDocument("large.txt", "x".repeat(EditBuffer.MAX_CHARS + 1), FileKind.TEXT, EditBuffer.MAX_CHARS + 1), null, false)))
    }

    @Test fun corruptRecoveryIsPreservedUntilExplicitConfirmedDiscard() {
        val file = File(context.noBackupFilesDir, "edits/current.json"); file.parentFile!!.mkdirs()
        val corrupt = "{\"version\":1,\"text\":\"partial"; file.writeText(corrupt)
        ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
            await { var failed = false; scenario.onActivity { failed = model(it).unavailable && model(it).recoveryFailed }; failed }
            assertEquals(corrupt, file.readText())
            scenario.onActivity { assertTrue(it.findViewById<Button>(R.id.editor_discard).isEnabled); it.findViewById<Button>(R.id.editor_discard).performClick() }
            click(R.string.cancel); assertEquals(corrupt, file.readText())
            scenario.onActivity { it.findViewById<Button>(R.id.editor_discard).performClick() }
            scenario.onActivity { roots().last { root -> root.findViewById<View>(android.R.id.button1) != null }.findViewById<Button>(android.R.id.button1).performClick() }
            await { !file.exists() }
        }
    }
    @Test fun largeReplaceRequiresConfirmationAndFindStateSurvivesRecreation() {
        val original = "x ".repeat(30) + "🚀"
        launch(original).use { scenario ->
            ready(scenario); scenario.onActivity { it.findViewById<Button>(R.id.editor_find).performClick() }
            scenario.onActivity {
                val root = roots().first { it.findViewById<View>(R.id.editor_query) != null }
                root.findViewById<EditText>(R.id.editor_query).setText("x"); root.findViewById<EditText>(R.id.editor_replacement).setText("y")
            }
            scenario.recreate(); ready(scenario)
            await { roots().any { it.findViewById<View>(R.id.editor_query) != null } }
            scenario.onActivity {
                val root = roots().first { it.findViewById<View>(R.id.editor_query) != null }
                assertEquals("x", root.findViewById<EditText>(R.id.editor_query).text.toString()); assertEquals("y", root.findViewById<EditText>(R.id.editor_replacement).text.toString())
                nativeButton(root, R.string.previous_match).performClick(); assertEquals(58, model(it).buffer!!.selection.start)
                nativeButton(root, R.string.next_match).performClick(); assertEquals(0, model(it).buffer!!.selection.start)
                root.findViewById<Button>(R.id.editor_replace_all).performClick(); assertEquals(original, model(it).buffer!!.text)
            }
            click(R.string.cancel)
            scenario.onActivity { roots().first { root -> root.findViewById<View>(R.id.editor_query) != null }.findViewById<Button>(R.id.editor_replace_all).performClick() }
            scenario.onActivity { roots().last { root -> root.findViewById<View>(android.R.id.button1) != null }.findViewById<Button>(android.R.id.button1).performClick() }
            await { var changed = false; scenario.onActivity { changed = model(it).buffer!!.text == original.replace('x', 'y') }; changed }
            click(R.string.done)
            scenario.onActivity { it.findViewById<Button>(R.id.editor_undo).performClick(); assertEquals(original, model(it).buffer!!.text) }
        }
    }
    @Test fun reopeningEditorRetainsOneSessionAndCleanCloseKeepsRecovery() {
        var id = ""
        launch().use { scenario ->
            ready(scenario)
            scenario.onActivity {
                id = model(it).draft!!.id
                val token = EditorInbox.put(EditorSnapshot(TextDocument("other.txt", "other", FileKind.TEXT, 5), null, false))!!
                EditorActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply { isAccessible = true }.invoke(it, Intent(it, EditorActivity::class.java).putExtra("token", token))
                assertNull(EditorInbox.take(token)); assertEquals(id, model(it).draft!!.id); assertEquals(source, model(it).buffer!!.text)
                assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK, context.packageManager.getActivityInfo(it.componentName, 0).launchMode)
                it.findViewById<Button>(R.id.editor_back).performClick()
            }
            await { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        }
        assertEquals(id, store.read()!!.id)
    }

    @Test fun maximumBufferRejectsOverflowWhileSoftwareKeyboardKeepsBackReachable() {
        val scale = device.executeShellCommand("settings get system font_scale").trim()
        val keyboard = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        try {
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            device.executeShellCommand("settings put system font_scale 2.0"); device.executeShellCommand("wm size 720x1280"); device.executeShellCommand("wm density 360")
            val maximum = ("x".repeat(63) + "\n").repeat(EditBuffer.MAX_CHARS / 64)
            launch(maximum, "maximum.txt").use { scenario ->
                ready(scenario)
                editor(scenario) {
                    it.requestFocus(); it.setSelection(it.length())
                    it.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(it, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                await {
                    var shown = false
                    scenario.onActivity {
                        val root = it.findViewById<android.view.View>(android.R.id.content)
                        shown = if (android.os.Build.VERSION.SDK_INT >= 30) root.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime()) else {
                            val frame = android.graphics.Rect(); root.getWindowVisibleDisplayFrame(frame)
                            root.rootView.height - frame.bottom > 200
                        }
                    }
                    shown
                }
                device.pressKeyCode(KeyEvent.KEYCODE_A)
                scenario.onActivity {
                    assertEquals(maximum, model(it).buffer!!.text); assertFalse(model(it).dirty)
                    val back = it.findViewById<Button>(R.id.editor_back); val rect = android.graphics.Rect()
                    assertTrue(back.getGlobalVisibleRect(rect)); assertEquals(back.height, rect.height())
                    val frame = android.graphics.Rect(); back.getWindowVisibleDisplayFrame(frame); assertTrue(rect.bottom <= frame.bottom)
                }
                device.pressBack()
            }
        } finally {
            device.executeShellCommand("wm size reset"); device.executeShellCommand("wm density reset"); device.executeShellCommand("settings put system font_scale $scale")
            if (keyboard == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard") else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $keyboard")
        }
    }

    @Test fun compactTwoHundredPercentSupportsKeyboardAndKeepsBackActionVisible() {
        val scale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale 2.0"); device.executeShellCommand("wm size 720x1280"); device.executeShellCommand("wm density 360")
            launch().use { scenario ->
                ready(scenario); editor(scenario) { it.text.append("!") }
                scenario.onActivity {
                    val back = it.findViewById<Button>(R.id.editor_back); val rect = android.graphics.Rect()
                    assertTrue(back.getGlobalVisibleRect(rect)); assertEquals(back.height, rect.height())
                    it.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
                    assertEquals(source, model(it).buffer!!.text)
                }
                scenario.recreate(); ready(scenario); editor(scenario) { assertEquals(source, it.text.toString()) }
            }
        } finally { device.executeShellCommand("wm size reset"); device.executeShellCommand("wm density reset"); device.executeShellCommand("settings put system font_scale $scale") }
    }
    private fun nativeButton(root: View, label: Int): Button {
        fun find(view: View): Button? {
            if (view is Button && view.text.toString() == context.getString(label)) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        return find(root) ?: throw AssertionError("Missing native action: ${context.getString(label)}")
    }
    private fun roots() = WindowInspector.getGlobalWindowViews()
    private fun monitor(uri: Uri?): Instrumentation.ActivityMonitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("text/markdown") },
        Instrumentation.ActivityResult(if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, uri?.let { Intent().setData(it) }), true)
    private fun clear(model: EditorSession) = instrumentation.runOnMainSync { EditorSession::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(model) }
    private fun click(label: Int) { val item = device.wait(Until.findObject(By.text(context.getString(label))), 5000); assertNotNull(context.getString(label), item); item!!.click(); instrumentation.waitForIdleSync() }
    private fun await(condition: () -> Boolean) { val until = System.currentTimeMillis() + 30_000; while (System.currentTimeMillis() < until) { if (condition()) return; Thread.sleep(50) }; fail("Editor did not become ready") }
}
