package de.utrost.textr

import java.util.concurrent.CancellationException

/** Rendering is optional; these budgets never truncate the readable source. */
object RenderPolicy {
    const val MAX_JSON_PREVIEW_BYTES = 1024 * 1024
    const val MAX_HIGHLIGHT_CHARS = 512 * 1024
    const val MAX_HIGHLIGHT_TOKENS = 10_000
    const val MAX_MARKDOWN_CHARS = 512 * 1024
    const val MAX_MARKDOWN_NODES = 20_000
    const val MAX_MARKDOWN_DEPTH = 64
    const val MAX_PREVIEW_CHARS = MAX_FILE_BYTES

    fun checkCancellation() {
        if (Thread.currentThread().isInterrupted) throw CancellationException()
    }
}

enum class PreviewNotice { NONE, UNAVAILABLE, MARKDOWN_LIMIT, JSON_LIMIT, HIGHLIGHT_LIMIT, CALENDAR_LIMIT, CSV_UNAVAILABLE }
