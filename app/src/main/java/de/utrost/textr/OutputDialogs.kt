package de.utrost.textr

import android.app.AlertDialog
import android.content.Context

object OutputDialogs {
    fun create(context: Context, status: OutputStatus, cancel: () -> Unit, acknowledge: () -> Unit): AlertDialog? {
        if (status.phase !in setOf(OutputPhase.WRITING, OutputPhase.COMPLETE)) return null
        val builder = AlertDialog.Builder(context).setTitle(R.string.save_copy)
        if (status.phase == OutputPhase.WRITING) return builder
            .setMessage(context.getString(R.string.output_writing, status.name)).setCancelable(false)
            .setNegativeButton(R.string.cancel) { _, _ -> cancel() }.create()
        val message = context.getString(when (status.result) {
            OutputResult.SAVED -> R.string.output_saved
            OutputResult.CANCELLED -> R.string.output_cancelled
            OutputResult.REFUSED -> R.string.output_refused
            OutputResult.INTERRUPTED -> R.string.output_interrupted
            else -> R.string.output_failed
        }, status.name) + if (status.partial) "\n\n" + context.getString(R.string.output_partial, status.target.toString()) else ""
        return builder.setMessage(message).setPositiveButton(R.string.done) { _, _ -> acknowledge() }
            .setOnCancelListener { acknowledge() }.create()
    }
}
