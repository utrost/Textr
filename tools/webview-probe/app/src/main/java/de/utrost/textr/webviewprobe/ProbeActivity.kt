package de.utrost.textr.webviewprobe

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout

/** Script-free, synthetic documents only. No Textr runtime code or document provider. */
class ProbeActivity : Activity() {
    lateinit var reader: WebView; private set
    private var table = false
    private var generation = 0
    private val sample get() = intent.getIntExtra("sample", 0)
    val marker get() = (if (table) "ProbeCell_" else "ProbeText_") + sample.toString().padStart(4, '0') + "_END"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        table = savedInstanceState?.getBoolean("table") ?: false
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val controls = LinearLayout(this)
        controls.addView(Button(this).apply { text = "Source"; setOnClickListener { showTable(false) } })
        controls.addView(Button(this).apply { text = "Table"; setOnClickListener { showTable(true) } })
        root.addView(controls)
        reader = WebView(this).apply {
            isSaveEnabled = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
            settings.apply {
                javaScriptEnabled = false; allowFileAccess = false; allowContentAccess = false
                blockNetworkLoads = true; blockNetworkImage = true; loadsImagesAutomatically = false
                domStorageEnabled = false; safeBrowsingEnabled = false
            }
        }
        root.addView(reader, LinearLayout.LayoutParams(-1, 0, 1f))
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
        render()
    }
    fun showTable(value: Boolean) { table = value; render() }
    private fun render() {
        val request = ++generation
        val url = "https://webview-probe.invalid/document?sample=$sample&table=$table&generation=$request"
        reader.tag = false
        reader.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, loaded: String) {
                if (request != generation || loaded != url) return
                view.postVisualStateCallback(request.toLong(), object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        view.postOnAnimation {
                            if (request == generation && !isDestroyed) {
                                view.tag = true
                            }
                        }
                        view.invalidate()
                    }
                })
            }
        }
        val body = if (!table) "<pre>$marker</pre>" else buildString {
            append("<table><thead><tr><th>Probe header 1</th><th>Probe header 2</th></tr></thead><tbody>")
            append("<tr><td>$marker</td><td>First row</td></tr>")
            repeat(25) { append("<tr><td>Row $it</td><td>Value $it</td></tr>") }
            append("</tbody></table>")
        }
        reader.loadDataWithBaseURL(url, "<!doctype html><html lang='en'><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{padding:20px;color:#111;background:white}pre{font:16px monospace}table{border-collapse:collapse}td,th{padding:10px;border:1px solid #777}</style></head><body><main>$body</main></body></html>", "text/html", "UTF-8", null)
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("table", table); super.onSaveInstanceState(outState) }
    override fun onDestroy() {
        generation++
        (reader.parent as? android.view.ViewGroup)?.removeView(reader)
        reader.destroy()
        super.onDestroy()
    }
}
