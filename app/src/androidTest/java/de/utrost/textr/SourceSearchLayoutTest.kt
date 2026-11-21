package de.utrost.textr

import android.graphics.Rect
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceSearchLayoutTest {
    @Test fun asyncResultsKeepOptionTouchTargetsInPlace() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var search: SourceSearchDialog
            scenario.onActivity {
                search = SourceSearchDialog(it, "needle\n".repeat(3), SourceSearchOptions("needle"), {}, {})
                search.dialog.setOnDismissListener { search.close() }
                search.dialog.show()
            }
            fun awaitCount(count: Int) {
                val deadline = System.nanoTime() + 10_000_000_000L
                while (System.nanoTime() < deadline) {
                    var ready = false
                    scenario.onActivity {
                        ready = search.dialog.findViewById<ListView>(R.id.source_results).count == count + 1 &&
                            search.dialog.findViewById<TextView>(R.id.source_result_count).text.toString() != it.getString(R.string.searching)
                    }
                    if (ready) { instrumentation.waitForIdleSync(); return }
                    Thread.sleep(50)
                }
                fail("Source results did not reach $count")
            }
            fun bounds(): Rect {
                val rect = Rect()
                scenario.onActivity {
                    val checkbox = search.dialog.findViewById<CheckBox>(R.id.match_case)
                    assertTrue(checkbox.getGlobalVisibleRect(Rect()))
                    val location = IntArray(2); checkbox.getLocationOnScreen(location)
                    rect.set(location[0], location[1], location[0] + checkbox.width, location[1] + checkbox.height)
                }
                return rect
            }
            try {
                awaitCount(3)
                val before = bounds()
                scenario.onActivity { search.dialog.findViewById<EditText>(R.id.source_query).setText("absent") }
                awaitCount(0)
                assertEquals("Clearing async results must not move the option touch target", before, bounds())
                scenario.onActivity { search.dialog.findViewById<EditText>(R.id.source_query).setText("needle") }
                awaitCount(3)
                assertEquals("Repopulating results must not move the option touch target", before, bounds())
            } finally { scenario.onActivity { search.dialog.dismiss() } }
        }
    }
    @Test fun queryOpensKeyboardReceivesTypingAndKeepsDoneVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        val previous = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        require(previous in setOf("0", "1", "null"))
        try {
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var search: SourceSearchDialog
                scenario.onActivity {
                    search = SourceSearchDialog(it, "needle\n", SourceSearchOptions(), {}, {})
                    search.dialog.setOnDismissListener { search.close() }
                    search.dialog.show()
                }
                try {
                    instrumentation.waitForIdleSync()
                    val query = device.wait(androidx.test.uiautomator.Until.findObject(
                        androidx.test.uiautomator.By.res(instrumentation.targetContext.packageName, "source_query")), 5000)
                    assertNotNull("Native source query", query)
                    query!!.click()
                    val deadline = System.nanoTime() + 10_000_000_000L
                    var shown = false
                    while (!shown && System.nanoTime() < deadline) {
                        var connected = false
                        scenario.onActivity {
                            val editor = search.dialog.findViewById<EditText>(R.id.source_query)
                            connected = it.getSystemService(android.view.inputmethod.InputMethodManager::class.java).isActive(editor)
                            shown = connected && androidx.core.view.ViewCompat.getRootWindowInsets(search.dialog.window!!.decorView)
                                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                        }
                        // API 29 dialog insets can omit the shown IME. Require both
                        // the actual server visibility and this editor's connection.
                        if (android.os.Build.VERSION.SDK_INT < 30) shown = connected &&
                            device.executeShellCommand("dumpsys input_method").contains("mInputShown=true")
                        if (!shown) Thread.sleep(100)
                    }
                    assertTrue("Source query connects to a visible software keyboard", shown)
                    assertTrue(device.pressKeyCode(android.view.KeyEvent.KEYCODE_N))
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        assertEquals("n", search.dialog.findViewById<EditText>(R.id.source_query).text.toString())
                        val done = search.dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                        val visible = Rect()
                        assertTrue("Done stays visible with keyboard", done.getGlobalVisibleRect(visible))
                        assertEquals("Done is fully visible", done.height, visible.height())
                    }
                } finally { scenario.onActivity { search.dialog.dismiss() } }
            }
        } finally {
            if (previous == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $previous")
        }
    }

}
