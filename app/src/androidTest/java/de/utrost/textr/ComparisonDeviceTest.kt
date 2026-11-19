package de.utrost.textr

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
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
class ComparisonDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val leftText = "same\r\nold\r\nanchor\nremove\nend"
    private val rightText = "same\r\nnew\r\nanchor\nadd\nend\n"
    private fun fixture(name: String, text: String): Uri = Uri.parse("content://de.utrost.textr.test.documents/$name").also {
        context.contentResolver.call(it, "replace", it.toString(), Bundle().apply { putString("text", text) })
    }
    private fun launch(uri: Uri) = ActivityScenario.launch<ComparisonActivity>(Intent(context, ComparisonActivity::class.java)
        .putExtra("uri", uri.toString()).putExtra("name", uri.lastPathSegment).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun model(activity: ComparisonActivity) = ViewModelProvider(activity)[ComparisonSession::class.java]
    private fun ready(scenario: ActivityScenario<ComparisonActivity>, pair: Boolean = false) = await {
        var ready = false; scenario.onActivity { val model = model(it); ready = !model.busy && if (pair) model.result != null else model.left != null }; ready
    }
    @Test fun explicitPickerCopyNavigationAndRecreationKeepSourcesReadOnly() {
        val left = fixture("compare-left.txt", leftText); val right = fixture("compare-right.txt", rightText)
        val filter = IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") }
        val monitor = instrumentation.addMonitor(filter, Instrumentation.ActivityResult(Activity.RESULT_OK,
            Intent().setData(right).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)), true)
        try {
            launch(left).use { scenario ->
                ready(scenario)
                scenario.onActivity { it.findViewById<Button>(R.id.compare_right).performClick() }
                click(R.string.compare_choose_file); ready(scenario, true)
                assertTrue(monitor.hits > 0)
                var first: ComparisonSession? = null
                scenario.onActivity {
                    first = model(it); assertEquals(leftText, first!!.left!!.document.text); assertEquals(rightText, first!!.right!!.document.text)
                    assertFalse(first!!.result!!.identical)
                    it.findViewById<Button>(R.id.compare_next).performClick(); assertEquals(1, first!!.selectedChange)
                    it.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0, KeyEvent.META_CTRL_ON))
                    assertEquals(0, first!!.selectedChange)
                    val state = Bundle(); ComparisonActivity::class.java.getDeclaredMethod("onSaveInstanceState", Bundle::class.java).apply { isAccessible = true }.invoke(it, state)
                    assertFalse(state.toString().contains("old\\r")); assertFalse(state.toString().contains(leftText))
                    val list = it.findViewById<ListView>(R.id.compare_rows)
                    val row = list.adapter.getView(0, null, list) as android.view.ViewGroup
                    fun heading(view: android.view.View): Button? {
                        if (view is Button) return view
                        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) heading(view.getChildAt(i))?.let { return it }
                        return null
                    }
                    heading(row)!!.performClick()
                }
                click(R.string.compare_copy_line)
                await { var copied = false; scenario.onActivity { copied = it.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString() == "same\r\n" }; copied }
                scenario.recreate(); ready(scenario, true)
                scenario.onActivity { assertSame(first, model(it)); assertEquals(0, model(it).selectedChange) }
                assertArrayEquals(leftText.toByteArray(), context.contentResolver.openInputStream(left)!!.use { it.readBytes() })
                assertArrayEquals(rightText.toByteArray(), context.contentResolver.openInputStream(right)!!.use { it.readBytes() })
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun metadataRestoreRereadsChangedVersionsAndReportsLostSharedInput() {
        val uri = fixture("compare-restore.txt", "before\n")
        launch(uri).use { scenario ->
            ready(scenario)
            val state = Bundle()
            scenario.onActivity { model(it).save(state) }
            fixture("compare-restore.txt", "after\n")
            var restored: ComparisonSession? = null
            instrumentation.runOnMainSync { restored = ComparisonSession(context.applicationContext as Application); restored!!.restore(state) }
            await { !restored!!.busy }
            assertEquals("after\n", restored!!.left!!.document.text); assertTrue(restored!!.changedSources)
            val shared = Bundle().apply { putBoolean("leftPresent", true); putString("leftName", "shared.txt") }
            instrumentation.runOnMainSync { restored!!.restore(shared) }
            await { !restored!!.busy }; assertEquals(ComparisonFailure.LOST_SHARED, restored!!.failure)
            clear(restored!!)
            scenario.onActivity { model(it).load(false, ComparisonRef(uri.toString(), "current.txt")) }
            ready(scenario, true)
            scenario.onActivity {
                assertFalse(model(it).result!!.identical)
                val snapshot = ComparisonSnapshot(ComparisonRef(null, "shared.txt", fingerprint = sourceFingerprint("after\n")), TextDocument("shared.txt", "after\n", FileKind.TEXT, 6))
                val token = ComparisonInbox.put(snapshot)!!
                ComparisonActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply { isAccessible = true }.invoke(it, Intent(it, ComparisonActivity::class.java).putExtra("token", token))
            }
            ready(scenario, true)
            scenario.onActivity { assertTrue(model(it).result!!.identical); assertEquals("after\n", model(it).left!!.document.text) }
        }
    }
    @Test fun deniedOversizeAndCancellationRemainRecoverable() {
        val uri = fixture("compare-small.txt", "small\n")
        launch(uri).use { scenario ->
            ready(scenario)
            scenario.onActivity { model(it).load(false, ComparisonRef("content://de.utrost.textr.test.documents/denied.txt", "denied.txt")) }
            await { var done = false; scenario.onActivity { done = !model(it).busy }; done }
            scenario.onActivity { assertEquals(ComparisonFailure.READ, model(it).failure) }
            val huge = fixture("compare-too-large.txt", "x".repeat(LineDiff.MAX_CHARS + 1))
            scenario.onActivity { model(it).load(false, ComparisonRef(huge.toString(), "large.txt")) }
            await { var done = false; scenario.onActivity { done = !model(it).busy }; done }
            scenario.onActivity {
                assertEquals(ComparisonFailure.INPUT_LIMIT, model(it).failure)
                model(it).load(false, ComparisonRef("content://de.utrost.textr.test.documents/slow.json", "slow.json"))
                it.findViewById<Button>(R.id.compare_cancel).performClick()
                assertEquals(ComparisonFailure.CANCELLED, model(it).failure); assertEquals("small\n", model(it).left!!.document.text)
                model(it).load(false, ComparisonRef(uri.toString(), "small.txt"))
            }
            ready(scenario, true)
            scenario.onActivity { assertTrue(model(it).result!!.identical); assertTrue(it.findViewById<TextView>(R.id.compare_status).text.contains("identical")) }
        }
    }
    @Test fun wideAndTwoHundredPercentLayoutsRetainSnapshotsAndSelectedChange() {
        val scale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale 1.0"); device.executeShellCommand("wm size 1200x1600"); device.executeShellCommand("wm density 160")
            launch(fixture("compare-wide-left.txt", leftText)).use { scenario ->
                ready(scenario)
                val right = fixture("compare-wide-right.txt", rightText)
                scenario.onActivity { model(it).load(false, ComparisonRef(right.toString(), "right.txt")) }
                ready(scenario, true)
                scenario.onActivity {
                    assertTrue(it.findViewById<TextView>(R.id.compare_layout).text.contains(it.getString(R.string.compare_wide)))
                    val row = it.findViewById<ListView>(R.id.compare_rows).getChildAt(0) as android.view.ViewGroup
                    assertEquals(2, row.childCount)
                    for (index in 0..1) {
                        val pane = row.getChildAt(index) as android.view.ViewGroup
                        assertTrue(pane.width >= 300); assertTrue(pane.height > 0)
                        assertTrue((pane.getChildAt(1) as TextView).height > 0)
                    }
                    it.findViewById<Button>(R.id.compare_next).performClick(); assertEquals(1, model(it).selectedChange)
                }
                device.executeShellCommand("settings put system font_scale 2.0"); device.executeShellCommand("wm size 720x1280"); device.executeShellCommand("wm density 360")
                await { var unified = false; scenario.onActivity { unified = it.findViewById<TextView>(R.id.compare_layout).text.contains(it.getString(R.string.compare_unified)) }; unified }
                scenario.onActivity {
                    assertEquals(1, model(it).selectedChange); assertEquals(leftText, model(it).left!!.document.text)
                    val back = (it.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.view.ViewGroup).let { root -> root.getChildAt(root.childCount - 1) }
                    val rect = android.graphics.Rect(); assertTrue(back.getGlobalVisibleRect(rect)); assertEquals(back.height, rect.height())
                    assertTrue(it.findViewById<ListView>(R.id.compare_rows).isShown)
                }
            }
        } finally { device.executeShellCommand("wm size reset"); device.executeShellCommand("wm density reset"); device.executeShellCommand("settings put system font_scale $scale") }
    }
    private fun clear(model: ComparisonSession) = instrumentation.runOnMainSync { ComparisonSession::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(model) }
    private fun click(label: Int) { val item = device.wait(Until.findObject(By.text(context.getString(label))), 5000); assertNotNull(item); item!!.click(); instrumentation.waitForIdleSync() }
    private fun await(condition: () -> Boolean) { val until = System.currentTimeMillis() + 30_000; while (System.currentTimeMillis() < until) { if (condition()) return; Thread.sleep(50) }; fail("Comparison did not become ready") }
}
