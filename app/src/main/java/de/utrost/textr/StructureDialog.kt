package de.utrost.textr

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** A virtualized native tree. Expanded IDs/scroll position are metadata, not copied source. */
class StructureDialog(context: Context, private val document: PreparedDocument, restored: Bundle?, private val dark: Boolean,
    private val jump: (StructureNode) -> Unit) : Dialog(context) {
    private val outline = document.structure!!
    private val expanded = restored?.getIntArray("expanded")?.filter { it in outline.nodes.indices }?.toMutableSet() ?: mutableSetOf(0)
    private var visible = emptyList<StructureNode>()
    private val list = ListView(context).apply { id = R.id.structure_tree }
    private var actionDialog: AlertDialog? = null
    private val parents = outline.nodes.map { it.parent }.filter { it >= 0 }.toSet()
    private val adapter = object : BaseAdapter() {
        override fun getCount() = visible.size
        override fun getItem(position: Int) = visible[position]
        override fun getItemId(position: Int) = visible[position].id.toLong()
        override fun hasStableIds() = true
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            ((convertView as? TextView) ?: TextView(context)).apply {
                val node = getItem(position)
                val state = if (node.id in parents) context.getString(if (node.id in expanded) R.string.structure_expanded else R.string.structure_collapsed) else ""
                val label = "${if (node.id in parents) { if (node.id in expanded) "− " else "+ " } else ""}${node.label} · ${typeName(node.type)}"
                text = label
                contentDescription = context.getString(R.string.structure_node_spoken, node.depth + 1, node.label, typeName(node.type), node.line, state)
                minHeight = dp(48)
                setPadding(dp(16 + node.depth.coerceAtMost(5) * 12), dp(10), dp(16), dp(10))
                setTextColor(if (isDark()) android.graphics.Color.rgb(227,233,229) else android.graphics.Color.rgb(32,41,35))
                textSize = 16f
            }
    }
    init {
        setTitle(R.string.structure_outline)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(if (isDark()) android.graphics.Color.rgb(23,28,26) else android.graphics.Color.rgb(250,250,247))
            addView(TextView(context).apply {
                setText(R.string.structure_outline); isAccessibilityHeading = true; textSize = 20f
                setPadding(dp(16), dp(12), dp(16), dp(8))
            })
            addView(TextView(context).apply {
                text = if (document.source.kind == FileKind.JSON_LINES) context.getString(if (outline.available) R.string.jsonl_help else R.string.jsonl_limit) else context.getString(if (outline.available) R.string.structure_help else R.string.structure_unavailable)
                setPadding(dp(16), 0, dp(16), dp(8))
            })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(Button(context).apply {
                id = android.R.id.button2; setText(R.string.done); isAllCaps = false; minHeight = dp(48)
                setOnClickListener { dismiss() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> actions(visible[position]) }
        refresh()
        setContentView(body)
        setOnShowListener {
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            list.setSelectionFromTop(restored?.getInt("position") ?: 0, restored?.getInt("top") ?: 0)
        }
    }
    fun readingState() = Bundle().apply {
        putIntArray("expanded", expanded.toIntArray())
        putInt("position", list.firstVisiblePosition)
        putInt("top", list.getChildAt(0)?.top ?: 0)
        putString("fingerprint", document.fingerprint)
    }
    override fun dismiss() { actionDialog?.dismiss(); actionDialog = null; super.dismiss() }
    private fun refresh() {
        val included = BooleanArray(outline.nodes.size)
        visible = outline.nodes.filter { node ->
            (node.parent < 0 || (included[node.parent] && node.parent in expanded)).also { included[node.id] = it }
        }
        adapter.notifyDataSetChanged()
    }
    private fun actions(node: StructureNode) {
        val options = mutableListOf<Pair<Int, () -> Unit>>()
        if (node.id in parents) options.add((if (node.id in expanded) R.string.structure_collapse else R.string.structure_expand) to {
            if (!expanded.remove(node.id)) expanded.add(node.id)
            val position = list.firstVisiblePosition; val top = list.getChildAt(0)?.top ?: 0
            refresh(); list.setSelectionFromTop(position, top)
        })
        options.add(R.string.structure_jump to { dismiss(); jump(node) })
        options.add(R.string.structure_copy_path to { copy(node.path) })
        if (node.end - node.start <= StructureParser.MAX_COPY_CHARS)
            options.add(R.string.structure_copy_value to { copy(document.source.text.substring(node.start, node.end)) })
        else options.add(R.string.structure_copy_limit to { Toast.makeText(context, R.string.structure_copy_limit, Toast.LENGTH_LONG).show() })
        actionDialog = AlertDialog.Builder(context).setTitle(node.label)
            .setItems(options.map { context.getString(it.first) }.toTypedArray()) { _, index -> options[index].second() }
            .setNegativeButton(R.string.cancel, null).create().also { it.show(); it.getButton(AlertDialog.BUTTON_NEGATIVE).isAllCaps = false }
    }
    private fun copy(value: String) {
        val clip = ClipData.newPlainText(context.getString(R.string.structure_outline), value)
        if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        Toast.makeText(context, R.string.structure_copied, Toast.LENGTH_SHORT).show()
    }
    private fun typeName(type: String) = context.getString(when (type) {
        "object" -> R.string.structure_object; "array" -> R.string.structure_array; "string" -> R.string.structure_string
        "number" -> R.string.structure_number; "boolean" -> R.string.structure_boolean; "null" -> R.string.structure_null
        "records" -> R.string.jsonl_records; "record" -> R.string.jsonl_record; "invalid_record" -> R.string.jsonl_invalid; "blank_record" -> R.string.jsonl_blank
        "attribute" -> R.string.structure_attribute; else -> R.string.structure_element
    })
    private fun isDark() = dark
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
