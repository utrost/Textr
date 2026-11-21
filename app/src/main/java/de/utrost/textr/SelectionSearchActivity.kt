package de.utrost.textr

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.lang.ref.WeakReference

/** Private native PROCESS_TEXT destination. No clipboard round trip or JavaScript bridge. */
class SelectionSearchActivity : Activity() {
    companion object {
        var reader = WeakReference<MainActivity>(null)
        var source = WeakReference<TextDocument>(null)
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (intent.action == Intent.ACTION_PROCESS_TEXT && callingPackage == packageName) {
            val target = reader.get()
            val expected = source.get()
            reader.clear(); source.clear()
            if (target != null && !target.isDestroyed && !target.isFinishing)
                target.findSelectedText(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT), expected)
            else Toast.makeText(this, R.string.selection_unavailable, Toast.LENGTH_LONG).show()
        }
        setResult(RESULT_CANCELED); finish()
    }
}
