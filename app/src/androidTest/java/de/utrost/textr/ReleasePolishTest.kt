package de.utrost.textr

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ReleasePolishTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val automation get() = instrumentation.getUiAutomation(Configurator.getInstance().uiAutomationFlags)
    private val talkbackRequested get() = InstrumentationRegistry.getArguments().getString("talkback") == "true"
    private fun intent(name: String? = null) = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (name != null) { action = Intent.ACTION_VIEW; data = Uri.parse("content://de.utrost.textr.test.documents/$name") }
    }
    @Before fun reset() {
        if (talkbackRequested) Configurator.getInstance().uiAutomationFlags = android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        context.getSharedPreferences("reading", 0).edit().clear().commit()
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        DocumentRepository(context).apply { setSessionReferences(emptySet()); clear() }
    }
    @Test fun stalledReadCanBeCancelledWithoutRememberingOrResurrectingIt() {
        val activity = instrumentation.startActivitySync(intent("slow.txt")) as MainActivity
        try {
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.slow_reading))), 4000))
            click(By.desc(context.getString(R.string.cancel_loading)))
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.recent_files))), 2000))
            instrumentation.runOnMainSync {
                assertNull(activity.findViewById<View>(R.id.reader_loading))
                activity.startActivity(intent("sample.json"))
            }
            await {
                var ready = false
                instrumentation.runOnMainSync { ready = activity.findViewById<WebView>(R.id.reader)?.tag == true }
                ready
            }
            Thread.sleep(5200)
            assertTrue(device.hasObject(By.text("sample.json")))
            assertFalse(DocumentRepository(context).recentFiles().any { it.name == "slow.txt" })
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun largeDocumentLayoutCanBeCancelledAndAnotherFileOpened() {
        val activity = instrumentation.startActivitySync(intent("escaped.txt")) as MainActivity
        try {
            await {
                var layingOut = false
                instrumentation.runOnMainSync {
                    layingOut = activity.findViewById<WebView>(R.id.reader)?.tag == false &&
                        activity.findViewById<TextView>(R.id.loading_stage)?.text == activity.getString(R.string.laying_out)
                }
                layingOut
            }
            instrumentation.runOnMainSync {
                fun cancel(view: View): Boolean {
                    if (view is android.widget.Button && view.contentDescription == activity.getString(R.string.cancel_loading)) {
                        view.performClick(); return true
                    }
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) if (cancel(view.getChildAt(i))) return true
                    return false
                }
                assertTrue("Cancel is exposed during WebView layout", cancel(activity.window.decorView))
                assertNull(activity.findViewById<View>(R.id.reader_loading))
                assertNull(activity.findViewById<WebView>(R.id.reader))
                activity.startActivity(intent("sample.json"))
            }
            await {
                var ready = false
                instrumentation.runOnMainSync { ready = activity.findViewById<WebView>(R.id.reader)?.tag == true }
                ready
            }
            assertTrue(device.wait(Until.hasObject(By.text("sample.json")), 5000))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun openingHelpIsContextualAndDismissalSurvivesRecreation() {
        ActivityScenario.launch<MainActivity>(intent()).use { scenario ->
            scenario.onActivity { assertTrue(it.findViewById<View>(R.id.opening_hint).isShown) }
            click(By.desc(context.getString(R.string.opening_help)))
            assertTrue(device.wait(Until.hasObject(By.textContains("MIME")), 3000))
            device.pressBack()
            click(By.desc(context.getString(R.string.dismiss_hint)))
            scenario.recreate()
            scenario.onActivity { assertNull(it.findViewById<View>(R.id.opening_hint)) }
            click(By.desc(context.getString(R.string.more_options)))
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.opening_help))), 2000))
            device.pressBack()
        }
    }
    @Test fun wideHintNeverEnablesSidebarAndDismissalPersists() {
        val size = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        val density = Regex("Override density: (\\d+)").find(device.executeShellCommand("wm density"))?.groupValues?.get(1)
        try {
            device.executeShellCommand("wm size 1200x1600")
            device.executeShellCommand("wm density 240")
            ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
                ready(scenario)
                await { var shown = false; scenario.onActivity { shown = it.findViewById<View>(R.id.sidebar_hint).isShown }; shown }
                scenario.onActivity { assertFalse(it.findViewById<View>(R.id.file_sidebar).isShown) }
                click(By.desc(context.getString(R.string.dismiss_hint)))
                scenario.recreate(); ready(scenario)
                scenario.onActivity { assertFalse(it.findViewById<View>(R.id.sidebar_hint).isShown) }
                assertFalse(context.getSharedPreferences("reading", 0).getBoolean("fileSidebar", false))
            }
        } finally {
            device.executeShellCommand("wm size ${size ?: "reset"}")
            device.executeShellCommand("wm density ${density ?: "reset"}")
        }
    }
    @Test fun germanResourcesAndPreviewLabelsPreserveDocumentContents() {
        val german = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) })
        assertEquals("Datei öffnen", german.getString(R.string.open_file))
        assertTrue(german.getString(R.string.opening_help_text).contains("MIME-Typ"))
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        val labels = english.resources.getStringArray(R.array.preview_labels).zip(german.resources.getStringArray(R.array.preview_labels)).toMap()
        val source = TextDocument("example.md", "# Keep my title\n![photo](https://example.com/image)", FileKind.MARKDOWN, 60)
        val prepared = DocumentRenderer.prepare(source, translate = { labels[it] ?: it })
        assertEquals(source.text, prepared.source.text)
        assertTrue(prepared.preview!!.contains("Keep my title"))
        assertTrue(prepared.preview.contains("Bild ausgelassen"))
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(android.app.LocaleManager::class.java)
            val original = manager.applicationLocales
            try {
                manager.applicationLocales = LocaleList.forLanguageTags("de")
                ActivityScenario.launch<MainActivity>(intent()).use { scenario ->
                    assertTrue(device.wait(Until.hasObject(By.text("Datei öffnen")), 5000))
                    scenario.onActivity { assertEquals("Weitere Optionen", it.getString(R.string.more_options)) }
                }
            } finally { manager.applicationLocales = original }
        }
    }
    @Test fun largeTextDialogActionsStayFullyVisibleOnCompactAndWideWindows() {
        val size = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        val density = Regex("Override density: (\\d+)").find(device.executeShellCommand("wm density"))?.groupValues?.get(1)
        val font = device.executeShellCommand("settings get system font_scale").trim()
        val manager = if (Build.VERSION.SDK_INT >= 33) context.getSystemService(android.app.LocaleManager::class.java) else null
        val locales = if (Build.VERSION.SDK_INT >= 33) manager?.applicationLocales else null
        try {
            if (Build.VERSION.SDK_INT >= 33) manager?.applicationLocales = LocaleList.forLanguageTags("de")
            device.executeShellCommand("settings put system font_scale 2.0")
            for ((dimensions, dpi) in listOf("720x1280" to 360, "1200x1600" to 240)) {
                device.executeShellCommand("wm size $dimensions")
                device.executeShellCommand("wm density $dpi")
                ActivityScenario.launch<MainActivity>(intent()).use { scenario ->
                    var menu = ""; var settings = ""
                    scenario.onActivity { menu = it.getString(R.string.more_options); settings = it.getString(R.string.settings) }
                    click(By.desc(menu)); click(By.text(settings))
                    await("Settings dialog is attached and measured before checking clipping") {
                        var measured = false
                        scenario.onActivity {
                            val action = android.view.inspector.WindowInspector.getGlobalWindowViews()
                                .firstNotNullOfOrNull { root -> root.findViewById<TextView>(android.R.id.button1) }
                            measured = action?.isShown == true && action.isLaidOut && action.width > 0 && action.height > 0
                        }
                        measured
                    }
                    scenario.onActivity {
                        val dialog = android.view.inspector.WindowInspector.getGlobalWindowViews()
                            .first { root -> root.findViewById<View>(android.R.id.button1)?.isShown == true }
                        for (id in listOf(android.R.id.button1, android.R.id.button2)) {
                            val action = dialog.findViewById<TextView>(id)
                            val visible = android.graphics.Rect()
                            assertTrue(action.getGlobalVisibleRect(visible))
                            assertEquals("Unclipped action height: ${action.text}", action.height, visible.height())
                            assertEquals("Unclipped action width: ${action.text}", action.width, visible.width())
                            assertTrue("Text fits action", action.layout.height <= action.height - action.totalPaddingTop - action.totalPaddingBottom)
                        }
                    }
                    click(By.res("android", "button2"))
                }
                val uri = Uri.parse("content://de.utrost.textr.test.documents/large-native.csv")
                context.contentResolver.call(uri, "replace", uri.toString(), android.os.Bundle().apply { putString("text", "Name,Value\nLarge target,Second value\n") })
                ActivityScenario.launch<MainActivity>(intent("large-native.csv")).use { scenario ->
                    ready(scenario)
                    var nativeLabel = ""; var nativeMenu = ""
                    scenario.onActivity { nativeLabel = it.getString(R.string.csv_accessible); nativeMenu = it.getString(R.string.more_options) }
                    click(By.desc(nativeMenu)); click(By.text(nativeLabel))
                    scenario.onActivity {
                        val dialog = android.view.inspector.WindowInspector.getGlobalWindowViews()
                            .first { root -> root.findViewById<View>(R.id.csv_accessible_table) != null }
                        for (id in listOf(android.R.id.button2, android.R.id.button3)) {
                            val action = dialog.findViewById<TextView>(id)
                            val visible = android.graphics.Rect()
                            assertTrue(action.getGlobalVisibleRect(visible))
                            assertEquals("Native table action height", action.height, visible.height())
                            assertEquals("Native table action width", action.width, visible.width())
                            assertTrue("Native action text fits", action.layout.height <= action.height - action.totalPaddingTop - action.totalPaddingBottom)
                        }
                    }
                    await("Native table cells remain reachable at 200% text") {
                        if (accessibleNode { it.text?.toString() == "Large target" } != null) true
                        else {
                            accessibleNode { it.isScrollable }?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                            false
                        }
                    }
                    click(By.res("android", "button2"))
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT >= 33) manager?.applicationLocales = locales ?: LocaleList.getEmptyLocaleList()
            device.executeShellCommand("wm size ${size ?: "reset"}")
            device.executeShellCommand("wm density ${density ?: "reset"}")
            device.executeShellCommand("settings put system font_scale ${font.takeUnless { it == "null" } ?: "1.0"}")
        }
    }

    @Test fun csvSourceFallbackRemainsAccessibleAcrossPreviewChanges() {
        val uri = Uri.parse("content://de.utrost.textr.test.documents/accessibility-probe.csv")
        val csv = "Heading,Other\nCell target,Second cell\n"
        context.contentResolver.call(uri, "replace", uri.toString(), android.os.Bundle().apply { putString("text", csv) })
        ActivityScenario.launch<MainActivity>(intent("accessibility-probe.csv")).use { scenario ->
            repeat(3) { iteration ->
                ready(scenario)
                click(By.res(context.packageName, "display_mode")); click(By.text(context.getString(R.string.preview)))
                ready(scenario)
                // WebView diagnostic only; the native compatibility table owns cell-navigation guarantees.
                val visible = accessibleTextContains("Cell target", 1500)
                android.util.Log.i("TextrCsvAccessibility", "iteration=$iteration cellExposed=$visible webview=${WebView.getCurrentWebViewPackage()?.versionName}")
                device.dumpWindowHierarchy(java.io.File(context.cacheDir, "csv-accessibility-$iteration.xml"))
                click(By.res(context.packageName, "display_mode")); click(By.text(context.getString(R.string.plain_text)))
                ready(scenario)
                click(By.desc(context.getString(R.string.more_options)))
                click(By.text(context.getString(R.string.csv_accessible)))
                click(By.res(context.packageName, "csv_accessible_source_toggle"))
                assertTrue("Source remains in Android accessibility tree", accessibleTextContains("Cell target", 5000))
                scenario.onActivity {
                    val source = androidx.lifecycle.ViewModelProvider(it)[ReaderSession::class.java].document!!.source
                    assertEquals(csv, source.text)
                    assertFalse(it.findViewById<WebView>(R.id.reader).settings.javaScriptEnabled)
                }
                click(By.text(context.getString(R.string.done)))
                scenario.recreate()
            }
        }
    }

    @Test fun accessibleCsvCellsKeepTableSemanticsAndFocusAcrossModeChanges() {
        assertTalkBackRunningIfRequested()
        // UiAutomation exercises the real Android accessibility bridge; DOM search
        // and painted text alone cannot establish screen-reader cell navigation.
        val uri = Uri.parse("content://de.utrost.textr.test.documents/accessibility-navigation.csv")
        val csv = "Heading,Other\nCell target,Second cell\n"
        context.contentResolver.call(uri, "replace", uri.toString(), android.os.Bundle().apply { putString("text", csv) })
        ActivityScenario.launch<MainActivity>(intent("accessibility-navigation.csv")).use { scenario ->
            repeat(4) {
                ready(scenario)
                click(By.res(context.packageName, "display_mode")); click(By.text(context.getString(R.string.preview)))
                ready(scenario)
                click(By.desc(context.getString(R.string.more_options)))
                click(By.text(context.getString(R.string.csv_accessible)))
                for (label in listOf("Heading", "Other", "Cell target", "Second cell")) {
                    await("CSV cell cannot receive accessibility focus: $label") {
                        accessibleNode { node -> node.text?.toString() == label }?.let { node ->
                            val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
                            if (manager.isTouchExplorationEnabled) {
                                if (!node.isAccessibilityFocused) node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                                node.refresh() && node.isAccessibilityFocused
                            } else node.isFocusable && node.actionList.any {
                                it.id == android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                            }
                        } == true
                    }
                }
                val table = accessibleNode { it.collectionInfo?.let { info -> info.rowCount >= 2 && info.columnCount == 2 } == true }
                assertNotNull("CSV retains native table row/column semantics", table)
                scenario.recreate()
                await { accessibleNode { it.text?.toString() == "Cell target" } != null }
                click(By.text(context.getString(R.string.done)))
                click(By.res(context.packageName, "display_mode")); click(By.text(context.getString(R.string.plain_text)))
                ready(scenario)
                click(By.desc(context.getString(R.string.more_options)))
                click(By.text(context.getString(R.string.csv_accessible)))
                click(By.res(context.packageName, "csv_accessible_source_toggle"))
                assertTrue("Source stays accessible after navigating cells", accessibleTextContains("Cell target", 5000))
                click(By.text(context.getString(R.string.done)))
                assertTalkBackRunningIfRequested()
            }
        }
    }

    @Test fun accessibleCsvSearchCopyRestorationAndSharingKeepOriginalBytes() {
        val uri = Uri.parse("content://de.utrost.textr.test.documents/native-table.csv")
        val value = "Far <&> value"
        val csv = "\uFEFFName;Type;Third;Fourth;Value\r\n" + (0 until 80).joinToString("\r\n") { row ->
            (0 until 5).joinToString(";") { column -> if (row == 70 && column == 4) value else "Row $row field $column" }
        } + "\r\n"
        context.contentResolver.call(uri, "replace", uri.toString(), android.os.Bundle().apply { putString("text", csv) })
        DocumentRepository(context).apply {
            remember(uri, "native-table.csv")
            saveReadingState(uri, ReadingState(csv = CsvOptions(';', true)))
        }
        ActivityScenario.launch<MainActivity>(intent("native-table.csv")).use { scenario ->
            ready(scenario)
            click(By.desc(context.getString(R.string.more_options))); click(By.text(context.getString(R.string.csv_accessible)))
            fun root() = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .first { it.findViewById<View>(R.id.csv_accessible_table) != null }
            scenario.onActivity { root().findViewById<android.widget.EditText>(R.id.csv_accessible_query).setText(value) }
            await { accessibleNode { it.text?.toString() == value && it.collectionItemInfo != null } != null }
            scenario.recreate()
            await { accessibleNode { it.text?.toString() == value && it.collectionItemInfo != null } != null }
            scenario.onActivity {
                assertEquals(value, root().findViewById<android.widget.EditText>(R.id.csv_accessible_query).text.toString())
                assertEquals(it.getString(R.string.csv_cell_matches, 1, 1), root().findViewById<TextView>(R.id.csv_accessible_results).text.toString())
                fun cell(view: View): TextView? {
                    if (view is TextView && view.id != R.id.csv_accessible_query && view.text.toString() == value) return view
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) cell(view.getChildAt(i))?.let { return it }
                    return null
                }
                val selected = cell(root())!!
                assertTrue(selected.isTextSelectable)
                android.text.Selection.setSelection(selected.text as android.text.Spannable, 0, value.length)
                assertTrue(selected.onTextContextMenuItem(android.R.id.copy))
                assertEquals(value, it.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
            }
            val target = accessibleNode { it.text?.toString() == value && it.collectionItemInfo != null }!!
            assertEquals(71, target.collectionItemInfo.rowIndex)
            assertEquals(4, target.collectionItemInfo.columnIndex)
            click(By.res(context.packageName, "csv_accessible_source_toggle"))
            await { accessibleNode { it.viewIdResourceName == context.packageName + ":id/csv_accessible_source" } != null }
            scenario.recreate()
            await { accessibleNode { it.viewIdResourceName == context.packageName + ":id/csv_accessible_source" } != null }
            scenario.onActivity {
                val source = root().findViewById<TextView>(R.id.csv_accessible_source)
                assertTrue(source.isTextSelectable)
                assertEquals(csv.removePrefix("\uFEFF"), source.text.toString())
                val hierarchy = android.util.SparseArray<android.os.Parcelable>()
                root().saveHierarchyState(hierarchy)
                val parcel = android.os.Parcel.obtain()
                try {
                    val state = android.os.Bundle().apply { putSparseParcelableArray("views", hierarchy) }
                    state.writeToParcel(parcel, 0)
                    assertFalse("Native hierarchy must not save document contents", String(parcel.marshall(), Charsets.UTF_16LE).contains("Row 69 field 2"))
                } finally { parcel.recycle() }
                assertTrue(root().findViewById<android.widget.CheckBox>(R.id.csv_accessible_source_toggle).isChecked)
                assertEquals(value, root().findViewById<android.widget.EditText>(R.id.csv_accessible_query).text.toString())
            }
            val captured = java.util.concurrent.atomic.AtomicReference<Intent>()
            val monitor = object : android.app.Instrumentation.ActivityMonitor() {
                override fun onStartActivity(launch: Intent): android.app.Instrumentation.ActivityResult? {
                    @Suppress("DEPRECATION")
                    val inner = if (launch.action == Intent.ACTION_CHOOSER) launch.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) else launch
                    if (inner?.action != Intent.ACTION_SEND) return null
                    captured.set(inner)
                    return android.app.Instrumentation.ActivityResult(0, null)
                }
            }
            instrumentation.addMonitor(monitor)
            try {
                val share = accessibleNode { it.text?.toString() == context.getString(R.string.share_file) && it.isClickable }
                assertNotNull("Native share action is accessible", share)
                assertTrue(share!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                await { captured.get() != null }
                val shared = captured.get()
                assertTrue(shared.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertEquals(0, shared.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                @Suppress("DEPRECATION") val sharedUri = shared.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
                assertArrayEquals(csv.toByteArray(Charsets.UTF_8), context.contentResolver.openInputStream(sharedUri)!!.use { it.readBytes() })
                assertArrayEquals(csv.toByteArray(Charsets.UTF_8), context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            } finally { instrumentation.removeMonitor(monitor) }
        }
    }

    @Test fun nativeTablePreparationCanBeCancelledAtTheCellBudget() {
        val uri = Uri.parse("content://de.utrost.textr.test.documents/native-budget.csv")
        val csv = (List(100) { "value" }.joinToString(",") + "\n").repeat(100)
        context.contentResolver.call(uri, "replace", uri.toString(), android.os.Bundle().apply { putString("text", csv) })
        val activity = instrumentation.startActivitySync(intent("native-budget.csv")) as MainActivity
        fun readyFile(name: String) = await {
            var loaded = false
            instrumentation.runOnMainSync {
                loaded = activity.findViewById<WebView>(R.id.reader)?.tag == true &&
                    androidx.lifecycle.ViewModelProvider(activity)[ReaderSession::class.java].document?.source?.name == name
            }
            loaded
        }
        try {
            readyFile("native-budget.csv")
            var menuLabel = ""; var tableLabel = ""
            instrumentation.runOnMainSync { menuLabel = activity.getString(R.string.more_options); tableLabel = activity.getString(R.string.csv_accessible) }
            click(By.desc(menuLabel))
            // Activate the actual popup item on the UI thread. Waiting for an
            // accessibility IPC/idle round trip can let a fast device finish the
            // entire table before this cancellation assertion begins.
            instrumentation.runOnMainSync {
                fun menu(view: View): android.widget.ListView? {
                    if (view is android.widget.ListView && (0 until view.adapter.count).any { index ->
                            (view.adapter.getItem(index) as? android.view.MenuItem)?.title?.toString() == tableLabel
                        }) return view
                    if (view is android.view.ViewGroup) for (index in 0 until view.childCount) menu(view.getChildAt(index))?.let { return it }
                    return null
                }
                val list = android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOf(::menu)
                val position = (0 until list.adapter.count).first { (list.adapter.getItem(it) as? android.view.MenuItem)?.title?.toString() == tableLabel }
                assertTrue(list.performItemClick(list.getChildAt(position - list.firstVisiblePosition), position, list.adapter.getItemId(position)))
                val root = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .first { it.findViewById<View>(R.id.csv_accessible_query) != null }
                assertFalse("Table is still being prepared", root.findViewById<View>(R.id.csv_accessible_query).isEnabled)
                assertTrue(root.findViewById<View>(android.R.id.button2).performClick())
            }
            await {
                android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .none { it.findViewById<View>(R.id.csv_accessible_query)?.isShown == true }
            }
            // ActivityScenario matches the original launch intent and loses its
            // lifecycle tracking after singleTop replaces that intent. Own this
            // activity explicitly, as the existing incoming-intent tests do.
            instrumentation.runOnMainSync { activity.startActivity(intent("sample.json").setFlags(0)) }
            readyFile("sample.json")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun assertTalkBackRunningIfRequested() {
        if (!talkbackRequested) return
        // Connecting automation normally suppresses other services. Keep TalkBack
        // active and verify that this run really exercises coexistence with it.
        automation
        val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        await("TalkBack must be bound with touch exploration for a requested TalkBack run") {
            manager.isTouchExplorationEnabled &&
                manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" }
        }
    }

    private fun accessibleNode(matches: (android.view.accessibility.AccessibilityNodeInfo) -> Boolean): android.view.accessibility.AccessibilityNodeInfo? {
        val automation = automation
        if (Build.VERSION.SDK_INT >= 33) automation.clearCache()
        val pending = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        automation.rootInActiveWindow?.let(pending::add)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (!node.refresh()) continue
            if (node.isVisibleToUser && matches(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(pending::add)
        }
        return null
    }

    private fun accessibleTextContains(text: String, timeoutMillis: Long): Boolean {
        val automation = automation
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        var labels = emptyList<String>()
        while (System.nanoTime() < deadline) {
            // The probe recreates virtual trees rapidly. Read fresh platform
            // nodes, as the CSV native-selection regression does, rather than
            // repeatedly traversing UiAutomator's cached empty descendants.
            if (Build.VERSION.SDK_INT >= 33) automation.clearCache()
            val pending = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            automation.rootInActiveWindow?.let(pending::add)
            val current = mutableListOf<String>()
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                if (!node.refresh()) continue
                if (node.isVisibleToUser) {
                    val label = node.text?.toString().orEmpty()
                    if (label.contains(text)) return true
                    if (label.isNotEmpty()) current.add(label)
                }
                for (index in 0 until node.childCount) node.getChild(index)?.let(pending::add)
            }
            labels = current
            Thread.sleep(100)
        }
        android.util.Log.i("TextrCsvAccessibility", "Missing $text; fresh visible labels: $labels")
        return false
    }

    private fun click(selector: BySelector) {
        var node = device.wait(Until.findObject(selector), 4000)
        if (node == null) node = device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
        assertNotNull("Missing $selector", node); node!!.click(); device.waitForIdle()
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var ready = false; scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }; ready
    }
    private fun await(message: String = "Reader polish condition timed out", condition: () -> Boolean) {
        val until = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < until) { if (condition()) return; Thread.sleep(100) }
        device.dumpWindowHierarchy(java.io.File(context.cacheDir, "reader-polish-timeout.xml"))
        android.util.Log.e("TextrCsvAccessibility", message)
        fail(message)
    }
}
