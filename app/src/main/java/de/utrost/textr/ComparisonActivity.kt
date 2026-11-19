package de.utrost.textr

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider

class ComparisonActivity : ReadingScreen() {
    private val model by lazy { ViewModelProvider(this)[ComparisonSession::class.java] }
    private lateinit var leftButton: Button
    private lateinit var rightButton: Button
    private lateinit var status: TextView
    private lateinit var modeLabel: TextView
    private lateinit var next: Button
    private lateinit var previous: Button
    private lateinit var cancel: Button
    private lateinit var list: ListView
    private var wide = false
    private var rows = emptyList<DiffDisplayRow>()
    private var displayed: LineDiffResult? = null
    private var restoreLeft = -1
    private var restoreRight = -1
    private var restoreTop = 0
    private var pickingLeft = false
    private var activeDialog: AlertDialog? = null
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { uri -> model.load(pickingLeft, ComparisonRef(uri.toString(), uri.lastPathSegment.orEmpty().take(256))) }
    }
    private val appearance by lazy { ReadingProfile.defaults(getSharedPreferences("reading", 0)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickingLeft = savedInstanceState?.getBoolean("pickingLeft") ?: false
        restoreLeft = savedInstanceState?.getInt("scrollLeft", -1) ?: -1
        restoreRight = savedInstanceState?.getInt("scrollRight", -1) ?: -1
        restoreTop = savedInstanceState?.getInt("scrollTop", 0) ?: 0
        build()
        if (!model.initialized) {
            model.initialized = true
            if (savedInstanceState != null) model.restore(savedInstanceState)
            else {
                val snapshot = ComparisonInbox.take(intent.getStringExtra("token"))
                if (snapshot != null) model.initial(snapshot)
                else intent.getStringExtra("uri")?.let { uri ->
                    model.load(true, ComparisonRef(uri, intent.getStringExtra("name").orEmpty(),
                        TextEncoding.entries.find { it.name == intent.getStringExtra("encoding") }, intent.getStringExtra("fingerprint")))
                }
            }
        }
    }
    private fun build() {
        wide = resources.configuration.screenWidthDp >= 900 && resources.configuration.screenHeightDp >= 480 &&
            resources.configuration.fontScale <= 1.3f && appearance.fontSize <= 22
        val compactControls = resources.configuration.screenHeightDp < 480 || resources.configuration.fontScale > 1.3f
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply { setText(R.string.compare_title); textSize = 20f; isAccessibilityHeading = true; setPadding(dp(16), dp(12), dp(16), dp(8)) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        val inputs = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        leftButton = button(R.string.compare_choose_left) { choose(true) }.apply { id = R.id.compare_left }
        rightButton = button(R.string.compare_choose_right) { choose(false) }.apply { id = R.id.compare_right }
        for (button in listOf(leftButton, rightButton)) inputs.addView(button,
            if (wide) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
        controls.addView(inputs)
        status = TextView(this).apply { id = R.id.compare_status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; setPadding(dp(4), dp(6), dp(4), dp(6)) }
        controls.addView(status)
        modeLabel = TextView(this).apply { id = R.id.compare_layout; text = getString(if (wide) R.string.compare_wide else R.string.compare_unified) + "\n" + getString(R.string.compare_legend) }
        controls.addView(modeLabel)
        val navigation = LinearLayout(this)
        previous = button(R.string.compare_previous) { navigate(-1) }.apply { id = R.id.compare_previous }
        next = button(R.string.compare_next) { navigate(1) }.apply { id = R.id.compare_next }
        navigation.addView(previous, LinearLayout.LayoutParams(0, -2, 1f)); navigation.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(navigation)
        cancel = button(R.string.cancel_loading) { model.cancel() }.apply { id = R.id.compare_cancel }
        controls.addView(cancel)
        list = ListView(this).apply { id = R.id.compare_rows; adapter = diffAdapter; isSaveEnabled = false }
        if (compactControls) {
            controls.addView(list, LinearLayout.LayoutParams(-1, dp(320)))
            root.addView(ScrollView(this).apply { addView(controls) }, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            root.addView(controls)
            root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        root.addView(button(R.string.back_to_files) { finish() }, LinearLayout.LayoutParams(-1, -2))
        install(root)
    }
    private fun label(left: Boolean): String {
        val snapshot = if (left) model.left else model.right
        val reference = if (left) model.leftRef else model.rightRef
        val name = snapshot?.document?.name ?: reference?.name ?: getString(R.string.compare_not_selected)
        val version = snapshot?.let { getString(R.string.compare_snapshot, it.document.encoding,
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.capturedAt))) }.orEmpty()
        return getString(R.string.compare_input, getString(if (left) R.string.compare_left_name else R.string.compare_right_name), name) + if (version.isNotEmpty()) "\n$version" else ""
    }
    private fun showDialog(dialog: AlertDialog) {
        activeDialog?.dismiss(); activeDialog = dialog
        dialog.setOnDismissListener { if (activeDialog === dialog) activeDialog = null }
        dialog.show(); dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isAllCaps = false
    }
    private fun choose(isLeft: Boolean) {
        if (model.busy) return
        val reference = if (isLeft) model.leftRef else model.rightRef
        val options = mutableListOf<Pair<String, () -> Unit>>()
        options.add(getString(R.string.compare_choose_file) to {
            pickingLeft = isLeft
            try { picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")) }
            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, R.string.compare_read_error, Toast.LENGTH_LONG).show() }
        })
        if (reference?.uri != null) {
            options.add(getString(R.string.reload) to { model.load(isLeft, reference) })
            options.add(getString(R.string.compare_open_reader) to {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reference.uri), this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            })
        }
        OpenDocuments(this).entries.filter { it.uri != null }.forEach { entry ->
            options.add(entry.name to { model.load(isLeft, ComparisonRef(entry.uri, entry.name, entry.reading.encoding)) })
        }
        showDialog(AlertDialog.Builder(this).setTitle(if (isLeft) R.string.compare_choose_left else R.string.compare_choose_right)
            .setItems(options.map { it.first }.toTypedArray()) { _, index -> options[index].second() }
            .setNegativeButton(R.string.cancel, null).create())
    }
    private fun refresh() {
        if (isDestroyed || isFinishing) return
        leftButton.text = label(true); rightButton.text = label(false)
        leftButton.isEnabled = !model.busy; rightButton.isEnabled = !model.busy
        cancel.visibility = if (model.busy) View.VISIBLE else View.GONE
        val result = model.result
        if (result !== displayed) {
            displayed = result; rows = result?.let { LineDiff.display(it, wide) }.orEmpty()
            diffAdapter.notifyDataSetChanged()
            if (result != null) list.post {
                if (model.changedSources) navigateToChange()
                else if (restoreLeft >= 0 || restoreRight >= 0) {
                    val position = rows.indexOfFirst { restoreLeft >= 0 && it.left == restoreLeft || restoreRight >= 0 && it.right == restoreRight }.coerceAtLeast(0)
                    list.setSelectionFromTop(position, restoreTop); restoreLeft = -1; restoreRight = -1
                } else navigateToChange()
            }
        }
        val changes = rows.mapNotNull { it.change }.distinct().size
        model.selectedChange = model.selectedChange.coerceIn(0, (changes - 1).coerceAtLeast(0))
        previous.isEnabled = changes > 0 && !model.busy; next.isEnabled = previous.isEnabled
        val message = when {
            model.busy -> getString(R.string.compare_loading)
            model.failure == ComparisonFailure.INPUT_LIMIT || result?.limit != null -> getString(R.string.compare_limited)
            model.failure == ComparisonFailure.READ -> getString(R.string.compare_read_error)
            model.failure == ComparisonFailure.CANCELLED -> getString(R.string.compare_cancelled)
            model.failure == ComparisonFailure.LOST_SHARED -> getString(R.string.compare_shared_lost)
            result?.identical == true -> getString(R.string.compare_identical)
            result != null -> getString(R.string.compare_change_count, model.selectedChange + 1, changes)
            else -> getString(R.string.compare_choose_help)
        }
        status.text = (if (model.changedSources) getString(R.string.compare_changed) + "\n" else "") + message
    }
    private fun navigate(delta: Int) {
        val count = rows.mapNotNull { it.change }.distinct().size
        if (count == 0 || model.busy) return
        model.selectedChange = Math.floorMod(model.selectedChange + delta, count)
        diffAdapter.notifyDataSetChanged(); refresh(); navigateToChange()
    }
    private fun navigateToChange() {
        val at = rows.indexOfFirst { it.change == model.selectedChange }
        if (at >= 0) list.setSelectionFromTop(at, 0)
    }
    private data class Panel(val root: LinearLayout, val heading: Button, val body: TextView)
    private data class Holder(val root: LinearLayout, val first: Panel, val second: Panel?)
    private fun panel() = LinearLayout(this).let { root ->
        root.orientation = LinearLayout.VERTICAL; root.setPadding(dp(4), dp(4), dp(4), dp(8))
        val heading = Button(this).apply { isAllCaps = false; textSize = 12f; minHeight = dp(48); isSaveEnabled = false }
        val body = TextView(this).apply {
            isSaveEnabled = false; textSize = appearance.fontSize.toFloat(); typeface = android.graphics.Typeface.MONOSPACE
            setLineSpacing(0f, appearance.lineSpacing / 100f); setTextIsSelectable(true); setTextColor(foreground())
        }
        root.addView(heading, LinearLayout.LayoutParams(-1, -2)); root.addView(body, LinearLayout.LayoutParams(-1, -2))
        Panel(root, heading, body)
    }
    private val diffAdapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder = convertView?.tag as? Holder ?: run {
                val root = LinearLayout(this@ComparisonActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(4), dp(8), dp(4)) }
                val first = panel(); val second = if (wide) panel() else null
                root.addView(first.root, LinearLayout.LayoutParams(0, -1, 1f))
                second?.let { root.addView(it.root, LinearLayout.LayoutParams(0, -1, 1f)) }
                Holder(root, first, second).also { root.tag = it }
            }
            val row = rows[position]
            fun bind(panel: Panel, isLeft: Boolean, lineIndex: Int?) {
                val source = if (isLeft) model.left else model.right
                val lines = if (isLeft) displayed?.left else displayed?.right
                val line = lineIndex?.let { lines?.getOrNull(it) }
                if (line == null || source == null) {
                    panel.heading.text = getString(R.string.compare_no_line); panel.heading.isEnabled = false; panel.body.text = ""
                    panel.root.setBackgroundColor(background()); return
                }
                val ending = source.document.text.substring(line.contentEnd, line.end).let { when (it) { "\r\n" -> "CRLF"; "\r" -> "CR"; "\n" -> "LF"; else -> getString(R.string.compare_no_ending) } }
                val prefix = if (row.change == null) "=" else if (isLeft) "−" else "+"
                val caption = "$prefix " + getString(R.string.compare_line, getString(if (isLeft) R.string.compare_left_name else R.string.compare_right_name), lineIndex!! + 1, ending)
                panel.heading.text = caption; panel.heading.contentDescription = "$caption. ${getString(R.string.compare_copy_line)}"; panel.heading.isEnabled = true
                panel.heading.setOnClickListener {
                    showDialog(AlertDialog.Builder(this@ComparisonActivity).setTitle(caption).setItems(arrayOf(getString(R.string.compare_copy_line))) { _, _ ->
                        val clip = ClipData.newPlainText(getString(R.string.compare_title), source.document.text.substring(line.start, line.end))
                        if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    }.setNegativeButton(R.string.cancel, null).create())
                }
                panel.body.text = source.document.text.substring(line.start, line.contentEnd)
                val color = if (row.change == null) background() else Color.parseColor(if (dark) { if (isLeft) "#492B2B" else "#1E3A2B" } else { if (isLeft) "#FBE7E7" else "#DFF2E5" })
                panel.root.setBackgroundColor(color)
            }
            if (wide) { bind(holder.first, true, row.left); bind(holder.second!!, false, row.right) }
            else bind(holder.first, row.left != null, row.left ?: row.right)
            holder.root.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(background()); if (row.change == model.selectedChange) setStroke(dp(2), foreground())
            }
            return holder.root
        }
    }
    // Activity keyboard dispatch is public; ComponentActivity repeats a library restriction.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && activeDialog == null && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
            if (event.repeatCount == 0) navigate(if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onStart() { super.onStart(); model.listener = ::refresh; refresh() }
    override fun onStop() { model.listener = null; super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        model.save(outState); outState.putBoolean("pickingLeft", pickingLeft)
        rows.getOrNull(list.firstVisiblePosition)?.let { outState.putInt("scrollLeft", it.left ?: -1); outState.putInt("scrollRight", it.right ?: -1) }
        outState.putInt("scrollTop", list.getChildAt(0)?.top ?: 0)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { activeDialog?.dismiss(); super.onDestroy() }
}
