package de.utrost.textr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** An opaque successful document URI, used only as the system picker's parent-location hint. */
class SaveLocation(context: Context) {
    private val preferences = context.getSharedPreferences("save_location", Context.MODE_PRIVATE)
    fun apply(intent: Intent): Intent = intent.apply {
        preferences.getString("uri", null)?.let(Uri::parse)?.takeIf { it.scheme == "content" }?.let {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
        }
    }
    fun saved(uri: Uri) { if (uri.scheme == "content") preferences.edit().putString("uri", uri.toString()).commit() }
    fun clear() { preferences.edit().clear().commit() }
}
