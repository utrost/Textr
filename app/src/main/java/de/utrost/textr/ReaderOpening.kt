package de.utrost.textr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Same-UID handoff. Never invent URI grants that a write-only provider did not offer. */
object ReaderOpening {
    const val RETAIN_READ = "de.utrost.textr.RETAIN_EXISTING_READ"
    fun open(activity: Activity, uri: Uri): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri, activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(RETAIN_READ, true))
        true
    } catch (_: SecurityException) {
        Toast.makeText(activity, R.string.saved_copy_unavailable, Toast.LENGTH_LONG).show(); false
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(activity, R.string.saved_copy_unavailable, Toast.LENGTH_LONG).show(); false
    }
}
