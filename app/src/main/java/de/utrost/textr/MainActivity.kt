package de.utrost.textr

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
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

data class ReaderReturnState(val document: PreparedDocument, val original: TextDocument,
    val uri: String?, val reading: ReadingState, val query: String, val searching: Boolean, val grantFlags: Int)

class ReaderSession : ViewModel() {
    val navigation = ReadingNavigation()
    var recentQuery = ""
    var recentKind: FileKind? = null
    var returnState: ReaderReturnState? = null
    // In-memory only: survive rotation without retaining document contents on disk.
    var document: PreparedDocument? = null
    var original: TextDocument? = null
    var previewLanguage: String? = null
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
    private var csvOptions = CsvOptions()
    private val bookmarks by lazy { Bookmarks(applicationContext) }
    private var pendingBookmark: Bookmark? = null
    private var pickerBookmark: Bookmark? = null
    private lateinit var readingProgress: TextView
    private var lineNumbers = false
    private var targetLine = 0
    private var anchor: String? = null
    private var sourceMatch: SourceMatch? = null
    private lateinit var documentStrip: HorizontalScrollView
    private lateinit var fileSidebar: ScrollView
    private var sidebarFits = false
    private lateinit var sidebarHint: LinearLayout
    private var slowLoading: Runnable? = null
    private var loadingControls: View? = null
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
    private var readingProfile: ReadingProfile? = null
    private var readingFont = ReadingFont.SANS
    private var lineSpacing = 160

