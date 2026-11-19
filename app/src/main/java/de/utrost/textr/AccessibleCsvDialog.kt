package de.utrost.textr

import android.app.Dialog
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_NEUTRAL
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*

/** Bounded native alternative to WebView's intermittently incomplete virtual cells. */
class AccessibleCsvDialog(
    context: Context, private val source: String, options: CsvOptions, dark: Boolean, fontSize: Int,
    restored: Bundle?, shareLabel: Int, font: ReadingFont = ReadingFont.SANS, lineSpacing: Int = 160, share: () -> Unit,
) : Dialog(context) {
    private val cancelButton = Button(context)
    private val shareButton = Button(context)
    private lateinit var dialogContent: View
    private val query = EditText(context)
    private val results = TextView(context)
    private val vertical = ScrollView(context)
    private val horizontal = HorizontalScrollView(context)
    private val sourceScroll = ScrollView(context)
    private val controlsScroll = ScrollView(context)
    private val sourceText = TextView(context)
    private val showSource = CheckBox(context)
    private val navigation = mutableListOf<Button>()
    private var sourceMatches = SourceSearchResult(emptyList(), false)
    private val cells = mutableListOf<TextView>()
    private var matches = emptyList<TextView>()
    private var selected = 0
    private var tableReady = false
    private var populateRows: Runnable? = null
    private var rowsHost: View? = null
    private val highlight = Color.parseColor(if (dark) "#465C50" else "#D5E9DC")
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    init {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val rows = CsvPreview.parse(source, options.delimiter)
        val columns = rows.maxOfOrNull { it.size } ?: 0
        val data = rows.drop(if (options.firstRowHeader) 1 else 0)
        val foreground = Color.parseColor(if (dark) "#E3E9E5" else "#202923")
        val table = TableLayout(context).apply {
            id = R.id.csv_accessible_table
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = GridView::class.java.name
                    info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(if (columns == 0) 0 else data.size + 1, columns, false)
                }
            }
        }
        fun addRow(values: List<String>, rowIndex: Int, heading: Boolean) {
            val row = TableRow(context)
            values.forEachIndexed { column, value ->
                val cell = TextView(context).apply {
                    text = value
                    isSaveEnabled = false
                    textSize = fontSize.toFloat()
                    typeface = android.graphics.Typeface.create(font.css, android.graphics.Typeface.NORMAL)
                    setLineSpacing(0f, lineSpacing / 100f)
                    setTextColor(foreground)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    minHeight = dp(48)
                    setTextIsSelectable(true)
                    isFocusable = true
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    isAccessibilityHeading = heading
                    if (value.isEmpty()) contentDescription = context.getString(R.string.csv_empty_cell)
                    if (heading) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    accessibilityDelegate = object : View.AccessibilityDelegate() {
                        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                            info.collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(rowIndex, 1, column, 1, heading)
                        }
                    }
                }
                cells.add(cell)
                row.addView(cell, TableRow.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            table.addView(row)
        }
        addRow((0 until columns).map { column ->
            if (options.firstRowHeader) rows.first().getOrNull(column).orEmpty()
            else context.getString(R.string.csv_column, column + 1)
        }, 0, true)
        // Inflate at most 100 cells per turn so cancellation and source access
        // remain responsive at the 10,000-cell CSV limit.
        var nextRow = 0
        rowsHost = table
        populateRows = object : Runnable {
            override fun run() {
                if (!isShowing) return
                var added = 0
                while (nextRow < data.size && added < 100) {
                    val values = data[nextRow]
                    val count = values.size.coerceAtLeast(1)
                    if (added > 0 && added + count > 100) break
                    addRow(values, nextRow + 1, false)
                    nextRow++
                    added += count
                }
                if (nextRow < data.size) table.post(this)
                else {
                    tableReady = true
                    getButton(BUTTON_NEGATIVE)?.setText(R.string.done)
                    query.isEnabled = true
                    if (!showSource.isChecked) {
                        updateMatches(query.text.toString())
                        selected = (restored?.getInt("match") ?: 0).coerceIn(0, (matchCount() - 1).coerceAtLeast(0))
                        showMatch()
                        table.post { restorePosition(restored) }
                    }
                }
            }
        }
        horizontal.addView(table)
        vertical.addView(horizontal)
        sourceText.apply {
            id = R.id.csv_accessible_source
            isSaveEnabled = false
            setTextIsSelectable(true)
            text = source
            textSize = fontSize.toFloat()
            setTextColor(foreground)
            typeface = android.graphics.Typeface.MONOSPACE
            setLineSpacing(0f, lineSpacing / 100f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        sourceScroll.addView(sourceText)
        sourceScroll.visibility = View.GONE
        showSource.apply { id = R.id.csv_accessible_source_toggle; setText(R.string.csv_show_source) }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setPadding(dp(12), 0, dp(12), 0)
        }
        query.apply {
            id = R.id.csv_accessible_query
            hint = context.getString(R.string.find_hint)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(1000))
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isEnabled = false
        }
        body.addView(showSource)
        body.addView(query)
        val actions = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        results.apply { id = R.id.csv_accessible_results; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        actions.addView(results, LinearLayout.LayoutParams(0, -2, 1f))
        for ((label, caption, step) in listOf(Triple(R.string.previous_match, "↑", -1), Triple(R.string.next_match, "↓", 1))) {
            actions.addView(Button(context).apply {
                text = caption; contentDescription = context.getString(label); minWidth = dp(48)
                setOnClickListener { if (matchCount() > 0) { selected = (selected + step + matchCount()) % matchCount(); showMatch() } }
                navigation.add(this)
            }, LinearLayout.LayoutParams(dp(56), dp(48)))
        }
        body.addView(actions)
        val compactControls = context.resources.configuration.screenHeightDp < 480 || context.resources.configuration.fontScale > 1.3f
        body.addView(FrameLayout(context).apply {
            addView(vertical, FrameLayout.LayoutParams(-1, -1))
            addView(sourceScroll, FrameLayout.LayoutParams(-1, -1))
        }, if (compactControls) LinearLayout.LayoutParams(-1, dp(200)) else LinearLayout.LayoutParams(-1, 0, 1f))
        showSource.setOnCheckedChangeListener { _, checked ->
            vertical.visibility = if (checked) View.GONE else View.VISIBLE
            sourceScroll.visibility = if (checked) View.VISIBLE else View.GONE
            query.isEnabled = checked || tableReady
            updateMatches(query.text.toString())
        }
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateMatches(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        setTitle(R.string.csv_accessible)
        val background = Color.parseColor(if (dark) "#171C1A" else "#FAFAF7")
        dialogContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setBackgroundColor(background)
            addView(TextView(context).apply {
                setText(R.string.csv_accessible); textSize = 20f; setTextColor(foreground)
                isAccessibilityHeading = true; setPadding(dp(16), dp(12), dp(16), dp(8))
            })
            addView(if (compactControls) controlsScroll.apply { addView(body) } else body, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), dp(8))
                for ((button, id, label) in listOf(Triple(shareButton, android.R.id.button3, shareLabel), Triple(cancelButton, android.R.id.button2, R.string.cancel))) {
                    button.id = id; button.setText(label); button.isAllCaps = false; button.minHeight = dp(48)
                    addView(button, LinearLayout.LayoutParams(-1, -2))
                }
            })
        }
        window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background))
        cancelButton.setOnClickListener { dismiss() }
        shareButton.setOnClickListener { if (isShowing) { share(); dismiss() } }
        showSource.isChecked = restored?.getBoolean("source") ?: false
        query.setText(restored?.getString("query").orEmpty())
        selected = (restored?.getInt("match") ?: 0).coerceIn(0, (matchCount() - 1).coerceAtLeast(0))
        showMatch()
        setOnShowListener {
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            if (showSource.isChecked) sourceScroll.post { restorePosition(restored) }
            populateRows?.let(table::post)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(dialogContent)
    }

    private fun getButton(which: Int): Button? = when (which) {
        BUTTON_NEGATIVE -> cancelButton
        BUTTON_NEUTRAL -> shareButton
        else -> null
    }

    private fun matchCount() = if (showSource.isChecked) sourceMatches.matches.size else matches.size

    private fun updateMatches(value: String) {
        selected = 0
        if (showSource.isChecked) sourceMatches = SourceSearch.find(source, SourceSearchOptions(value))
        else matches = if (value.isEmpty()) emptyList() else cells.filter { it.text.contains(value, ignoreCase = true) }
        showMatch()
    }

    private fun showMatch() {
        navigation.forEach { it.isEnabled = matchCount() > 0 }
        results.text = when {
            !showSource.isChecked && !tableReady -> context.getString(R.string.rendering)
            query.text.isNullOrEmpty() -> ""
            matchCount() == 0 -> context.getString(R.string.no_matches)
            showSource.isChecked -> context.getString(R.string.matches_spoken, selected + 1, matchCount()) + if (sourceMatches.truncated) "+" else ""
            else -> context.getString(R.string.csv_cell_matches, selected + 1, matchCount())
        }
        if (showSource.isChecked) {
            val text = sourceText.text as android.text.Spannable
            text.getSpans(0, text.length, android.text.style.BackgroundColorSpan::class.java).forEach(text::removeSpan)
            sourceMatches.matches.getOrNull(selected)?.let { match ->
                text.setSpan(android.text.style.BackgroundColorSpan(highlight), match.start, match.end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sourceText.post {
                    sourceText.layout?.let { layout ->
                        val bounds = Rect()
                        sourceText.getLineBounds(layout.getLineForOffset(match.start), bounds)
                        sourceText.requestRectangleOnScreen(bounds, false)
                    }
                }
            }
        } else {
            val current = matches.getOrNull(selected)
            cells.forEach { it.setBackgroundColor(if (it === current) highlight else Color.TRANSPARENT) }
            current?.requestRectangleOnScreen(Rect(0, 0, current.width, current.height), false)
        }
    }

    private fun restorePosition(restored: Bundle?) {
        if (!isShowing || restored == null) return
        vertical.scrollTo(0, restored.getInt("vertical"))
        horizontal.scrollTo(restored.getInt("horizontal"), 0)
        sourceScroll.scrollTo(0, restored.getInt("sourceVertical"))
        controlsScroll.scrollTo(0, restored.getInt("controlsVertical"))
    }

    override fun onStop() {
        populateRows?.let { rowsHost?.removeCallbacks(it) }
        super.onStop()
    }

    fun readingState() = Bundle().apply {
        putString("query", query.text.toString())
        putInt("match", selected)
        putInt("vertical", vertical.scrollY)
        putInt("horizontal", horizontal.scrollX)
        putBoolean("source", showSource.isChecked)
        putInt("sourceVertical", sourceScroll.scrollY)
        putInt("controlsVertical", controlsScroll.scrollY)
    }
}
