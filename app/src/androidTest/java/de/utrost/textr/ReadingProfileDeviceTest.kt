package de.utrost.textr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingProfileDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = context.getSharedPreferences("reading", Context.MODE_PRIVATE)
    private val repository get() = DocumentRepository(context)
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun intent(name: String) = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    @Before fun clean() {
        for (name in listOf("reading", "recent_files", "open_documents", "bookmarks"))
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun cleanup() = clean()

    @Test fun documentOverrideSurvivesSwitchingRecreationAndMetadataReloadThenResets() {
        ReadingProfile(17, true, ReadingFont.SANS, 150).save(preferences)
        ActivityScenario.launch<MainActivity>(intent("sample.md")).use { scenario ->
            ready(scenario)
            settings(scenario) { root ->
                root.findViewById<CheckBox>(R.id.document_reading_setting).isChecked = true
                root.findViewById<SeekBar>(R.id.font_size).progress = 12 // 24sp
                root.findViewById<CheckBox>(R.id.line_wrap).isChecked = false
                root.findViewById<Spinner>(R.id.reading_font).setSelection(ReadingFont.SERIF.ordinal)
                root.findViewById<SeekBar>(R.id.line_spacing).progress = 8 // 200%
            }
            ready(scenario)
            val expected = ReadingProfile(24, false, ReadingFont.SERIF, 200)
            assertEquals(ReadingProfile(17, true, ReadingFont.SANS, 150), ReadingProfile.defaults(preferences))
            assertEquals(expected, repository.readingState(uri("sample.md")).profile)
            scenario.recreate(); ready(scenario)
            scenario.onActivity { assertEquals(24, web(it.window.decorView)!!.settings.defaultFontSize) }
            scenario.onActivity { it.onNewIntentForTest(intent("sample.json")) }
            ready(scenario)
            scenario.onActivity { assertEquals(17, web(it.window.decorView)!!.settings.defaultFontSize) }
            scenario.onActivity { it.onNewIntentForTest(intent("sample.md")) }
            ready(scenario)
            assertEquals(expected, OpenDocuments(context).entries.first { it.uri == uri("sample.md").toString() }.reading.profile)
            scenario.onActivity { assertEquals(24, web(it.window.decorView)!!.settings.defaultFontSize) }
            settings(scenario) { it.findViewById<View>(R.id.reset_document_reading).performClick() }
            ready(scenario)
            assertNull(repository.readingState(uri("sample.md")).profile)
            scenario.onActivity { assertEquals(17, web(it.window.decorView)!!.settings.defaultFontSize) }
        }
    }

    @Test fun migrationBoundsBookmarkSnapshotAndUnavailableReferenceCleanup() {
        val legacy = JSONObject("""{"uri":"${uri("missing.txt")}","name":"Missing","openedAt":1,"fontSize":25}""")
        context.getSharedPreferences("recent_files", Context.MODE_PRIVATE).edit().putString("entries", "[$legacy]").commit()
        assertNull(repository.readingState(uri("missing.txt")).profile)
        val profile = ReadingProfile.fromJson(JSONObject("""{"fontSize":999,"lineSpacing":1,"font":"injected;css"}"""))!!
        assertEquals(ReadingProfile(28, true, ReadingFont.SANS, 120), profile)
        val state = ReadingState(profile = profile)
        repository.saveReadingState(uri("missing.txt"), state)
        assertEquals(state, DocumentRepository(context).readingState(uri("missing.txt")))
        val bookmarks = Bookmarks(context)
        assertTrue(bookmarks.add(uri("missing.txt").toString(), "Missing", "Position", 0.5, "fingerprint", state))
        assertEquals(state, Bookmarks(context).entries().single().reading)
        repository.remove(uri("missing.txt").toString())
        assertEquals(ReadingState(), repository.readingState(uri("missing.txt")))
        assertEquals(profile, bookmarks.entries().single().reading.profile)
        bookmarks.clear()
        assertTrue(bookmarks.entries().isEmpty())
    }

    @Test fun sharedTextProfileIsSessionOnlyAndGlobalDefaultsCanChange() {
        val shared = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Private ephemeral fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(shared).use { scenario ->
            ready(scenario)
            settings(scenario) {
                it.findViewById<CheckBox>(R.id.document_reading_setting).isChecked = true
                it.findViewById<SeekBar>(R.id.font_size).progress = 10
            }
            ready(scenario); scenario.recreate(); ready(scenario)
            scenario.onActivity { assertEquals(22, web(it.window.decorView)!!.settings.defaultFontSize) }
            assertTrue(repository.recentFiles().isEmpty())
            assertTrue(OpenDocuments(context).entries.isEmpty())
            assertEquals(16, ReadingProfile.defaults(preferences).fontSize)
        }
    }

    private fun MainActivity.onNewIntentForTest(intent: Intent) {
        MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply { isAccessible = true }.invoke(this, intent)
    }
    private fun settings(scenario: ActivityScenario<MainActivity>, change: (View) -> Unit) {
        scenario.onActivity {
            MainActivity::class.java.getDeclaredMethod("showSettings").apply { isAccessible = true }.invoke(it)
        }
        await {
            var found = false
            scenario.onActivity { found = WindowInspector.getGlobalWindowViews().any { it.findViewById<View>(R.id.font_size)?.isLaidOut == true } }
            found
        }
        scenario.onActivity {
            val root = WindowInspector.getGlobalWindowViews().first { it.findViewById<View>(R.id.font_size) != null }
            change(root)
            root.findViewById<View>(android.R.id.button1).performClick()
        }
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var ready = false
        scenario.onActivity { ready = web(it.window.decorView)?.tag == true }
        ready
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) { if (condition()) return; Thread.sleep(50) }
        fail("Reader/control did not become ready")
    }
    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
        return null
    }
}
