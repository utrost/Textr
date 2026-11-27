package de.utrost.textr

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider

class EditorActivity : ReadingScreen() {
    private val model by lazy { ViewModelProvider(this)[EditorSession::class.java] }
    private val outputs by lazy { ViewModelProvider(this)[OutputSession::class.java] }
    private lateinit var editor: EditText
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var undo: Button
    private lateinit var redo: Button
    private lateinit var save: Button
    private lateinit var discard: Button
    private lateinit var find: Button
    private lateinit var resume: Button
    private lateinit var preview: Button
    private lateinit var goLine: Button
    private lateinit var indent: Button
    private lateinit var unindent: Button
    private lateinit var compare: Button
    private var draftPreview: DraftPreview? = null
    private var destroying = false
    private lateinit var retryRecovery: Button
    private var replacing = false
    private var exitAfterSave = false
    private var dialog: AlertDialog? = null
    private var confirmation: AlertDialog? = null
    private var outputDialog: AlertDialog? = null
    private var displayedOutput: OutputStatus? = null
    private var pendingSelection: EditSelection? = null
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = if (result.resultCode == RESULT_OK) result.data?.data else null
        if (target == null) exitAfterSave = false
        outputs.pickerResult(target)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitAfterSave = savedInstanceState?.getBoolean("exitAfterSave") ?: false
        pendingSelection = savedInstanceState?.let { EditSelection(it.getInt("selectionStart"), it.getInt("selectionEnd")) }
        build()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = leave() })
        if (savedInstanceState?.getBoolean("outputPending") == true)
            outputs.restorePending(savedInstanceState.getString("outputName").orEmpty(), savedInstanceState.getString("outputSource"))
        if (!model.initialized) {
            model.initialized = true
            model.initialize(if (savedInstanceState == null) EditorInbox.take(intent.getStringExtra("token")) else null)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        EditorInbox.take(intent.getStringExtra("token"))
        Toast.makeText(this, R.string.editor_existing, Toast.LENGTH_LONG).show()
    }
    private fun build() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        title = TextView(this).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 20f; isAccessibilityHeading = true; setPadding(0, dp(10), 0, dp(6)); setText(R.string.editor_title) }
        body.addView(title)
        val tools = LinearLayout(this)
        undo = button(R.string.editor_undo) { model.buffer?.undo(); renderBuffer(); model.changed() }.apply { id = R.id.editor_undo }
        redo = button(R.string.editor_redo) { model.buffer?.redo(); renderBuffer(); model.changed() }.apply { id = R.id.editor_redo }
        find = button(R.string.editor_find) { showFind() }.apply { id = R.id.editor_find }
        preview = button(R.string.draft_preview) { showPreview() }.apply { id = R.id.preview_draft }
        goLine = button(R.string.go_to_line) { showGoLine() }.apply { id = R.id.editor_go_line }
        indent = button(R.string.editor_indent) { indent(false) }.apply { id = R.id.editor_indent }
        unindent = button(R.string.editor_unindent) { indent(true) }.apply { id = R.id.editor_unindent }
        compare = button(R.string.editor_compare) { compareDraft() }.apply { id = R.id.editor_compare }
        save = button(R.string.save_copy) { saveCopy() }.apply { id = R.id.editor_save }
        discard = button(R.string.editor_discard) {
            present(AlertDialog.Builder(this).setTitle(R.string.editor_discard).setMessage(R.string.editor_discard_help)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.editor_discard) { _, _ -> model.discard { if (it) finish() } }.create())
        }.apply { id = R.id.editor_discard }
        for (button in listOf(undo, redo, find, preview, goLine, indent, unindent, compare, save, discard)) tools.addView(button, LinearLayout.LayoutParams(-2, -2))
        body.addView(HorizontalScrollView(this).apply { addView(tools) }, LinearLayout.LayoutParams(-1, -2))
        status = TextView(this).apply { id = R.id.editor_status; textSize = 13f; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; setPadding(0, dp(6), 0, dp(6)) }
        body.addView(status)
        resume = button(R.string.editor_resume) { model.resumeExisting() }.apply { id = R.id.editor_resume; visibility = View.GONE }
        body.addView(resume, LinearLayout.LayoutParams(-1, -2))
        retryRecovery = button(R.string.retry_recovery) { model.retryRecovery() }.apply { id = R.id.retry_recovery; visibility = View.GONE }
        body.addView(retryRecovery, LinearLayout.LayoutParams(-1, -2))
        editor = EditText(this).apply {
            id = R.id.editor_text; isSaveEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setTextColor(foreground()); setBackgroundColor(background()); textSize = 16f
            typeface = android.graphics.Typeface.MONOSPACE; gravity = android.view.Gravity.TOP or android.view.Gravity.START
            contentDescription = getString(R.string.editor_buffer); setPadding(dp(8), dp(8), dp(8), dp(8))
            filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
                val input = source.subSequence(start, end)
                val normalized = editorInsertion(input, model.draft?.endings.orEmpty())
                normalized.takeIf { it != input.toString() }
            }, InputFilter { source, start, end, dest, dstart, dend ->
                if (dest.length - (dend - dstart) + end - start > EditBuffer.MAX_CHARS ||
                    !EditBuffer.accepts(dest.subSequence(0, dstart).toString() + source.subSequence(start, end) + dest.subSequence(dend, dest.length))) {
                    Toast.makeText(this@EditorActivity, R.string.editor_limit, Toast.LENGTH_LONG).show()
                    dest.subSequence(dstart, dend)
                } else null
            })
        }
        var changedStart = 0; var removed = 0; var added = 0; var before = EditSelection(0)
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!replacing) { changedStart = start; removed = count; before = EditSelection(editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0)) }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (!replacing) added = count }
            override fun afterTextChanged(s: Editable?) {
                if (replacing || s == null) return
                val buffer = model.buffer ?: return
                buffer.replace(changedStart, changedStart + removed, s.substring(changedStart, changedStart + added), before, EditSelection(changedStart + added))
                model.changed()
            }
        })
        val compact = resources.configuration.screenHeightDp < 480 || resources.configuration.fontScale > 1.3f
        if (compact) {
            body.addView(editor, LinearLayout.LayoutParams(-1, dp(360)))
            root.addView(ScrollView(this).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            body.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        root.addView(button(R.string.back_to_files) { leave() }.apply { id = R.id.editor_back }, LinearLayout.LayoutParams(-1, -2))
        install(root)
    }
    private fun renderBuffer() {
        val buffer = model.buffer ?: return
        replacing = true
        // Programmatic undo/recovery preserves every original newline, bypassing insertion filters.
        val filters = editor.filters; editor.filters = emptyArray()
        editor.setText(buffer.text); editor.filters = filters
        val selection = pendingSelection ?: buffer.selection
        editor.setSelection(selection.start.coerceIn(0, buffer.text.length), selection.end.coerceIn(0, buffer.text.length))
        buffer.select(selection); pendingSelection = null; replacing = false
        editor.post { editor.scrollTo(0, model.editorScroll) }
    }
    private fun refresh() {
        if (isDestroyed || isFinishing) return
        val buffer = model.buffer; val draft = model.draft
        draftPreview?.checkCurrent()
        if (buffer != null && editor.text.toString() != buffer.text) renderBuffer()
        val enabled = buffer != null && !model.loading && !outputs.busy
        editor.isEnabled = enabled; save.isEnabled = enabled; find.isEnabled = enabled; discard.isEnabled = enabled || (model.unavailable && model.recoveryFailed && !model.loading && !outputs.busy)
        preview.visibility = if (draft?.markdown == true) View.VISIBLE else View.GONE
        preview.isEnabled = enabled
        listOf(goLine, indent, unindent).forEach { it.isEnabled = enabled }
        compare.isEnabled = enabled && draft?.originalText != null
        undo.isEnabled = enabled && buffer?.canUndo == true; redo.isEnabled = enabled && buffer?.canRedo == true
        retryRecovery.visibility = if (model.recoveryFailed) View.VISIBLE else View.GONE
        retryRecovery.isEnabled = !model.loading && !outputs.busy
        resume.visibility = if (model.blockedByExisting) View.VISIBLE else View.GONE
        title.text = getString(R.string.editor_title) + (draft?.let { " · ${it.name}" } ?: "")
        val message = when {
            model.loading -> getString(R.string.editor_loading)
            model.blockedByExisting -> getString(R.string.editor_existing)
            model.unavailable -> getString(if (model.recoveryFailed) R.string.editor_recovery_unreadable else R.string.editor_unavailable)
            draft != null && buffer != null -> getString(if (model.dirty) R.string.editor_dirty else R.string.editor_clean) + "\n" +
                getString(R.string.editor_format, draft.encoding.charsetName, if (draft.bom) getString(R.string.editor_with_bom) else getString(R.string.editor_no_bom), draft.endings.ifBlank { getString(R.string.editor_no_endings) }) + "\n" +
                getString(if (model.recoveryFailed) R.string.editor_recovery_failed else if (model.checkpointRevision >= buffer.revision) R.string.editor_recovery_ready else if (!model.dirty && model.checkpointRevision < 0) R.string.editor_saved_recovery_removed else R.string.editor_recovery_pending)
            else -> getString(R.string.editor_loading)
        }
        status.text = message + if (buffer != null && draft?.originalText == null) "\n" + getString(R.string.editor_compare_unavailable) else ""
        if (model.previewOpen && draftPreview == null && enabled && !destroying) showPreview()
        handleOutput(outputs.status)
        if (buffer != null && model.findState.open && draftPreview == null && dialog == null && !outputs.busy && !model.loading) showFind()
    }
    private fun present(value: AlertDialog, dismissed: () -> Unit = {}) {
        dialog?.dismiss(); dialog = value
        value.setOnDismissListener { if (dialog === value) dialog = null; dismissed() }
        value.show()
        for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) value.getButton(which)?.isAllCaps = false
    }
    private fun showPreview() {
        val buffer = model.buffer ?: return
        if (model.loading || outputs.busy || draftPreview != null) return
        if (model.draft?.markdown != true) return
        buffer.select(EditSelection(editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0)))
        if (!model.previewOpen) model.editorScroll = editor.scrollY
        val revision = buffer.revision
        model.previewOpen = true
        draftPreview = DraftPreview(this, buffer.text, dark, model.previewScroll,
            { model.buffer === buffer && buffer.revision == revision }) { scroll ->
            model.previewScroll = scroll; draftPreview = null
            if (!destroying) model.previewOpen = false
        }.also { it.dialog.show() }
    }

    private fun captureSelection(): EditBuffer? = model.buffer?.also {
        it.select(EditSelection(editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0)))
    }
    private fun showCaret() {
        editor.requestFocus()
        editor.post { editor.bringPointIntoView(editor.selectionEnd.coerceAtLeast(0)) }
    }
    private fun indent(remove: Boolean) {
        val buffer = captureSelection() ?: return
        if (!EditorLines.indent(buffer, remove)) Toast.makeText(this, R.string.editor_limit, Toast.LENGTH_LONG).show()
        else { renderBuffer(); model.changed(); showCaret() }
    }
    private fun showGoLine() {
        val buffer = captureSelection() ?: return
        val input = EditText(this).apply { id = R.id.editor_line_input; inputType = InputType.TYPE_CLASS_NUMBER; hint = getString(R.string.line_number); contentDescription = hint; isSingleLine = true }
        val count = EditorLines.starts(buffer.text).size
        val value = AlertDialog.Builder(this).setTitle(R.string.go_to_line).setMessage(getString(R.string.editor_line_help, count))
            .setView(input).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.done, null).create()
        present(value)
        value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val line = input.text.toString().toIntOrNull()
            if (line == null || !EditorLines.goTo(buffer, line)) input.error = getString(R.string.editor_line_help, count)
            else { editor.setSelection(buffer.selection.end); model.saveSelection(buffer.selection); value.dismiss(); showCaret() }
        }
    }
    private fun compareDraft() {
        val buffer = captureSelection() ?: return
        val draft = model.draft ?: return
        val original = draft.originalText ?: return
        val token = DraftComparisonInbox.put(draft.name, original, buffer.text, draft.sourceUri, draft.originalEncoding.charsetName, draft.originalBom, draft.encoding.charsetName, draft.bom) ?: run {
            Toast.makeText(this, R.string.compare_limited, Toast.LENGTH_LONG).show(); return
        }
        model.editorScroll = editor.scrollY
        startActivity(Intent(this, ComparisonActivity::class.java).putExtra("draftToken", token))
    }

    private fun saveCopy() {
        val draft = model.draft ?: return
        if (model.buffer == null || model.loading || outputs.busy) return
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val encoding = Spinner(this).apply {
            id = R.id.editor_encoding; contentDescription = getString(R.string.editor_encoding)
            adapter = ArrayAdapter(this@EditorActivity, android.R.layout.simple_spinner_dropdown_item, TextEncoding.entries.map { it.charsetName })
            setSelection(TextEncoding.entries.indexOf(draft.encoding))
        }
        val bom = CheckBox(this).apply { id = R.id.editor_bom; setText(R.string.editor_bom); isChecked = draft.bom }
        encoding.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                bom.isEnabled = TextEncoding.entries[position] in setOf(TextEncoding.UTF8, TextEncoding.UTF16LE, TextEncoding.UTF16BE)
                if (!bom.isEnabled) bom.isChecked = false
            }
        }
        body.addView(TextView(this).apply { setText(R.string.editor_save_help) }); body.addView(encoding); body.addView(bom)
        val scroll = ScrollView(this).apply { addView(body) }
        present(AlertDialog.Builder(this).setTitle(R.string.save_copy).setView(scroll).setOnCancelListener { exitAfterSave = false }.setNegativeButton(R.string.cancel) { _, _ -> exitAfterSave = false }
            .setPositiveButton(R.string.choose_destination) { _, _ ->
                val selected = TextEncoding.entries[encoding.selectedItemPosition]
                fun begin() {
                    model.format(selected, bom.isChecked)
                    val value = model.draft ?: return
                    val name = OutputNames.copy(value.name, value.markdown)
                    val mime = if (value.markdown) "text/markdown" else "text/plain"
                    val intent = outputs.begin(OutputRequest(name, mime, model.buffer!!.text,
                        value.sourceUri, value.encoding, value.bom, value.id, model.buffer!!.revision))
                    try { picker.launch(intent) } catch (_: android.content.ActivityNotFoundException) { outputs.pickerUnavailable() } catch (_: SecurityException) { outputs.pickerUnavailable() }
                }
                if (selected != draft.encoding || bom.isChecked != draft.bom)
                    present(AlertDialog.Builder(this).setTitle(R.string.editor_conversion_title).setOnCancelListener { exitAfterSave = false }.setMessage(getString(R.string.editor_conversion, selected.charsetName))
                        .setNegativeButton(R.string.cancel) { _, _ -> exitAfterSave = false }.setPositiveButton(R.string.done) { _, _ -> begin() }.create())
                else begin()
            }.create())
    }
    private fun handleOutput(value: OutputStatus) {
        if (value.result == OutputResult.SAVED && value.draftId == model.draft?.id && model.buffer != null) {
            val key = "${value.draftId}:${value.revision}"
            if (model.handledOutput != key && !model.loading && value.revision == model.buffer?.revision) {
                model.saved(value) { refresh() }; return
            }
            if (model.loading) return
        }
        if (displayedOutput == value && outputDialog?.isShowing == true) return
        outputDialog?.dismiss(); outputDialog = null; displayedOutput = value
        outputDialog = OutputDialogs.create(this, value, outputs::cancel, retry = if (model.buffer != null) ({
            exitAfterSave = false
            outputs.acknowledge()
            saveCopy()
        }) else null) {
            outputs.acknowledge()
            if (exitAfterSave && !model.dirty && value.result == OutputResult.SAVED) finish() else exitAfterSave = false
        }?.also {
            if (value.result == OutputResult.SAVED && value.target != null) {
                val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
                body.addView(TextView(this).apply { text = getString(R.string.output_saved, value.name) + "\n\n" + value.target.toString() + "\n\n" + getString(R.string.editor_saved_access) })
                if (!model.dirty) body.addView(button(R.string.editor_open_saved) {
                    if (ReaderOpening.open(this, value.target)) { outputs.acknowledge(); finish() }
                }, LinearLayout.LayoutParams(-1, -2))
                it.setMessage(null); it.setView(ScrollView(this).apply { addView(body) })
            }
            it.show()
            for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) it.getButton(which)?.isAllCaps = false
        }
    }
    private fun leave() {
        if (model.loading || outputs.busy) return
        if (model.blockedByExisting || model.buffer == null) { finish(); return }
        if (model.dirty) {
            present(AlertDialog.Builder(this).setTitle(R.string.editor_unsaved).setMessage(R.string.editor_leave_help)
                .setNegativeButton(R.string.cancel, null).setNeutralButton(R.string.editor_discard) { _, _ -> model.discard { if (it) finish() } }
                .setPositiveButton(R.string.save_copy) { _, _ -> exitAfterSave = true; saveCopy() }.create())
        } else finish()
    }
    private fun showFind() {
        val buffer = model.buffer ?: return
        val state = model.findState; state.open = true
        buffer.select(EditSelection(editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val query = EditText(this).apply { id = R.id.editor_query; hint = getString(R.string.editor_find); isSingleLine = true; isSaveEnabled = false; filters = arrayOf(InputFilter.LengthFilter(EditBuffer.MAX_QUERY)); contentDescription = hint }
        val replacement = EditText(this).apply {
            id = R.id.editor_replacement; hint = getString(R.string.editor_replace_hint); isSaveEnabled = false; contentDescription = hint; maxLines = 4
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
                if (dest.length - (dend - dstart) + end - start > EditBuffer.MAX_REPLACEMENT) {
                    Toast.makeText(this@EditorActivity, R.string.editor_replace_limit, Toast.LENGTH_LONG).show(); dest.subSequence(dstart, dend)
                } else null
            })
        }
        val case = CheckBox(this).apply { setText(R.string.match_case) }
        val word = CheckBox(this).apply { setText(R.string.whole_word) }
        val count = TextView(this).apply { id = R.id.editor_match_count; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        query.setText(state.query); replacement.setText(state.replacement); case.isChecked = state.options.caseSensitive; word.isChecked = state.options.wholeWord
        var ranges = emptyList<IntRange>(); var selected = state.selected
        fun update() {
            val found = buffer.matches(query.text.toString(), LiteralOptions(case.isChecked, word.isChecked)); ranges = found.ranges
            if (selected >= ranges.size) selected = -1
            state.query = query.text.toString(); state.replacement = replacement.text.toString(); state.options = LiteralOptions(case.isChecked, word.isChecked); state.selected = selected
            count.text = getString(if (found.truncated) R.string.editor_matches_limited else R.string.editor_matches, ranges.size)
        }
        query.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { selected = -1; update() }; override fun afterTextChanged(s: Editable?) = Unit })
        replacement.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { state.replacement = s.toString() }; override fun afterTextChanged(s: Editable?) = Unit })
        case.setOnCheckedChangeListener { _, _ -> selected = -1; update() }; word.setOnCheckedChangeListener { _, _ -> selected = -1; update() }
        fun choose(delta: Int) {
            update(); if (ranges.isEmpty()) return
            selected = if (selected < 0) { if (delta < 0) ranges.lastIndex else 0 } else Math.floorMod(selected + delta, ranges.size); state.selected = selected
            val range = ranges[selected]; editor.setSelection(range.first, range.last + 1); buffer.select(EditSelection(range.first, range.last + 1))
            count.text = getString(R.string.editor_match_position, selected + 1, ranges.size)
        }
        body.addView(query); body.addView(replacement); body.addView(case); body.addView(word); body.addView(count)
        body.addView(button(R.string.previous_match) { choose(-1) }); body.addView(button(R.string.next_match) { choose(1) })
        body.addView(button(R.string.editor_replace) {
            update(); if (selected !in ranges.indices) { choose(1); return@button }
            val range = ranges[selected]
            if (buffer.replace(range.first, range.last + 1, editorInsertion(replacement.text, model.draft?.endings.orEmpty()))) {
                val after = buffer.selection.end
                renderBuffer(); model.changed(); update()
                val following = ranges.indexOfFirst { it.first >= after }.takeIf { it >= 0 } ?: 0
                selected = following - 1; choose(1)
            } else Toast.makeText(this, R.string.editor_limit, Toast.LENGTH_LONG).show()
        })
        body.addView(button(R.string.editor_replace_all) {
            val queryText = query.text.toString()
            val replaceText = editorInsertion(replacement.text, model.draft?.endings.orEmpty())
            val options = LiteralOptions(case.isChecked, word.isChecked)
            val total = buffer.countMatches(queryText, options)
            fun replace() {
                val changed = buffer.replaceAll(queryText, replaceText, options)
                if (changed == null) Toast.makeText(this, R.string.editor_limit, Toast.LENGTH_LONG).show()
                else { renderBuffer(); model.changed(); selected = -1; update(); count.text = getString(R.string.editor_replaced, changed) }
            }
            if (total > 20) {
                confirmation?.dismiss()
                confirmation = AlertDialog.Builder(this).setTitle(R.string.editor_replace_all).setMessage(getString(R.string.editor_replace_confirm, total))
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.editor_replace_all) { _, _ -> replace() }.show()
                    .also { confirmation -> confirmation.getButton(AlertDialog.BUTTON_POSITIVE).isAllCaps = false; confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).isAllCaps = false }
            } else replace()
        }.apply { id = R.id.editor_replace_all })
        update()
        present(AlertDialog.Builder(this).setTitle(R.string.editor_find_replace).setView(ScrollView(this).apply { addView(body) }).setNegativeButton(R.string.done, null).create()) {
            confirmation?.dismiss(); confirmation = null
            if (!isChangingConfigurations) state.open = false
        }
    }
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && draftPreview == null && dialog == null && !outputs.busy && !model.loading) {
            val action = if (event.isAltPressed) when (event.keyCode) {
                KeyEvent.KEYCODE_G -> goLine
                KeyEvent.KEYCODE_RIGHT_BRACKET -> indent
                KeyEvent.KEYCODE_LEFT_BRACKET -> unindent
                KeyEvent.KEYCODE_D -> compare
                else -> null
            } else when (event.keyCode) {
                KeyEvent.KEYCODE_Z -> if (event.isShiftPressed) redo else undo
                KeyEvent.KEYCODE_Y -> redo
                KeyEvent.KEYCODE_F -> find
                KeyEvent.KEYCODE_S -> save
                else -> null
            }
            if (action != null) { if (event.repeatCount == 0 && action.isEnabled) action.performClick(); return true }
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onStart() { super.onStart(); model.listener = ::refresh; outputs.listener = { refresh() }; refresh() }
    override fun onStop() { model.editorScroll = editor.scrollY; model.saveSelection(EditSelection(editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0))); model.listener = null; outputs.listener = null; super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("exitAfterSave", exitAfterSave); outState.putInt("selectionStart", editor.selectionStart.coerceAtLeast(0)); outState.putInt("selectionEnd", editor.selectionEnd.coerceAtLeast(0))
        if (outputs.status.phase == OutputPhase.PICKING) { outState.putBoolean("outputPending", true); outState.putString("outputName", outputs.status.name); outState.putString("outputSource", outputs.pendingSource()) }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { destroying = true; draftPreview?.dismiss(); confirmation?.dismiss(); dialog?.dismiss(); outputDialog?.dismiss(); super.onDestroy() }
}
