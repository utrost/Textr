package de.utrost.textr

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.webkit.*
import android.widget.*
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.Executors

/** A cancellable preview of one immutable draft revision; never writes output or recovery. */
class DraftPreview(private val context: Context, text: String, dark: Boolean, scroll: Int,
    private val current: () -> Boolean, private val closed: (Int) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var disposed = false
    private var rendererGone = false
    private class PreviewWebView(context: Context) : WebView(context) {
        var afterDraw: (() -> Unit)? = null
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            afterDraw?.let { afterDraw = null; post(it) }
        }
    }
    private val view = PreviewWebView(context)
    private val status = TextView(context).apply { setText(R.string.preview_loading); accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
    val dialog: Dialog
    init {
        view.id = R.id.draft_preview
        view.setBackgroundColor(Color.parseColor(if (dark) "#171C1A" else "#FAFAF7"))
        view.settings.apply {
            javaScriptEnabled = false; allowFileAccess = false; allowContentAccess = false
            blockNetworkLoads = true; blockNetworkImage = true; loadsImagesAutomatically = false
            domStorageEnabled = false; setGeolocationEnabled(false); cacheMode = WebSettings.LOAD_NO_CACHE
            textZoom = (context.resources.configuration.fontScale * 100).toInt()
        }
        dialog = Dialog(context).apply { requestWindowFeature(android.view.Window.FEATURE_NO_TITLE) }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(TextView(context).apply { setText(R.string.draft_preview); textSize = 20f; isAccessibilityHeading = true })
            addView(status)
            addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(Button(context).apply { setText(R.string.preview_back); isAllCaps = false; setOnClickListener { dialog.dismiss() } })
        }
        dialog.setContentView(body, android.view.ViewGroup.LayoutParams(-1, -1))
        dialog.setOnDismissListener { dispose() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(-1, -1)
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }).resources.getStringArray(R.array.preview_labels)
        val translations = english.zip(context.resources.getStringArray(R.array.preview_labels)).toMap()
        worker.submit {
            try {
                val prepared = DocumentRenderer.prepare(TextDocument("draft.md", text, FileKind.MARKDOWN, text.length)) { translations[it] ?: it }
                if (prepared.preview == null) throw IllegalStateException()
                val page = DocumentRenderer.page(prepared, DisplayMode.PREVIEW, dark, true)
                handler.post {
                    if (disposed) return@post
                    if (!current()) { status.setText(R.string.preview_stale); return@post }
                    val url = "https://textr.invalid/draft"
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse =
                            if (request.isForMainFrame && request.url.toString().substringBefore('#') == url)
                                WebResourceResponse("text/html", "UTF-8", page.openStream())
                            else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                            if (request.url.toString().substringBefore('#') == url && request.url.fragment != null) return false
                            if (request.hasGesture() && request.isForMainFrame && request.url.scheme in setOf("http", "https", "mailto") && request.url.host != "textr.invalid") {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addCategory(Intent.CATEGORY_BROWSABLE)) }
                                catch (_: ActivityNotFoundException) { Toast.makeText(context, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
                                catch (_: SecurityException) { Toast.makeText(context, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
                            }
                            return true
                        }
                        override fun onPageFinished(web: WebView, loaded: String) {
                            if (disposed) return
                            if (!current()) { view.visibility = android.view.View.GONE; status.setText(R.string.preview_stale); return }
                            view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                                override fun onComplete(id: Long) {
                                    if (!disposed && !rendererGone && current()) {
                                        view.afterDraw = {
                                            if (!disposed && !rendererGone && current()) {
                                                view.scrollTo(0, scroll); view.tag = true; status.setText(R.string.preview_unsaved)
                                            }
                                        }
                                        view.invalidate()
                                    }
                                }
                            })
                        }
                        override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                            rendererGone = true; status.setText(R.string.draft_preview_unavailable); (view.parent as? android.view.ViewGroup)?.removeView(view); view.destroy(); return true
                        }
                    }
                    view.loadUrl(url)
                }
            } catch (_: Exception) { handler.post { if (!disposed) status.setText(R.string.draft_preview_unavailable) } }
        }
    }
    private fun dispose() {
        if (disposed) return
        disposed = true; val scroll = view.scrollY
        worker.shutdownNow(); handler.removeCallbacksAndMessages(null)
        if (!rendererGone) { view.stopLoading(); (view.parent as? android.view.ViewGroup)?.removeView(view); view.destroy() }
        closed(scroll)
    }
    fun checkCurrent() {
        if (!disposed && !rendererGone && !current()) {
            view.stopLoading(); view.visibility = android.view.View.GONE; status.setText(R.string.preview_stale)
        }
    }
    fun dismiss() { dialog.dismiss(); dispose() }
}