    private fun applyReadingProfile() {
        val defaults = ReadingProfile.defaults(preferences)
        val effective = readingProfile?.takeUnless { it.inheritTypography } ?: defaults
        fontSize = effective.fontSize
        wrap = readingProfile?.wrap ?: defaults.wrap
        readingFont = effective.font
        lineSpacing = effective.lineSpacing
    }
    private var pendingScroll = 0
    // Generation-bound snapshot of the last visible reader before pause callbacks.
    private var pausedReadingPosition: Pair<Int, Int>? = null
    private var formatOverride: FileKind? = null
    private var requestedMode: DisplayMode? = null
    private var homeShown = false
    private var resumed = false
    private var sharing = false
    private val dialogs = mutableSetOf<android.app.Dialog>()
    private var activeMenu: PopupMenu? = null
    private var accessibleCsvDialog: AccessibleCsvDialog? = null
    private var pendingAccessibleCsv: Bundle? = null
    private var resumeDraftButton: Button? = null
    private var structureDialog: StructureDialog? = null
    private var pendingStructure: Bundle? = null
    private val outputs by lazy { ViewModelProvider(this)[OutputSession::class.java] }
    private var outputDialog: AlertDialog? = null
    private var outputDialogStatus: OutputStatus? = null
    private val outputPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data == null) outputs.pickerUnavailable()
        else outputs.pickerResult(if (result.resultCode == RESULT_OK) result.data?.data else null)
    }
    private fun canInteract() = !isDestroyed && !isFinishing && !worker.isShutdown

    private fun <T : android.app.Dialog> T.present(dismissed: () -> Unit = {}): T {
        if (!canInteract()) return this
        dialogs.add(this)
        setOnDismissListener { dialogs.remove(this); dismissed() }
        show()
        // Match our other controls and avoid oversized uppercase translated actions.
        if (this is AlertDialog) for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            getButton(which)?.isAllCaps = false
        }
        return this
    }
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.let { data ->
            data.data?.let { uri ->
                val bookmark = pickerBookmark
                pickerBookmark = null
                if (bookmark != null && bookmark.uri != uri.toString()) {
                    AlertDialog.Builder(this).setTitle(R.string.bookmark_reconnect_title)
                        .setMessage(R.string.bookmark_reconnect_help)
                        .setNegativeButton(R.string.cancel, null)
                        .setNeutralButton(R.string.open_separately) { _, _ -> openDocument(uri, grantFlags = data.flags) }
                        .setPositiveButton(R.string.bookmark_reconnect) { _, _ -> openDocument(uri, grantFlags = data.flags, bookmark = bookmark) }
                        .create().present()
                } else openDocument(uri, grantFlags = data.flags, bookmark = bookmark)
            }
        }
        pickerBookmark = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        dark = when (preferences.getString("theme", "system")) {
            "dark" -> true
            "light" -> false
            else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        setTheme(if (dark) R.style.Theme_Textr_Dark else R.style.Theme_Textr)
        super.onCreate(savedInstanceState)
        ReadingCollections(this).reconcile()
        val barStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        applyReadingProfile()
        lineNumbers = preferences.getBoolean("lineNumbers", false)
        repository = DocumentRepository(applicationContext)
        repository.setBookmarkReferences(bookmarks.entries().map { it.uri }.toSet())
        if (session.openDocuments == null) session.openDocuments = OpenDocuments(applicationContext)
        repository.undoAvailable()
        ShareFiles(this).prune()
        buildLayout()
        if (savedInstanceState?.getBoolean("outputPending") == true)
            outputs.restorePending(savedInstanceState.getString("outputName").orEmpty(), savedInstanceState.getString("outputSource"))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateBack() }
        })
        if (savedInstanceState != null) {
            readingProfile = ReadingProfile.fromJson(savedInstanceState.getString("readingProfile")?.let { org.json.JSONObject(it) })
            applyReadingProfile()
            pendingStructure = savedInstanceState.getBundle("structure")
            pendingAccessibleCsv = savedInstanceState.getBundle("accessibleCsv")
            formatOverride = FileKind.entries.find { it.name == savedInstanceState.getString("format") }
            encodingOverride = TextEncoding.entries.find { it.name == savedInstanceState.getString("encoding") }
            val savedCsv = CsvOptions(savedInstanceState.getString("csvDelimiter")?.firstOrNull()?.takeIf { it in CsvOptions.DELIMITERS } ?: ',', savedInstanceState.getBoolean("csvHeader"))
            csvOptions = if (savedInstanceState.containsKey("csvConfigured")) savedCsv.copy(configured = savedInstanceState.getBoolean("csvConfigured"))
                else savedCsv.copy(configured = savedCsv.configured || formatOverride == FileKind.CSV)
            pendingBookmark = bookmarks.entries().find { it.id == savedInstanceState.getString("pendingBookmark") }
            pickerBookmark = bookmarks.entries().find { it.id == savedInstanceState.getString("pickerBookmark") }
            pendingGrantFlags = savedInstanceState.getInt("grantFlags")
            pendingScroll = savedInstanceState.getInt("scroll")
            searchField.setText(savedInstanceState.getString("query", ""))
            searchRow.visibility = if (savedInstanceState.getBoolean("search")) View.VISIBLE else View.GONE
            val savedMode = savedInstanceState.getString("mode")?.let { value -> DisplayMode.entries.find { it.name == value } }
            val uri = savedInstanceState.getString("uri")
            if (document != null) {
                currentUri = uri?.let(Uri::parse)
                mode = savedMode?.takeIf { it in document!!.modes } ?: document!!.defaultMode
                val original = session.original
                if (original != null && session.previewLanguage != resources.configuration.locales.toLanguageTags()) {
                    load(currentUri, mode, pendingGrantFlags) { original }
                } else render()
            } else if (uri != null) openDocument(Uri.parse(uri), restoredMode = savedMode, restoring = true, bookmark = pendingBookmark)
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
        sidebarHint = LinearLayout(this).apply {
            id = R.id.sidebar_hint
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(16), 0, dp(16), 0)
            addView(TextView(this@MainActivity).apply { setText(R.string.sidebar_discovery); setTextColor(foreground()) })
            addView(row().apply {
                addView(button(R.string.settings) { showSettings() })
                addView(button(R.string.dismiss_hint) {
                    preferences.edit().putBoolean("sidebarHintDismissed", true).apply()
                    updateDocumentsButton()
                })
            })
        }
        root.addView(sidebarHint)
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
                    if (searchField.hasFocus() && !s.isNullOrEmpty()) rememberPosition()
                    if (searchField.hasFocus()) (webView as? ReaderWebView)?.cancelFindRestore()
                    webView?.takeIf { it.tag == true }?.findAllAsync(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            setOnEditorActionListener { _, _, _ -> findNext(true); true }
        }
        searchRow.addView(searchField, if (wide) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
        val searchActions = row()
        searchCount = TextView(this).apply {
            id = R.id.search_count
            textSize = 14f; setTextColor(foreground())
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        searchActions.addView(searchCount, if (wide) LinearLayout.LayoutParams(-2, -2) else LinearLayout.LayoutParams(0, -2, 1f))
        searchActions.addView(button(R.string.previous_match, "↑") { findNext(false) })
        searchActions.addView(button(R.string.next_match, "↓") { findNext(true) })
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
        readingProgress = TextView(this).apply {
            id = R.id.reading_progress; textSize = 12f; setTextColor(foreground())
            // Progress must not resize the viewport when its percentage changes.
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(16), dp(2), dp(16), dp(2)); visibility = View.GONE
        }
        root.addView(readingProgress)
        readingArea.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            // Measure the actual inset-adjusted app window, including split-screen resizing.
            val density = resources.displayMetrics.density
            val fits = view.width / density >= 600 &&
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
        (webView as? ReaderWebView)?.cancelFindRestore()
        generation++
        stopSlowLoading()
        cancellation?.cancel()
        cancellation = null
        work?.cancel(true)
        sharing = false
    }

    private fun clearContent(stopLoading: Boolean = true) {
        removeLoadingControls()
        if (::readingProgress.isInitialized) readingProgress.visibility = View.GONE
        webView?.let { view ->
            content.removeView(view)
            if (stopLoading) view.stopLoading()
            view.destroy()
        }
        webView = null
        updateScreenAwake()
        content.removeAllViews()
    }

    private fun showHome() {
        if (!canInteract()) return
        accessibleCsvDialog?.dismiss()
        structureDialog?.dismiss()
        pendingStructure = null
        pendingAccessibleCsv = null
        rememberReading()
        cancelWork()
        document = null
        session.original = null
        session.returnState = null
        formatOverride = null
        encodingOverride = null
        csvOptions = CsvOptions()
        readingProfile = null
        applyReadingProfile()
        pendingBookmark = null
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
        val recents = repository.recentFiles().sortedWith(compareByDescending<RecentFile> { it.pinned }.thenByDescending { it.openedAt })
        recents.maxByOrNull { it.openedAt }?.let { recent ->
            val caption = getString(R.string.continue_reading_file, recent.name) +
                (recent.progress?.let { "\n" + getString(R.string.continue_reading_progress, it) } ?: "")
            body.addView(button(R.string.continue_reading, caption) { openDocument(Uri.parse(recent.uri)) }.apply {
                id = R.id.continue_reading
                contentDescription = caption
                maxLines = 4
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, -2))
        }
        resumeDraftButton = button(R.string.editor_resume) { startActivity(Intent(this, EditorActivity::class.java)) }.apply {
            id = R.id.editor_resume; visibility = if (EditDraftStore(this@MainActivity).exists()) View.VISIBLE else View.GONE
        }
        body.addView(resumeDraftButton, LinearLayout.LayoutParams(-1, -2))
        body.addView(button(R.string.collections) { startActivity(Intent(this, CollectionsActivity::class.java)) }.apply { id = R.id.collections })
        val recentHeader = row()
        recentHeader.addView(TextView(this).apply { text = getString(R.string.recent_files); textSize = 20f; setTextColor(foreground()) }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(recentHeader)
        if (recents.isEmpty()) body.addView(TextView(this).apply {
            text = getString(R.string.empty_history); textSize = 16f; setTextColor(foreground()); setPadding(0, dp(16), 0, 0)
        })
        if (recents.isEmpty() && !preferences.getBoolean("openingHintDismissed", false)) {
            body.addView(LinearLayout(this).apply {
                id = R.id.opening_hint
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    setText(R.string.opening_discovery); setTextColor(foreground()); setPadding(0, dp(16), 0, 0)
                })
                addView(button(R.string.opening_help) { showOpeningHelp() })
                addView(button(R.string.dismiss_hint) {
                    preferences.edit().putBoolean("openingHintDismissed", true).apply()
                    showHome()
                })
            })
        }
        if (repository.undoAvailable()) {
            body.addView(button(R.string.undo_remove) { repository.undoRemoval(); showHome() })
            undoHandler.removeCallbacks(finishUndo)
            undoHandler.postDelayed(finishUndo, repository.undoRemainingMillis() + 1)
        }
        val recentList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; id = R.id.recent_results }
        val query = EditText(this).apply {
            id = R.id.recent_query; hint = getString(R.string.recent_search); contentDescription = hint
            isSingleLine = true; isSaveEnabled = false
            filters = arrayOf(android.text.InputFilter.LengthFilter(256)); setText(session.recentQuery)
        }
        val kinds = listOf<FileKind?>(null) + FileKind.entries
        val format = Spinner(this).apply {
            id = R.id.recent_format; contentDescription = getString(R.string.recent_format)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, kinds.map { if (it == null) getString(R.string.all_formats) else formatName(it) })
            setSelection(kinds.indexOf(session.recentKind).coerceAtLeast(0))
        }
        fun filterRecents() {
            recentList.removeAllViews()
            val filtered = RecentFiltering.filter(recents, session.recentQuery, session.recentKind)
            if (filtered.isEmpty() && recents.isNotEmpty()) recentList.addView(TextView(this).apply { setText(R.string.recent_no_matches) })
            filtered.forEach { recent ->
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
                entry.addView(button(R.string.clear_history, "×") { repository.removeWithUndo(recent.uri); openDocuments.forgetClosed(recent.uri); showHome() }.apply {
                    contentDescription = getString(R.string.remove_recent, recent.name)
                })
                recentList.addView(entry)
            }
        }
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { session.recentQuery = s.toString(); filterRecents() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        format.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { session.recentKind = kinds[position]; filterRecents() }
        }
        if (recents.isNotEmpty()) {
            body.addView(query); body.addView(format)
            body.addView(button(R.string.clear_filters) { session.recentKind = null; session.recentQuery = ""; query.setText(""); format.setSelection(0); filterRecents() })
        }
        body.addView(recentList); filterRecents()
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
                item(R.string.copy_all, ready) { copyAll() }
                item(R.string.previous_position, ready && session.navigation.hasPrevious(documentKey())) { moveReadingPosition(forward = false) }
                if (document?.source?.kind == FileKind.HTML) item(R.string.copy_markdown, ready && document?.convertedMarkdown != null) {
                    document?.convertedMarkdown?.let { copyText(it, R.string.copy_markdown) }
                }
                item(if (currentUri == null && (session.original?.bytes ?: 0) < 100_000) R.string.share_text else R.string.share_file,
                    ready && !sharing) { shareDocument() }
                item(R.string.open_with, ready && !sharing) { shareDocument(openWith = true) }
                item(R.string.reload, ready && currentUri != null) { reloadDocument() }
                item(R.string.treat_as, ready) { showFormats() }
                item(R.string.document_info, ready) { showDocumentInfo() }
                item(R.string.compare_title, ready) { startComparison() }
                if (session.original?.kind in setOf(FileKind.TEXT, FileKind.MARKDOWN)) item(R.string.editor_edit_copy, ready && !outputs.busy) { startEditor() }
                if (document?.source?.kind == FileKind.HTML) {
                    if (document?.convertedMarkdown != null) item(R.string.html_export, ready && !outputs.busy) { exportHtml() }
                    else item(R.string.html_export_unavailable, ready) {
                        AlertDialog.Builder(this@MainActivity).setTitle(R.string.html_export).setMessage(R.string.html_export_unavailable_help)
                            .setPositiveButton(R.string.done, null).create().present()
                    }
                }
                if (document?.structure != null) item(R.string.structure_outline, ready) { showStructure() }
                if (document?.source?.kind == FileKind.CSV) {
                    item(R.string.csv_accessible, ready && document?.preview != null) { showAccessibleCsv() }
                    item(R.string.csv_options, ready) { showCsvOptions() }
                }
                item(R.string.add_bookmark, ready && currentUri != null && !(document?.source?.kind == FileKind.CSV && mode == DisplayMode.PREVIEW)) { addBookmark() }
                if (mode != DisplayMode.PREVIEW) item(R.string.go_to_line, ready) { showGoToLine() }
                if (mode == DisplayMode.PREVIEW && document?.headings?.isNotEmpty() == true) item(R.string.outline, ready) { showOutline() }
            } else if (repository.recentFiles().isNotEmpty()) {
                item(R.string.clear_history) {
                    AlertDialog.Builder(this@MainActivity).setTitle(R.string.clear_history).setMessage(R.string.clear_history_message)
                        .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.done) { _, _ ->
                            if (canInteract()) { repository.clear(); openDocuments.clearClosed(); session.recentQuery = ""; session.recentKind = null; showHome() }
                        }.create().present()
                }
            }
            item(R.string.reopen_closed, openDocuments.hasClosed) { reopenClosed() }
            if (openDocuments.entries.isNotEmpty()) item(R.string.open_documents) { showDocuments() }
            item(R.string.bookmarks) { showBookmarks() }
            item(R.string.collections) { startActivity(Intent(this@MainActivity, CollectionsActivity::class.java)) }
            item(R.string.settings) { showSettings() }
            if (!homeShown) {
                val ready = document != null && webView?.tag == true
                item(R.string.next_position, ready && session.navigation.hasNext(documentKey())) { moveReadingPosition(forward = true) }
                item(R.string.add_to_collection, ready && currentUri != null) { addCurrentToCollection() }
                if (mode != DisplayMode.PREVIEW) {
                    item(if (wrap) R.string.disable_line_wrap else R.string.enable_line_wrap, ready) { toggleLineWrap() }
                }
            }
            if (homeShown) item(R.string.opening_help) { showOpeningHelp() }
            item(R.string.about) { showAbout() }
            show()
        }
    }

    private fun toggleLineWrap() {
        if (document == null || webView?.tag != true || mode == DisplayMode.PREVIEW) return
        val profile = readingProfile ?: ReadingProfile(inheritTypography = true)
        readingProfile = profile.copy(wrap = !wrap)
        applyReadingProfile()
        rememberReading()
        render()
    }

    private fun addCurrentToCollection() {
        val uri = currentUri ?: return
        val name = document?.source?.name ?: return
        if (webView?.tag != true) return
        val store = ReadingCollections(this)
        val member = CollectionMember(uri.toString(), name)
        val collections = store.entries()
        val available = collections.filter { collection ->
            collection.members.size < ReadingCollections.MAX_MEMBERS && collection.members.none { it.uri == member.uri }
        }
        val space = collections.sumOf { it.members.size } < ReadingCollections.MAX_TOTAL
        fun result(added: Boolean) {
            Toast.makeText(this, if (added) R.string.collection_added else R.string.collection_cannot_add, Toast.LENGTH_SHORT).show()
        }
        fun createCollection() {
            val field = EditText(this).apply {
                hint = getString(R.string.collection_name)
                isSingleLine = true
                filters = arrayOf(android.text.InputFilter.LengthFilter(ReadingCollections.MAX_NAME))
            }
            val dialog = AlertDialog.Builder(this).setTitle(R.string.collection_create).setView(field)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.collection_create, null).create()
            dialog.present()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (store.create(field.text.toString(), member) == null) field.error = getString(R.string.collection_limit)
                else { dialog.dismiss(); result(true) }
            }
        }
        val canCreate = space && collections.size < ReadingCollections.MAX_COLLECTIONS
        if (!space || available.isEmpty() && !canCreate) {
            result(false)
            return
        }
        val chooser = AlertDialog.Builder(this).setTitle(R.string.add_to_collection)
            .setNegativeButton(R.string.cancel, null)
        if (available.isEmpty()) chooser.setMessage(R.string.collection_choose_create)
        else chooser.setItems(available.map { it.name }.toTypedArray()) { _, index ->
            result(store.add(available[index].id, member))
        }
        if (canCreate) chooser.setNeutralButton(R.string.collection_create) { _, _ -> createCollection() }
        chooser.create().present()
    }

    private fun copyAll() {
        if (!canInteract() || webView?.tag != true) return
        val source = session.original ?: return
        copyText(source.text, R.string.copy_all)
    }

    private fun copyText(text: String, label: Int) {
        // Bound the UTF-16 clipboard payload below Android's shared Binder transaction budget.
        // Never truncate: a rejected copy leaves the existing clipboard untouched.
        if (text.length > 256 * 1024) {
            AlertDialog.Builder(this).setTitle(label).setMessage(if (label == R.string.copy_markdown) R.string.copy_markdown_limit else R.string.copy_all_limit)
                .setPositiveButton(R.string.done, null).create().present()
            return
        }
        try {
            val clip = ClipData.newPlainText(getString(R.string.app_name), text)
            if (Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.structure_copied, Toast.LENGTH_SHORT).show()
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.copy_all_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showAbout() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.about_details, version, "").trimEnd()
                textSize = 20f
                isAccessibilityHeading = true
                setTextIsSelectable(true)
            })
            addView(button(R.string.source_code) { openExternal("https://github.com/utrost/Textr") })
            addView(button(R.string.report_issue) { openExternal("https://github.com/utrost/Textr/issues") })
            addView(button(R.string.privacy_policy) { openExternal("https://github.com/utrost/Textr/blob/main/docs/privacy.md") })
            addView(button(R.string.licenses) { showLicenses() })
            addView(button(R.string.opening_help) { showOpeningHelp() })
            addView(button(R.string.keyboard_shortcuts) { showShortcutHelp() })
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.about_privacy); textSize = 16f
                setTextIsSelectable(true); setPadding(0, dp(12), 0, 0)
            })
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
        FileKind.ENV -> ".env"
        FileKind.PROPERTIES -> ".properties"
        FileKind.DOCKERFILE -> "Dockerfile"
        FileKind.TYPESCRIPT -> "TypeScript"
        FileKind.TSX -> "TypeScript / TSX"
        FileKind.JSON_LINES -> "JSON Lines / NDJSON"
        FileKind.ASCIIDOC -> "AsciiDoc"
        FileKind.RESTRUCTUREDTEXT -> "reStructuredText"
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

    internal fun findSelectedText(selection: CharSequence?, expected: TextDocument? = document?.source) {
        if (selection.isNullOrBlank() || selection.length > SourceSearch.MAX_QUERY) {
            Toast.makeText(this, R.string.selection_limit, Toast.LENGTH_LONG).show(); return
        }
        val source = document
        if (source == null || source.source !== expected || webView?.tag != true) {
            Toast.makeText(this, R.string.selection_unavailable, Toast.LENGTH_LONG).show(); return
        }
        val selected = selection.toString()
        root.post { if (canInteract() && document === source && webView?.tag == true) showSourceSearch(selected) }
    }

    private fun showSourceSearch(selected: String? = null) {
        val source = document?.source ?: return
        if (webView?.tag != true) return
        val entry = openDocuments.entries.find { it.key == (currentUri?.toString() ?: "shared-text") } ?: return
        val initial = entry.sourceSearch.let { if (selected != null) it.copy(query = selected) else if (it.query.isEmpty()) it.copy(query = searchField.text.toString().take(SourceSearch.MAX_QUERY)) else it }
        val search = SourceSearchDialog(this, source.text, initial, { entry.sourceSearch = it }) { match ->
            if (canInteract() && document?.source === source) {
                rememberPosition()
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

    private fun startEditor() {
        val original = session.original ?: return
        if (original.kind !in setOf(FileKind.TEXT, FileKind.MARKDOWN) || !EditBuffer.accepts(original.text)) {
            AlertDialog.Builder(this).setTitle(R.string.editor_edit_copy).setMessage(R.string.editor_limit)
                .setPositiveButton(R.string.done, null).create().present()
            return
        }
        val snapshot = EditorSnapshot(original, currentUri?.toString(), original.hasBom)
        AlertDialog.Builder(this).setTitle(R.string.editor_edit_copy).setMessage(R.string.editor_recovery_notice)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.editor_edit_copy) { _, _ ->
                val token = EditorInbox.put(snapshot) ?: return@setPositiveButton
                startActivity(Intent(this, EditorActivity::class.java).putExtra("token", token))
            }.create().present()
    }

    private fun startComparison() {
        val original = session.original ?: return
        val reference = ComparisonRef(currentUri?.toString(), original.name, encodingOverride, sourceFingerprint(original.text))
        val token = ComparisonInbox.put(ComparisonSnapshot(reference, original))
        if (token == null) {
            AlertDialog.Builder(this).setTitle(R.string.compare_title).setMessage(R.string.compare_limited)
                .setPositiveButton(R.string.done, null).create().present()
            return
        }
        startActivity(Intent(this, ComparisonActivity::class.java).putExtra("token", token)
            .putExtra("uri", reference.uri).putExtra("name", reference.name)
            .putExtra("encoding", reference.encoding?.name).putExtra("fingerprint", reference.fingerprint))
    }

    private fun exportHtml() {
        val prepared = document ?: return
        val markdown = prepared.convertedMarkdown ?: return
        if (outputs.busy || webView?.tag != true) return
        val sourceUri = currentUri?.toString()
        val name = OutputNames.markdown(prepared.source.name)
        AlertDialog.Builder(this).setTitle(R.string.html_export).setMessage(R.string.html_export_help)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.choose_destination) { _, _ ->
                if (!canInteract() || outputs.busy) return@setPositiveButton
                val intent = outputs.begin(OutputRequest(name, "text/markdown", markdown, sourceUri))
                try { outputPicker.launch(intent) }
                catch (_: ActivityNotFoundException) { outputs.pickerUnavailable() }
                catch (_: SecurityException) { outputs.pickerUnavailable() }
            }.create().present()
    }

    private fun showOutputStatus(status: OutputStatus) {
        if (!canInteract() || outputDialogStatus == status && outputDialog?.isShowing == true) return
        outputDialog?.dismiss()
        outputDialogStatus = status
        outputDialog = OutputDialogs.create(this, status, outputs::cancel,
            retry = if (document?.convertedMarkdown != null && webView?.tag == true) ({ outputs.acknowledge(); exportHtml() }) else null,
            acknowledge = outputs::acknowledge)?.also { it.present() }
    }

    private fun shareDocument(openWith: Boolean = false) {
        if (!canInteract()) return
        val source = session.original ?: return
        if (!openWith && currentUri == null && source.bytes < 100_000) {
            launchShare(Intent(Intent.ACTION_SEND).setType(if (source.kind == FileKind.HTML) "text/html" else "text/plain").putExtra(Intent.EXTRA_TEXT, source.text))
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

    private fun openPicker(bookmark: Bookmark? = null) {
        pickerBookmark = bookmark
        try {
            picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            })
        } catch (_: ActivityNotFoundException) {
            pickerBookmark = null
            Toast.makeText(this, R.string.picker_unavailable, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            pickerBookmark = null
            Toast.makeText(this, R.string.picker_unavailable, Toast.LENGTH_LONG).show()
        }
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
            openDocument(uri, intent.type, grantFlags = if (intent.getBooleanExtra(ReaderOpening.RETAIN_READ, false)) Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION else intent.flags)
        } else {
            val sharedHtml = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
            val sharedText = sharedHtml ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (intent.action == Intent.ACTION_SEND && sharedText != null) {
                rememberReading()
                closeSearch()
                pendingScroll = 0
                formatOverride = null
                encodingOverride = null
                csvOptions = CsvOptions()
                readingProfile = null
                applyReadingProfile()
                pendingBookmark = null
                val htmlInput = sharedHtml != null || TextDocuments.kind("", intent.type) == FileKind.HTML
                // Shared text has no reopenable URI and is deliberately excluded from history.
                load(null) {
                    TextDocuments.read(sharedText.byteInputStream(), getString(if (htmlInput) R.string.shared_html_name else R.string.shared_text_name), if (htmlInput) "text/html" else "text/plain")
                }
            } else showError(R.string.unsupported_uri)
        }
        return true
    }

    private fun openDocument(uri: Uri, mime: String? = null, restoredMode: DisplayMode? = null, grantFlags: Int = 0, restoring: Boolean = false, bookmark: Bookmark? = null) {
        rememberReading()
        pendingBookmark = bookmark
        val existing = openDocuments.entries.find { it.uri == uri.toString() }
        val state = bookmark?.reading ?: existing?.reading ?: repository.readingState(uri)
        if (!restoring) {
            closeSearch()
            searchField.setText(if (bookmark == null) existing?.query.orEmpty() else "")
            searchRow.visibility = if (bookmark == null && existing?.searching == true) View.VISIBLE else View.GONE
            formatOverride = state.format
            encodingOverride = state.encoding
            readingProfile = state.profile
            applyReadingProfile()
            csvOptions = state.csv
            pendingScroll = state.scroll
        }
        val encoding = encodingOverride
        load(uri, if (restoring) restoredMode else state.mode, if (restoring) pendingGrantFlags else grantFlags) { signal -> repository.read(uri, mime, signal, encoding) }
    }

    private fun load(uri: Uri?, restoredMode: DisplayMode? = null, grantFlags: Int = 0, reader: (CancellationSignal) -> TextDocument) {
        if (!canInteract()) return
        accessibleCsvDialog?.dismiss()
        structureDialog?.dismiss()
        cancelWork()
        requestedMode = restoredMode
        document = null
        currentUri = uri
        pendingGrantFlags = grantFlags
        sourceMatch = null
        val signal = CancellationSignal().also { cancellation = it }
        val request = generation
        val selectedFormat = formatOverride
        val selectedCsv = csvOptions
        val language = resources.configuration.locales.toLanguageTags()
        val english = createConfigurationContext(Configuration(resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) })
            .resources.getStringArray(R.array.preview_labels)
        val translations = english.zip(resources.getStringArray(R.array.preview_labels)).toMap()
        showProgress(R.string.loading)
        work = worker.submit {
            try {
                val original = reader(signal)
                signal.throwIfCanceled()
                runOnUiThread { if (request == generation && !isDestroyed) showProgress(R.string.rendering) }
                val resolvedCsv = if (selectedCsv.configured) selectedCsv else selectedCsv.copy(delimiter = original.defaultCsvDelimiter)
                val prepared = DocumentRenderer.prepare(original.copy(kind = selectedFormat ?: original.kind), resolvedCsv) { translations[it] ?: it }.also { it.fingerprint }
                runOnUiThread {
                    if (request != generation || isDestroyed) return@runOnUiThread
                    csvOptions = resolvedCsv
                    session.original = original
                    session.previewLanguage = language
                    val key = uri?.toString() ?: "shared-text"
                    val evicted = openDocuments.entries.firstOrNull()?.takeIf {
                        openDocuments.entries.size >= OpenDocuments.LIMIT && openDocuments.entries.none { it.key == key }
                    }
                    openDocuments.opened(uri?.toString(), original)
                    if (evicted != null) Toast.makeText(this, getString(R.string.session_evicted, evicted.name), Toast.LENGTH_LONG).show()
                    updateDocumentsButton()
                    session.navigation.bind(key, prepared.fingerprint)
                    document = prepared
                    mode = restoredMode?.takeIf { it in prepared.modes } ?: prepared.defaultMode
                    if (uri != null) {
                        repository.retainReadAccess(uri, grantFlags)
                        repository.remember(uri, prepared.source.name, original.kind)
                        pendingBookmark?.let {
                            if (it.uri != uri.toString()) bookmarks.reconnect(it.id, uri.toString(), prepared.source.name)
                        }
                    }
                    render()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (request == generation && !isDestroyed) showError(readErrorMessage(error))
                }
            }
        }
    }

    private fun showProgress(message: Int, keepReader: Boolean = false) {
        if (keepReader && webView != null) {
            removeLoadingControls()
            webView?.apply {
                stopLoading()
                tag = false
                (this as ReaderWebView).afterNextDraw = null
            }
        } else clearContent()
        homeShown = false
        backButton.visibility = View.VISIBLE
        findButton.visibility = View.VISIBLE
        toolbar.visibility = View.GONE
        modeButton.isEnabled = false
        findButton.isEnabled = false
        readingProgress.visibility = View.GONE
        titleView.text = getString(message)
        updateDocumentsButton()
        updateScreenAwake()
        showLoadingControls(message)
    }

    private fun stopSlowLoading() {
        slowLoading?.let(undoHandler::removeCallbacks)
        slowLoading = null
    }

    private fun removeLoadingControls() {
        stopSlowLoading()
        loadingControls?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        loadingControls = null
    }

    private fun showLoadingControls(message: Int) {
        removeLoadingControls()
        val besideReader = webView != null
        val overlay = ScrollView(this).apply {
            id = R.id.reader_loading
            setBackgroundColor(background())
            isFillViewport = true
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        body.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(if (besideReader) 24 else 48), dp(if (besideReader) 24 else 48)))
        body.addView(TextView(this).apply {
            id = R.id.loading_stage; setText(message); textSize = 18f; setTextColor(foreground())
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        })
        val explanation = TextView(this).apply {
            id = R.id.loading_explanation; visibility = View.GONE; setTextColor(foreground())
            setText(if (message == R.string.loading) R.string.slow_reading else R.string.slow_rendering)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(button(R.string.cancel_loading) { returnToReader() })
        body.addView(button(R.string.open_file) { openPicker() })
        body.addView(explanation)
        overlay.addView(body)
        loadingControls = overlay
        if (besideReader) {
            // A separate native panel keeps the WebView visible to the platform
            // while it builds its virtual accessibility tree. Never cover it with
            // an opaque loading sibling in the same FrameLayout.
            val available = (root.height - root.paddingTop - root.paddingBottom)
                .takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            root.addView(overlay, root.indexOfChild(searchRow) + 1,
                LinearLayout.LayoutParams(-1, minOf(dp(240), available / 2).coerceAtLeast(dp(80))))
        } else content.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        slowLoading = Runnable {
            if (canInteract() && loadingControls === overlay) explanation.visibility = View.VISIBLE
        }.also { undoHandler.postDelayed(it, 1500) }
    }

    private fun showError(message: Int) {
        cancelWork()
        document = null
        session.original = null
        updateDocumentsButton()
        findButton.visibility = View.GONE
        clearContent()
        toolbar.visibility = View.GONE
        readingProgress.visibility = View.GONE
        closeSearch()
        titleView.text = getString(R.string.cannot_open)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        body.addView(TextView(this).apply { text = getString(message); textSize = 18f; setTextColor(foreground()) })
        if (currentUri != null && message in setOf(R.string.unsupported_encoding, R.string.binary_file))
            body.addView(button(R.string.reopen_encoding) { showEncodings() })
        if (pendingBookmark != null) body.addView(TextView(this).apply { text = getString(R.string.bookmark_unavailable); setTextColor(foreground()) })
        if (currentUri != null && message in setOf(R.string.access_error, R.string.access_denied, R.string.file_unavailable, R.string.provider_unavailable))
            body.addView(button(R.string.retry_open) {
                currentUri?.let { openDocument(it, restoredMode = requestedMode, grantFlags = pendingGrantFlags, restoring = true, bookmark = pendingBookmark) }
            })
        body.addView(button(R.string.open_again) { openPicker(pendingBookmark) })
        body.addView(TextView(this).apply { setText(R.string.reselect_help); setTextColor(foreground()) })
        body.addView(button(if (session.returnState != null) R.string.return_to_document else R.string.back_to_files) { returnToReader() })
        content.addView(ScrollView(this).apply { isFillViewport = true; addView(body) }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun render() {
        if (!canInteract()) return
        val prepared = document ?: return
        val scroll = readerPosition()
        pendingScroll = scroll
        cancelWork()
        val request = generation
        showProgress(R.string.rendering, keepReader = true)
        val selectedMode = mode
        val selectedMatch = sourceMatch
        val selectedLine = targetLine
        targetLine = 0
        val selectedAnchor = anchor
        anchor = null
        work = worker.submit {
            val page = try {
                DocumentRenderer.page(prepared, selectedMode, dark, wrap, lineNumbers, selectedLine, selectedMatch, readingFont, lineSpacing)
            } catch (_: java.util.concurrent.CancellationException) {
                return@submit
            } catch (_: RuntimeException) {
                runOnUiThread { if (request == generation && !isDestroyed) showRenderRecovery() }
                return@submit
            }
            runOnUiThread {
                if (request != generation || isDestroyed) return@runOnUiThread
                removeLoadingControls()
                titleView.text = prepared.source.name
                updateDocumentsButton()
                modeButton.text = getString(modeLabel(selectedMode))
                modeButton.contentDescription = getString(R.string.mode_description, getString(modeLabel(selectedMode)))
                toolbar.visibility = if (prepared.modes.size > 1) View.VISIBLE else View.GONE
                modeButton.isEnabled = false
                findButton.isEnabled = false
                // Reuse the attached reader when changing modes/settings. Replacing
                // it tears down Chromium's accessibility bridge on every transition.
                val view = createWebView(scroll, page, selectedAnchor, request, webView as? ReaderWebView)
                webView = view
                if (view.parent == null) content.addView(view, FrameLayout.LayoutParams(-1, -1))
                showLoadingControls(R.string.laying_out)
                // Stream in-memory content rather than embedding multi-MiB text in a data URL.
                view.loadUrl(view.documentUrl)
                if (prepared.notice != PreviewNotice.NONE) {
                    Toast.makeText(this, if (prepared.notice == PreviewNotice.MARKDOWN_LIMIT) R.string.markdown_limit
                        else if (prepared.notice == PreviewNotice.HTML_LIMIT) R.string.html_limit
                        else if (prepared.notice == PreviewNotice.JSON_LIMIT) R.string.json_limit
                        else if (prepared.notice == PreviewNotice.HIGHLIGHT_LIMIT) R.string.highlight_limit
                        else if (prepared.notice == PreviewNotice.CALENDAR_LIMIT) R.string.calendar_limit
                        else if (prepared.notice == PreviewNotice.CSV_UNAVAILABLE) R.string.csv_unavailable
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

    private inner class ReaderWebView(context: android.content.Context) : WebView(context) {
        var restoringPosition: Int? = null; private set
        private var pendingFindPosition: Int? = null
        private var findRestore: Runnable? = null
        private var restoreDeadline = 0L
        fun cancelFindRestore() {
            findRestore?.let(::removeCallbacks)
            findRestore = null; restoringPosition = null; pendingFindPosition = null
        }
        fun prepareFindRestore(position: Int?) {
            cancelFindRestore()
            pendingFindPosition = position; restoringPosition = position
        }
        fun completeFindRestore(current: () -> Boolean) {
            val position = pendingFindPosition ?: return
            cancelFindRestore()
            restoringPosition = position
            restoreDeadline = android.os.SystemClock.uptimeMillis() + 2000
            findRestore = Runnable {
                cancelFindRestore()
                if (current() && tag == true) scrollTo(0, position)
            }
            settleFindScroll()
        }
        private fun settleFindScroll() {
            findRestore?.let { restore ->
                removeCallbacks(restore)
                // Chromium may animate to the active match after find completion.
                // Restore after scrolling settles, with a bounded wait and user cancellation.
                postDelayed(restore, minOf(200L, (restoreDeadline - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0)))
            }
        }
        override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
            super.onScrollChanged(left, top, oldLeft, oldTop)
            settleFindScroll()
        }
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) cancelFindRestore()
            return super.dispatchKeyEvent(event)
        }
        override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
            if (event.actionMasked == android.view.MotionEvent.ACTION_SCROLL) cancelFindRestore()
            return super.onGenericMotionEvent(event)
        }
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) cancelFindRestore()
            return super.onTouchEvent(event)
        }
        override fun startActionMode(callback: android.view.ActionMode.Callback, type: Int): android.view.ActionMode? {
            val wrapped = object : android.view.ActionMode.Callback2() {
                override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = callback.onCreateActionMode(mode, menu)
                override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = callback.onPrepareActionMode(mode, menu)
                override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                    if (item.intent?.component?.className == SelectionSearchActivity::class.java.name) {
                        SelectionSearchActivity.reader = java.lang.ref.WeakReference(this@MainActivity)
                        SelectionSearchActivity.source = java.lang.ref.WeakReference(document?.source)
                    }
                    return callback.onActionItemClicked(mode, item)
                }
                override fun onDestroyActionMode(mode: android.view.ActionMode) = callback.onDestroyActionMode(mode)
                override fun onGetContentRect(mode: android.view.ActionMode, view: View, rect: android.graphics.Rect) {
                    if (callback is android.view.ActionMode.Callback2) callback.onGetContentRect(mode, view, rect) else super.onGetContentRect(mode, view, rect)
                }
            }
            return super.startActionMode(wrapped, type)
        }
        var documentUrl = DOCUMENT_URL
        var afterNextDraw: (() -> Unit)? = null
        var onViewportDraw: (() -> Unit)? = null

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            onViewportDraw?.let { post(it) }
            afterNextDraw?.let { action ->
                afterNextDraw = null
                post { action() }
            }
        }
    }

    private fun createWebView(scroll: Int, page: HtmlPage, initialAnchor: String?, renderGeneration: Int,
                              existing: ReaderWebView?) = (existing ?: ReaderWebView(this)).apply {
        val pageUrl = "$DOCUMENT_URL?render=$renderGeneration"
        documentUrl = pageUrl
        fun current() = webView === this && generation == renderGeneration && canInteract()
        id = R.id.reader
        tag = false
        prepareFindRestore(if (searchRow.visibility == View.VISIBLE && searchField.text.isNotEmpty()) scroll else null)
        // Keep the WebView accessibility bridge enabled throughout loading.
        // Do not invalidate its virtual tree by toggling descendant exposure.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        onViewportDraw = { if (webView === this) updateProgress() }
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
        setOnScrollChangeListener { _, _, _, _, _ -> updateProgress() }
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateProgress() }
        setFindListener { active, total, done ->
            if (done && current() && tag == true) {
                completeFindRestore(::current)
                searchCount.text = if (searchField.text.isEmpty()) "" else if (total == 0) getString(R.string.no_matches) else getString(R.string.matches, active + 1, total)
                searchCount.contentDescription = if (searchField.text.isEmpty()) "" else if (total == 0) getString(R.string.no_matches) else getString(R.string.matches_spoken, active + 1, total)
            }
        }
        webViewClient = object : WebViewClient() {
            private var laidOut = false
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                if (request.isForMainFrame && request.url.toString().substringBefore('#') == pageUrl) {
                    WebResourceResponse("text/html", "UTF-8", page.openStream())
                } else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (webView === view) showRenderRecovery(rendererGone = true)
                else view.destroy()
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.isForMainFrame && request.url.toString().substringBefore('#') == pageUrl && request.url.fragment != null) return false
                if (request.hasGesture() && request.isForMainFrame) {
                    val uri = request.url
                    if (uri.toString().substringBefore('#') == pageUrl && uri.fragment != null) return false
                    if (uri.scheme in setOf("https", "http", "mailto") && uri.host != "textr.invalid") {
                        try { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        catch (_: ActivityNotFoundException) { Toast.makeText(this@MainActivity, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
                        catch (_: SecurityException) { Toast.makeText(this@MainActivity, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
                    } else Toast.makeText(this@MainActivity, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!current() || laidOut || url.substringBefore('#') != pageUrl) return
                laidOut = true
                view.clearHistory()
                // The visual callback promises the DOM on the next draw, not the
                // current native contentHeight. Clamp only after that draw so an
                // initial zero height cannot erase a saved reading position.
                view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (!current()) return
                        val reader = view as ReaderWebView
                        reader.afterNextDraw = {
                            if (current()) {
                                removeLoadingControls()
                                // Removing the native panel expands the viewport. Restore
                                // position only after that final layout has been drawn.
                                reader.afterNextDraw = {
                                    if (current()) {
                                        if (initialAnchor != null) view.loadUrl("$pageUrl#$initialAnchor") else {
                                            @Suppress("DEPRECATION")
                                            val bottom = (view.contentHeight * view.scale).toInt() - view.height
                                            val saved = pendingBookmark
                                            val position = saved?.let { (bottom.coerceAtLeast(0) * it.fraction).toInt() } ?: scroll
                                            view.scrollTo(0, position.coerceIn(0, bottom.coerceAtLeast(0)))
                                            if (saved != null && saved.fingerprint != document?.fingerprint)
                                                Toast.makeText(this@MainActivity, R.string.bookmark_changed, Toast.LENGTH_LONG).show()
                                            pendingBookmark = null
                                        }
                                        pendingScroll = view.scrollY
                                        view.tag = true
                                        updateScreenAwake()
                                        updateProgress()
                                        modeButton.isEnabled = (document?.modes?.size ?: 0) > 1
                                        findButton.isEnabled = true
                                        updateDocumentsButton()
                                        rememberReading()
                                        pendingStructure?.let { saved ->
                                            pendingStructure = null
                                            if (saved.getString("fingerprint") == document?.fingerprint) showStructure(saved)
                                        }
                                        pendingAccessibleCsv?.let { saved ->
                                            pendingAccessibleCsv = null
                                            if (saved.getString("uri") == currentUri?.toString()) showAccessibleCsv(saved)
                                        }
                                        // Announce the completed page without disabling/re-enabling
                                        // the native WebView accessibility bridge.
                                        view.postOnAnimation {
                                            val accessibility = getSystemService(android.view.accessibility.AccessibilityManager::class.java)
                                            if (current() && accessibility.isEnabled) {
                                                val event = android.view.accessibility.AccessibilityEvent.obtain(
                                                    android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                                                event.contentChangeTypes = android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                                                view.sendAccessibilityEventUnchecked(event)
                                            }
                                        }
                                        if (searchRow.visibility == View.VISIBLE) view.findAllAsync(searchField.text.toString())
                                    }
                                }
                                view.invalidate()
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
        if (::sidebarHint.isInitialized) sidebarHint.visibility = if (sidebarFits &&
            !preferences.getBoolean("fileSidebar", false) && !preferences.getBoolean("sidebarHintDismissed", false) &&
            (homeShown || webView?.tag == true)) View.VISIBLE else View.GONE
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
                session.navigation.clear(entry.key)
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
            readingProfile = entry.reading.profile; applyReadingProfile()
            csvOptions = entry.reading.csv; pendingBookmark = null
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
                session.navigation.clear(entry.key)
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

    private fun showAccessibleCsv(restored: Bundle? = null) {
        val prepared = document ?: return
        if (!canInteract() || accessibleCsvDialog != null || prepared.source.kind != FileKind.CSV || prepared.preview == null) return
        val shareLabel = if (currentUri == null && (session.original?.bytes ?: 0) < 100_000) R.string.share_text else R.string.share_file
        val uri = currentUri
        val dialog = AccessibleCsvDialog(this, prepared.source.text, csvOptions, dark, fontSize, restored, shareLabel, readingFont, lineSpacing) {
            if (canInteract() && document === prepared && currentUri == uri) shareDocument()
        }
        accessibleCsvDialog = dialog
        dialog.present { if (accessibleCsvDialog === dialog) accessibleCsvDialog = null }
    }

    private fun showCsvOptions() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        body.addView(TextView(this).apply { text = getString(R.string.csv_explanation) })
        val delimiter = Spinner(this).apply {
            id = R.id.csv_delimiter; contentDescription = getString(R.string.csv_delimiter)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                resources.getStringArray(R.array.csv_delimiters))
            setSelection(CsvOptions.DELIMITERS.indexOf(csvOptions.delimiter))
        }
        body.addView(delimiter)
        val header = CheckBox(this).apply {
            id = R.id.csv_header; text = getString(R.string.csv_header); isChecked = csvOptions.firstRowHeader
        }
        body.addView(header)
        AlertDialog.Builder(this).setTitle(R.string.csv_options).setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.preview) { _, _ ->
                if (!canInteract()) return@setPositiveButton
                val original = session.original ?: return@setPositiveButton
                rememberReading(); closeSearch()
                csvOptions = CsvOptions(CsvOptions.DELIMITERS[delimiter.selectedItemPosition], header.isChecked, configured = true)
                pendingScroll = 0
                load(currentUri, DisplayMode.PREVIEW) { original }
            }.create().present()
    }

    @Suppress("DEPRECATION")
    private fun readingFraction(position: Int? = null): Double {
        val view = webView ?: return 0.0
        if ((position == null || position == view.scrollY) && !view.canScrollVertically(1)) return 1.0
        val bottom = (view.contentHeight * view.scale).toInt() - view.height
        return if (bottom <= 0) 1.0 else ((position ?: view.scrollY).toDouble() / bottom).coerceIn(0.0, 1.0)
    }

    private fun updateProgress() {
        if (!::readingProgress.isInitialized) return
        val visible = document != null && webView?.tag == true && !homeShown
        readingProgress.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return
        val csvTable = document?.source?.kind == FileKind.CSV && mode == DisplayMode.PREVIEW
        val label = if (csvTable) getString(R.string.csv_progress) else
            getString(R.string.reading_progress, (readingFraction() * 100).toInt())
        // Avoid live-region announcements on every scroll. TalkBack can focus the status.
        if (readingProgress.text.toString() != label) readingProgress.text = label
    }

    private fun bookmarkName(initial: String, save: (String) -> Unit) {
        val field = EditText(this).apply {
            id = R.id.bookmark_name; setText(initial); selectAll(); isSingleLine = true
            hint = getString(R.string.bookmark_name); contentDescription = getString(R.string.bookmark_name)
            filters = arrayOf(android.text.InputFilter.LengthFilter(Bookmarks.LABEL_LIMIT))
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.bookmark_name).setView(field)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create().present()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (field.text.isNullOrBlank()) field.error = getString(R.string.bookmark_name_required)
            else { save(field.text.toString()); dialog.dismiss() }
        }
    }

    private fun addBookmark() {
        val uri = currentUri ?: return
        val prepared = document ?: return
        if (webView?.tag != true) return
        if (bookmarks.entries().size >= Bookmarks.LIMIT) {
            Toast.makeText(this, R.string.bookmark_limit, Toast.LENGTH_LONG).show(); return
        }
        val fraction = readingFraction()
        val state = ReadingState(mode, 0, formatOverride, encodingOverride, csvOptions, readingProfile)
        bookmarkName(getString(R.string.bookmark_default, (fraction * 100).toInt())) { label ->
            if (bookmarks.add(uri.toString(), prepared.source.name, label, fraction, prepared.fingerprint, state))
                Toast.makeText(this, R.string.bookmark_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBookmarks() {
        val entries = bookmarks.entries()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.bookmarks).setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.done, null).create()
        entries.forEach { bookmark ->
            body.addView(button(R.string.bookmarks, "${bookmark.label}\n${bookmark.fileName}") {
                dialog.dismiss(); showBookmarkActions(bookmark)
            }.apply { contentDescription = getString(R.string.bookmark_named, bookmark.label, bookmark.fileName); maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, -2))
        }
        body.addView(TextView(this).apply { text = getString(if (entries.isEmpty()) R.string.bookmarks_empty else R.string.bookmarks_explanation) })
        if (entries.isNotEmpty()) dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.clear_bookmarks)) { _, _ ->
            AlertDialog.Builder(this).setTitle(R.string.clear_bookmarks).setMessage(R.string.clear_bookmarks_message)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.clear_bookmarks) { _, _ -> bookmarks.clear() }.create().present()
        }
        dialog.present()
    }

    private fun showBookmarkActions(bookmark: Bookmark) {
        AlertDialog.Builder(this).setTitle(bookmark.label).setItems(arrayOf(getString(R.string.open_file),
            getString(R.string.rename_bookmark), getString(R.string.reconnect_bookmark), getString(R.string.delete_bookmark))) { _, index ->
            if (!canInteract()) return@setItems
            when (index) {
                0 -> openDocument(Uri.parse(bookmark.uri), bookmark = bookmark)
                1 -> bookmarkName(bookmark.label) { bookmarks.rename(bookmark.id, it) }
                2 -> openPicker(bookmark)
                3 -> bookmarks.remove(bookmark.id)
            }
        }.create().present()
    }

    private fun showShortcutHelp() {
        AlertDialog.Builder(this).setTitle(R.string.keyboard_shortcuts).setMessage(R.string.keyboard_shortcuts_text)
            .setPositiveButton(R.string.done, null).create().present()
    }

    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: android.view.Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data.add(KeyboardShortcutGroup(getString(R.string.app_name), listOf(
            KeyboardShortcutInfo(getString(R.string.open_file), KeyEvent.KEYCODE_O, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo(getString(R.string.find), KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo(getString(R.string.go_to_line), KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo(getString(R.string.next_document), KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo(getString(R.string.previous_document), KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        )))
    }

    // Activity's public event contracts must run before WebView consumes browser
    // shortcuts (including Ctrl+G). AndroidX marks its forwarding bridges restricted.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        handleReaderShortcut(event) || super.dispatchKeyEvent(event)

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean =
        handleReaderShortcut(event) || super.dispatchKeyShortcutEvent(event)

    private fun handleReaderShortcut(event: KeyEvent): Boolean {
        if (!canInteract() || dialogs.any { it.isShowing } || activeMenu != null || currentFocus is EditText ||
            !event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.action != KeyEvent.ACTION_DOWN ||
            (event.isShiftPressed && event.keyCode != KeyEvent.KEYCODE_TAB)) return false
        val ready = document != null && webView?.tag == true
        val action: (() -> Unit)? = when (event.keyCode) {
            KeyEvent.KEYCODE_O -> ({ openPicker() })
            KeyEvent.KEYCODE_F -> if (ready) ({ if (searchRow.visibility != View.VISIBLE) toggleSearch() else searchField.requestFocus() }) else null
            KeyEvent.KEYCODE_G -> if (ready && mode != DisplayMode.PREVIEW) ({ showGoToLine() }) else null
            KeyEvent.KEYCODE_TAB -> if (ready && openDocuments.entries.size > 1) ({
                val entries = openDocuments.entries.sortedWith(compareBy({ it.name.lowercase(java.util.Locale.ROOT) }, { it.key }))
                val index = entries.indexOfFirst { it.key == (currentUri?.toString() ?: "shared-text") }
                val next = Math.floorMod(index + if (event.isShiftPressed) -1 else 1, entries.size)
                switchDocument(entries[next])
            }) else null
            else -> null
        }
        if (action == null) return false
        if (event.repeatCount == 0) action()
        return true
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
                rememberPosition(); dialog.dismiss(); closeSearch(); sourceMatch = null; targetLine = line; anchor = "line-$line"; render()
            }
        }
    }

    private fun showStructure(restored: Bundle? = null) {
        val prepared = document ?: return
        if (prepared.structure == null || webView?.tag != true) return
        val dialog = StructureDialog(this, prepared, restored, dark) { node ->
            if (canInteract() && document === prepared) {
                rememberPosition()
                closeSearch()
                mode = DisplayMode.PLAIN
                // Jump to the original line; huge container values are never duplicated into a highlight.
                sourceMatch = null
                targetLine = node.line
                anchor = "line-${node.line}"
                render()
            }
        }
        structureDialog = dialog
        dialog.present { if (structureDialog === dialog) structureDialog = null }
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
                if (canInteract()) { rememberPosition(); closeSearch(); (webView as? ReaderWebView)?.let { it.loadUrl("${it.documentUrl}#${headings[index].id}") } }
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
        DisplayMode.PREVIEW -> if (document?.source?.kind == FileKind.HTML) R.string.html_reading_view else R.string.preview
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
        (webView as? ReaderWebView)?.cancelFindRestore()
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
        val sizeControl = SeekBar(this).apply {
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
        }
        body.addView(sizeControl, LinearLayout.LayoutParams(-1, dp(48)))
        val wrapping = CheckBox(this).apply {
            id = R.id.line_wrap
            text = getString(R.string.wrap_lines)
            isChecked = wrap
            minimumHeight = dp(48)
        }
        body.addView(wrapping)
        val onlyDocument = CheckBox(this).apply {
            id = R.id.document_reading_setting
            setText(R.string.document_reading_setting)
            isChecked = readingProfile != null
            visibility = if (document != null) View.VISIBLE else View.GONE
            minimumHeight = dp(48)
        }
        body.addView(onlyDocument)
        body.addView(TextView(this).apply { setText(R.string.reading_preferences_help) })
        body.addView(TextView(this).apply { setText(R.string.reading_font) })
        val fonts = Spinner(this).apply {
            id = R.id.reading_font
            minimumHeight = dp(48)
            contentDescription = getString(R.string.reading_font)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.font_sans), getString(R.string.font_serif), getString(R.string.font_monospace)))
            setSelection(readingFont.ordinal)
        }
        body.addView(fonts)
        var selectedSpacing = lineSpacing
        val spacingLabel = TextView(this).apply { text = getString(R.string.line_spacing_value, selectedSpacing) }
        body.addView(spacingLabel)
        val spacingControl = SeekBar(this).apply {
            id = R.id.line_spacing
            max = 10; progress = (lineSpacing - 120) / 10
            minimumHeight = dp(48)
            contentDescription = getString(R.string.line_spacing)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedSpacing = 120 + progress * 10
                    spacingLabel.text = getString(R.string.line_spacing_value, selectedSpacing)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        body.addView(spacingControl, LinearLayout.LayoutParams(-1, dp(48)))
        if (document != null) body.addView(button(R.string.use_global_reading_defaults) {
            val defaults = ReadingProfile.defaults(preferences)
            onlyDocument.isChecked = false
            selectedSize = defaults.fontSize
            selectedSpacing = defaults.lineSpacing
            sizeLabel.text = getString(R.string.font_size_value, selectedSize)
            spacingLabel.text = getString(R.string.line_spacing_value, selectedSpacing)
            sizeControl.progress = defaults.fontSize - 12
            wrapping.isChecked = defaults.wrap
            fonts.setSelection(defaults.font.ordinal)
            spacingControl.progress = (defaults.lineSpacing - 120) / 10
        }.apply { id = R.id.reset_document_reading })
        val numbers = CheckBox(this).apply { text = getString(R.string.line_numbers); isChecked = lineNumbers; minimumHeight = dp(48) }
        body.addView(numbers)
        val awake = CheckBox(this).apply { id = R.id.keep_awake; setText(R.string.keep_awake); isChecked = preferences.getBoolean("keepAwake", false) }
        body.addView(awake)
        body.addView(TextView(this).apply { setText(R.string.keep_awake_help) })
        body.addView(button(R.string.forget_save_location) { SaveLocation(this).clear(); Toast.makeText(this, R.string.save_location_forgotten, Toast.LENGTH_SHORT).show() })
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
                val selected = ReadingProfile(selectedSize, wrapping.isChecked, ReadingFont.entries[fonts.selectedItemPosition], selectedSpacing)
                if (onlyDocument.isChecked && document != null) readingProfile = selected
                else { readingProfile = null; selected.save(preferences) }
                applyReadingProfile()
                lineNumbers = numbers.isChecked
                if (lineNumbers && (document?.source?.text?.let(DocumentRenderer::logicalLines) ?: 0) > DocumentRenderer.MAX_NUMBERED_LINES)
                    Toast.makeText(this, R.string.line_number_limit, Toast.LENGTH_LONG).show()
                preferences.edit().putBoolean("keepAwake", awake.isChecked).putBoolean("fileSidebar", sidebar.isChecked).putBoolean("lineNumbers", lineNumbers).putString("theme", theme).apply()
                rememberReading()
                if (themeChanged) recreate() else {
                    updateDocumentsButton()
                    if (document != null) render()
                }
            }.create().present()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Framework/view callbacks can change the old WebView while super saves it.
        // Capture once beforehand for both reading metadata and activity recovery.
        val savedScroll = checkpointPosition()
        rememberReading(savedScroll)
        super.onSaveInstanceState(outState)
        outState.putBoolean("outputPending", outputs.status.phase == OutputPhase.PICKING)
        if (outputs.status.phase == OutputPhase.PICKING) {
            outState.putString("outputName", outputs.status.name)
            outState.putString("outputSource", outputs.pendingSource())
        }
        outState.putString("uri", currentUri?.toString())
        // While loading, remember only a mode explicitly requested for this URI,
        // never the previous document's mode. Format changes request automatic mode.
        outState.putString("mode", if (document != null) mode.name else requestedMode?.name)
        outState.putInt("scroll", savedScroll)
        outState.putString("query", searchField.text.toString())
        outState.putString("format", formatOverride?.name)
        outState.putString("encoding", encodingOverride?.name)
        outState.putInt("grantFlags", pendingGrantFlags)
        outState.putString("readingProfile", readingProfile?.json()?.toString())
        outState.putString("csvDelimiter", csvOptions.delimiter.toString())
        outState.putBoolean("csvHeader", csvOptions.firstRowHeader)
        outState.putBoolean("csvConfigured", csvOptions.configured)
        outState.putString("pendingBookmark", pendingBookmark?.id)
        outState.putString("pickerBookmark", pickerBookmark?.id)
        outState.putBoolean("search", searchRow.visibility == View.VISIBLE)
        val accessible = accessibleCsvDialog?.readingState()?.apply { putString("uri", currentUri?.toString()) } ?: pendingAccessibleCsv
        outState.putBundle("accessibleCsv", accessible)
        outState.putBundle("structure", structureDialog?.takeIf { it.isShowing }?.readingState() ?: pendingStructure)
    }

    private fun checkpointPosition() = pausedReadingPosition?.takeIf { it.first == generation }?.second ?: readerPosition()

    private fun rememberReading(position: Int = checkpointPosition()) {
        val view = webView ?: return
        if (document != null && view.tag == true) {
            val reading = ReadingState(mode, position.coerceAtLeast(0), formatOverride, encodingOverride, csvOptions, readingProfile)
            session.original?.let { original ->
                session.returnState = ReaderReturnState(document!!, original, currentUri?.toString(), reading,
                    searchField.text.toString().take(1000), searchRow.visibility == View.VISIBLE, pendingGrantFlags)
            }
            val csvTable = document?.source?.kind == FileKind.CSV && mode == DisplayMode.PREVIEW
            val progress = if (csvTable) null else (readingFraction(position) * 100).toInt()
            currentUri?.let { repository.saveReadingState(it, reading, progress) }
            openDocuments.entries.find { it.key == (currentUri?.toString() ?: "shared-text") }?.let {
                it.reading = reading
                it.query = searchField.text.toString().take(1000)
                it.searching = searchRow.visibility == View.VISIBLE
            }
            openDocuments.save()
        }
    }

    override fun onStart() {
        super.onStart()
        if (homeShown) resumeDraftButton?.visibility = if (EditDraftStore(this).exists()) View.VISIBLE else View.GONE
        outputs.listener = ::showOutputStatus
        showOutputStatus(outputs.status)
    }

    override fun onStop() {
        outputs.listener = null
        rememberReading()
        super.onStop()
    }

    private fun documentKey() = currentUri?.toString() ?: "shared-text"
    private fun rememberPosition() {
        if (document == null || webView?.tag != true) return
        session.navigation.push(documentKey(), navigationPosition())
    }
    private fun navigationPosition() = NavigationPosition(
        mode, readerPosition(), searchField.text.toString().take(SourceSearch.MAX_QUERY),
        searchRow.visibility == View.VISIBLE,
        openDocuments.entries.find { it.key == documentKey() }?.sourceSearch ?: SourceSearchOptions()
    )
    private fun findNext(forward: Boolean) { rememberPosition(); (webView as? ReaderWebView)?.cancelFindRestore(); webView?.findNext(forward) }
    private fun moveReadingPosition(forward: Boolean) {
        val prepared = document ?: return
        if (webView?.tag != true) return
        val position = session.navigation.move(documentKey(), navigationPosition(), forward) ?: return
        mode = position.mode.takeIf { it in prepared.modes } ?: prepared.defaultMode
        anchor = null; sourceMatch = null; targetLine = 0
        pendingScroll = position.scroll
        // Restoring the query must not launch a find on the outgoing page.
        webView?.tag = false
        searchField.clearFocus()
        searchField.setText(position.query)
        searchRow.visibility = if (position.searching) View.VISIBLE else View.GONE
        openDocuments.entries.find { it.key == documentKey() }?.sourceSearch = position.sourceSearch
        render()
    }
    private fun reopenClosed() {
        val entry = openDocuments.reopen() ?: return
        switchDocument(entry)
    }
    private fun updateScreenAwake() {
        val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (resumed && document != null && webView?.tag == true && !homeShown && preferences.getBoolean("keepAwake", false)) window.addFlags(flag)
        else window.clearFlags(flag)
    }
    override fun onResume() {
        super.onResume(); pausedReadingPosition = null; resumed = true; updateScreenAwake()
    }
    override fun onPause() {
        // Android may clamp a ready WebView during pause, before state saving.
        // Loading pages keep their pending position; newer renders invalidate this snapshot.
        pausedReadingPosition = if (document != null && webView?.tag == true) generation to readerPosition() else null
        resumed = false; updateScreenAwake(); super.onPause()
    }

    // An unfinished WebView reports transient zero coordinates during recreation.
    private fun readerPosition() = (webView as? ReaderWebView)?.restoringPosition ?: webView?.takeIf { it.tag == true }?.scrollY ?: pendingScroll

    private fun returnToReader() {
        val saved = session.returnState ?: return showHome()
        cancelWork()
        clearContent()
        pendingBookmark = null
        pendingStructure = null
        pendingAccessibleCsv = null
        document = saved.document
        session.original = saved.original
        currentUri = saved.uri?.let(Uri::parse)
        pendingGrantFlags = saved.grantFlags
        mode = saved.reading.mode ?: saved.document.defaultMode
        formatOverride = saved.reading.format
        encodingOverride = saved.reading.encoding
        csvOptions = saved.reading.csv
        readingProfile = saved.reading.profile
        applyReadingProfile()
        pendingScroll = saved.reading.scroll
        searchField.setText(saved.query)
        searchRow.visibility = if (saved.searching) View.VISIBLE else View.GONE
        render()
    }

    private fun navigateBack() {
        if (document == null && session.returnState != null) returnToReader()
        else if (searchRow.visibility == View.VISIBLE) closeSearch()
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
