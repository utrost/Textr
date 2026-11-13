package de.utrost.textr

import android.app.UiAutomation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Also runs with an installed TalkBack service enabled; the connection must not suppress it. */
@RunWith(AndroidJUnit4::class)
class AccessibilityNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private var previousAutomationFlags = 0

    @Before fun preserveAccessibilityServices() {
        val configuration = Configurator.getInstance()
        previousAutomationFlags = configuration.uiAutomationFlags
        configuration.uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        automation
    }

    @After fun restoreAutomationConnection() {
        Configurator.getInstance().uiAutomationFlags = previousAutomationFlags
        // Changing flags reconnects UiAutomation. Complete that transition before
        // another test creates its WebView, not during its first node lookup.
        instrumentation.getUiAutomation(previousAutomationFlags)
    }

    @Test fun accessibilityFocusAndActionsReachReaderHistoryAndRecovery() {
        val services = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services")
        android.util.Log.i("TextrAccessibility", "Enabled services: $services")
        if (InstrumentationRegistry.getArguments().getString("talkback") == "true") {
            assertTrue("TalkBack enabled for this acceptance run", services.orEmpty().contains("TalkBackService"))
            val manager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!manager.isTouchExplorationEnabled && System.nanoTime() < deadline) Thread.sleep(100)
            assertTrue("TalkBack touch exploration is running", manager.isTouchExplorationEnabled)
        }
        context.getSharedPreferences("open_documents", 0).edit().clear().commit()
        DocumentRepository(context).setSessionReferences(emptySet())
        DocumentRepository(context).clear()
        context.getSharedPreferences("reading", 0).edit().clear().commit()
        ActivityScenario.launch<MainActivity>(intent("sample.json")).use { scenario ->
            ready(scenario)
            // Connecting UiAutomation enables WebView's accessibility bridge asynchronously.
            node { it.text?.contains("Textr fixture") == true }
            activate("Display mode: Preview")
            activate("Plain text")
            ready(scenario)
            activate("Find")
            val query = node { it.viewIdResourceName == "de.utrost.textr:id/search_query" }
            assertTrue(query.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "fixture")
            }))
            node { it.contentDescription?.toString() == "Match 1 of 1" }
            activate("Next match")
            activate("Previous match")
            activate("Close search")
            activate("More options")
            activate("Reading settings")
            node { it.viewIdResourceName == "android:id/button1" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ready(scenario)
            activate("Back to files")
            activate("sample.json")
            ready(scenario)
            activate("Back to files")
            focus(node { it.contentDescription?.toString() == "Remove sample.json from recent files" })
            activate("More options")
            activate("Clear history")
            // Confirmation action is reachable through the accessibility tree.
            node { it.viewIdResourceName == "android:id/button1" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            activate("Open file")
            node { it.packageName?.toString()?.contains("documentsui") == true }
            dismissPicker()
            node { it.packageName == context.packageName && it.isVisibleToUser && it.contentDescription == "Open file" }
        }
        ActivityScenario.launch<MainActivity>(intent("missing.txt")).use {
            activate("Open file again")
            node { it.packageName?.toString()?.contains("documentsui") == true }
            dismissPicker()
            node { it.packageName == context.packageName && it.isVisibleToUser && it.contentDescription == "Open file again" }
        }
    }

    private fun intent(name: String) = Intent(Intent.ACTION_VIEW,
        Uri.parse("content://de.utrost.textr.test.documents/$name"), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun dismissPicker() {
        // DocumentsUI may remember a nested folder; Back first traverses parents.
        repeat(12) {
            if (automation.rootInActiveWindow?.packageName?.toString() == context.packageName) return
            assertTrue(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            val deadline = System.nanoTime() + 2_000_000_000L
            while (System.nanoTime() < deadline) {
                if (automation.rootInActiveWindow?.packageName?.toString() == context.packageName) return
                Thread.sleep(100)
            }
        }
        fail("Picker did not return to Textr")
    }

    private fun activate(label: String) {
        var target = node { it.isVisibleToUser && (it.contentDescription?.toString() == label || it.text?.toString().equals(label, true)) }
        // Native popup menus expose their label as a child of the actionable row.
        while (!target.isClickable) target = target.parent ?: throw AssertionError("No actionable parent for $label")
        focus(target)
        assertTrue("Accessibility activation: $label", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun focus(target: AccessibilityNodeInfo) {
        val manager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        // Android 10 rejects accessibility focus when no touch-exploration service
        // is running. CI still checks tree exposure/actions; the TalkBack run also
        // verifies real accessibility focus with the service left active.
        if (manager.isTouchExplorationEnabled) {
            assertTrue("Accessibility focus: ${target.contentDescription ?: target.text}", target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) || target.isAccessibilityFocused)
        }
    }
    private fun allNodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        automation.rootInActiveWindow?.let(pending::add)
        while (pending.isNotEmpty()) {
            val item = pending.removeFirst()
            result.add(item)
            for (i in 0 until item.childCount) item.getChild(i)?.let(pending::add)
        }
        return result
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            allNodes().firstOrNull(predicate)?.let { return it }
            Thread.sleep(100)
        }
        throw AssertionError("Accessibility node missing. Visible nodes: " + allNodes().map { "${it.text}/${it.contentDescription}/${it.viewIdResourceName}" })
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < deadline) {
            var ready = false
            scenario.onActivity { ready = it.findViewById<WebView>(R.id.reader)?.tag == true }
            if (ready) { Thread.sleep(300); return }
            Thread.sleep(100)
        }
        fail("Reader did not load")
    }
}
