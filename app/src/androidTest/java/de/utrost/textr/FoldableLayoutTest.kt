package de.utrost.textr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoldableLayoutTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")
    private fun intent(name: String) = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun tracePosition(stage: String, activity: android.app.Activity, state: Bundle? = null) {
        if (activity !is MainActivity) return
        val reader = activity.findViewById<WebView>(R.id.reader)
        android.util.Log.i("TextrPositionLifecycle", "$stage id=${System.identityHashCode(activity)} changing=${activity.isChangingConfigurations} saved=${state?.getInt("scroll", -1)} scroll=${reader?.scrollY} ready=${reader?.tag} content=${reader?.contentHeight} viewport=${reader?.width}x${reader?.height} widthDp=${activity.resources.configuration.screenWidthDp}")
    }
    private val positionTrace = object : android.app.Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: android.app.Activity, state: Bundle?) = tracePosition("created", activity, state)
        override fun onActivityStarted(activity: android.app.Activity) = tracePosition("started", activity)
        override fun onActivityResumed(activity: android.app.Activity) = tracePosition("resumed", activity)
        override fun onActivityPrePaused(activity: android.app.Activity) = tracePosition("pre-pause", activity)
        override fun onActivityPaused(activity: android.app.Activity) = tracePosition("paused", activity)
        override fun onActivityPostPaused(activity: android.app.Activity) = tracePosition("post-pause", activity)
        override fun onActivityStopped(activity: android.app.Activity) = tracePosition("stopped", activity)
        override fun onActivityPreSaveInstanceState(activity: android.app.Activity, state: Bundle) = tracePosition("pre-save", activity, state)
        override fun onActivitySaveInstanceState(activity: android.app.Activity, state: Bundle) = tracePosition("save-callback", activity, state)
        override fun onActivityPostSaveInstanceState(activity: android.app.Activity, state: Bundle) = tracePosition("post-save", activity, state)
        override fun onActivityDestroyed(activity: android.app.Activity) = tracePosition("destroyed", activity)
    }
    @Before fun observePositionLifecycle() {
        (context.applicationContext as android.app.Application).registerActivityLifecycleCallbacks(positionTrace)
    }
    @After fun stopObservingPositionLifecycle() {
        (context.applicationContext as android.app.Application).unregisterActivityLifecycleCallbacks(positionTrace)
    }

    @Before fun reset() {
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        DocumentRepository(context).setSessionReferences(emptySet())
        DocumentRepository(context).clear()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
    }

    @Test fun sidebarIsOptInAndSupportsRecentSwitchAndClose() = display {
        DocumentRepository(context).remember(uri("copy.txt"), "copy.txt")
        ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
            ready(scenario)
            sidebar(scenario, false)
            click(By.desc("More options")); click(By.text("Reading settings"))
            click(By.res(context.packageName, "sidebar_setting")); click(By.res("android", "button1"))
            ready(scenario); sidebar(scenario, true)
            assertTrue(context.getSharedPreferences("reading", 0).getBoolean("fileSidebar", false))
            click(By.desc("Open recent file: copy.txt")); ready(scenario)
            click(By.desc("Switch to sample.json")); ready(scenario)
            scenario.onActivity {
                assertEquals("sample.json", ViewModelProvider(it)[ReaderSession::class.java].document!!.source.name)
                assertEquals(View.GONE, it.findViewById<View>(R.id.document_strip).visibility)
                val reader = it.findViewById<WebView>(R.id.reader)
                val files = it.findViewById<View>(R.id.file_sidebar)
                val readerPosition = IntArray(2); val filesPosition = IntArray(2)
                reader.getLocationOnScreen(readerPosition); files.getLocationOnScreen(filesPosition)
                assertTrue(readerPosition[0] >= filesPosition[0] + files.width)
                assertTrue(reader.width / it.resources.displayMetrics.density >= 480)
                assertFalse(reader.settings.javaScriptEnabled)
            }
            click(By.desc("Close copy.txt"))
            // Input injection can return before the Android 10 click handler finishes.
            // Wait for completion before inspecting state; constructing OpenDocuments
            // here would itself save a potentially stale snapshot back to preferences.
            val closed = device.wait(Until.hasObject(By.desc("Open recent file: copy.txt")), 10000)
            if (!closed) {
                device.dumpWindowHierarchy(java.io.File(context.cacheDir, "foldable-close.xml"))
                device.takeScreenshot(java.io.File(context.cacheDir, "foldable-close.png"))
            }
            assertTrue("Closed document should return to sidebar recents", closed)
            scenario.onActivity {
                assertFalse(ViewModelProvider(it)[ReaderSession::class.java].openDocuments!!.entries.any { entry -> entry.name == "copy.txt" })
            }
            assertFalse(context.getSharedPreferences("open_documents", 0).getString("entries", "")!!.contains("copy.txt"))
            scenario.recreate(); ready(scenario); sidebar(scenario, true)
            // Switching the option off restores tabs without closing documents.
            click(By.desc("Open recent file: copy.txt")); ready(scenario)
            click(By.desc("More options")); click(By.text("Reading settings"))
            click(By.res(context.packageName, "sidebar_setting")); click(By.res("android", "button1"))
            ready(scenario); sidebar(scenario, false)
            scenario.onActivity { assertEquals(View.VISIBLE, it.findViewById<View>(R.id.document_strip).visibility) }
            scenario.onActivity { assertEquals(2, ViewModelProvider(it)[ReaderSession::class.java].openDocuments!!.entries.size) }
            assertFalse(context.getSharedPreferences("reading", 0).getBoolean("fileSidebar", true))
        }
    }

    @Test fun windowTransitionsPreserveDocumentModePositionAndFind() = display {
        context.getSharedPreferences("reading", 0).edit().putBoolean("fileSidebar", true).commit()
        val text = "{\n" + (1..300).joinToString(",\n") { "  \"entry$it\": \"needle\"" } + "\n}"
        context.contentResolver.call(uri("fold.json"), "replace", uri("fold.json").toString(), Bundle().apply { putString("text", text) })
        ActivityScenario.launch<MainActivity>(intent("fold.json")).use { scenario ->
            ready(scenario); sidebar(scenario, true)
            click(By.res(context.packageName, "display_mode")); click(By.text("Plain text")); ready(scenario)
            scenario.onActivity { it.findViewById<WebView>(R.id.reader).scrollTo(0, 600) }
            resize(scenario, "600x1200", 400)
            sidebar(scenario, false)
            scenario.onActivity {
                assertEquals("Plain text", it.findViewById<TextView>(R.id.display_mode).text.toString())
                assertEquals(600, it.findViewById<WebView>(R.id.reader).scrollY)
                assertEquals(text, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text)
            }
            // Inner displays can report only a medium-width window at default display scaling.
            resize(scenario, "900x1200", 600)
            sidebar(scenario, true)
            scenario.onActivity {
                assertTrue(it.findViewById<WebView>(R.id.reader).width / it.resources.displayMetrics.density >= 380)
                assertEquals("Plain text", it.findViewById<TextView>(R.id.display_mode).text.toString())
            }
            click(By.desc("Find"))
            scenario.onActivity {
                val field = it.findViewById<EditText>(R.id.search_query)
                field.setText("needle")
                (it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(field.windowToken, 0)
            }
            resize(scenario, "1200x1600", 800)
            sidebar(scenario, true)
            scenario.onActivity {
                assertEquals("needle", it.findViewById<EditText>(R.id.search_query).text.toString())
                assertTrue(it.findViewById<EditText>(R.id.search_query).isShown)
                assertEquals("Plain text", it.findViewById<TextView>(R.id.display_mode).text.toString())
                assertEquals(text, ViewModelProvider(it)[ReaderSession::class.java].document!!.source.text)
            }
            click(By.desc("Close search"))
            click(By.desc("Close fold.json"))
            assertTrue(device.wait(Until.hasObject(By.text("Recent files")), 5000))
            sidebar(scenario, false)
            await {
                var empty = false
                scenario.onActivity { empty = ViewModelProvider(it)[ReaderSession::class.java].openDocuments!!.entries.isEmpty() }
                empty
            }
            assertEquals("[]", context.getSharedPreferences("open_documents", 0).getString("entries", ""))
        }
    }

    @Test fun stateSaveRetainsPositionBeforePauseCallbacks() = display {
        val app = context.applicationContext as android.app.Application
        var target: MainActivity? = null
        var injected = false
        var stoppedPosition = -1
        val callbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: android.app.Activity) {
                if (activity === target && !injected) {
                    injected = true
                    activity.findViewById<WebView>(R.id.reader).scrollTo(0, 0)
                }
            }
            override fun onActivityCreated(activity: android.app.Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {
                if (activity === target) stoppedPosition = DocumentRepository(activity).readingState(uri("long.ini")).scroll
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
                ready(scenario)
                scenario.onActivity {
                    target = it
                    val reader = it.findViewById<WebView>(R.id.reader)
                    reader.scrollTo(0, 600); assertEquals(600, reader.scrollY)
                }
                scenario.recreate(); ready(scenario)
                scenario.onActivity {
                    assertTrue("Injected a clamp during framework pause callbacks", injected)
                    assertEquals("Stop-time checkpoint keeps the last visible position", 600, stoppedPosition)
                    assertEquals("Keep the last visible position through pause and save", 600, it.findViewById<WebView>(R.id.reader).scrollY)
                }
            }
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
    }

    @Test fun stateSaveCapturesPositionBeforeFrameworkViewCallbacks() = display {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = context.applicationContext as android.app.Application
        var target: MainActivity? = null
        var injected = false
        val callbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: Bundle) {
                if (activity === target && !injected) {
                    injected = true
                    // A resize/native state-save callback can clamp the old WebView.
                    // The activity must already have captured its reading position.
                    activity.findViewById<WebView>(R.id.reader).scrollTo(0, 0)
                }
            }
            override fun onActivityCreated(activity: android.app.Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
                ready(scenario)
                scenario.onActivity { activity ->
                    target = activity
                    val reader = activity.findViewById<WebView>(R.id.reader)
                    reader.scrollTo(0, 600); assertEquals(600, reader.scrollY)
                    val saved = Bundle()
                    instrumentation.callActivityOnSaveInstanceState(activity, saved)
                    assertTrue(injected); assertEquals(0, reader.scrollY)
                    assertEquals(600, saved.getInt("scroll", -1))
                }
            }
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
    }

    @Test fun recreationDuringPageLayoutKeepsPendingPosition() = display {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = context.applicationContext as android.app.Application
        val captured = java.util.concurrent.CountDownLatch(1)
        val savedPosition = java.util.concurrent.atomic.AtomicInteger(-1)
        val callbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, state: Bundle?) {
                if (activity !is MainActivity || state == null) return
                val decor = activity.window.decorView
                val listener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        val reader = activity.findViewById<WebView>(R.id.reader) ?: return
                        if (reader.tag == true || captured.count == 0L) return
                        decor.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        val saved = Bundle()
                        instrumentation.callActivityOnSaveInstanceState(activity, saved)
                        savedPosition.set(saved.getInt("scroll", -1))
                        captured.countDown()
                        activity.recreate()
                    }
                }
                decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
            }
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        }
        ActivityScenario.launch<MainActivity>(intent("long.ini")).use { scenario ->
            ready(scenario)
            scenario.onActivity {
                val reader = it.findViewById<WebView>(R.id.reader)
                reader.scrollTo(0, 600)
                assertEquals(600, reader.scrollY)
            }
            app.registerActivityLifecycleCallbacks(callbacks)
            try {
                scenario.recreate()
                assertTrue("Captured recreation during unfinished WebView layout", captured.await(15, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals("A loading WebView must not replace the pending saved position", 600, savedPosition.get())
                ready(scenario)
                scenario.onActivity { assertEquals(600, it.findViewById<WebView>(R.id.reader).scrollY) }
            } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
        }
    }

    @Test fun largeTextAndShortWindowsKeepSinglePane() = display {
        context.getSharedPreferences("reading", 0).edit().putBoolean("fileSidebar", true).commit()
        ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
            ready(scenario); sidebar(scenario, true)
            device.executeShellCommand("settings put system font_scale 2.0")
            await {
                var large = false
                scenario.onActivity { large = it.resources.configuration.fontScale >= 1.99f }
                large
            }
            ready(scenario); sidebar(scenario, false)
            scenario.onActivity { assertTrue(it.findViewById<WebView>(R.id.reader).width / it.resources.displayMetrics.density >= 790) }
            device.executeShellCommand("settings put system font_scale 1.0")
            await {
                var normal = false
                scenario.onActivity { normal = it.resources.configuration.fontScale <= 1.01f }
                normal
            }
            ready(scenario); sidebar(scenario, true)
            resize(scenario, "1200x600", 800)
            sidebar(scenario, false)
        }
    }

    private fun display(test: () -> Unit) {
        val size = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        val density = Regex("Override density: (\\d+)").find(device.executeShellCommand("wm density"))?.groupValues?.get(1)
        val font = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("wm size 1200x1600")
            device.executeShellCommand("wm density 240")
            device.executeShellCommand("settings put system font_scale 1.0")
            device.waitForIdle()
            test()
        } finally {
            device.executeShellCommand("wm size ${size ?: "reset"}")
            device.executeShellCommand("wm density ${density ?: "reset"}")
            device.executeShellCommand("settings put system font_scale ${font.takeUnless { it == "null" } ?: "1.0"}")
            device.waitForIdle()
        }
    }
    private fun resize(scenario: ActivityScenario<MainActivity>, size: String, width: Int) {
        var previous: MainActivity? = null
        scenario.onActivity { previous = it }
        scenario.onActivity { tracePosition("before-resize", it) }
        device.executeShellCommand("wm size $size")
        await {
            var changed = false
            scenario.onActivity { changed = it !== previous && it.resources.configuration.screenWidthDp in (width - 10)..(width + 10) }
            changed
        }
        ready(scenario)
        scenario.onActivity { tracePosition("resize-ready", it) }
    }
    private fun sidebar(scenario: ActivityScenario<MainActivity>, visible: Boolean) = await {
        var matches = false
        scenario.onActivity { matches = (it.findViewById<View>(R.id.file_sidebar).visibility == View.VISIBLE) == visible }
        matches
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var ready = false
        scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }
        ready
    }
    private fun click(selector: BySelector) {
        repeat(3) { attempt ->
            device.waitForIdle()
            var node = device.wait(Until.findObject(selector), 3000)
            if (node == null) node = device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
            assertNotNull("Missing $selector", node)
            try {
                node!!.click(); device.waitForIdle(); return
            } catch (error: StaleObjectException) {
                // Layout/discovery updates can invalidate a UiAutomator snapshot
                // between lookup and input. Reacquire it; do not retry assertions.
                if (attempt == 2) throw error
            }
        }
    }
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < until) { if (condition()) return; Thread.sleep(100) }
        fail("Foldable layout condition timed out")
    }
}
