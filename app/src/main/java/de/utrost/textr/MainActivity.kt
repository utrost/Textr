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
import android.webkit.RenderProcessGoneDetail
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
    var original: TextDocument? = null
    var openDocuments: OpenDocuments? = null
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
    private lateinit var backButton: Button
    private lateinit var menuButton: Button
    private lateinit var modeButton: Button
    private lateinit var documentsButton: Button
    private val openDocuments get() = session.openDocuments!!
    private var lineNumbers = false
    private var targetLine = 0
    private var anchor: String? = null
    private var sourceMatch: SourceMatch? = null
    private lateinit var documentStrip: HorizontalScrollView
    private lateinit var fileSidebar: ScrollView
    private var sidebarFits = false
    private var encodingOverride: TextEncoding? = null
    private var pendingGrantFlags = 0
    private val undoHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val finishUndo = Runnable { repository.undoAvailable(); if (homeShown && canInteract()) showHome() }
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
    private var formatOverride: FileKind? = null
    private var requestedMode: DisplayMode? = null
    private var homeShown = false
    private var sharing = false
    private val dialogs = mutableSetOf<AlertDialog>()
    private var activeMenu: PopupMenu? = null
    private fun canInteract() = !isDestroyed && !isFinishing && !worker.isShutdown

    private fun AlertDialog.present(dismissed: () -> Unit = {}): AlertDialog {
        if (!canInteract()) return this
        dialogs.add(this)
        setOnDismissListener { dialogs.remove(this); dismissed() }
        show()
        return this
    }
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.let { data ->
            data.data?.let { uri ->
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
        lineNumbers = preferences.getBoolean("lineNumbers", false)
        repository = DocumentRepository(applicationContext)
        if (session.openDocuments == null) session.openDocuments = OpenDocuments(applicationContext)
        repository.undoAvailable()
        ShareFiles(this).prune()
        buildLayout()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateBack() }
        })
        if (savedInstanceState != null) {
            formatOverride = FileKind.entries.find { it.name == savedInstanceState.getString("format") }
            encodingOverride = TextEncoding.entries.find { it.name == savedInstanceState.getString("encoding") }
            pendingGrantFlags = savedInstanceState.getInt("grantFlags")
            pendingScroll = savedInstanceState.getInt("scroll")
            searchField.setText(savedInstanceState.getString("query", ""))
            searchRow.visibility = if (savedInstanceState.getBoolean("search")) View.VISIBLE else View.GONE
            val savedMode = savedInstanceState.getString("mode")?.let { value -> DisplayMode.entries.find { it.name == value } }
            val uri = savedInstanceState.getString("uri")
            if (document != null) {
                currentUri = uri?.let(Uri::parse)
                mode = savedMode?.takeIf { it in document!!.modes } ?: document!!.defaultMode
                render()
            } else if (uri != null) openDocument(Uri.parse(uri), restoredMode = savedMode, restoring = true)
            else showHome()
        } else if (!handleIntent(intent)) showHome()
    }

    private fun buildLayout() {
        val wide = resources.configuration.screenWidthDp >= 600
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
        backButton = iconButton(R.string.back_to_files, R.drawable.ic_back) { showHome() }
        header.addView(backButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        titleView = TextView(this).apply {
            isAccessibilityHeading = true
            textSize = 20f
            setTextColor(foreground())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(8), 0, dp(4), 0)
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        findButton = iconButton(R.string.find, R.drawable.ic_find) { toggleSearch() }
        header.addView(findButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        menuButton = iconButton(R.string.more_options, R.drawable.ic_more) { showMenu() }
        documentsButton = button(R.string.open_documents, "0") { showDocuments() }.apply {
            textSize = 14f
            setPadding(dp(4), 0, dp(4), 0)
            val icon = getDrawable(R.drawable.ic_documents)!!.mutate().apply { setTint(foreground()); setBounds(0, 0, dp(20), dp(20)) }
            setCompoundDrawables(icon, null, null, null)
            compoundDrawablePadding = dp(2)
            tooltipText = getString(R.string.open_documents)
            val value = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
            setBackgroundResource(value.resourceId)
        }
        header.addView(documentsButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        documentStrip = HorizontalScrollView(this).apply { id = R.id.document_strip; isHorizontalScrollBarEnabled = false }
        root.addView(documentStrip)
        updateDocumentsButton()

        toolbar = row().apply { setPadding(dp(12), 0, dp(12), 0) }
        modeButton = button(R.string.plain_text) { showModes() }.apply { id = R.id.display_mode }
        modeButton.apply {
            val arrow = getDrawable(R.drawable.ic_expand)!!.mutate().apply { setTint(foreground()); setBounds(0, 0, dp(24), dp(24)) }
            setCompoundDrawables(null, null, arrow, null)
            compoundDrawablePadding = dp(4)
            val value = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            setBackgroundResource(value.resourceId)
        }
        toolbar.addView(modeButton, LinearLayout.LayoutParams(-2, -2))
        root.addView(toolbar)

        searchRow = row().apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL; visibility = View.GONE; setPadding(dp(12), 0, dp(12), 0) }
        searchField = EditText(this).apply {
            id = R.id.search_query
            hint = getString(R.string.find_hint)
            textSize = 16f
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s.isNullOrEmpty()) searchCount.text = ""
                    webView?.findAllAsync(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            setOnEditorActionListener { _, _, _ -> webView?.findNext(true); true }
        }
        searchRow.addView(searchField, if (wide) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
        val searchActions = row()
        searchCount = TextView(this).apply {
            id = R.id.search_count
            textSize = 14f; setTextColor(foreground())
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        searchActions.addView(searchCount, if (wide) LinearLayout.LayoutParams(-2, -2) else LinearLayout.LayoutParams(0, -2, 1f))
        searchActions.addView(button(R.string.previous_match, "↑") { webView?.findNext(false) })
        searchActions.addView(button(R.string.next_match, "↓") { webView?.findNext(true) })
        searchActions.addView(button(R.string.search_source, "⋯") { showSourceSearch() })
        searchActions.addView(button(R.string.close_search, "×") { closeSearch() })
        searchRow.addView(searchActions, LinearLayout.LayoutParams(if (wide) -2 else -1, -2))
        root.addView(searchRow)
        content = FrameLayout(this)
        val readingArea = row()
        fileSidebar = ScrollView(this).apply {
            id = R.id.file_sidebar; visibility = View.GONE
            setBackgroundColor(Color.parseColor(if (dark) "#202923" else "#EEF1ED"))
        }
        readingArea.addView(fileSidebar, LinearLayout.LayoutParams(dp(220), -1))
        readingArea.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(readingArea, LinearLayout.LayoutParams(-1, 0, 1f))
        readingArea.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            // Measure the actual inset-adjusted app window, including split-screen resizing.
            val density = resources.displayMetrics.density
            val fits = view.width / density >= 720 &&
                (root.height - root.paddingTop - root.paddingBottom) / density >= 480 &&
                resources.configuration.fontScale <= 1.3f
            if (fits != sidebarFits) {
                sidebarFits = fits
                updateDocumentsButton()
            }
        }
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
        setOnClickListener { if (canInteract()) action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun iconButton(label: Int, icon: Int, action: () -> Unit) = button(label, "", action).apply {
        setPadding(dp(12), dp(12), dp(12), dp(12))
        val image = getDrawable(icon)!!.mutate().apply { setTint(foreground()); setBounds(0, 0, dp(24), dp(24)) }
        setCompoundDrawables(image, null, null, null)
        val value = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
        setBackgroundResource(value.resourceId)
        tooltipText = getString(label)
    }
    private fun background() = Color.parseColor(if (dark) "#171C1A" else "#FAFAF7")
    private fun foreground() = Color.parseColor(if (dark) "#E3E9E5" else "#202923")

    private fun cancelWork() {
        generation++
        cancellation?.cancel()
        cancellation = null
        work?.cancel(true)
        sharing = false
    }

    private fun clearContent(stopLoading: Boolean = true) {
        webView?.let { view ->
            content.removeView(view)
            if (stopLoading) view.stopLoading()
            view.destroy()
        }
        webView = null
        content.removeAllViews()
    }

    private fun showHome() {
        if (!canInteract()) return
        rememberReading()
        cancelWork()
        document = null
        session.original = null
        formatOverride = null
        encodingOverride = null
        currentUri = null
        updateDocumentsButton()
        homeShown = true
        backButton.visibility = View.GONE
        findButton.visibility = View.GONE
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
        val recents = repository.recentFiles().sortedWith(compareByDescending<RecentFile> { it.pinned }.thenByDescending { it.openedAt })
        body.addView(recentHeader)
        if (recents.isEmpty()) body.addView(TextView(this).apply {
            text = getString(R.string.empty_history); textSize = 16f; setTextColor(foreground()); setPadding(0, dp(16), 0, 0)
        })
        if (repository.undoAvailable()) {
            body.addView(button(R.string.undo_remove) { repository.undoRemoval(); showHome() })
            undoHandler.removeCallbacks(finishUndo)
            undoHandler.postDelayed(finishUndo, repository.undoRemainingMillis() + 1)
        }
        recents.forEach { recent ->
            val entry = row()
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(Button(this).apply {
                text = recent.name; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                val value = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                setBackgroundResource(value.resourceId)
                minimumHeight = dp(56)
                setOnClickListener { openDocument(Uri.parse(recent.uri)) }
            }, LinearLayout.LayoutParams(-1, -2))
            details.addView(TextView(this).apply {
                val uri = Uri.parse(recent.uri)
                val location = if (uri.scheme == "file") uri.path?.substringBeforeLast('/') else uri.authority
                text = android.text.format.DateUtils.getRelativeTimeSpanString(recent.openedAt).toString() +
                    (location?.let { " · $it" } ?: "")
                textSize = 12f; setTextColor(foreground()); maxLines = 2
                setPadding(dp(8), 0, dp(8), dp(8))
            })
            entry.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            entry.addView(button(if (recent.pinned) R.string.unpin else R.string.pin, if (recent.pinned) "★" else "☆") {
                if (!repository.togglePin(recent.uri)) Toast.makeText(this, R.string.pin_limit, Toast.LENGTH_SHORT).show()
                showHome()
            }.apply { contentDescription = getString(if (recent.pinned) R.string.unpin_named else R.string.pin_named, recent.name) })
            entry.addView(button(R.string.clear_history, "×") { repository.removeWithUndo(recent.uri); showHome() }.apply {
                contentDescription = getString(R.string.remove_recent, recent.name)
            })
            body.addView(entry)
        }
        scroll.addView(body)
        content.addView(scroll)
    }

    private fun showMenu() {
        if (!canInteract()) return
        PopupMenu(this, menuButton).apply {
            activeMenu = this
            setOnDismissListener { if (activeMenu === this) activeMenu = null }
            fun item(label: Int, enabled: Boolean = true, action: () -> Unit) {
                menu.add(label).apply { isEnabled = enabled; setOnMenuItemClickListener { if (canInteract()) action(); true } }
            }
            if (!homeShown) {
                item(R.string.open_file) { openPicker() }
                val ready = document != null && webView?.tag == true
                item(if (currentUri == null && (session.original?.bytes ?: 0) < 100_000) R.string.share_text else R.string.share_file,
                    ready && !sharing) { shareDocument() }
                item(R.string.open_with, ready && !sharing) { shareDocument(openWith = true) }
                item(R.string.reload, ready && currentUri != null) { reloadDocument() }
                item(R.string.treat_as, ready) { showFormats() }
                item(R.string.document_info, ready) { showDocumentInfo() }
                if (mode != DisplayMode.PREVIEW) item(R.string.go_to_line, ready) { showGoToLine() }
                if (mode == DisplayMode.PREVIEW && document?.headings?.isNotEmpty() == true) item(R.string.outline, ready) { showOutline() }
            } else if (repository.recentFiles().isNotEmpty()) {
                item(R.string.clear_history) {
                    AlertDialog.Builder(this@MainActivity).setTitle(R.string.clear_history).setMessage(R.string.clear_history_message)
                        .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.done) { _, _ ->
                            if (canInteract()) { repository.clear(); showHome() }
                        }.create().present()
                }
            }
            if (openDocuments.entries.isNotEmpty()) item(R.string.open_documents) { showDocuments() }
            item(R.string.settings) { showSettings() }
            if (homeShown) item(R.string.opening_help) { showOpeningHelp() }
            item(R.string.about) { showAbout() }
            show()
        }
    }

    private fun showAbout() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.about_details, version, getString(R.string.about_privacy))
                textSize = 16f
                setTextIsSelectable(true)
            })
            addView(button(R.string.source_code) { openExternal("https://github.com/utrost/Textr") })
            addView(button(R.string.report_issue) { openExternal("https://github.com/utrost/Textr/issues") })
            addView(button(R.string.licenses) { showLicenses() })
            addView(button(R.string.opening_help) { showOpeningHelp() })
        }
        AlertDialog.Builder(this).setTitle(R.string.about).setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.done, null).create().present()
    }

    private fun openExternal(url: String) {
        if (!canInteract()) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
        catch (_: SecurityException) { Toast.makeText(this, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
    }

    private fun formatName(kind: FileKind?): String = when (kind) {
        null -> getString(R.string.automatic)
        FileKind.TEXT -> getString(R.string.plain_text)
        FileKind.MARKDOWN -> "Markdown"
        FileKind.JAVA -> "Java"
        FileKind.JAVASCRIPT -> "JavaScript"
        FileKind.KOTLIN -> "Kotlin"
        FileKind.PYTHON -> "Python"
        FileKind.SHELL -> "Shell"
        FileKind.DIFF -> "Diff / patch"
        FileKind.ICALENDAR -> "iCalendar"
        else -> kind.name
    }

    private fun showDocumentInfo() {
        val source = session.original ?: return
        var infoDialog: AlertDialog? = null
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), 0)
            addView(TextView(this@MainActivity).apply {
                textSize = 16f
                setTextIsSelectable(true)
                text = getString(R.string.document_details, source.name,
                    android.text.format.Formatter.formatShortFileSize(this@MainActivity, source.bytes.toLong()),
                    formatName(source.kind), formatName(formatOverride), source.encoding, source.info.lines,
                    source.info.endings.ifEmpty { getString(R.string.no_line_endings) })
            })
            if (currentUri != null) addView(button(R.string.reopen_encoding) { infoDialog?.dismiss(); showEncodings() })
        }
        infoDialog = AlertDialog.Builder(this).setTitle(R.string.document_info)
            .setView(ScrollView(this).apply { addView(body) })
            .setNeutralButton(R.string.treat_as) { _, _ -> showFormats() }
            .setPositiveButton(R.string.done, null).create().present()
    }

    private fun showEncodings() {
        val uri = currentUri ?: return
        val choices = listOf(null) + TextEncoding.entries
        AlertDialog.Builder(this).setTitle(R.string.reopen_encoding)
            .setSingleChoiceItems(choices.map { it?.charsetName ?: getString(R.string.automatic) }.toTypedArray(), choices.indexOf(encodingOverride)) { dialog, index ->
                if (!canInteract()) return@setSingleChoiceItems
                rememberReading()
                val selected = choices[index]
                encodingOverride = selected
                pendingScroll = 0
                dialog.dismiss()
                load(uri, document?.let { mode }, pendingGrantFlags) { signal -> repository.read(uri, null, signal, selected) }
            }.setNegativeButton(R.string.cancel, null).create().present()
    }

    private fun showSourceSearch() {
        val source = document?.source ?: return
        if (webView?.tag != true) return
        val entry = openDocuments.entries.find { it.key == (currentUri?.toString() ?: "shared-text") } ?: return
        val initial = entry.sourceSearch.let { if (it.query.isEmpty()) it.copy(query = searchField.text.toString().take(SourceSearch.MAX_QUERY)) else it }
        val search = SourceSearchDialog(this, source.text, initial, { entry.sourceSearch = it }) { match ->
            if (canInteract() && document?.source === source) {
                closeSearch()
                mode = DisplayMode.PLAIN
                sourceMatch = match
                anchor = "source-match"
                render()
            }
        }
        search.dialog.present { search.close() }
    }

    private fun showOpeningHelp() {
        val text = TextView(this).apply {
            setText(R.string.opening_help_text)
            textSize = 16f
            setTextIsSelectable(true)
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        AlertDialog.Builder(this).setTitle(R.string.opening_help).setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton(R.string.done, null).create().present()
    }

    private fun showFormats() {
        if (!canInteract()) return
        val formats = listOf(null) + FileKind.entries
        AlertDialog.Builder(this).setTitle(R.string.treat_as)
            .setSingleChoiceItems(formats.map(::formatName).toTypedArray(), formats.indexOf(formatOverride)) { dialog, index ->
                if (!canInteract()) return@setSingleChoiceItems
                dialog.dismiss()
                val original = session.original ?: return@setSingleChoiceItems
                rememberReading()
                formatOverride = formats[index]
                pendingScroll = 0
                closeSearch()
                load(currentUri) { original }
            }.create().present()
    }

    private fun shareDocument(openWith: Boolean = false) {
        if (!canInteract()) return
        val source = session.original ?: return
        if (!openWith && currentUri == null && source.bytes < 100_000) {
            launchShare(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, source.text))
            return
        }
        val uri = currentUri
        cancelWork()
        sharing = true
        val request = generation
        val signal = CancellationSignal().also { cancellation = it }
        Toast.makeText(this, R.string.preparing_share, Toast.LENGTH_SHORT).show()
        work = worker.submit {
            try {
                val share = ShareFiles(applicationContext).prepare(uri, source, signal)
                runOnUiThread {
                    if (request == generation && !isDestroyed) { sharing = false; if (openWith) launchOpenWith(share) else launchShare(share) }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (request == generation && !isDestroyed) {
                        sharing = false
                        Toast.makeText(this, R.string.share_error, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun launchShare(intent: Intent) {
        try { startActivity(Intent.createChooser(intent, null)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
        catch (_: SecurityException) { Toast.makeText(this, R.string.share_error, Toast.LENGTH_LONG).show() }
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
            if (!canInteract()) return@setItems
            val text = assets.open("licenses/${files[index]}").bufferedReader().use { it.readText() }
            val body = TextView(this).apply {
                this.text = text
                setTextIsSelectable(true)
                textSize = 14f
                setPadding(dp(20), dp(16), dp(20), dp(16))
            }
            AlertDialog.Builder(this).setTitle(files[index])
                .setView(ScrollView(this).apply { addView(body) }).setPositiveButton(R.string.done, null).create().present()
        }.create().present()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent): Boolean {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)))
                ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
            else -> return false
        }
        if (uri != null) {
            openDocument(uri, intent.type, grantFlags = intent.flags)
        } else {
            val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (intent.action == Intent.ACTION_SEND && sharedText != null) {
                rememberReading()
                closeSearch()
                pendingScroll = 0
                formatOverride = null
                encodingOverride = null
                // Shared text has no reopenable URI and is deliberately excluded from history.
                load(null) {
                    TextDocuments.read(sharedText.byteInputStream(), getString(R.string.shared_text_name), "text/plain")
                }
            } else showError(R.string.unsupported_uri)
        }
        return true
    }

    private fun openDocument(uri: Uri, mime: String? = null, restoredMode: DisplayMode? = null, grantFlags: Int = 0, restoring: Boolean = false) {
        rememberReading()
        val existing = openDocuments.entries.find { it.uri == uri.toString() }
        val state = existing?.reading ?: repository.readingState(uri)
        if (!restoring) {
            closeSearch()
            searchField.setText(existing?.query.orEmpty())
            searchRow.visibility = if (existing?.searching == true) View.VISIBLE else View.GONE
            formatOverride = state.format
            encodingOverride = state.encoding
            pendingScroll = state.scroll
        }
        val encoding = encodingOverride
        load(uri, if (restoring) restoredMode else state.mode, if (restoring) pendingGrantFlags else grantFlags) { signal -> repository.read(uri, mime, signal, encoding) }
    }

    private fun load(uri: Uri?, restoredMode: DisplayMode? = null, grantFlags: Int = 0, reader: (CancellationSignal) -> TextDocument) {
        if (!canInteract()) return
        cancelWork()
        requestedMode = restoredMode
        document = null
        currentUri = uri
        pendingGrantFlags = grantFlags
        sourceMatch = null
        val signal = CancellationSignal().also { cancellation = it }
        val request = generation
        val selectedFormat = formatOverride
        showProgress(R.string.loading)
        work = worker.submit {
            try {
                val original = reader(signal)
                val prepared = DocumentRenderer.prepare(original.copy(kind = selectedFormat ?: original.kind))
                runOnUiThread {
                    if (request != generation || isDestroyed) return@runOnUiThread
                    session.original = original
                    val key = uri?.toString() ?: "shared-text"
                    val evicted = openDocuments.entries.firstOrNull()?.takeIf {
                        openDocuments.entries.size >= OpenDocuments.LIMIT && openDocuments.entries.none { it.key == key }
                    }
                    openDocuments.opened(uri?.toString(), original)
                    if (evicted != null) Toast.makeText(this, getString(R.string.session_evicted, evicted.name), Toast.LENGTH_LONG).show()
                    updateDocumentsButton()
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
        homeShown = false
        backButton.visibility = View.VISIBLE
        findButton.visibility = View.VISIBLE
        toolbar.visibility = View.GONE
        modeButton.isEnabled = false
        findButton.isEnabled = false
        titleView.text = getString(message)
        updateDocumentsButton()
        content.addView(ProgressBar(this), FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
    }

    private fun showError(message: Int) {
        cancelWork()
        document = null
        session.original = null
        updateDocumentsButton()
        findButton.visibility = View.GONE
        clearContent()
        toolbar.visibility = View.GONE
        closeSearch()
        titleView.text = getString(R.string.cannot_open)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        body.addView(TextView(this).apply { text = getString(message); textSize = 18f; setTextColor(foreground()) })
        if (currentUri != null && message in setOf(R.string.unsupported_encoding, R.string.binary_file))
            body.addView(button(R.string.reopen_encoding) { showEncodings() })
        body.addView(button(R.string.open_again) { openPicker() })
        content.addView(body, FrameLayout.LayoutParams(-1, -1))
    }

    private fun render() {
        if (!canInteract()) return
        val prepared = document ?: return
        val scroll = webView?.scrollY ?: pendingScroll
        cancelWork()
        val request = generation
        showProgress(R.string.rendering)
        val selectedMode = mode
        val selectedMatch = sourceMatch
        val selectedLine = targetLine
        targetLine = 0
        val selectedAnchor = anchor
        anchor = null
        work = worker.submit {
            val page = try {
                DocumentRenderer.page(prepared, selectedMode, dark, wrap, lineNumbers, selectedLine, selectedMatch)
            } catch (_: java.util.concurrent.CancellationException) {
                return@submit
            } catch (_: RuntimeException) {
                runOnUiThread { if (request == generation && !isDestroyed) showRenderRecovery() }
                return@submit
            }
            runOnUiThread {
                if (request != generation || isDestroyed) return@runOnUiThread
                clearContent()
                titleView.text = prepared.source.name
                updateDocumentsButton()
                modeButton.text = getString(modeLabel(selectedMode))
                modeButton.contentDescription = getString(R.string.mode_description, getString(modeLabel(selectedMode)))
                toolbar.visibility = if (prepared.modes.size > 1) View.VISIBLE else View.GONE
                modeButton.isEnabled = false
                findButton.isEnabled = false
                val view = createWebView(scroll, page, selectedAnchor)
                webView = view
                content.addView(view, FrameLayout.LayoutParams(-1, -1))
                content.addView(ProgressBar(this).apply { id = R.id.reader_loading },
                    FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
                // Stream in-memory content rather than embedding multi-MiB text in a data URL.
                view.loadUrl(DOCUMENT_URL)
                pendingScroll = 0
                if (prepared.notice != PreviewNotice.NONE) {
                    Toast.makeText(this, if (prepared.notice == PreviewNotice.MARKDOWN_LIMIT) R.string.markdown_limit
                        else if (prepared.notice == PreviewNotice.JSON_LIMIT) R.string.json_limit
                        else if (prepared.notice == PreviewNotice.HIGHLIGHT_LIMIT) R.string.highlight_limit
                        else if (prepared.notice == PreviewNotice.CALENDAR_LIMIT) R.string.calendar_limit
                        else if (prepared.source.kind == FileKind.ICALENDAR) R.string.calendar_unavailable
                        else if (prepared.source.kind == FileKind.JSON) R.string.invalid_json else R.string.preview_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showRenderRecovery(rendererGone: Boolean = false) {
        cancelWork()
        clearContent(stopLoading = !rendererGone)
        toolbar.visibility = View.GONE
        findButton.visibility = View.GONE
        closeSearch()
        titleView.text = document?.source?.name ?: getString(R.string.app_name)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        body.addView(TextView(this).apply {
            text = getString(R.string.render_failed)
            textSize = 18f
            setTextColor(foreground())
        })
        body.addView(button(R.string.retry_plain) { mode = DisplayMode.PLAIN; pendingScroll = 0; render() })
        body.addView(button(R.string.open_file) { openPicker() })
        content.addView(body, FrameLayout.LayoutParams(-1, -1))
    }

    private class ReaderWebView(context: android.content.Context) : WebView(context) {
        var afterNextDraw: (() -> Unit)? = null

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            afterNextDraw?.let { action ->
                afterNextDraw = null
                post { action() }
            }
        }
    }

    private fun createWebView(scroll: Int, page: HtmlPage, initialAnchor: String? = null) = ReaderWebView(this).apply {
        id = R.id.reader
        tag = false
        setBackgroundColor(background())
        settings.apply {
            javaScriptEnabled = false
            safeBrowsingEnabled = false
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
            if (done) {
                searchCount.text = if (searchField.text.isEmpty()) "" else if (total == 0) getString(R.string.no_matches) else getString(R.string.matches, active + 1, total)
                searchCount.contentDescription = if (searchField.text.isEmpty()) "" else if (total == 0) getString(R.string.no_matches) else getString(R.string.matches_spoken, active + 1, total)
            }
        }
        webViewClient = object : WebViewClient() {
            private var laidOut = false
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                if (request.isForMainFrame && request.url.toString().substringBefore('#') == DOCUMENT_URL) {
                    WebResourceResponse("text/html", "UTF-8", page.openStream())
                } else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (webView === view) showRenderRecovery(rendererGone = true)
                else view.destroy()
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.isForMainFrame && request.url.toString().substringBefore('#') == DOCUMENT_URL && request.url.fragment != null) return false
                if (request.hasGesture() && request.isForMainFrame) {
                    val uri = request.url
                    if (uri.toString().substringBefore('#') == DOCUMENT_URL && uri.fragment != null) return false
                    if (uri.scheme in setOf("https", "http", "mailto") && uri.host != "textr.invalid") {
                        try { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        catch (_: ActivityNotFoundException) { Toast.makeText(this@MainActivity, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
                        catch (_: SecurityException) { Toast.makeText(this@MainActivity, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
                    } else Toast.makeText(this@MainActivity, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (laidOut || url.substringBefore('#') != DOCUMENT_URL) return
                laidOut = true
                // The visual callback promises the DOM on the next draw, not the
                // current native contentHeight. Clamp only after that draw so an
                // initial zero height cannot erase a saved reading position.
                view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (webView !== view) return
                        (view as ReaderWebView).afterNextDraw = {
                            if (webView === view) {
                                if (initialAnchor != null) view.loadUrl("$DOCUMENT_URL#$initialAnchor") else {
                                    @Suppress("DEPRECATION")
                                    val bottom = (view.contentHeight * view.scale).toInt() - view.height
                                    view.scrollTo(0, scroll.coerceIn(0, bottom.coerceAtLeast(0)))
                                }
                                view.tag = true
                                content.findViewById<View>(R.id.reader_loading)?.let(content::removeView)
                                modeButton.isEnabled = (document?.modes?.size ?: 0) > 1
                                findButton.isEnabled = true
                                rememberReading()
                                if (searchRow.visibility == View.VISIBLE) view.findAllAsync(searchField.text.toString())
                            }
                        }
                        view.invalidate()
                    }
                })
            }
        }
    }

    private fun updateDocumentsButton() {
        documentsButton.text = openDocuments.entries.size.toString()
        documentsButton.contentDescription = getString(R.string.documents_count, openDocuments.entries.size)
        documentsButton.visibility = if (openDocuments.entries.isEmpty()) View.GONE else View.VISIBLE
        val sidebarVisible = sidebarFits && preferences.getBoolean("fileSidebar", false) && document != null && !homeShown
        if (::fileSidebar.isInitialized) {
            fileSidebar.visibility = if (sidebarVisible) View.VISIBLE else View.GONE
            if (sidebarVisible) updateFileSidebar()
        }
        if (!::documentStrip.isInitialized) return
        documentStrip.removeAllViews()
        // At large font scales keep the compact switcher so document content retains space.
        documentStrip.visibility = if (!sidebarVisible && resources.configuration.screenWidthDp >= 600 &&
            resources.configuration.fontScale <= 1.3f && openDocuments.entries.size > 1 && document != null && !homeShown) View.VISIBLE else View.GONE
        if (documentStrip.visibility != View.VISIBLE) return
        val tabs = row()
        documentStrip.addView(tabs)
        // Stable alphabetical order: choosing a tab must not move it with LRU updates.
        openDocuments.entries.sortedWith(compareBy({ it.name.lowercase(java.util.Locale.ROOT) }, { it.key })).forEach { entry ->
            val active = entry.key == (currentUri?.toString() ?: "shared-text")
            val tab = button(R.string.open_file, if (active) "✓ ${entry.name}" else entry.name) { switchDocument(entry) }.apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                contentDescription = if (active) getString(R.string.current_document, entry.name) else getString(R.string.switch_document, entry.name)
                isSelected = active
                tooltipText = entry.name
            }
            tabs.addView(tab, LinearLayout.LayoutParams(dp(180), -2))
            if (active) documentStrip.post { if (tab.parent === tabs) documentStrip.smoothScrollTo(tab.left, 0) }
        }
    }

    private fun updateFileSidebar() {
        val previousScroll = fileSidebar.scrollY
        fileSidebar.removeAllViews()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(16))
        }
        fun heading(label: Int) {
            body.addView(TextView(this).apply {
                text = getString(label); textSize = 16f; setTextColor(foreground())
                isAccessibilityHeading = true; setPadding(dp(8), dp(12), dp(8), dp(8))
            })
        }
        fun fileButton(name: String, description: String, action: () -> Unit) = button(R.string.open_file, name, action).apply {
            contentDescription = description
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; tooltipText = name
        }
        body.addView(button(R.string.open_file) { openPicker() }, LinearLayout.LayoutParams(-1, -2))
        heading(R.string.open_documents)
        openDocuments.entries.sortedWith(compareBy({ it.name.lowercase(java.util.Locale.ROOT) }, { it.key })).forEach { entry ->
            val active = entry.key == (currentUri?.toString() ?: "shared-text")
            val entryRow = row()
            entryRow.addView(fileButton(if (active) "✓ ${entry.name}" else entry.name,
                getString(if (active) R.string.current_document else R.string.switch_document, entry.name)) { switchDocument(entry) }
                .apply { isSelected = active }, LinearLayout.LayoutParams(0, -2, 1f))
            entryRow.addView(button(R.string.close_document, "×") {
                if (active) showHome()
                openDocuments.close(entry.key)
                updateDocumentsButton()
            }.apply { contentDescription = getString(R.string.close_named, entry.name) }, LinearLayout.LayoutParams(dp(48), -2))
            body.addView(entryRow)
        }
        val openUris = openDocuments.entries.map { it.key }.toSet()
        val recents = repository.recentFiles().filter { it.uri !in openUris }
            .sortedWith(compareByDescending<RecentFile> { it.pinned }.thenByDescending { it.openedAt })
        if (recents.isNotEmpty()) {
            heading(R.string.recent_files)
            recents.forEach { recent ->
                body.addView(fileButton(if (recent.pinned) "★ ${recent.name}" else recent.name,
                    getString(R.string.open_recent, recent.name)) { openDocument(Uri.parse(recent.uri)) }, LinearLayout.LayoutParams(-1, -2))
            }
        }
        fileSidebar.addView(body)
        fileSidebar.post { if (body.parent === fileSidebar) fileSidebar.scrollTo(0, previousScroll) }
    }

    private fun switchDocument(entry: OpenDocument) {
        if (document != null && entry.key == (currentUri?.toString() ?: "shared-text")) return
        if (entry.uri != null) openDocument(Uri.parse(entry.uri))
        else entry.shared?.let { shared ->
            rememberReading(); closeSearch()
            pendingScroll = entry.reading.scroll; formatOverride = entry.reading.format; encodingOverride = null
            searchField.setText(entry.query); searchRow.visibility = if (entry.searching) View.VISIBLE else View.GONE
            load(null, entry.reading.mode) { shared }
        }
    }

    private fun showDocuments() {
        rememberReading()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.open_documents).setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton(R.string.done, null).create()
        openDocuments.entries.toList().asReversed().forEach { entry ->
            val row = row()
            val active = document != null && entry.key == (currentUri?.toString() ?: "shared-text")
            row.addView(button(R.string.open_file, if (active) "✓ ${entry.name}" else entry.name) {
                dialog.dismiss()
                switchDocument(entry)
            }.apply { contentDescription = if (active) getString(R.string.current_document, entry.name) else entry.name; isSelected = active; maxLines = 2 }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button(R.string.close_document, "×") {
                dialog.dismiss()
                if (entry.key == (currentUri?.toString() ?: if (document != null) "shared-text" else null)) showHome()
                openDocuments.close(entry.key)
                updateDocumentsButton()
                if (openDocuments.entries.isNotEmpty()) showDocuments()
            }.apply { contentDescription = getString(R.string.close_named, entry.name) })
            body.addView(row)
        }
        dialog.present()
    }

    private fun reloadDocument() {
        val uri = currentUri ?: return
        rememberReading()
        pendingScroll = webView?.scrollY ?: pendingScroll
        val encoding = encodingOverride
        load(uri, mode, pendingGrantFlags) { signal -> repository.read(uri, null, signal, encoding) }
    }

    private fun showGoToLine() {
        val source = document?.source ?: return
        val total = DocumentRenderer.logicalLines(source.text)
        val field = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; hint = "1–$total"; contentDescription = getString(R.string.line_number) }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.go_to_line).setView(field)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.go, null).create().present()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!canInteract()) return@setOnClickListener
            val line = field.text.toString().toIntOrNull()
            if (line == null || line !in 1..total) field.error = getString(R.string.line_range, total)
            else {
                dialog.dismiss(); closeSearch(); sourceMatch = null; targetLine = line; anchor = "line-$line"; render()
            }
        }
    }

    private fun showOutline() {
        val headings = document?.headings.orEmpty()
        val labels = headings.map { "${"  ".repeat(it.level - 1)}${it.title}" }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, recycled: View?, parent: android.view.ViewGroup): View =
                super.getView(position, recycled, parent).apply {
                    contentDescription = getString(R.string.heading_spoken, headings[position].level, headings[position].title)
                    minimumHeight = dp(48)
                }
        }
        AlertDialog.Builder(this).setTitle(R.string.outline)
            .setAdapter(adapter) { _, index ->
                if (canInteract()) { closeSearch(); webView?.loadUrl("$DOCUMENT_URL#${headings[index].id}") }
            }.create().present()
    }

    private fun launchOpenWith(shared: Intent) {
        @Suppress("DEPRECATION") val uri = shared.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, shared.type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = shared.clipData }
        val targets = packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).filter {
            it.activityInfo.packageName != packageName && it.activityInfo.exported
        }.distinctBy { it.activityInfo.packageName to it.activityInfo.name }
        if (targets.isEmpty()) { showNoFileHandler(); return }
        AlertDialog.Builder(this).setTitle(R.string.open_copy_title)
            .setItems(targets.map { it.loadLabel(packageManager).toString() }.toTypedArray()) { _, index ->
                if (canInteract()) try {
                    startActivity(Intent(intent).setClassName(targets[index].activityInfo.packageName, targets[index].activityInfo.name))
                } catch (_: ActivityNotFoundException) { showNoFileHandler() }
                  catch (_: SecurityException) { showNoFileHandler() }
            }.create().present()
    }

    private fun showNoFileHandler() {
        AlertDialog.Builder(this).setTitle(R.string.open_with).setMessage(R.string.no_file_handler)
            .setPositiveButton(R.string.done, null).create().present()
    }

    private fun modeLabel(value: DisplayMode): Int = when (value) {
        DisplayMode.PLAIN -> R.string.plain_text
        DisplayMode.HIGHLIGHTED -> R.string.highlighted
        DisplayMode.PREVIEW -> R.string.preview
    }

    private fun showModes() {
        if (!canInteract()) return
        val modes = document?.modes ?: return
        AlertDialog.Builder(this).setSingleChoiceItems(modes.map { getString(modeLabel(it)) }.toTypedArray(), modes.indexOf(mode)) { dialog, index ->
            if (!canInteract()) return@setSingleChoiceItems
            mode = modes[index]; pendingScroll = 0; webView?.scrollTo(0, 0); dialog.dismiss(); render()
        }.create().present()
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
            id = R.id.font_size
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
        }, LinearLayout.LayoutParams(-1, dp(48)))
        val wrapping = CheckBox(this).apply {
            id = R.id.line_wrap
            text = getString(R.string.wrap_lines)
            isChecked = wrap
            minimumHeight = dp(48)
        }
        body.addView(wrapping)
        val numbers = CheckBox(this).apply { text = getString(R.string.line_numbers); isChecked = lineNumbers; minimumHeight = dp(48) }
        body.addView(numbers)
        body.addView(TextView(this).apply { text = getString(R.string.theme) })
        val themeValues = listOf("system", "light", "dark")
        val themes = Spinner(this).apply {
            id = R.id.theme_choice
            minimumHeight = dp(48)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)))
            setSelection(themeValues.indexOf(preferences.getString("theme", "system")).coerceAtLeast(0))
            contentDescription = getString(R.string.theme)
        }
        body.addView(themes)
        val sidebar = CheckBox(this).apply {
            id = R.id.sidebar_setting
            text = getString(R.string.sidebar_setting)
            isChecked = preferences.getBoolean("fileSidebar", false)
            minimumHeight = dp(48)
        }
        body.addView(sidebar)
        body.addView(TextView(this).apply { text = getString(R.string.sidebar_help); setPadding(dp(8), 0, dp(8), dp(12)) })
        AlertDialog.Builder(this).setTitle(R.string.settings).setView(ScrollView(this).apply { addView(body) }).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.done) { _, _ ->
                if (!canInteract()) return@setPositiveButton
                val theme = themeValues[themes.selectedItemPosition]
                val themeChanged = theme != preferences.getString("theme", "system")
                fontSize = selectedSize; wrap = wrapping.isChecked; lineNumbers = numbers.isChecked
                if (lineNumbers && (document?.source?.text?.let(DocumentRenderer::logicalLines) ?: 0) > DocumentRenderer.MAX_NUMBERED_LINES)
                    Toast.makeText(this, R.string.line_number_limit, Toast.LENGTH_LONG).show()
                preferences.edit().putBoolean("fileSidebar", sidebar.isChecked).putBoolean("lineNumbers", lineNumbers).putInt("fontSize", fontSize).putBoolean("wrap", wrap).putString("theme", theme).apply()
                if (themeChanged) recreate() else {
                    updateDocumentsButton()
                    if (document != null) render()
                }
            }.create().present()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberReading()
        super.onSaveInstanceState(outState)
        outState.putString("uri", currentUri?.toString())
        // While loading, remember only a mode explicitly requested for this URI,
        // never the previous document's mode. Format changes request automatic mode.
        outState.putString("mode", if (document != null) mode.name else requestedMode?.name)
        outState.putInt("scroll", webView?.scrollY ?: pendingScroll)
        outState.putString("query", searchField.text.toString())
        outState.putString("format", formatOverride?.name)
        outState.putString("encoding", encodingOverride?.name)
        outState.putInt("grantFlags", pendingGrantFlags)
        outState.putBoolean("search", searchRow.visibility == View.VISIBLE)
    }

    private fun rememberReading() {
        val view = webView ?: return
        if (document != null && view.tag == true) {
            val reading = ReadingState(mode, view.scrollY.coerceAtLeast(0), formatOverride, encodingOverride)
            currentUri?.let { repository.saveReadingState(it, reading) }
            openDocuments.entries.find { it.key == (currentUri?.toString() ?: "shared-text") }?.let {
                it.reading = reading
                it.query = searchField.text.toString().take(1000)
                it.searching = searchRow.visibility == View.VISIBLE
            }
            openDocuments.save()
        }
    }

    override fun onStop() {
        rememberReading()
        super.onStop()
    }

    private fun navigateBack() {
        if (searchRow.visibility == View.VISIBLE) closeSearch()
        else if (currentUri != null || document != null) showHome()
        else finish()
    }

    override fun onDestroy() {
        cancelWork()
        worker.shutdownNow()
        undoHandler.removeCallbacksAndMessages(null)
        activeMenu?.dismiss()
        dialogs.toList().forEach { it.dismiss() }
        clearContent()
        super.onDestroy()
    }

    companion object {
        const val DOCUMENT_URL = "https://textr.invalid/document"
    }
}
