package de.utrost.textr

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ReaderSession : ViewModel() {
    // In-memory only: survive rotation without retaining document contents on disk.
    var document: PreparedDocument? = null
}

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var work: Future<*>? = null
    private var cancellation: CancellationSignal? = null
    private var generation = 0
    private lateinit var repository: DocumentRepository
    private val preferences by lazy { getSharedPreferences("reading", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var titleView: TextView
    private lateinit var toolbar: LinearLayout
    private lateinit var findButton: Button
    private lateinit var modeButton: Button
    private lateinit var searchRow: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var searchCount: TextView
    private var webView: WebView? = null
    private val session by lazy { ViewModelProvider(this)[ReaderSession::class.java] }
    private var document: PreparedDocument?
        get() = session.document
        set(value) { session.document = value }
    private var currentUri: Uri? = null
    private var mode = DisplayMode.PLAIN
    private var dark = false
    private var wrap = true
    private var fontSize = 16
    private var pendingScroll = 0
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.let { data ->
            data.data?.let { uri ->
                pendingScroll = 0
                closeSearch()
                openDocument(uri, grantFlags = data.flags)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        dark = when (preferences.getString("theme", "system")) {
            "dark" -> true
            "light" -> false
            else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        setTheme(if (dark) R.style.Theme_Textr_Dark else R.style.Theme_Textr)
        super.onCreate(savedInstanceState)
        val barStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        fontSize = preferences.getInt("fontSize", 16).coerceIn(12, 28)
        wrap = preferences.getBoolean("wrap", true)
        repository = DocumentRepository(applicationContext)
        buildLayout()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateBack() }
        })
        if (savedInstanceState != null) {
            pendingScroll = savedInstanceState.getInt("scroll")
            searchField.setText(savedInstanceState.getString("query", ""))
            searchRow.visibility = if (savedInstanceState.getBoolean("search")) View.VISIBLE else View.GONE
            val savedMode = savedInstanceState.getString("mode")?.let { value -> DisplayMode.entries.find { it.name == value } }
            val uri = savedInstanceState.getString("uri")
            if (document != null) {
                currentUri = uri?.let(Uri::parse)
                mode = savedMode?.takeIf { it in document!!.modes } ?: document!!.defaultMode
                render()
            } else if (uri != null) openDocument(Uri.parse(uri), restoredMode = savedMode)
            else showHome()
        } else if (!handleIntent(intent)) showHome()
    }

    private fun buildLayout() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background())
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        setContentView(root)
        val header = row().apply { setPadding(dp(8), dp(4), dp(8), dp(4)) }
        header.addView(button(R.string.home) { showHome() })
        titleView = TextView(this).apply {
            textSize = 20f
            setTextColor(foreground())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(8), 0, dp(4), 0)
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button(R.string.settings, "⋮") { showSettings() })
        root.addView(header)

        toolbar = row().apply { setPadding(dp(12), 0, dp(12), 0) }
        modeButton = button(R.string.plain_text) { showModes() }
        toolbar.addView(modeButton, LinearLayout.LayoutParams(0, -2, 1f))
        findButton = button(R.string.find) { toggleSearch() }
        toolbar.addView(findButton)
        toolbar.addView(button(R.string.open_file) { openPicker() })
        root.addView(toolbar)

        searchRow = row().apply { visibility = View.GONE; setPadding(dp(12), 0, dp(12), 0) }
        searchField = EditText(this).apply {
            hint = getString(R.string.find_hint)
            textSize = 16f
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    webView?.findAllAsync(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            setOnEditorActionListener { _, _, _ -> webView?.findNext(true); true }
        }
        searchRow.addView(searchField, LinearLayout.LayoutParams(0, -2, 1f))
        searchCount = TextView(this).apply { textSize = 12f; setTextColor(foreground()) }
        searchRow.addView(searchCount)
        searchRow.addView(button(R.string.previous_match, "↑") { webView?.findNext(false) })
        searchRow.addView(button(R.string.next_match, "↓") { webView?.findNext(true) })
        searchRow.addView(button(R.string.close_search, "×") { closeSearch() })
        root.addView(searchRow)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun button(label: Int, caption: String = getString(label), action: () -> Unit) = Button(this).apply {
        text = caption
        contentDescription = getString(label)
        isAllCaps = false
        minWidth = dp(48)
        minimumWidth = dp(48)
        minHeight = dp(48)
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun background() = Color.parseColor(if (dark) "#171C1A" else "#FAFAF7")
    private fun foreground() = Color.parseColor(if (dark) "#E3E9E5" else "#202923")

    private fun cancelWork() {
        generation++
        cancellation?.cancel()
        cancellation = null
        work?.cancel(true)
    }

    private fun clearContent() {
        webView?.let { view ->
            content.removeView(view)
            view.stopLoading()
            view.destroy()
        }
        webView = null
        content.removeAllViews()
    }

    private fun showHome() {
        cancelWork()
        document = null
        currentUri = null
        pendingScroll = 0
        closeSearch()
        clearContent()
        titleView.text = getString(R.string.app_name)
        toolbar.visibility = View.GONE
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        body.addView(TextView(this).apply { text = getString(R.string.home_description); textSize = 18f; setTextColor(foreground()) })
        body.addView(button(R.string.open_file) { openPicker() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24); bottomMargin = dp(32) })
        val recentHeader = row()
        recentHeader.addView(TextView(this).apply { text = getString(R.string.recent_files); textSize = 20f; setTextColor(foreground()) }, LinearLayout.LayoutParams(0, -2, 1f))
        val recents = repository.recentFiles()
        if (recents.isNotEmpty()) recentHeader.addView(button(R.string.clear_history) {
            AlertDialog.Builder(this).setTitle(R.string.clear_history).setMessage(R.string.clear_history_message)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.clear_history) { _, _ -> repository.clear(); showHome() }.show()
        })
        body.addView(recentHeader)
        if (recents.isEmpty()) body.addView(TextView(this).apply {
            text = getString(R.string.empty_history); textSize = 16f; setTextColor(foreground()); setPadding(0, dp(16), 0, 0)
        })
        recents.forEach { recent ->
            val entry = row()
            entry.addView(Button(this).apply {
                text = recent.name; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener { openDocument(Uri.parse(recent.uri)) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            entry.addView(button(R.string.clear_history, "×") { repository.remove(recent.uri); showHome() }.apply {
                contentDescription = getString(R.string.remove_recent, recent.name)
            })
            body.addView(entry)
        }
        body.addView(button(R.string.about) {
            AlertDialog.Builder(this).setTitle(R.string.about).setMessage(R.string.about_message)
                .setNeutralButton(R.string.licenses) { _, _ -> showLicenses() }
                .setPositiveButton(R.string.done, null).show()
        })
        scroll.addView(body)
        content.addView(scroll)
    }

    private fun openPicker() {
        try {
            picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            })
        } catch (_: ActivityNotFoundException) { showError(R.string.access_error) }
    }

    private fun showLicenses() {
        val files = assets.list("licenses").orEmpty().sorted()
        AlertDialog.Builder(this).setTitle(R.string.licenses).setItems(files.toTypedArray()) { _, index ->
            val text = assets.open("licenses/${files[index]}").bufferedReader().use { it.readText() }
            val body = TextView(this).apply {
                this.text = text
                setTextIsSelectable(true)
                textSize = 14f
                setPadding(dp(20), dp(16), dp(20), dp(16))
            }
            AlertDialog.Builder(this).setTitle(files[index])
                .setView(ScrollView(this).apply { addView(body) }).setPositiveButton(R.string.done, null).show()
        }.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent): Boolean {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            else -> return false
        }
        pendingScroll = 0
        closeSearch()
        if (uri != null) {
            openDocument(uri, intent.type, grantFlags = intent.flags)
        } else {
            val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (intent.action == Intent.ACTION_SEND && sharedText != null) {
                // Shared text has no reopenable URI and is deliberately excluded from history.
                load(null) {
                    TextDocuments.read(sharedText.byteInputStream(), getString(R.string.shared_text_name), "text/plain")
                }
            } else showError(R.string.unsupported_uri)
        }
        return true
    }

    private fun openDocument(uri: Uri, mime: String? = null, restoredMode: DisplayMode? = null, grantFlags: Int = 0) {
        load(uri, restoredMode, grantFlags) { signal -> repository.read(uri, mime, signal) }
    }

    private fun load(uri: Uri?, restoredMode: DisplayMode? = null, grantFlags: Int = 0, reader: (CancellationSignal) -> TextDocument) {
        cancelWork()
        document = null
        currentUri = uri
        val signal = CancellationSignal().also { cancellation = it }
        val request = generation
        showProgress(R.string.loading)
        work = worker.submit {
            try {
                val prepared = DocumentRenderer.prepare(reader(signal))
                runOnUiThread {
                    if (request != generation || isDestroyed) return@runOnUiThread
                    document = prepared
                    mode = restoredMode?.takeIf { it in prepared.modes } ?: prepared.defaultMode
                    if (uri != null) {
                        repository.retainReadAccess(uri, grantFlags)
                        repository.remember(uri, prepared.source.name)
                    }
                    render()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (request == generation && !isDestroyed) showError(when ((error as? DocumentException)?.reason) {
                        ReadFailure.TOO_LARGE -> R.string.too_large
                        ReadFailure.BINARY -> R.string.binary_file
                        ReadFailure.ENCODING -> R.string.unsupported_encoding
                        ReadFailure.SOURCE -> R.string.unsupported_uri
                        else -> R.string.access_error
                    })
                }
            }
        }
    }

    private fun showProgress(message: Int) {
        clearContent()
        toolbar.visibility = View.VISIBLE
        modeButton.isEnabled = false
        findButton.isEnabled = false
        titleView.text = getString(message)
        content.addView(ProgressBar(this), FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
    }

    private fun showError(message: Int) {
        cancelWork()
        document = null
        clearContent()
        toolbar.visibility = View.GONE
        closeSearch()
        titleView.text = getString(R.string.cannot_open)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        body.addView(TextView(this).apply { text = getString(message); textSize = 18f; setTextColor(foreground()) })
        body.addView(button(R.string.open_again) { openPicker() })
        content.addView(body, FrameLayout.LayoutParams(-1, -1))
    }

    private fun render() {
        val prepared = document ?: return
        val scroll = webView?.scrollY ?: pendingScroll
        cancelWork()
        val request = generation
        showProgress(R.string.rendering)
        val selectedMode = mode
        work = worker.submit {
            val html = DocumentRenderer.html(prepared, selectedMode, dark, wrap)
            runOnUiThread {
                if (request != generation || isDestroyed) return@runOnUiThread
                clearContent()
                titleView.text = prepared.source.name
                modeButton.text = getString(modeLabel(selectedMode))
                modeButton.isEnabled = prepared.modes.size > 1
                findButton.isEnabled = true
                val view = createWebView(scroll)
                webView = view
                content.addView(view, FrameLayout.LayoutParams(-1, -1))
                view.loadDataWithBaseURL("https://textr.invalid/", html, "text/html", "UTF-8", null)
                pendingScroll = 0
                if (prepared.preview == null && prepared.source.kind in setOf(FileKind.JSON, FileKind.MARKDOWN)) {
                    Toast.makeText(this, if (prepared.source.kind == FileKind.JSON) R.string.invalid_json else R.string.preview_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createWebView(scroll: Int) = WebView(this).apply {
        setBackgroundColor(background())
        settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            blockNetworkImage = true
            loadsImagesAutomatically = false
            domStorageEnabled = false
            setGeolocationEnabled(false)
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            defaultFontSize = fontSize
            defaultFixedFontSize = fontSize
            textZoom = (resources.configuration.fontScale * 100).toInt()
            builtInZoomControls = true
            displayZoomControls = false
        }
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setFindListener { active, total, done ->
            if (done) searchCount.text = if (total == 0) getString(R.string.no_matches) else getString(R.string.matches, active + 1, total)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.hasGesture() && request.isForMainFrame) {
                    val uri = request.url
                    if (uri.host == "textr.invalid" && uri.fragment != null) return false
                    if (uri.scheme in setOf("https", "http", "mailto") && uri.host != "textr.invalid") {
                        try { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        catch (_: ActivityNotFoundException) { Toast.makeText(this@MainActivity, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
                    } else Toast.makeText(this@MainActivity, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.post { if (webView === view) view.scrollTo(0, scroll) }
                if (searchRow.visibility == View.VISIBLE) view.findAllAsync(searchField.text.toString())
            }
        }
    }

    private fun modeLabel(value: DisplayMode): Int = when (value) {
        DisplayMode.PLAIN -> R.string.plain_text
        DisplayMode.HIGHLIGHTED -> R.string.highlighted
        DisplayMode.PREVIEW -> R.string.preview
    }

    private fun showModes() {
        val modes = document?.modes ?: return
        AlertDialog.Builder(this).setSingleChoiceItems(modes.map { getString(modeLabel(it)) }.toTypedArray(), modes.indexOf(mode)) { dialog, index ->
            mode = modes[index]; pendingScroll = 0; webView?.scrollTo(0, 0); dialog.dismiss(); render()
        }.show()
    }

    private fun toggleSearch() {
        if (searchRow.visibility == View.VISIBLE) closeSearch()
        else {
            searchRow.visibility = View.VISIBLE
            searchField.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeSearch() {
        searchRow.visibility = View.GONE
        searchField.setText("")
        searchField.clearFocus()
        webView?.clearMatches()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchField.windowToken, 0)
    }

    private fun showSettings() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), 0) }
        val sizeLabel = TextView(this).apply { text = getString(R.string.font_size_value, fontSize) }
        body.addView(sizeLabel)
        var selectedSize = fontSize
        body.addView(SeekBar(this).apply {
            max = 16; progress = fontSize - 12
            minimumHeight = dp(48)
            contentDescription = getString(R.string.font_size)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedSize = progress + 12; sizeLabel.text = getString(R.string.font_size_value, selectedSize)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        val wrapping = CheckBox(this).apply {
            text = getString(R.string.wrap_lines)
            isChecked = wrap
            minimumHeight = dp(48)
        }
        body.addView(wrapping)
        body.addView(TextView(this).apply { text = getString(R.string.theme) })
        val themeValues = listOf("system", "light", "dark")
        val themes = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)))
            setSelection(themeValues.indexOf(preferences.getString("theme", "system")).coerceAtLeast(0))
            contentDescription = getString(R.string.theme)
        }
        body.addView(themes)
        AlertDialog.Builder(this).setTitle(R.string.settings).setView(body).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.done) { _, _ ->
                val theme = themeValues[themes.selectedItemPosition]
                val themeChanged = theme != preferences.getString("theme", "system")
                fontSize = selectedSize; wrap = wrapping.isChecked
                preferences.edit().putInt("fontSize", fontSize).putBoolean("wrap", wrap).putString("theme", theme).apply()
                if (themeChanged) recreate() else if (document != null) render()
            }.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("uri", currentUri?.toString())
        outState.putString("mode", mode.name)
        outState.putInt("scroll", webView?.scrollY ?: pendingScroll)
        outState.putString("query", searchField.text.toString())
        outState.putBoolean("search", searchRow.visibility == View.VISIBLE)
    }

    private fun navigateBack() {
        if (searchRow.visibility == View.VISIBLE) closeSearch()
        else if (currentUri != null || document != null) showHome()
        else finish()
    }

    override fun onDestroy() {
        cancelWork()
        worker.shutdownNow()
        clearContent()
        super.onDestroy()
    }
}
