package de.utrost.textr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.widget.ListView
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
class StructureDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun open(name: String, text: String): ActivityScenario<MainActivity> {
        val uri = Uri.parse("content://de.utrost.textr.test.documents/$name")
        context.contentResolver.call(uri, "replace", uri.toString(), Bundle().apply { putString("text", text) })
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    @Test fun jsonTreeCopiesExactDuplicateValuesAndPathsThenJumpsToUnchangedSource() {
        val source = "{\r\n\"🚀\": 1e+002,\r\n\"🚀\": -0.00,\r\n\"a\": [true]}"
        open("tree.json", source).use { scenario ->
            ready(scenario); show(scenario)
            scenario.onActivity { it.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("fixture", "unchanged")) }
            tree(scenario) { assertEquals(4, it.count); assertTrue(it.getChildAt(1).contentDescription.toString().isNotBlank()) }
            action(scenario, 2, R.string.structure_copy_path)
            scenario.onActivity { assertEquals("$[\"🚀\"]@2", it.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()) }
            action(scenario, 2, R.string.structure_copy_value)
            scenario.onActivity { assertEquals("-0.00", it.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()) }
            action(scenario, 3, R.string.structure_expand)
            tree(scenario) { assertEquals(5, it.count) }
            scenario.recreate(); ready(scenario)
            await { var found = false; scenario.onActivity { found = roots().any { it.findViewById<View>(R.id.structure_tree) != null } }; found }
            tree(scenario) { assertEquals(5, it.count) }
            action(scenario, 2, R.string.structure_jump)
            ready(scenario)
            scenario.onActivity {
                assertEquals(source, prepared(it).source.text)
                assertEquals(it.getString(R.string.plain_text), it.findViewById<android.widget.TextView>(R.id.display_mode).text.toString())
                assertFalse(it.findViewById<WebView>(R.id.reader).settings.javaScriptEnabled)
            }
        }
    }
    @Test fun androidXmlParserRetainsNamespacesAndRejectsXxeWithoutLosingSource() {
        val source = "<r xmlns=\"urn:r\" xmlns:p=\"urn:p\"><p:x id='a&amp;b'>  text </p:x><x/></r>"
        open("tree.xml", source).use { scenario ->
            ready(scenario)
            scenario.onActivity {
                val outline = prepared(it).structure!!
                assertTrue(outline.available)
                assertEquals(listOf("r", "p:x", "@id", "x"), outline.nodes.map { n -> n.label })
                assertEquals("/{\"urn:r\"}r[1]/{\"urn:p\"}x[1]", outline.nodes[1].path)
            }
            show(scenario); action(scenario, 1, R.string.structure_expand)
            tree(scenario) { assertEquals(4, it.count) }
            action(scenario, 2, R.string.structure_copy_value)
            scenario.onActivity { assertEquals("a&amp;b", it.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()) }
        }
        val xxe = "<!DOCTYPE r [<!ENTITY x SYSTEM 'file:///data/data/de.utrost.textr/shared_prefs/reading.xml'>]><r>&x;</r>"
        open("unsafe-tree.xml", xxe).use { scenario ->
            ready(scenario); show(scenario)
            tree(scenario) { assertEquals(0, it.count) }
            scenario.onActivity { assertEquals(xxe, prepared(it).source.text); assertFalse(prepared(it).structure!!.available) }
        }
    }
    @Test fun nativeTreeActionsFitCompactAndWideWindowsAtTwoHundredPercent() {
        val originalScale = device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            for ((size, density) in listOf("720x1280" to 360, "1200x1600" to 240)) {
                device.executeShellCommand("wm size $size")
                device.executeShellCommand("wm density $density")
                context.getSharedPreferences("reading", 0).edit().putString("theme", "dark").commit()
                open("large-tree.json", "{\"item\":[1,2,3],\"other\":true}").use { scenario ->
                    ready(scenario); show(scenario)
                    tree(scenario) { assertEquals(3, it.count); assertTrue(it.getChildAt(0).height > 0) }
                    scenario.onActivity {
                        val root = roots().first { root -> root.findViewById<View>(R.id.structure_tree) != null }
                        val done = root.findViewById<android.widget.TextView>(android.R.id.button2)
                        val rect = android.graphics.Rect()
                        assertTrue(done.getGlobalVisibleRect(rect))
                        assertEquals(done.height, rect.height()); assertEquals(done.width, rect.width())
                        assertTrue(done.layout.height <= done.height - done.totalPaddingTop - done.totalPaddingBottom)
                    }
                    action(scenario, 0, R.string.structure_collapse)
                    tree(scenario) { assertEquals(1, it.count) }
                    // Native list selection is keyboard focusable and uses the same action menu.
                    device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_DOWN) // enter keyboard mode
                    tree(scenario) { assertTrue(it.requestFocus()); it.setSelection(0) }
                    instrumentation.waitForIdleSync()
                    device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                    assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.structure_expand))), 5_000))
                    device.pressBack()
                }
            }
        } finally {
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
            device.executeShellCommand("settings put system font_scale $originalScale")
            context.getSharedPreferences("reading", 0).edit().remove("theme").commit()
        }
    }

    private fun prepared(activity: MainActivity) = MainActivity::class.java.getDeclaredMethod("getDocument")
        .apply { isAccessible = true }.invoke(activity) as PreparedDocument
    private fun show(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { MainActivity::class.java.getDeclaredMethod("showStructure", Bundle::class.java).apply { isAccessible = true }.invoke(it, null) }
        await { var found = false; scenario.onActivity { found = roots().any { it.findViewById<View>(R.id.structure_tree)?.isLaidOut == true } }; found }
    }
    private fun roots() = WindowInspector.getGlobalWindowViews()
    private fun tree(scenario: ActivityScenario<MainActivity>, action: (ListView) -> Unit) = scenario.onActivity {
        action(roots().firstNotNullOf { it.findViewById<ListView>(R.id.structure_tree) })
    }
    private fun action(scenario: ActivityScenario<MainActivity>, position: Int, label: Int) {
        tree(scenario) { it.performItemClick(it.getChildAt(position - it.firstVisiblePosition), position, it.adapter.getItemId(position)) }
        val item = device.wait(Until.findObject(By.text(context.getString(label))), 5_000)
        assertNotNull("Node action", item); item.click()
        assertTrue("Node action finished", device.wait(Until.gone(By.text(context.getString(label))), 5_000))
        instrumentation.waitForIdleSync()
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var ready = false; scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }; ready
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) { if (condition()) return; Thread.sleep(50) }
        fail("Structure UI did not become ready")
    }
}
