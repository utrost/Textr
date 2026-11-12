package de.utrost.textr

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentRoutingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(name: String) = Uri.parse("content://de.utrost.textr.test.documents/$name")

    @Test fun implicitViewAndShareRoutesResolveAndReadTheSelectedDocument() {
        val types = listOf(
            "sample.txt" to "text/plain", "sample.log" to "text/plain",
            "sample.conf" to "application/octet-stream", "sample.cfg" to "application/octet-stream",
            "sample.ini" to "application/ini", "sample.json" to "application/json",
            "sample.md" to "text/markdown", "sample.yaml" to "application/yaml",
            "sample.toml" to "application/toml", "sample.xml" to "application/xml",
            "sample.csv" to "text/csv", "UPPER.JSON" to "application/octet-stream",
            "extensionless-json" to "application/json", "null-metadata.txt" to null
        )
        for ((name, type) in types) for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_SEND)) {
            // Android has no reliable file-sharing contract when a sender omits SEND's type.
            if (type == null && action == Intent.ACTION_SEND) continue
            val intent = Intent(action).setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (action == Intent.ACTION_VIEW) intent.setDataAndType(uri(name), type)
            else intent.setType(type).putExtra(Intent.EXTRA_STREAM, uri(name))
            assertTrue("No handler for $action / $name / $type",
                context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty())
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                val deadline = System.nanoTime() + 30_000_000_000L
                var opened = false
                while (System.nanoTime() < deadline && !opened) {
                    scenario.onActivity {
                        val document = ViewModelProvider(it)[ReaderSession::class.java].document
                        opened = document?.source?.name == name && it.findViewById<WebView>(R.id.reader)?.tag == true
                    }
                    if (!opened) Thread.sleep(50)
                }
                assertTrue("Did not open $action / $name / $type", opened)
            }
        }
    }
}
