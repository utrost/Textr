package de.utrost.textr

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

class CollectionsActivity : ReadingScreen() {
    private val store by lazy { ReadingCollections(this) }
    private var selected: String? = null
    private var replacingUri: String? = null
    private var dialog: AlertDialog? = null
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val old = replacingUri; replacingUri = null
        val uri = if (result.resultCode == RESULT_OK) result.data?.data else null
        val id = selected
        if (uri != null && old != null && id != null) {
            val member = store.entries().find { it.id == id }?.members?.find { it.uri == old }
            if (member != null) present(AlertDialog.Builder(this).setTitle(R.string.collection_reselect)
                .setMessage(getString(R.string.collection_reselect_confirm, member.name)).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.done) { _, _ ->
                    if (store.reconnect(id, old, CollectionMember(uri.toString(), uri.lastPathSegment.orEmpty())))
                        DocumentRepository(this).retainReadAccess(uri, result.data?.flags ?: 0)
                    else failure()
                    render()
                }.create())
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); store.reconcile(); selected = state?.getString("collection"); replacingUri = state?.getString("replacing")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (selected != null) { selected = null; render() } else finish() }
        })
        render()
    }
    private fun present(value: AlertDialog) {
        dialog?.dismiss(); dialog = value; value.show()
        for (button in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) value.getButton(button)?.isAllCaps = false
    }
    private fun failure() = Toast.makeText(this, R.string.collection_limit, Toast.LENGTH_LONG).show()
    private fun name(id: String? = null) {
        val current = store.entries().find { it.id == id }
        val input = EditText(this).apply { this.id = R.id.collection_name; isSingleLine = true; hint = getString(R.string.collection_name); contentDescription = hint; filters = arrayOf(InputFilter.LengthFilter(ReadingCollections.MAX_NAME)); setText(current?.name.orEmpty()) }
        val value = AlertDialog.Builder(this).setTitle(if (id == null) R.string.collection_create else R.string.collection_rename)
            .setView(input).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.done, null).create()
        present(value)
        value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val saved = if (id == null) store.create(input.text.toString()) != null else store.rename(id, input.text.toString())
            if (saved) { value.dismiss(); render() } else input.error = getString(R.string.collection_limit)
        }
    }
    private fun render() {
        val collection = store.entries().find { it.id == selected }
        if (selected != null && collection == null) selected = null
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        body.addView(TextView(this).apply { text = collection?.name ?: getString(R.string.collections); textSize = 20f; isAccessibilityHeading = true })
        body.addView(TextView(this).apply { setText(R.string.collection_help) })
        if (collection == null) {
            body.addView(button(R.string.collection_create) { name() }.apply { id = R.id.collection_create })
            store.entries().forEach { entry -> body.addView(Button(this).apply {
                text = entry.name; isAllCaps = false; minHeight = dp(48); setOnClickListener { selected = entry.id; render() }
            }) }
        } else {
            body.addView(button(R.string.collection_add) {
                val recent = DocumentRepository(this).recentFiles().filter { item -> collection.members.none { it.uri == item.uri } }
                if (recent.isEmpty()) Toast.makeText(this, R.string.collection_no_recent, Toast.LENGTH_LONG).show()
                else present(AlertDialog.Builder(this).setTitle(R.string.collection_add).setItems(recent.map { it.name }.toTypedArray()) { _, index ->
                    val item = recent[index]
                    if (!store.add(collection.id, CollectionMember(item.uri, item.name))) failure()
                    render()
                }.setNegativeButton(R.string.cancel, null).create())
            }.apply { id = R.id.collection_add })
            body.addView(button(R.string.collection_rename) { name(collection.id) })
            body.addView(button(R.string.collection_delete) {
                present(AlertDialog.Builder(this).setTitle(R.string.collection_delete).setMessage(R.string.collection_delete_help)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.collection_delete) { _, _ -> if (store.delete(collection.id)) selected = null else failure(); render() }.create())
            })
            collection.members.forEach { member -> body.addView(Button(this).apply {
                text = member.name; isAllCaps = false; minHeight = dp(48)
                setOnClickListener {
                    val options = arrayOf(getString(R.string.collection_open), getString(R.string.collection_reselect), getString(R.string.collection_remove))
                    present(AlertDialog.Builder(this@CollectionsActivity).setTitle(member.name).setItems(options) { _, index ->
                        when (index) {
                            0 -> ReaderOpening.open(this@CollectionsActivity, Uri.parse(member.uri))
                            1 -> {
                                replacingUri = member.uri
                                try { picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")) }
                                catch (_: android.content.ActivityNotFoundException) { replacingUri = null; failure() }
                                catch (_: SecurityException) { replacingUri = null; failure() }
                            }
                            2 -> { if (!store.remove(collection.id, member.uri)) failure(); render() }
                        }
                    }.setNegativeButton(R.string.cancel, null).create())
                }
            }) }
        }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button(R.string.done) { if (selected != null) { selected = null; render() } else finish() }.apply { id = R.id.collection_done }, LinearLayout.LayoutParams(-1, -2))
        install(root)
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("collection", selected); outState.putString("replacing", replacingUri); super.onSaveInstanceState(outState) }
    override fun onDestroy() { dialog?.dismiss(); super.onDestroy() }
}
