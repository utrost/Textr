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
}
