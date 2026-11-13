package de.utrost.textr

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Owns cancellable search work only for the lifetime of this native dialog. */
class SourceSearchDialog(context: Context, source: String, initial: SourceSearchOptions,
    private val changed: (SourceSearchOptions) -> Unit, selected: (SourceMatch) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var work: Future<*>? = null
    private var generation = 0
    private var closed = false
    val dialog: AlertDialog

    init {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        val query = EditText(context).apply {
            id = R.id.source_query
            hint = context.getString(R.string.source_query_hint)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            filters = arrayOf(android.text.InputFilter.LengthFilter(SourceSearch.MAX_QUERY))
            setText(initial.query)
        }
        val matchCase = CheckBox(context).apply { id = R.id.match_case; setText(R.string.match_case); isChecked = initial.matchCase; minimumHeight = dp(48) }
        val wholeWord = CheckBox(context).apply { id = R.id.whole_word; setText(R.string.whole_word); isChecked = initial.wholeWord; minimumHeight = dp(48) }
        val options = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(matchCase, LinearLayout.LayoutParams(0, -2, 1f)); addView(wholeWord, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val status = TextView(context).apply { id = R.id.source_result_count; accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val results = ListView(context).apply { id = R.id.source_results; itemsCanFocus = true }
        body.addView(query); body.addView(options); body.addView(status)
        // A single scrolling list keeps both controls and results reachable when the
        // keyboard or large landscape fonts leave too little space for a fixed header.
        results.addHeaderView(body, null, false)
        val matches = mutableListOf<SourceMatch>()
        val adapter = object : ArrayAdapter<SourceMatch>(context, android.R.layout.simple_list_item_2, android.R.id.text1, matches) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val match = getItem(position)!!
                view.findViewById<TextView>(android.R.id.text1).text = context.getString(R.string.source_result_line, match.line)
                view.findViewById<TextView>(android.R.id.text2).apply { text = match.snippet; maxLines = 3 }
                view.minimumHeight = dp(64)
                return view
            }
        }
        results.adapter = adapter
        dialog = AlertDialog.Builder(context).setTitle(R.string.search_source).setView(results).setNegativeButton(R.string.done, null).create()
        results.setOnItemClickListener { _, _, position, _ ->
            matches.getOrNull(position - results.headerViewsCount)?.let { match -> dialog.dismiss(); selected(match) }
        }
        fun update() {
            val settings = SourceSearchOptions(query.text.toString(), matchCase.isChecked, wholeWord.isChecked)
            changed(settings)
            val request = ++generation
            handler.removeCallbacksAndMessages(null)
            work?.cancel(true)
            matches.clear()
            adapter.notifyDataSetChanged()
            status.setText(if (settings.query.isEmpty()) R.string.source_search_explanation else R.string.searching)
            if (settings.query.isEmpty()) return
            handler.postDelayed({
                if (closed) return@postDelayed
                work = worker.submit {
                    val found = try { SourceSearch.find(source, settings) } catch (_: java.util.concurrent.CancellationException) { return@submit }
                    handler.post {
                        if (closed || request != generation || !dialog.isShowing) return@post
                        status.text = if (found.truncated) context.getString(R.string.source_results_limited, SourceSearch.MAX_RESULTS)
                            else if (found.matches.isEmpty()) context.getString(R.string.no_matches)
                            else context.getString(R.string.source_results_count, found.matches.size)
                        matches.addAll(found.matches)
                        adapter.notifyDataSetChanged()
                    }
                }
            }, 250)
        }
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        matchCase.setOnCheckedChangeListener { _, _ -> update() }
        wholeWord.setOnCheckedChangeListener { _, _ -> update() }
        dialog.setOnShowListener {
            update()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    fun close() { closed = true; generation++; handler.removeCallbacksAndMessages(null); work?.cancel(true); worker.shutdownNow() }
}
